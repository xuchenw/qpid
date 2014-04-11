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
package org.apache.qpid.server.model.port;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;

@ManagedObject( category = false, type = "RMI")
public class RmiPort extends AbstractPort<RmiPort>
{
    public RmiPort(final UUID id,
                   final Broker<?> broker,
                   final Map<String, Object> attributes,
                   final TaskExecutor taskExecutor)
    {
        super(id, broker, attributes, taskExecutor);
    }

    @Override
    public void validate()
    {
        super.validate();

        validateOnlyOneInstance();

        if (getTransports().contains(Transport.SSL))
        {
            throw new IllegalConfigurationException("Can't create RMI registry port which requires SSL");
        }

    }

    @Override
    protected Set<Protocol> getDefaultProtocols()
    {
        return Collections.singleton(Protocol.RMI);
    }
}
