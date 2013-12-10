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


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.apache.commons.configuration.PropertiesConfiguration;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.amqp_8_0.BasicConsumeBodyImpl;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.protocol.v0_8.MessageMetaData;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQPriorityQueue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.ConflationQueue;
import org.apache.qpid.server.protocol.v0_8.IncomingMessage;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This tests the MessageStores by using the available interfaces.
 *
 * For persistent stores, it validates that Exchanges, Queues, Bindings and
 * Messages are persisted and recovered correctly.
 */
public class MessageStoreTest extends QpidTestCase
{
    public static final int DEFAULT_PRIORTY_LEVEL = 5;
    public static final String SELECTOR_VALUE = "Test = 'MST'";
    public static final String LVQ_KEY = "MST-LVQ-KEY";

    private String nonDurableExchangeName = "MST-NonDurableDirectExchange";
    private String directExchangeName = "MST-DirectExchange";
    private String topicExchangeName = "MST-TopicExchange";

    private String durablePriorityTopicQueueName = "MST-PriorityTopicQueue-Durable";
    private String durableTopicQueueName = "MST-TopicQueue-Durable";
    private String priorityTopicQueueName = "MST-PriorityTopicQueue";
    private String topicQueueName = "MST-TopicQueue";

    private String durableExclusiveQueueName = "MST-Queue-Durable-Exclusive";
    private String durablePriorityQueueName = "MST-PriorityQueue-Durable";
    private String durableLastValueQueueName = "MST-LastValueQueue-Durable";
    private String durableQueueName = "MST-Queue-Durable";
    private String priorityQueueName = "MST-PriorityQueue";
    private String queueName = "MST-Queue";

    private String directRouting = "MST-direct";
    private String topicRouting = "MST-topic";

    private String queueOwner = "MST";

    private PropertiesConfiguration _config;

    private VirtualHost _virtualHost;
    private org.apache.qpid.server.model.VirtualHost _virtualHostModel;
    private Broker _broker;
    private String _storePath;

    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();

        _storePath = System.getProperty("QPID_WORK") + File.separator + getName();

        _config = new PropertiesConfiguration();
        _config.addProperty("store.class", getTestProfileMessageStoreClassName());
        _config.addProperty("store.environment-path", _storePath);
        _virtualHostModel = mock(org.apache.qpid.server.model.VirtualHost.class);
        when(_virtualHostModel.getAttribute(eq(org.apache.qpid.server.model.VirtualHost.STORE_PATH))).thenReturn(_storePath);



        cleanup(new File(_storePath));

        _broker = BrokerTestHelper.createBrokerMock();

