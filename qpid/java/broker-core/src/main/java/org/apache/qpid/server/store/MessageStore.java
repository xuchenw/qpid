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
package org.apache.qpid.server.store;

import java.util.Map;

/**
 * MessageStore defines the interface to a storage area, which can be used to preserve the state of messages.
 *
 */
public interface MessageStore
{
    String STORE_TYPE                           = "storeType";
    String STORE_PATH                           = "storePath";
    String UNDERFULL_SIZE                       = "storeUnderfullSize";
    String OVERFULL_SIZE                        = "storeOverfullSize";

    /**
     * Called after instantiation in order to open and initialize the message store. A particular implementation can define
     * whatever parameters it wants.
     * @param virtualHostName virtual host name
     * @param messageStoreSettings store settings
     */
    void openMessageStore(String virtualHostName, Map<String, Object> messageStoreSettings);

    /**
     * Called after opening to recover messages and transactions with given recovery handlers
     *
     * @param messageRecoveryHandler
     * @param transactionLogRecoveryHandler
     */
    void recoverMessageStore(MessageStoreRecoveryHandler messageRecoveryHandler, TransactionLogRecoveryHandler transactionLogRecoveryHandler);

    public <T extends StorableMessageMetaData> StoredMessage<T> addMessage(T metaData);


    /**
     * Is this store capable of persisting the data
     *
     * @return true if this store is capable of persisting data
     */
    boolean isPersistent();

    Transaction newTransaction();

    /**
     * Called to close and cleanup any resources used by the message store.
     */
    void closeMessageStore();

    void addEventListener(EventListener eventListener, Event... events);

    String getStoreLocation();

    String getStoreType();

    void onDelete();
}
