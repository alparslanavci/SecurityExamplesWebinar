import com.hazelcast.config.Config;
import com.hazelcast.config.ConfigPatternMatcher;
import com.hazelcast.config.PermissionConfig;
import com.hazelcast.config.SecurityConfig;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.security.ClusterPrincipal;
import com.hazelcast.security.IPermissionPolicy;
import com.hazelcast.security.impl.DefaultPermissionPolicy;
import com.hazelcast.security.permission.*;

import javax.security.auth.Subject;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import static com.hazelcast.security.SecurityUtil.addressMatches;
import static com.hazelcast.security.SecurityUtil.createPermission;
import static java.lang.Thread.currentThread;

public class JCachePermissionsPolicy implements IPermissionPolicy {
    private static final ILogger LOGGER = Logger.getLogger(DefaultPermissionPolicy.class.getName());
    private static final PermissionCollection DENY_ALL = new DenyAllPermissionCollection();
    private static final PermissionCollection ALLOW_ALL = new AllPermissions.AllPermissionsCollection(true);

    // configured permissions
    final ConcurrentMap<PrincipalKey, PermissionCollection> configPermissions
            = new ConcurrentHashMap<PrincipalKey, PermissionCollection>();

    // principal permissions
    final ConcurrentMap<String, PrincipalPermissionsHolder> principalPermissions
            = new ConcurrentHashMap<String, PrincipalPermissionsHolder>();

    volatile ConfigPatternMatcher configPatternMatcher;

    public void configure(Config config, Properties properties) {
        LOGGER.log(Level.FINEST, "Configuring and initializing policy.");
        configPatternMatcher = config.getConfigPatternMatcher();

        SecurityConfig securityConfig = config.getSecurityConfig();
        final Set<PermissionConfig> permissionConfigs = securityConfig.getClientPermissionConfigs();
        for (PermissionConfig permCfg : permissionConfigs) {
            final ClusterPermission permission = createPermission(permCfg);
            handlePermission(permCfg, permission);

        }

        //all properties are assumed as CachePermissions
        for (Object o : properties.values()) {
            PermissionConfig permCfg = (PermissionConfig)o;
            final Set<String> actionSet = permCfg.getActions();
            final String[] actions = actionSet.toArray(new String[actionSet.size()]);
            CachePermission permission = new CachePermission(permCfg.getName(), actions);
            handlePermission(permCfg, permission);
        }
    }

    private void handlePermission(PermissionConfig permCfg, ClusterPermission permission) {
        // allow all principals
        final String principal = permCfg.getPrincipal() != null ? permCfg.getPrincipal() : "*";

        final Set<String> endpoints = permCfg.getEndpoints();
        if (endpoints.isEmpty()) {
            // allow all endpoints
            endpoints.add("*.*.*.*");
        }

        PermissionCollection coll;
        for (final String endpoint : endpoints) {
            final PrincipalKey key = new PrincipalKey(principal, endpoint);
            coll = configPermissions.get(key);
            if (coll == null) {
                coll = new ClusterPermissionCollection();
                configPermissions.put(key, coll);
            }
            coll.add(permission);
        }
    }

    public PermissionCollection getPermissions(Subject subject, Class<? extends Permission> type) {
        final ClusterPrincipal principal = getPrincipal(subject);
        if (principal == null) {
            return DENY_ALL;
        }

        ensurePrincipalPermissions(principal);
        final PrincipalPermissionsHolder permissionsHolder = principalPermissions.get(principal.getName());
        if (!permissionsHolder.prepared) {
            try {
                synchronized (permissionsHolder) {
                    while (!permissionsHolder.prepared) {
                        permissionsHolder.wait();
                    }
                }
            } catch (InterruptedException ignored) {
                currentThread().interrupt();
                throw new HazelcastException("Interrupted while waiting for the permissions holder to get prepared");
            }
        }

        if (permissionsHolder.hasAllPermissions) {
            return ALLOW_ALL;
        }
        PermissionCollection coll = permissionsHolder.permissions.get(type);
        if (coll == null) {
            coll = DENY_ALL;
            permissionsHolder.permissions.putIfAbsent(type, coll);
        }
        return coll;
    }

