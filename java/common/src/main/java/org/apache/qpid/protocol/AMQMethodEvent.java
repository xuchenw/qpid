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
package org.apache.qpid.protocol;

import org.apache.qpid.framing.AMQMethodBody;

/**
 * An event that is passed to AMQMethodListeners describing a particular method.
 * It supplies the:
 * <ul><li>channel id</li>
 * <li>protocol method</li>
 * to listeners. This means that listeners do not need to be stateful.
 *
 * In the StateAwareMethodListener, other useful objects such as the protocol session
 * are made available.
 * 
 */
public class AMQMethodEvent<M extends AMQMethodBody>
{
    private final M _method;
    private final int _channelId;
    private final long _requestId;

    public AMQMethodEvent(int channelId, M method, long requestId)
    {
        _channelId = channelId;
        _method = method;
        _requestId = requestId;
    }

    public M getMethod()
    {
        return _method;
    }

    public int getChannelId()
    {
        return _channelId;
    }

    public long getRequestId()
    {
        return _requestId;
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder("Method event: \n");
        buf.append("Channel id: \n").append(_channelId);
        buf.append("Method: \n").append(_method);
        buf.append("Request Id: ").append(_requestId);
        return buf.toString();
    }
}