        reloadVirtualHost();
    }

    protected String getStorePath()
    {
        return _storePath;
    }

    protected org.apache.qpid.server.model.VirtualHost getVirtualHostModel()
    {
        return _virtualHostModel;
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHost != null)
            {
                _virtualHost.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public PropertiesConfiguration getConfig()
    {
        return _config;
    }

    protected void reloadVirtualHost()
    {
        VirtualHost original = getVirtualHost();

        if (getVirtualHost() != null)
        {
            try
            {
                getVirtualHost().close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }

        try
        {
            _virtualHost = BrokerTestHelper.createVirtualHost(new VirtualHostConfiguration(getClass().getName(), _config, _broker),null,getVirtualHostModel());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }

        assertTrue("Virtualhost has not changed, reload was not successful", original != getVirtualHost());
    }

    /**
     * Old MessageStoreTest segment which runs against both persistent and non-persistent stores
     * creating queues, exchanges and bindings and then verifying message delivery to them.
     */
    public void testQueueExchangeAndBindingCreation() throws Exception
    {
        assertEquals("Should not be any existing queues", 0,  getVirtualHost().getQueues().size());

        createAllQueues();
        createAllTopicQueues();

        //Register Non-Durable DirectExchange
        Exchange nonDurableExchange = createExchange(DirectExchange.TYPE, nonDurableExchangeName, false);
        bindAllQueuesToExchange(nonDurableExchange, directRouting);

        //Register DirectExchange
        Exchange directExchange = createExchange(DirectExchange.TYPE, directExchangeName, true);
        bindAllQueuesToExchange(directExchange, directRouting);

        //Register TopicExchange
        Exchange topicExchange = createExchange(TopicExchange.TYPE, topicExchangeName, true);
        bindAllTopicQueuesToExchange(topicExchange, topicRouting);

        //Send Message To NonDurable direct Exchange = persistent
        sendMessageOnExchange(nonDurableExchange, directRouting, true);
        // and non-persistent
        sendMessageOnExchange(nonDurableExchange, directRouting, false);

        //Send Message To direct Exchange = persistent
        sendMessageOnExchange(directExchange, directRouting, true);
        // and non-persistent
        sendMessageOnExchange(directExchange, directRouting, false);

        //Send Message To topic Exchange = persistent
        sendMessageOnExchange(topicExchange, topicRouting, true);
        // and non-persistent
        sendMessageOnExchange(topicExchange, topicRouting, false);

        //Ensure all the Queues have four messages (one transient, one persistent) x 2 exchange routings
        validateMessageOnQueues(4, true);
        //Ensure all the topics have two messages (one transient, one persistent)
        validateMessageOnTopics(2, true);

        assertEquals("Not all queues correctly registered",
                10, getVirtualHost().getQueues().size());
    }

    /**
     * Tests message persistence by running the testQueueExchangeAndBindingCreation() method above
     * before reloading the virtual host and ensuring that the persistent messages were restored.
     *
     * More specific testing of message persistence is left to store-specific unit testing.
     */
    public void testMessagePersistence() throws Exception
    {
        testQueueExchangeAndBindingCreation();

        reloadVirtualHost();

        //Validate durable queues and subscriptions still have the persistent messages
        validateMessageOnQueues(2, false);
        validateMessageOnTopics(1, false);
    }

    /**
     * Tests message removal by running the testMessagePersistence() method above before
     * clearing the queues, reloading the virtual host, and ensuring that the persistent
     * messages were removed from the queues.
     */
    public void testMessageRemoval() throws Exception
    {
        testMessagePersistence();

        assertEquals("Incorrect number of queues registered after recovery",
                6,  getVirtualHost().getQueues().size());

        //clear the queue
        _virtualHost.getQueue(durableQueueName).clearQueue();

        //check the messages are gone
        validateMessageOnQueue(durableQueueName, 0);

        //reload and verify messages arent restored
        reloadVirtualHost();

        validateMessageOnQueue(durableQueueName, 0);
    }

    /**
     * Tests queue persistence by creating a selection of queues with differing properties, both
     * durable and non durable, and ensuring that following the recovery process the correct queues
     * are present and any property manipulations (eg queue exclusivity) are correctly recovered.
     */
    public void testQueuePersistence() throws Exception
    {
        assertEquals("Should not be any existing queues",
                0, getVirtualHost().getQueues().size());

        //create durable and non durable queues/topics
        createAllQueues();
        createAllTopicQueues();

        //reload the virtual host, prompting recovery of the queues/topics
        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after recovery",
                6,  getVirtualHost().getQueues().size());

        //Validate the non-Durable Queues were not recovered.
        assertNull("Non-Durable queue still registered:" + priorityQueueName,
                getVirtualHost().getQueue(priorityQueueName));
        assertNull("Non-Durable queue still registered:" + queueName,
                getVirtualHost().getQueue(queueName));
        assertNull("Non-Durable queue still registered:" + priorityTopicQueueName,
                getVirtualHost().getQueue(priorityTopicQueueName));
        assertNull("Non-Durable queue still registered:" + topicQueueName,
                getVirtualHost().getQueue(topicQueueName));

        //Validate normally expected properties of Queues/Topics
        validateDurableQueueProperties();

        //Update the durable exclusive queue's exclusivity
        setQueueExclusivity(false);
        validateQueueExclusivityProperty(false);
    }

    /**
     * Tests queue removal by creating a durable queue, verifying it recovers, and
     * then removing it from the store, and ensuring that following the second reload
     * process it is not recovered.
     */
    public void testDurableQueueRemoval() throws Exception
    {
        //Register Durable Queue
        createQueue(durableQueueName, false, true, false, false);

        assertEquals("Incorrect number of queues registered before recovery",
                1,  getVirtualHost().getQueues().size());

        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after first recovery",
                1,  getVirtualHost().getQueues().size());

        //test that removing the queue means it is not recovered next time
        final AMQQueue queue = getVirtualHost().getQueue(durableQueueName);
        DurableConfigurationStoreHelper.removeQueue(getVirtualHost().getDurableConfigurationStore(),queue);

        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after second recovery",
                0,  getVirtualHost().getQueues().size());
        assertNull("Durable queue was not removed:" + durableQueueName,
                getVirtualHost().getQueue(durableQueueName));
    }

    /**
     * Tests exchange persistence by creating a selection of exchanges, both durable
     * and non durable, and ensuring that following the recovery process the correct
     * durable exchanges are still present.
     */
    public void testExchangePersistence() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        Map<String, Exchange> oldExchanges = createExchanges();

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 3, getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        //verify the exchanges present after recovery
        validateExchanges(origExchangeCount, oldExchanges);
    }

    /**
     * Tests exchange removal by creating a durable exchange, verifying it recovers, and
     * then removing it from the store, and ensuring that following the second reload
     * process it is not recovered.
     */
    public void testDurableExchangeRemoval() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        createExchange(DirectExchange.TYPE, directExchangeName, true);

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 1,  getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        assertEquals("Incorrect number of exchanges registered after first recovery",
                origExchangeCount + 1,  getVirtualHost().getExchanges().size());

        //test that removing the exchange means it is not recovered next time
        final Exchange exchange = getVirtualHost().getExchange(directExchangeName);
        DurableConfigurationStoreHelper.removeExchange(getVirtualHost().getDurableConfigurationStore(), exchange);

        reloadVirtualHost();

        assertEquals("Incorrect number of exchanges registered after second recovery",
                origExchangeCount,  getVirtualHost().getExchanges().size());
        assertNull("Durable exchange was not removed:" + directExchangeName,
                getVirtualHost().getExchange(directExchangeName));
    }

    /**
     * Tests binding persistence by creating a selection of queues and exchanges, both durable
     * and non durable, then adding bindings with and without selectors before reloading the
     * virtual host and verifying that following the recovery process the correct durable
     * bindings (those for durable queues to durable exchanges) are still present.
     */
    public void testBindingPersistence() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        createAllQueues();
        createAllTopicQueues();

        Map<String, Exchange> exchanges = createExchanges();

        Exchange nonDurableExchange = exchanges.get(nonDurableExchangeName);
        Exchange directExchange = exchanges.get(directExchangeName);
        Exchange topicExchange = exchanges.get(topicExchangeName);

        bindAllQueuesToExchange(nonDurableExchange, directRouting);
        bindAllQueuesToExchange(directExchange, directRouting);
        bindAllTopicQueuesToExchange(topicExchange, topicRouting);

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 3, getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        validateExchanges(origExchangeCount, exchanges);

        validateBindingProperties();
    }

    /**
     * Tests binding removal by creating a durable exchange, and queue, binding them together,
     * recovering to verify the persistence, then removing it from the store, and ensuring
     * that following the second reload process it is not recovered.
     */
    public void testDurableBindingRemoval() throws Exception
    {
        //create durable queue and exchange, bind them
        Exchange exch = createExchange(DirectExchange.TYPE, directExchangeName, true);
        createQueue(durableQueueName, false, true, false, false);
        bindQueueToExchange(exch, directRouting, getVirtualHost().getQueue(durableQueueName), false);

        assertEquals("Incorrect number of bindings registered before recovery",
                1, getVirtualHost().getQueue(durableQueueName).getBindings().size());

        //verify binding is actually normally recovered
        reloadVirtualHost();

        assertEquals("Incorrect number of bindings registered after first recovery",
                1, getVirtualHost().getQueue(durableQueueName).getBindings().size());

        exch = getVirtualHost().getExchange(directExchangeName);
        assertNotNull("Exchange was not recovered", exch);

        //remove the binding and verify result after recovery
        unbindQueueFromExchange(exch, directRouting, getVirtualHost().getQueue(durableQueueName), false);

        reloadVirtualHost();

        assertEquals("Incorrect number of bindings registered after second recovery",
                0, getVirtualHost().getQueue(durableQueueName).getBindings().size());
    }

    /**
     * Validates that the durable exchanges are still present, the non durable exchange is not,
     * and that the new exchanges are not the same objects as the provided list (i.e. that the
     * reload actually generated new exchange objects)
     */
    private void validateExchanges(int originalNumExchanges, Map<String, Exchange> oldExchanges)
    {
        Collection<Exchange> exchanges = getVirtualHost().getExchanges();
        Collection<String> exchangeNames = new ArrayList(exchanges.size());
        for(Exchange exchange : exchanges)
        {
            exchangeNames.add(exchange.getName());
        }
        assertTrue(directExchangeName + " exchange NOT reloaded",
                exchangeNames.contains(directExchangeName));
        assertTrue(topicExchangeName + " exchange NOT reloaded",
                exchangeNames.contains(topicExchangeName));
        assertTrue(nonDurableExchangeName + " exchange reloaded",
                !exchangeNames.contains(nonDurableExchangeName));

        //check the old exchange objects are not the same as the new exchanges
        assertTrue(directExchangeName + " exchange NOT reloaded",
                getVirtualHost().getExchange(directExchangeName) != oldExchanges.get(directExchangeName));
        assertTrue(topicExchangeName + " exchange NOT reloaded",
                getVirtualHost().getExchange(topicExchangeName) != oldExchanges.get(topicExchangeName));

        // There should only be the original exchanges + our 2 recovered durable exchanges
        assertEquals("Incorrect number of exchanges available",
                originalNumExchanges + 2, getVirtualHost().getExchanges().size());
    }

    /** Validates the Durable queues and their properties are as expected following recovery */
    private void validateBindingProperties()
    {

        assertEquals("Incorrect number of (durable) queues following recovery", 6, getVirtualHost().getQueues().size());

        validateBindingProperties(getVirtualHost().getQueue(durablePriorityQueueName).getBindings(), false);
        validateBindingProperties(getVirtualHost().getQueue(durablePriorityTopicQueueName).getBindings(), true);
        validateBindingProperties(getVirtualHost().getQueue(durableQueueName).getBindings(), false);
        validateBindingProperties(getVirtualHost().getQueue(durableTopicQueueName).getBindings(), true);
        validateBindingProperties(getVirtualHost().getQueue(durableExclusiveQueueName).getBindings(), false);
    }

    /**
     * Validate that each queue is bound only once following recovery (i.e. that bindings for non durable
     * queues or to non durable exchanges are not recovered), and if a selector should be present
     * that it is and contains the correct value
     *
     * @param bindings     the set of bindings to validate
     * @param useSelectors if set, check the binding has a JMS_SELECTOR argument and the correct value for it
     */
    private void validateBindingProperties(List<Binding> bindings, boolean useSelectors)
    {
        assertEquals("Each queue should only be bound once.", 1, bindings.size());

        Binding binding = bindings.get(0);

        if (useSelectors)
        {
            assertTrue("Binding does not contain a Selector argument.",
                    binding.getArguments().containsKey(AMQPFilterTypes.JMS_SELECTOR.toString()));
            assertEquals("The binding selector argument is incorrect", SELECTOR_VALUE,
                    binding.getArguments().get(AMQPFilterTypes.JMS_SELECTOR.toString()).toString());
        }
    }

    private void setQueueExclusivity(boolean exclusive) throws AMQException
    {
        AMQQueue queue = getVirtualHost().getQueue(durableExclusiveQueueName);

        queue.setExclusive(exclusive);
    }

    private void validateQueueExclusivityProperty(boolean expected)
    {
        AMQQueue queue = getVirtualHost().getQueue(durableExclusiveQueueName);

        assertEquals("Queue exclusivity was incorrect", queue.isExclusive(), expected);
    }


    private void validateDurableQueueProperties()
    {
        validateQueueProperties(getVirtualHost().getQueue(durablePriorityQueueName), true, true, false, false);
        validateQueueProperties(getVirtualHost().getQueue(durablePriorityTopicQueueName), true, true, false, false);
        validateQueueProperties(getVirtualHost().getQueue(durableQueueName), false, true, false, false);
        validateQueueProperties(getVirtualHost().getQueue(durableTopicQueueName), false, true, false, false);
        validateQueueProperties(getVirtualHost().getQueue(durableExclusiveQueueName), false, true, true, false);
        validateQueueProperties(getVirtualHost().getQueue(durableLastValueQueueName), false, true, true, true);
    }

    private void validateQueueProperties(AMQQueue queue, boolean usePriority, boolean durable, boolean exclusive, boolean lastValueQueue)
    {
        if(usePriority || lastValueQueue)
        {
            assertNotSame("Queues cant be both Priority and LastValue based", usePriority, lastValueQueue);
        }

        if (usePriority)
        {
            assertEquals("Queue is no longer a Priority Queue", AMQPriorityQueue.class, queue.getClass());
            assertEquals("Priority Queue does not have set priorities",
                    DEFAULT_PRIORTY_LEVEL, ((AMQPriorityQueue) queue).getPriorities());
        }
        else if (lastValueQueue)
        {
            assertEquals("Queue is no longer a LastValue Queue", ConflationQueue.class, queue.getClass());
            assertEquals("LastValue Queue Key has changed", LVQ_KEY, ((ConflationQueue) queue).getConflationKey());
        }
        else
        {
            assertEquals("Queue is not 'simple'", SimpleAMQQueue.class, queue.getClass());
        }

        assertEquals("Queue owner is not as expected", queueOwner, queue.getOwner());
        assertEquals("Queue durability is not as expected", durable, queue.isDurable());
        assertEquals("Queue exclusivity is not as expected", exclusive, queue.isExclusive());
    }

    /**
     * Delete the Store Environment path
     *
     * @param environmentPath The configuration that contains the store environment path.
     */
    private void cleanup(File environmentPath)
    {
        if (environmentPath.exists())
        {
            FileUtils.delete(environmentPath, true);
        }
    }

    private void sendMessageOnExchange(Exchange exchange, String routingKey, boolean deliveryMode) throws AMQStoreException
    {
        //Set MessagePersistence
        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        properties.setDeliveryMode(deliveryMode ? Integer.valueOf(2).byteValue() : Integer.valueOf(1).byteValue());
        FieldTable headers = properties.getHeaders();
        headers.setString("Test", "MST");
        properties.setHeaders(headers);

        MessagePublishInfo messageInfo = new TestMessagePublishInfo(exchange, false, false, routingKey);

        final IncomingMessage currentMessage;


        currentMessage = new IncomingMessage(messageInfo);

        currentMessage.setExchange(exchange);

        ContentHeaderBody headerBody = new ContentHeaderBody(BasicConsumeBodyImpl.CLASS_ID,0,properties,0l);

        try
        {
            currentMessage.setContentHeaderBody(headerBody);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }

        currentMessage.setExpiration();

        MessageMetaData mmd = currentMessage.headersReceived(System.currentTimeMillis());
        currentMessage.setStoredMessage(getVirtualHost().getMessageStore().addMessage(mmd));
        currentMessage.getStoredMessage().flushToStore();
        currentMessage.route();


        // check and deliver if header says body length is zero
        if (currentMessage.allContentReceived())
        {
            ServerTransaction trans = new AutoCommitTransaction(getVirtualHost().getMessageStore());
            final List<? extends BaseQueue> destinationQueues = currentMessage.getDestinationQueues();
            trans.enqueue(currentMessage.getDestinationQueues(), currentMessage, new ServerTransaction.Action() {
                public void postCommit()
                {
                    try
                    {
                        AMQMessage message = new AMQMessage(currentMessage.getStoredMessage());

                        for(BaseQueue queue : destinationQueues)
                        {
                            queue.enqueue(message);
                        }
                    }
                    catch (AMQException e)
                    {
                        e.printStackTrace();
                    }
                }

                public void onRollback()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });
        }
    }

    private void createAllQueues()
    {
        //Register Durable Priority Queue
        createQueue(durablePriorityQueueName, true, true, false, false);

        //Register Durable Simple Queue
        createQueue(durableQueueName, false, true, false, false);

        //Register Durable Exclusive Simple Queue
        createQueue(durableExclusiveQueueName, false, true, true, false);

        //Register Durable LastValue Queue
        createQueue(durableLastValueQueueName, false, true, true, true);

        //Register NON-Durable Priority Queue
        createQueue(priorityQueueName, true, false, false, false);

        //Register NON-Durable Simple Queue
        createQueue(queueName, false, false, false, false);
    }

    private void createAllTopicQueues()
    {
        //Register Durable Priority Queue
        createQueue(durablePriorityTopicQueueName, true, true, false, false);

        //Register Durable Simple Queue
        createQueue(durableTopicQueueName, false, true, false, false);

        //Register NON-Durable Priority Queue
        createQueue(priorityTopicQueueName, true, false, false, false);

        //Register NON-Durable Simple Queue
        createQueue(topicQueueName, false, false, false, false);
    }

    private void createQueue(String queueName, boolean usePriority, boolean durable, boolean exclusive, boolean lastValueQueue)
    {

        Map<String,Object> queueArguments = null;

        if(usePriority || lastValueQueue)
        {
            assertNotSame("Queues cant be both Priority and LastValue based", usePriority, lastValueQueue);
        }

        if (usePriority)
        {
            queueArguments = Collections.singletonMap(Queue.PRIORITIES, (Object) DEFAULT_PRIORTY_LEVEL);
        }

        if (lastValueQueue)
        {
            queueArguments = Collections.singletonMap(Queue.LVQ_KEY, (Object) LVQ_KEY);
        }

        AMQQueue queue = null;

        //Ideally we would be able to use the QueueDeclareHandler here.
        try
        {
            queue = getVirtualHost().createQueue(UUIDGenerator.generateRandomUUID(), queueName, durable, queueOwner, false, exclusive,
                    false, queueArguments);

            validateQueueProperties(queue, usePriority, durable, exclusive, lastValueQueue);

        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }

    }

    private Map<String, Exchange> createExchanges()
    {
        Map<String, Exchange> exchanges = new HashMap<String, Exchange>();

        //Register non-durable DirectExchange
        exchanges.put(nonDurableExchangeName, createExchange(DirectExchange.TYPE, nonDurableExchangeName, false));

        //Register durable DirectExchange and TopicExchange
        exchanges.put(directExchangeName ,createExchange(DirectExchange.TYPE, directExchangeName, true));
        exchanges.put(topicExchangeName,createExchange(TopicExchange.TYPE, topicExchangeName, true));

        return exchanges;
    }

    private Exchange createExchange(ExchangeType<?> type, String name, boolean durable)
    {
        Exchange exchange = null;

        try
        {
            exchange = getVirtualHost().createExchange(null, name, type.getType(), durable, false, null);
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }

        return exchange;
    }

    private void bindAllQueuesToExchange(Exchange exchange, String routingKey)
    {
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durablePriorityQueueName), false);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durableQueueName), false);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(priorityQueueName), false);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(queueName), false);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durableExclusiveQueueName), false);
    }

    private void bindAllTopicQueuesToExchange(Exchange exchange, String routingKey)
    {

        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durablePriorityTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durableTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(priorityTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(topicQueueName), true);
    }


    protected void bindQueueToExchange(Exchange exchange,
                                       String routingKey,
                                       AMQQueue queue,
                                       boolean useSelector)
    {
        Map<String,Object> bindArguments = new HashMap<String, Object>();

        if (useSelector)
        {
            bindArguments.put(AMQPFilterTypes.JMS_SELECTOR.toString(), SELECTOR_VALUE );
        }

        try
        {
            exchange.addBinding(routingKey, queue, bindArguments);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    protected void unbindQueueFromExchange(Exchange exchange,
                                           String routingKey,
                                           AMQQueue queue,
                                           boolean useSelector)
    {
        Map<String,Object> bindArguments = new HashMap<String, Object>();

        if (useSelector)
        {
            bindArguments.put(AMQPFilterTypes.JMS_SELECTOR.toString(), SELECTOR_VALUE );
        }

        try
        {
            exchange.removeBinding(routingKey, queue, bindArguments);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    private void validateMessageOnTopics(long messageCount, boolean allQueues)
    {
        validateMessageOnQueue(durablePriorityTopicQueueName, messageCount);
        validateMessageOnQueue(durableTopicQueueName, messageCount);

        if (allQueues)
        {
            validateMessageOnQueue(priorityTopicQueueName, messageCount);
            validateMessageOnQueue(topicQueueName, messageCount);
        }
    }

    private void validateMessageOnQueues(long messageCount, boolean allQueues)
    {
        validateMessageOnQueue(durablePriorityQueueName, messageCount);
        validateMessageOnQueue(durableQueueName, messageCount);

        if (allQueues)
        {
            validateMessageOnQueue(priorityQueueName, messageCount);
            validateMessageOnQueue(queueName, messageCount);
        }
    }

    private void validateMessageOnQueue(String queueName, long messageCount)
    {
        AMQQueue queue = getVirtualHost().getQueue(queueName);

        assertNotNull("Queue(" + queueName + ") not correctly registered:", queue);

        assertEquals("Incorrect Message count on queue:" + queueName, messageCount, queue.getMessageCount());
    }

    private class TestMessagePublishInfo implements MessagePublishInfo
    {

        Exchange _exchange;
        boolean _immediate;
        boolean _mandatory;
        String _routingKey;

        TestMessagePublishInfo(Exchange exchange, boolean immediate, boolean mandatory, String routingKey)
        {
            _exchange = exchange;
            _immediate = immediate;
            _mandatory = mandatory;
            _routingKey = routingKey;
        }

        public AMQShortString getExchange()
        {
            return new AMQShortString(_exchange.getName());
        }

        public void setExchange(AMQShortString exchange)
        {
            //no-op
        }

        public boolean isImmediate()
        {
            return _immediate;
        }

        public boolean isMandatory()
        {
            return _mandatory;
        }

        public AMQShortString getRoutingKey()
        {
            return new AMQShortString(_routingKey);
        }
    }
}
