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
package org.apache.qpid.test.unit.client.temporaryqueue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.TestNetworkConnection;
import org.apache.qpid.transport.TestNetworkTransport;
import org.apache.qpid.transport.network.Transport;

public class TemporaryQueueNameTest extends QpidBrokerTestCase
{
    private class QueueNameSession extends AMQProtocolSession
    {
        public QueueNameSession(AMQProtocolHandler protocolHandler, AMQConnection connection)
        {
            super(protocolHandler, connection);
        }

        public AMQShortString genQueueName()
        {
            return generateQueueName();
        }
    }
 
    private class QueueNameProtocolHandler extends AMQProtocolHandler
    {
        public QueueNameProtocolHandler(AMQConnection connection)
        {
            super(connection);
        }

        @Override
        public SocketAddress getLocalAddress()
        {
            return _transport.getAddress();
        }
    }

    private QueueNameSession _queueNameSession;
    private TestNetworkTransport _transport;

    protected void setUp() throws Exception
    {
        super.setUp();
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        QueueNameProtocolHandler queueNameHandler = new QueueNameProtocolHandler(con);
        _queueNameSession = new QueueNameSession(queueNameHandler , con);
        _transport = new TestNetworkTransport();
    }
    
    public void testTemporaryQueueWildcard() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(1234), "tmp_0_0_0_0_0_0_0_0_1234_");
    }
    
    public void testTemporaryQueueLocalhostAddr() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1234), "tmp_127_0_0_1_1234_");
    }
    
    public void testTemporaryQueueLocalhostName() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("localhost"), 1234), "tmp_localhost_127_0_0_1_1234_");
    }
    
    public void testTemporaryQueueInet4() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("192.168.1.2"), 1234), "tmp_192_168_1_2_1234_");
    }
    
    public void testTemporaryQueueInet6() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("1080:0:0:0:8:800:200C:417A"), 1234), "tmp_1080_0_0_0_8_800_200c_417a_1234_");
    }
    
    public void testTemporaryQueuePipe() throws UnknownHostException
    {
        checkTempQueueName(new VmPipeAddress(1), "tmp_vm_1_");
    }
    
    private void checkTempQueueName(SocketAddress address, String expectedQueueName)
    {
        _transport.setAddress(address);
        String queueName = _queueNameSession.genQueueName().asString();
        assertTrue("Wrong queue name: " + queueName, queueName.startsWith(expectedQueueName));
    }
}
