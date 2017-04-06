/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.PermissionConfig;
import com.hazelcast.config.PermissionPolicyConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.security.permission.ActionConstants;

import java.util.Properties;

public class JCachePermissionsMember {

    public static void main(String[] args) {
        Config config = new ClasspathXmlConfig("hazelcast.xml");
        PermissionPolicyConfig policyConfig = new PermissionPolicyConfig();
        policyConfig.setClassName("JCachePermissionsPolicy");

        PermissionConfig permissionConfig = new PermissionConfig();
        permissionConfig.setName("/hz/my-cache-manager/example");
        permissionConfig.addAction(ActionConstants.ACTION_CREATE);
        permissionConfig.addAction(ActionConstants.ACTION_PUT);
//        permissionConfig.addEndpoint("endpoint");
        permissionConfig.setPrincipal("webinar");

        Properties properties = new Properties();
        properties.put("key", permissionConfig);
        policyConfig.setProperties(properties);
        config.getSecurityConfig().setClientPolicyConfig(policyConfig);
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        instance.getMap("map");
    }
}
