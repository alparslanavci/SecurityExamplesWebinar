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

import com.hazelcast.map.impl.MapService;
import com.hazelcast.security.Credentials;
import com.hazelcast.security.Parameters;
import com.hazelcast.security.SecurityInterceptor;

import java.security.AccessControlException;

public class SimpleSecurityInterceptor implements SecurityInterceptor {

    public void before(Credentials credentials, String objectType, String objectName, String methodName, Parameters parameters)
            throws AccessControlException {
        if (objectType.equals(MapService.SERVICE_NAME)) {
            if (objectName.equals("map")) {
                if (methodName.equals("put")) {
                    if (parameters.get(0).equals("key")) {
                        if (parameters.get(1).equals("value")) {
                            throw new AccessControlException("You cannot insert `key`-`value` as an entry");
                        }
                    }
                }
            }
        }
    }

    public void after(Credentials credentials, String objectType, String objectName, String methodName, Parameters parameters) {
        System.err.println("fatal " + objectType + ", " + objectName + ", " + methodName + ", " + parameters);
    }
}
