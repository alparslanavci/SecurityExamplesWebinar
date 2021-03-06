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

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public class JCachePermissionsClient {

    public static void main(String[] args) throws URISyntaxException {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setLicenseKey("LicenceKey");
        clientConfig.setInstanceName( "my-named-hazelcast-instance" );
        clientConfig.setCredentials(new SimpleCredentials("webinar"));
        HazelcastInstance instance = HazelcastClient.newHazelcastClient(clientConfig);

        CachingProvider cachingProvider = Caching.getCachingProvider("com.hazelcast.client.cache.impl.HazelcastClientCachingProvider");

        Properties properties = new Properties();
        properties.setProperty( HazelcastCachingProvider.HAZELCAST_INSTANCE_NAME,
                "my-named-hazelcast-instance" );

        URI cacheManagerName = new URI( "my-cache-manager" );

        CacheManager cacheManager = cachingProvider.getCacheManager(cacheManagerName, null, properties);

        CompleteConfiguration<String, String> config =
                new MutableConfiguration<String, String>()
                        .setTypes( String.class, String.class );

        Cache<String, String> cache = cacheManager.createCache( "example", config );

        cache.put( "world", "Hello World" );

        //read should throw exception since no permissions defined in the server
        cache.get("world");
    }
}