    private ClusterPrincipal getPrincipal(Subject subject) {
        final Set<Principal> principals = subject.getPrincipals();
        for (Principal p : principals) {
            if (p instanceof ClusterPrincipal) {
                return (ClusterPrincipal) p;
            }
        }
        return null;
    }

    @SuppressWarnings("checkstyle:npathcomplexity")
    private void ensurePrincipalPermissions(ClusterPrincipal principal) {
        if (principal == null) {
            return;
        }
        final String fullName = principal.getName();
        if (principalPermissions.containsKey(fullName)) {
            return;
        }
        final PrincipalPermissionsHolder permissionsHolder = new PrincipalPermissionsHolder();
        if (principalPermissions.putIfAbsent(fullName, permissionsHolder) != null) {
            return;
        }

        final String endpoint = principal.getEndpoint();
        final String principalName = principal.getPrincipal();
        try {
            LOGGER.log(Level.FINEST, "Preparing permissions for: " + fullName);
            final ClusterPermissionCollection allMatchingPermissionsCollection = new ClusterPermissionCollection();
            for (Map.Entry<PrincipalKey, PermissionCollection> e : configPermissions.entrySet()) {
                final PrincipalKey key = e.getKey();
                if (nameMatches(principalName, key.principal) && addressMatches(endpoint, key.endpoint)) {
                    allMatchingPermissionsCollection.add(e.getValue());
                }
            }
            final Set<Permission> allMatchingPermissions = allMatchingPermissionsCollection.getPermissions();
            for (Permission perm : allMatchingPermissions) {
                if (perm instanceof AllPermissions) {
                    permissionsHolder.permissions.clear();
                    permissionsHolder.hasAllPermissions = true;
                    LOGGER.log(Level.FINEST, "Granted all-permissions to: " + fullName);
                    return;
                }
                Class<? extends Permission> type = perm.getClass();
                ClusterPermissionCollection coll = (ClusterPermissionCollection) permissionsHolder.permissions.get(type);
                if (coll == null) {
                    coll = new ClusterPermissionCollection(type);
                    permissionsHolder.permissions.put(type, coll);
                }
                coll.add(perm);
            }

            LOGGER.log(Level.FINEST, "Compacting permissions for: " + fullName);
            final Collection<PermissionCollection> principalCollections = permissionsHolder.permissions.values();
            for (PermissionCollection coll : principalCollections) {
                ((ClusterPermissionCollection) coll).compact();
            }

        } finally {
            synchronized (permissionsHolder) {
                permissionsHolder.prepared = true;
                permissionsHolder.notifyAll();
            }
        }
    }

    private boolean nameMatches(String name, String pattern) {
        if (name.equals(pattern)) {
            return true;
        }
        Set<String> patterns = Collections.singleton(pattern);
        return configPatternMatcher.matches(patterns, name) != null;
    }

    private static class PrincipalKey {
        final String principal;
        final String endpoint;

        PrincipalKey(String principal, String endpoint) {
            this.principal = principal;
            this.endpoint = endpoint;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((endpoint == null) ? 0 : endpoint.hashCode());
            result = prime * result + ((principal == null) ? 0 : principal.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final PrincipalKey other = (PrincipalKey) obj;
            return (endpoint != null ? endpoint.equals(other.endpoint) : other.endpoint == null)
                    && (principal != null ? principal.equals(other.principal) : other.principal == null);
        }
    }

    private static class PrincipalPermissionsHolder {
        volatile boolean prepared;
        boolean hasAllPermissions;
        final ConcurrentMap<Class<? extends Permission>, PermissionCollection> permissions =
                new ConcurrentHashMap<Class<? extends Permission>, PermissionCollection>();
    }

    public void destroy() {
        principalPermissions.clear();
        configPermissions.clear();
    }
}
