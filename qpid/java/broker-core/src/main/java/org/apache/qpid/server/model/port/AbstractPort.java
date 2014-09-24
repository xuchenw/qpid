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

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.access.Operation;

abstract public class AbstractPort<X extends AbstractPort<X>> extends AbstractConfiguredObject<X> implements Port<X>
{
    private static final Logger LOGGER = Logger.getLogger(AbstractPort.class);

    private static final Set<InetAddress> LOCAL_ADDRESSES = new CopyOnWriteArraySet<>();
    private static final Set<String> LOCAL_ADDRESS_NAMES = new CopyOnWriteArraySet<>();
    private static final Lock ADDRESS_LOCK = new ReentrantLock();
    private static final AtomicBoolean ADDRESSES_COMPUTED = new AtomicBoolean();

    static
    {
        Thread thread = new Thread(new Runnable()
        {
            public void run()
            {
                Lock lock = ADDRESS_LOCK;

                lock.lock();
                try
                {
                    for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces()))
                    {
                        for (InterfaceAddress inetAddress : networkInterface.getInterfaceAddresses())
                        {
                            InetAddress address = inetAddress.getAddress();
                            LOCAL_ADDRESSES.add(address);
                            String hostAddress = address.getHostAddress();
                            if (hostAddress != null)
                            {
                                LOCAL_ADDRESS_NAMES.add(hostAddress);
                            }
                            String hostName = address.getHostName();
                            if (hostName != null)
                            {
                                LOCAL_ADDRESS_NAMES.add(hostName);
                            }
                            String canonicalHostName = address.getCanonicalHostName();
                            if (canonicalHostName != null)
                            {
                                LOCAL_ADDRESS_NAMES.add(canonicalHostName);
                            }
                        }
                    }
                }
                catch (SocketException e)
                {
                    // ignore
                }
                finally
                {
                    ADDRESSES_COMPUTED.set(true);
                    lock.unlock();
                }
            }
        }, "Network Address Resolver");
        thread.start();
    }

    private final Broker<?> _broker;

    @ManagedAttributeField
    private int _port;

    @ManagedAttributeField
    private KeyStore<?> _keyStore;

    @ManagedAttributeField
    private Collection<TrustStore> _trustStores;

    @ManagedAttributeField
    private Set<Transport> _transports;

    @ManagedAttributeField
    private Set<Protocol> _protocols;

    public AbstractPort(Map<String, Object> attributes,
                        Broker<?> broker)
    {
        super(parentsMap(broker), attributes);

        _broker = broker;

    }

    @Override
    public void onValidate()
    {
        super.onValidate();

        boolean useTLSTransport = isUsingTLSTransport();

        if(useTLSTransport && getKeyStore() == null)
        {
            throw new IllegalConfigurationException("Can't create a port which uses a secure transport but has no KeyStore");
        }

        if(!isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }

        for (Port p : _broker.getPorts())
        {
            if (p.getPort() == getPort() && p != this)
            {
                throw new IllegalConfigurationException("Can't add port "
                                                        + getName()
                                                        + " because port number "
                                                        + getPort()
                                                        + " is already configured for port "
                                                        + p.getName());
            }
        }
    }

    protected final boolean isUsingTLSTransport()
    {
        return isUsingTLSTransport(getTransports());
    }

    protected final boolean isUsingTLSTransport(final Collection<Transport> transports)
    {
        boolean usesTLS = false;
        if(transports != null)
        {
            for (Transport transport : transports)
            {
                if (transport.isSecure())
                {
                    usesTLS = true;
                    break;
                }
            }
        }
        return usesTLS;
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
        Port<?> updated = (Port<?>)proxyForValidation;


        if(!getName().equals(updated.getName()))
        {
            throw new IllegalConfigurationException("Changing the port name is not allowed");
        }

        if(changedAttributes.contains(PORT))
        {
            int newPort = updated.getPort();
            if (getPort() != newPort)
            {
                for (Port p : _broker.getPorts())
                {
                    if (p.getPort() == newPort)
                    {
                        throw new IllegalConfigurationException("Port number "
                                                                + newPort
                                                                + " is already in use by port "
                                                                + p.getName());
                    }
                }
            }
        }


        Collection<Transport> transports = updated.getTransports();

        Collection<Protocol> protocols = updated.getProtocols();


        boolean usesSsl = isUsingTLSTransport(transports);
        if (usesSsl)
        {
            if (updated.getKeyStore() == null)
            {
                throw new IllegalConfigurationException("Can't create port which requires SSL but has no key store configured.");
            }
        }

        if (protocols != null && protocols.contains(Protocol.RMI) && usesSsl)
        {
            throw new IllegalConfigurationException("Can't create RMI Registry port which requires SSL.");
        }

    }

    @Override
    public int getPort()
    {
        return _port;
    }

    @Override
    public Set<Transport> getTransports()
    {
        return _transports;
    }

    @Override
    public Set<Protocol> getProtocols()
    {
        return _protocols;
    }

    @Override
    public Collection<VirtualHostAlias> getVirtualHostBindings()
    {
        List<VirtualHostAlias> aliases = new ArrayList<VirtualHostAlias>();
        for(VirtualHostNode<?> vhn : _broker.getVirtualHostNodes())
        {
            VirtualHost<?, ?, ?> vh = vhn.getVirtualHost();
            if (vh != null)
            {
                for(VirtualHostAlias<?> alias : vh.getAliases())
                {
                    if(alias.getPort().equals(this))
                    {
                        aliases.add(alias);
                    }
                }
            }
        }
        return Collections.unmodifiableCollection(aliases);
    }

    @Override
    public Collection<Connection> getConnections()
    {
        return null;
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == Connection.class)
        {
            return (Collection<C>) getConnections();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    @Override
    public Object getAttribute(String name)
    {
        if(STATE.equals(name))
        {
            return getState();
        }
        return super.getAttribute(name);
    }

    @StateTransition(currentState = { State.ACTIVE, State.QUIESCED, State.ERRORED}, desiredState = State.DELETED )
    private void doDelete()
    {
        close();
        setState(State.DELETED);
    }

    @StateTransition( currentState = {State.UNINITIALIZED, State.QUIESCED}, desiredState = State.ACTIVE )
    protected void activate()
    {
        try
        {
            setState(onActivate());
        }
        catch (RuntimeException e)
        {
            setState(State.ERRORED);
            LOGGER.error("Unable to active port '" + getName() + "'of type " + getType() + " on port " + getPort(),
                         e);
        }
    }

    @StateTransition( currentState = State.UNINITIALIZED, desiredState = State.QUIESCED)
    private void startQuiesced()
    {
        setState(State.QUIESCED);
    }


    protected State onActivate()
    {
        // no-op: expected to be overridden by subclass
        return State.ACTIVE;
    }


    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Port.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of port is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Port.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of port attributes is denied");
        }
    }

    @Override
    public KeyStore getKeyStore()
    {
        return _keyStore;
    }

    @Override
    public Collection<TrustStore> getTrustStores()
    {
        return _trustStores;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + getId() + ", name=" + getName() + ", port=" + getPort() + "]";
    }


    protected void validateOnlyOneInstance()
    {
        Broker<?> broker = getParent(Broker.class);
        if(!broker.isManagementMode())
        {
            //ManagementMode needs this relaxed to allow its overriding management ports to be inserted.

            //Enforce only a single port of each management protocol, as the plugins will only use one.
            Collection<Port<?>> existingPorts = new HashSet<Port<?>>(broker.getPorts());
            existingPorts.remove(this);

            for (Port<?> existingPort : existingPorts)
            {
                Collection<Protocol> portProtocols = existingPort.getProtocols();
                if (portProtocols != null)
                {
                    final ArrayList<Protocol> intersection = new ArrayList<>(portProtocols);
                    intersection.retainAll(getProtocols());
                    if(!intersection.isEmpty())
                    {
                        throw new IllegalConfigurationException("Port for protocols " + intersection + " already exists. Only one management port per protocol can be created.");
                    }
                }
            }
        }
    }

    public boolean isLocalMachine(final String host)
    {
        while(!ADDRESSES_COMPUTED.get())
        {
            Lock lock = ADDRESS_LOCK;
            lock.lock();
            lock.unlock();
        }

        boolean isNetworkAddress = true;
        if (!LOCAL_ADDRESS_NAMES.contains(host))
        {
            try
            {
                InetAddress inetAddress = InetAddress.getByName(host);
                if (!LOCAL_ADDRESSES.contains(inetAddress))
                {
                    isNetworkAddress = false;
                }
                else
                {
                    LOCAL_ADDRESS_NAMES.add(host);
                }
            }
            catch (UnknownHostException e)
            {
                // ignore
                isNetworkAddress = false;
            }
        }
        return isNetworkAddress;

    }

}
