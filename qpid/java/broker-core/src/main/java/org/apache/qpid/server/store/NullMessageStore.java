/*
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
package org.apache.qpid.server.store;

import java.util.Map;
import java.util.UUID;

public abstract class NullMessageStore implements MessageStore, DurableConfigurationStore
{
    @Override
    public void openConfigurationStore(String virtualHostName, Map<String, Object> storeSettings)
    {
    }

    @Override
    public void recoverConfigurationStore(ConfigurationRecoveryHandler recoveryHandler)
    {
    }

    @Override
    public void update(UUID id, String type, Map<String, Object> attributes)
    {
    }

    @Override
    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records)
    {
    }


    @Override
    public void remove(UUID id, String type)
    {
    }

    @Override
    public UUID[] removeConfiguredObjects(final UUID... objects)
    {
        return objects;
    }

    @Override
    public void create(UUID id, String type, Map<String, Object> attributes)
    {
    }

    @Override
    public void openMessageStore(String virtualHostName, Map<String, Object> messageStoreSettings)
    {
    }

    @Override
    public void closeMessageStore()
    {
    }

    @Override
    public void closeConfigurationStore()
    {
    }

    @Override
    public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(T metaData)
    {
        return null;
    }

    @Override
    public boolean isPersistent()
    {
        return false;
    }

    @Override
    public Transaction newTransaction()
    {
        return null;
    }

    @Override
    public void recoverMessageStore(MessageStoreRecoveryHandler messageRecoveryHandler, TransactionLogRecoveryHandler transactionLogRecoveryHandler)
    {
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
    }

    @Override
    public String getStoreLocation()
    {
        return null;
    }

    @Override
    public void onDelete()
    {
    }
}
