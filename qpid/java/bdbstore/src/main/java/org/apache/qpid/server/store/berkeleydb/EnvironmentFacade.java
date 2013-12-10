/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.store.berkeleydb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.store.StoreFuture;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

public interface EnvironmentFacade
{
    @SuppressWarnings("serial")
    final Map<String, String> ENVCONFIG_DEFAULTS = Collections.unmodifiableMap(new HashMap<String, String>()
    {{
        put(EnvironmentConfig.LOCK_N_LOCK_TABLES, "7");
        // Turn off stats generation - feature introduced (and on by default) from BDB JE 5.0.84
        put(EnvironmentConfig.STATS_COLLECT, "false");
    }});

    Environment getEnvironment();

    StoreFuture commit(com.sleepycat.je.Transaction tx, boolean syncCommit) throws AMQStoreException;

    AMQStoreException handleDatabaseException(String contextMessage, DatabaseException e);

    void openDatabases(String[] databaseNames, DatabaseConfig dbConfig) throws AMQStoreException;

    void close();

    Database getOpenDatabase(String name);
}
