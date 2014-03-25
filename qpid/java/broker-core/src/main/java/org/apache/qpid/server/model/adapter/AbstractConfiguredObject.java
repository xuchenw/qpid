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
package org.apache.qpid.server.model.adapter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessControlException;
import java.security.AccessController;
import java.util.*;

import org.apache.qpid.server.model.*;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.ChangeAttributesTask;
import org.apache.qpid.server.configuration.updater.ChangeStateTask;
import org.apache.qpid.server.configuration.updater.CreateChildTask;
import org.apache.qpid.server.configuration.updater.SetAttributeTask;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

import javax.security.auth.Subject;

public abstract class AbstractConfiguredObject<X extends ConfiguredObject<X>> implements ConfiguredObject<X>
{
    private static final String ID = "id";

    private static final Map<Class<? extends ConfiguredObject>, Collection<Attribute<?,?>>> _allAttributes =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Collection<Attribute<?, ?>>>());

    private static final Map<Class<? extends ConfiguredObject>, Collection<Statistic<?,?>>> _allStatistics =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Collection<Statistic<?, ?>>>());

    private static final Map<Class<? extends ConfiguredObject>, Map<String, Attribute<?,?>>> _allAttributeTypes =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Map<String, Attribute<?, ?>>>());

    private static final Map<Class<? extends ConfiguredObject>, Map<String, Field>> _allAutomatedFields =
            Collections.synchronizedMap(new HashMap<Class<? extends ConfiguredObject>, Map<String, Field>>());
    private static final Map<Class, Object> SECURE_VALUES;
    static
    {
        Map<Class,Object> secureValues = new HashMap<Class, Object>();
        secureValues.put(String.class, "********");
        secureValues.put(Integer.class, 0);
        secureValues.put(Long.class, 0l);
        secureValues.put(Byte.class, (byte)0);
        secureValues.put(Short.class, (short)0);
        secureValues.put(Double.class, (double)0);
        secureValues.put(Float.class, (float)0);

        SECURE_VALUES = Collections.unmodifiableMap(secureValues);
    }


    private final Map<String,Object> _attributes = new HashMap<String, Object>();
    private final Map<Class<? extends ConfiguredObject>, ConfiguredObject> _parents =
            new HashMap<Class<? extends ConfiguredObject>, ConfiguredObject>();
    private final Collection<ConfigurationChangeListener> _changeListeners =
            new ArrayList<ConfigurationChangeListener>();

    @ManagedAttributeField
    private final UUID _id;

    private final Map<String, Object> _defaultAttributes = new HashMap<String, Object>();
    private final TaskExecutor _taskExecutor;

    @ManagedAttributeField
    private long _createdTime;

    @ManagedAttributeField
    private String _createdBy;

    @ManagedAttributeField
    private long _lastUpdatedTime;

    @ManagedAttributeField
    private String _lastUpdatedBy;

    @ManagedAttributeField
    private String _name;

    private final Map<String, Attribute<?,?>> _attributeTypes;
    private final Map<String, Field> _automatedFields;

    protected AbstractConfiguredObject(UUID id,
                                       Map<String, Object> defaults,
                                       Map<String, Object> attributes,
                                       TaskExecutor taskExecutor)
    {
        this(defaults, combineIdWithAttributes(id,attributes), taskExecutor);
    }

    public static Map<String,Object> combineIdWithAttributes(UUID id, Map<String,Object> attributes)
    {
        Map<String,Object> combined = new HashMap<String, Object>(attributes);
        combined.put(ID, id);
        return combined;
    }


    protected AbstractConfiguredObject(UUID id, Map<String, Object> defaults, Map<String, Object> attributes,
                                       TaskExecutor taskExecutor, boolean filterAttributes)

    {
        this(Collections.<Class<? extends ConfiguredObject>, ConfiguredObject<?>>emptyMap(),
             defaults, combineIdWithAttributes(id, attributes), taskExecutor, filterAttributes);
    }

    protected AbstractConfiguredObject(Map<String, Object> defaults,
                                       Map<String, Object> attributes,
                                       TaskExecutor taskExecutor)
    {
        this(Collections.<Class<? extends ConfiguredObject>, ConfiguredObject<?>>emptyMap(),
             defaults, attributes, taskExecutor, true);
    }

    protected AbstractConfiguredObject(final Map<Class<? extends ConfiguredObject>, ConfiguredObject<?>> parents,
                                       Map<String, Object> defaults,
                                       Map<String, Object> attributes,
                                       TaskExecutor taskExecutor)
    {
        this(parents, defaults, attributes, taskExecutor, true);
    }

    protected AbstractConfiguredObject(final Map<Class<? extends ConfiguredObject>, ConfiguredObject<?>> parents,
                                       Map<String, Object> defaults,
                                       Map<String, Object> attributes,
                                       TaskExecutor taskExecutor,
                                       boolean filterAttributes)
    {
        _taskExecutor = taskExecutor;
        _id = (UUID)attributes.get(ID);
        _attributeTypes = getAttributeTypes(getClass());
        _automatedFields = getAutomatedFields(getClass());
        for(Map.Entry<Class<? extends ConfiguredObject>, ConfiguredObject<?>> entry : parents.entrySet())
        {
            addParent((Class<ConfiguredObject>) entry.getKey(), entry.getValue());
        }

        Collection<String> names = getAttributeNames();
        if(names!=null)
        {
            if(filterAttributes)
            {
                for (String name : names)
                {
                    if (attributes.containsKey(name))
                    {
                        final Object value = attributes.get(name);
                        if(value != null)
                        {
                            _attributes.put(name, value);
                        }
                        if(_automatedFields.containsKey(name))
                        {
                            automatedSetValue(name, value);
                        }
                    }
                }
            }
            else
            {
                for(Map.Entry<String, Object> entry : attributes.entrySet())
                {
                    if(entry.getValue()!=null)
                    {
                        _attributes.put(entry.getKey(),entry.getValue());
                        if(_automatedFields.containsKey(entry.getKey()))
                        {
                            automatedSetValue(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }

        }

        if (defaults != null)
        {
            _defaultAttributes.putAll(defaults);
            for(Map.Entry<String,Object> defaultedEntry : defaults.entrySet())
            {
                if(_automatedFields.containsKey(defaultedEntry.getKey()) && !attributes.containsKey(defaultedEntry.getKey()))
                {
                    automatedSetValue(defaultedEntry.getKey(),defaultedEntry.getValue());
                }
            }
        }
        if(!_attributes.containsKey(CREATED_BY))
        {
            final AuthenticatedPrincipal currentUser = SecurityManager.getCurrentUser();
            if(currentUser != null)
            {
                _attributes.put(CREATED_BY, currentUser);
            }
        }
        if(!_attributes.containsKey(CREATED_TIME))
        {
            _attributes.put(CREATED_TIME, System.currentTimeMillis());
        }
        for(Attribute<?,?> attr : _attributeTypes.values())
        {
            if(attr.getAnnotation().mandatory() && !(attributes.containsKey(attr.getName())|| defaults.containsKey(attr.getName())))
            {
                throw new IllegalArgumentException("Mandatory attribute " + attr.getName() + " not supplied for instance of " + getClass().getName());
            }
        }
    }

    private void automatedSetValue(final String name, final Object value)
    {
        try
        {
            final Attribute attribute = _attributeTypes.get(name);
            _automatedFields.get(name).set(this, attribute.convert(value, this));
        }
        catch (IllegalAccessException e)
        {
            throw new ServerScopedRuntimeException("Unable to set the automated attribute " + name + " on the configure object type " + getClass().getName(),e);
        }
    }

    protected AbstractConfiguredObject(UUID id, TaskExecutor taskExecutor)
    {
        this(id, Collections.<String,Object>emptyMap(), Collections.<String,Object>emptyMap(), taskExecutor);
    }

    public final UUID getId()
    {
        return _id;
    }

    public final String getName()
    {
        return _name;
    }

    public Class<? extends ConfiguredObject> getCategoryClass()
    {
        return getCategory(getClass());
    }

    public State getDesiredState()
    {
        return null;  //TODO
    }

    @Override
    public final State setDesiredState(final State currentState, final State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        if (_taskExecutor.isTaskExecutorThread())
        {
            authoriseSetDesiredState(currentState, desiredState);
            if (setState(currentState, desiredState))
            {
                notifyStateChanged(currentState, desiredState);
                return desiredState;
            }
            else
            {
                return getState();
            }
        }
        else
        {
            return _taskExecutor.submitAndWait(new ChangeStateTask(this, currentState, desiredState));
        }
    }

    /**
     * @return true when the state has been successfully updated to desiredState or false otherwise
     */
    protected abstract boolean setState(State currentState, State desiredState);

    protected void notifyStateChanged(final State currentState, final State desiredState)
    {
        synchronized (_changeListeners)
        {
            List<ConfigurationChangeListener> copy = new ArrayList<ConfigurationChangeListener>(_changeListeners);
            for(ConfigurationChangeListener listener : copy)
            {
                listener.stateChanged(this, currentState, desiredState);
            }
        }
    }

    public void addChangeListener(final ConfigurationChangeListener listener)
    {
        if(listener == null)
        {
            throw new NullPointerException("Cannot add a null listener");
        }
        synchronized (_changeListeners)
        {
            if(!_changeListeners.contains(listener))
            {
                _changeListeners.add(listener);
            }
        }
    }

    public boolean removeChangeListener(final ConfigurationChangeListener listener)
    {
        if(listener == null)
        {
            throw new NullPointerException("Cannot remove a null listener");
        }
        synchronized (_changeListeners)
        {
            return _changeListeners.remove(listener);
        }
    }

    protected void childAdded(ConfiguredObject child)
    {
        synchronized (_changeListeners)
        {
            List<ConfigurationChangeListener> copy = new ArrayList<ConfigurationChangeListener>(_changeListeners);
            for(ConfigurationChangeListener listener : copy)
            {
                listener.childAdded(this, child);
            }
        }
    }

    protected void childRemoved(ConfiguredObject child)
    {
        synchronized (_changeListeners)
        {
            List<ConfigurationChangeListener> copy = new ArrayList<ConfigurationChangeListener>(_changeListeners);
            for(ConfigurationChangeListener listener : copy)
            {
                listener.childRemoved(this, child);
            }
        }
    }

    protected void attributeSet(String attributeName, Object oldAttributeValue, Object newAttributeValue)
    {

        final AuthenticatedPrincipal currentUser = SecurityManager.getCurrentUser();
        if(currentUser != null)
        {
            _attributes.put(LAST_UPDATED_BY, currentUser.getName());
            _lastUpdatedBy = currentUser.getName();
        }
        final long currentTime = System.currentTimeMillis();
        _attributes.put(LAST_UPDATED_TIME, currentTime);
        _lastUpdatedTime = currentTime;

        synchronized (_changeListeners)
        {
            List<ConfigurationChangeListener> copy = new ArrayList<ConfigurationChangeListener>(_changeListeners);
            for(ConfigurationChangeListener listener : copy)
            {
                listener.attributeSet(this, attributeName, oldAttributeValue, newAttributeValue);
            }
        }
    }

    private final Object getDefaultAttribute(String name)
    {
        return _defaultAttributes.get(name);
    }

    @Override
    public Object getAttribute(String name)
    {
        Attribute<X,?> attr = (Attribute<X, ?>) _attributeTypes.get(name);
        if(attr != null && attr.getAnnotation().automate())
        {
            Object value = attr.getValue((X)this);
            if(value != null && attr.getAnnotation().secure() &&
               !SecurityManager.isSystemProcess())
            {
                return SECURE_VALUES.get(value.getClass());
            }
            else
            {
                return value;
            }
        }
        else
        {
            Object value = getActualAttribute(name);
            if (value == null)
            {
                value = getDefaultAttribute(name);
            }
            return value;
        }
    }

    @Override
    public String getDescription()
    {
        return (String) getAttribute(DESCRIPTION);
    }

    @Override
    public <T> T getAttribute(final Attribute<? super X, T> attr)
    {
        return (T) getAttribute(attr.getName());
    }

    @Override
    public final Map<String, Object> getActualAttributes()
    {
        synchronized (_attributes)
        {
            return new HashMap<String, Object>(_attributes);
        }
    }

    private Object getActualAttribute(final String name)
    {
        if(CREATED_BY.equals(name))
        {
            return getCreatedBy();
        }
        else if(CREATED_TIME.equals(name))
        {
            return getCreatedTime();
        }
        else
        {
            synchronized (_attributes)
            {
                return _attributes.get(name);
            }
        }
    }

    public Object setAttribute(final String name, final Object expected, final Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        if (_taskExecutor.isTaskExecutorThread())
        {
            authoriseSetAttribute(name, expected, desired);
            if (changeAttribute(name, expected, desired))
            {
                attributeSet(name, expected, desired);
                return desired;
            }
            else
            {
                return getAttribute(name);
            }
        }
        else
        {
            return _taskExecutor.submitAndWait(new SetAttributeTask(this, name, expected, desired));
        }
    }

    protected boolean changeAttribute(final String name, final Object expected, final Object desired)
    {
        synchronized (_attributes)
        {
            Object currentValue = getAttribute(name);
            if((currentValue == null && expected == null)
               || (currentValue != null && currentValue.equals(expected)))
            {
                //TODO: don't put nulls
                _attributes.put(name, desired);
                Attribute<?,?> attr = _attributeTypes.get(name);
                if(attr != null && attr.getAnnotation().automate())
                {
                    if(desired == null && _defaultAttributes.containsKey(name))
                    {
                        automatedSetValue(name, _defaultAttributes.get(name));
                    }
                    else
                    {
                        automatedSetValue(name, desired);
                    }
                }
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    public <T extends ConfiguredObject> T getParent(final Class<T> clazz)
    {
        synchronized (_parents)
        {
            return (T) _parents.get(clazz);
        }
    }

    protected <T extends ConfiguredObject> void addParent(Class<T> clazz, T parent)
    {
        synchronized (_parents)
        {
            _parents.put(clazz, parent);
        }
    }

    protected  <T extends ConfiguredObject> void removeParent(Class<T> clazz)
    {
        synchronized (this)
        {
            _parents.remove(clazz);
        }
    }

    public Collection<String> getAttributeNames()
    {
        synchronized(_attributes)
        {
            return new ArrayList<String>(_attributes.keySet());
        }
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + _id + ", name=" + getName() + "]";
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if (_taskExecutor.isTaskExecutorThread())
        {
            authoriseCreateChild(childClass, attributes, otherParents);
            C child = addChild(childClass, attributes, otherParents);
            if (child != null)
            {
                childAdded(child);
            }
            return child;
        }
        else
        {
            return (C)_taskExecutor.submitAndWait(new CreateChildTask(this, childClass, attributes, otherParents));
        }
    }

    protected <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }


    protected TaskExecutor getTaskExecutor()
    {
        return _taskExecutor;
    }

    @Override
    public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        if (getTaskExecutor().isTaskExecutorThread())
        {
            authoriseSetAttributes(attributes);
            changeAttributes(attributes);
        }
        else
        {
            getTaskExecutor().submitAndWait(new ChangeAttributesTask(this, attributes));
        }
    }

    protected void changeAttributes(final Map<String, Object> attributes)
    {
        validateChangeAttributes(attributes);
        Collection<String> names = getAttributeNames();
        for (String name : names)
        {
            if (attributes.containsKey(name))
            {
                Object desired = attributes.get(name);
                Object expected = getAttribute(name);
                if (changeAttribute(name, expected, desired))
                {
                    attributeSet(name, expected, desired);
                }
            }
        }
    }

    protected void validateChangeAttributes(final Map<String, Object> attributes)
    {
        if (attributes.containsKey(ID))
        {
            UUID id = getId();
            Object idAttributeValue = attributes.get(ID);
            if (idAttributeValue != null && !(idAttributeValue.equals(id) || idAttributeValue.equals(id.toString())))
            {
                throw new IllegalConfigurationException("Cannot change existing configured object id");
            }
        }
    }

    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        // allowed by default
    }

    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        // allowed by default
    }

    protected <C extends ConfiguredObject> void authoriseCreateChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents) throws AccessControlException
    {
        // allowed by default
    }

    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        // allowed by default
    }

    protected Map<String, Object> getDefaultAttributes()
    {
        return _defaultAttributes;
    }

    /**
     * Returns a map of effective attribute values that would result
     * if applying the supplied changes. Does not apply the changes.
     */
    protected Map<String, Object> generateEffectiveAttributes(Map<String,Object> changedValues)
    {
        //Build a new set of effective attributes that would be
        //the result of applying the attribute changes, so we
        //can validate the configuration that would result

        Map<String, Object> defaultValues = getDefaultAttributes();
        Map<String, Object> existingActualValues = getActualAttributes();

        //create a new merged map, starting with the defaults
        Map<String, Object> merged =  new HashMap<String, Object>(defaultValues);

        for(String name : getAttributeNames())
        {
            if(changedValues.containsKey(name))
            {
                Object changedValue = changedValues.get(name);
                if(changedValue != null)
                {
                    //use the new non-null value for the merged values
                    merged.put(name, changedValue);
                }
                else
                {
                    //we just use the default (if there was one) since the changed
                    //value is null and effectively clears any existing actual value
                }
            }
            else if(existingActualValues.get(name) != null)
            {
                //Use existing non-null actual value for the merge
                merged.put(name, existingActualValues.get(name));
            }
            else
            {
                //There was neither a change or an existing non-null actual
                //value, so just use the default value (if there was one).
            }
        }

        return merged;
    }

    @Override
    public String getLastUpdatedBy()
    {
        return _lastUpdatedBy;
    }

    @Override
    public long getLastUpdatedTime()
    {
        return _lastUpdatedTime;
    }

    @Override
    public String getCreatedBy()
    {
        return _createdBy;
    }

    protected String getCurrentUserName()
    {
        Subject currentSubject = Subject.getSubject(AccessController.getContext());
        Set<AuthenticatedPrincipal> principals =
                currentSubject == null ? null : currentSubject.getPrincipals(AuthenticatedPrincipal.class);
        if(principals == null || principals.isEmpty())
        {
            return null;
        }
        else
        {
            return principals.iterator().next().getName();
        }
    }

    @Override
    public long getCreatedTime()
    {
        return _createdTime;
    }

    @Override
    public String getType()
    {
        return (String)getAttribute(TYPE);
    }


    @Override
    public Map<String,Number> getStatistics()
    {
        Collection<Statistic> stats = getStatistics(getClass());
        Map<String,Number> map = new HashMap<String,Number>();
        for(Statistic stat : stats)
        {
            map.put(stat.getName(), (Number) stat.getValue(this));
        }
        return map;
    }

    //=========================================================================================

    private static abstract class AttributeOrStatistic<C extends ConfiguredObject, T>
    {

        protected final String _name;
        protected final Class<T> _type;
        protected final Converter<T> _converter;
        protected final Method _getter;

        private AttributeOrStatistic(
                String name, final Method getter, Class<T> type)
        {
            _name = name;
            _getter = getter;
            _type = type;
            _converter = getConverter(type);

        }

        public String getName()
        {
            return _name;
        }

        public Class<T> getType()
        {
            return _type;
        }

        public T getValue(C configuredObject)
        {
            try
            {
                return (T) _getter.invoke(configuredObject);
            }
            catch (IllegalAccessException e)
            {
                Object o = configuredObject.getAttribute(_name);
                return _converter.convert(o, configuredObject);
            }
            catch (InvocationTargetException e)
            {
                Object o = configuredObject.getAttribute(_name);
                return _converter.convert(o, configuredObject);
            }

        }

    }

    private static final class Statistic<C extends ConfiguredObject, T extends Number> extends AttributeOrStatistic<C,T>
    {
        private Statistic(Class<C> clazz, String name, Class<T> type, final Method getter)
        {
            super(name, getter, type);
            addToStatisticsSet(clazz, this);
        }
    }

    public static final class Attribute<C extends ConfiguredObject, T> extends AttributeOrStatistic<C,T>
    {

        private final ManagedAttribute _annotation;

        private Attribute(Class<C> clazz,
                          String name,
                          Class<T> type,
                          final Method getter,
                          final ManagedAttribute annotation)
        {
            super(name, getter, type);
            _annotation = annotation;
            addToAttributesSet(clazz, this);
        }

        public ManagedAttribute getAnnotation()
        {
            return _annotation;
        }

        public T convert(final Object value, C object)
        {
            return _converter.convert(value, object);
        }
    }


    private static interface Converter<T>
    {
        T convert(Object value, final ConfiguredObject object);
    }

    private static final Converter<String> STRING_CONVERTER = new Converter<String>()
    {
        @Override
        public String convert(final Object value, final ConfiguredObject object)
        {
            return value == null ? null : value.toString();
        }
    };

    private static final Converter<UUID> UUID_CONVERTER = new Converter<UUID>()
    {
        @Override
        public UUID convert(final Object value, final ConfiguredObject object)
        {
            if(value instanceof UUID)
            {
                return (UUID) value;
            }
            else if(value instanceof String)
            {
                return UUID.fromString((String) value);
            }
            else if(value == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a UUID");
            }
        }
    };

    private static final Converter<Long> LONG_CONVERTER = new Converter<Long>()
    {

        @Override
        public Long convert(final Object value, final ConfiguredObject object)
        {
            if(value instanceof Long)
            {
                return (Long) value;
            }
            else if(value instanceof Number)
            {
                return ((Number) value).longValue();
            }
            else if(value instanceof String)
            {
                return Long.valueOf((String) value);
            }
            else if(value == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a Long");
            }
        }
    };

    private static final Converter<Integer> INT_CONVERTER = new Converter<Integer>()
    {

        @Override
        public Integer convert(final Object value, final ConfiguredObject object)
        {
            if(value instanceof Integer)
            {
                return (Integer) value;
            }
            else if(value instanceof Number)
            {
                return ((Number) value).intValue();
            }
            else if(value instanceof String)
            {
                return Integer.valueOf((String) value);
            }
            else if(value == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to an Integer");
            }
        }
    };


    private static final Converter<Short> SHORT_CONVERTER = new Converter<Short>()
    {

        @Override
        public Short convert(final Object value, final ConfiguredObject object)
        {
            if(value instanceof Short)
            {
                return (Short) value;
            }
            else if(value instanceof Number)
            {
                return ((Number) value).shortValue();
            }
            else if(value instanceof String)
            {
                return Short.valueOf((String) value);
            }
            else if(value == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a Short");
            }
        }
    };

    private static final Converter<Boolean> BOOLEAN_CONVERTER = new Converter<Boolean>()
    {

        @Override
        public Boolean convert(final Object value, final ConfiguredObject object)
        {
            if(value instanceof Boolean)
            {
                return (Boolean) value;
            }
            else if(value instanceof String)
            {
                return Boolean.valueOf((String) value);
            }
            else if(value == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a Boolean");
            }
        }
    };

    private static final Converter<List> LIST_CONVERTER = new Converter<List>()
    {
        @Override
        public List convert(final Object value, final ConfiguredObject object)
        {
            if(value instanceof List)
            {
                return (List) value;
            }
            else if(value == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a List");
            }
        }
    };

    private static final Converter<Collection> COLLECTION_CONVERTER = new Converter<Collection>()
    {
        @Override
        public Collection convert(final Object value, final ConfiguredObject object)
        {
            if(value instanceof Collection)
            {
                return (Collection) value;
            }
            else if(value == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a List");
            }
        }
    };

    private static final Converter<Map> MAP_CONVERTER = new Converter<Map>()
    {
        @Override
        public Map convert(final Object value, final ConfiguredObject object)
        {
            if(value instanceof Map)
            {
                return (Map) value;
            }
            else if(value == null)
            {
                return null;
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a Map");
            }
        }
    };

    private static final class EnumConverter<X extends Enum<X>> implements Converter<X>
    {
        private final Class<X> _klazz;

        private EnumConverter(final Class<X> klazz)
        {
            _klazz = klazz;
        }

        @Override
        public X convert(final Object value, final ConfiguredObject object)
        {
            if(value == null)
            {
                return null;
            }
            else if(_klazz.isInstance(value))
            {
                return (X) value;
            }
            else if(value instanceof String)
            {
                return Enum.valueOf(_klazz,(String) value);
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a " + _klazz.getName());
            }
        }
    }

    private static final class ConfiguredObjectConverter<X extends ConfiguredObject<X>> implements Converter<X>
    {
        private final Class<X> _klazz;

        private ConfiguredObjectConverter(final Class<X> klazz)
        {
            _klazz = klazz;
        }

        @Override
        public X convert(final Object value, final ConfiguredObject object)
        {
            if(value == null)
            {
                return null;
            }
            else if(_klazz.isInstance(value))
            {
                return (X) value;
            }
            else if(value instanceof UUID)
            {
                Collection<X> reachable = getReachableObjects(object,_klazz);
                for(X candidate : reachable)
                {
                    if(candidate.getId().equals(value))
                    {
                        return candidate;
                    }
                }
                throw new IllegalArgumentException("Cannot find a " + _klazz.getName() + " with id " + value);
            }
            else if(value instanceof String)
            {
                Collection<X> reachable = getReachableObjects(object,_klazz);
                for(X candidate : reachable)
                {
                    if(candidate.getName().equals(value))
                    {
                        return candidate;
                    }
                }
                try
                {
                    UUID id = UUID.fromString((String)value);
                    return convert(id, object);
                }
                catch (IllegalArgumentException e)
                {
                    throw new IllegalArgumentException("Cannot find a " + _klazz.getSimpleName() + " with name '" + value + "'");
                }
            }
            else
            {
                throw new IllegalArgumentException("Cannot convert type " + value.getClass() + " to a " + _klazz.getName());
            }
        }

    }

    private static <X> Converter<X> getConverter(final Class<X> type)
    {
        if(type == String.class)
        {
            return (Converter<X>) STRING_CONVERTER;
        }
        else if(type == Integer.class)
        {
            return (Converter<X>) INT_CONVERTER;
        }
        else if(type == Short.class)
        {
            return (Converter<X>) SHORT_CONVERTER;
        }
        else if(type == Long.class)
        {
            return (Converter<X>) LONG_CONVERTER;
        }
        else if(type == Boolean.class)
        {
            return (Converter<X>) BOOLEAN_CONVERTER;
        }
        else if(type == UUID.class)
        {
            return (Converter<X>) UUID_CONVERTER;
        }
        else if(Enum.class.isAssignableFrom(type))
        {
            return (Converter<X>) new EnumConverter((Class<? extends Enum>)type);
        }
        else if(List.class.isAssignableFrom(type))
        {
            return (Converter<X>) LIST_CONVERTER;
        }
        else if(Map.class.isAssignableFrom(type))
        {
            return (Converter<X>) MAP_CONVERTER;
        }
        else if(Collection.class.isAssignableFrom(type))
        {
            return (Converter<X>) COLLECTION_CONVERTER;
        }
        else if(ConfiguredObject.class.isAssignableFrom(type))
        {
            return (Converter<X>) new ConfiguredObjectConverter(type);
        }
        throw new IllegalArgumentException("Cannot create attributes of type " + type.getName());
    }

    private static void addToAttributesSet(final Class<? extends ConfiguredObject> clazz, final Attribute<?, ?> attribute)
    {
        synchronized (_allAttributes)
        {
            Collection<Attribute<?,?>> classAttributes = _allAttributes.get(clazz);
            if(classAttributes == null)
            {
                classAttributes = new ArrayList<Attribute<?, ?>>();
                for(Map.Entry<Class<? extends ConfiguredObject>, Collection<Attribute<?,?>>> entry : _allAttributes.entrySet())
                {
                    if(entry.getKey().isAssignableFrom(clazz))
                    {
                        classAttributes.addAll(entry.getValue());
                    }
                }
                _allAttributes.put(clazz, classAttributes);

            }
            for(Map.Entry<Class<? extends ConfiguredObject>, Collection<Attribute<?,?>>> entry : _allAttributes.entrySet())
            {
                if(clazz.isAssignableFrom(entry.getKey()))
                {
                    entry.getValue().add(attribute);
                }
            }

        }
    }
    private static void addToStatisticsSet(final Class<? extends ConfiguredObject> clazz, final Statistic<?, ?> statistic)
    {
        synchronized (_allStatistics)
        {
            Collection<Statistic<?,?>> classAttributes = _allStatistics.get(clazz);
            if(classAttributes == null)
            {
                classAttributes = new ArrayList<Statistic<?, ?>>();
                for(Map.Entry<Class<? extends ConfiguredObject>, Collection<Statistic<?,?>>> entry : _allStatistics.entrySet())
                {
                    if(entry.getKey().isAssignableFrom(clazz))
                    {
                        classAttributes.addAll(entry.getValue());
                    }
                }
                _allStatistics.put(clazz, classAttributes);

            }
            for(Map.Entry<Class<? extends ConfiguredObject>, Collection<Statistic<?,?>>> entry : _allStatistics.entrySet())
            {
                if(clazz.isAssignableFrom(entry.getKey()))
                {
                    entry.getValue().add(statistic);
                }
            }

        }
    }


    private static <X extends ConfiguredObject> void processAttributes(final Class<X> clazz)
    {
        synchronized (_allAttributes)
        {
            if(_allAttributes.containsKey(clazz))
            {
                return;
            }


            for(Class<?> parent : clazz.getInterfaces())
            {
                if(ConfiguredObject.class.isAssignableFrom(parent))
                {
                    processAttributes((Class<? extends ConfiguredObject>)parent);
                }
            }
            final Class<? super X> superclass = clazz.getSuperclass();
            if(superclass != null && ConfiguredObject.class.isAssignableFrom(superclass))
            {
                processAttributes((Class<? extends ConfiguredObject>) superclass);
            }

            final ArrayList<Attribute<?, ?>> attributeList = new ArrayList<Attribute<?, ?>>();
            final ArrayList<Statistic<?, ?>> statisticList = new ArrayList<Statistic<?, ?>>();

            _allAttributes.put(clazz, attributeList);
            _allStatistics.put(clazz, statisticList);

            for(Class<?> parent : clazz.getInterfaces())
            {
                if(ConfiguredObject.class.isAssignableFrom(parent))
                {
                    Collection<Attribute<?, ?>> attrs = _allAttributes.get(parent);
                    for(Attribute<?,?> attr : attrs)
                    {
                        if(!attributeList.contains(attr))
                        {
                            attributeList.add(attr);
                        }
                    }
                    Collection<Statistic<?, ?>> stats = _allStatistics.get(parent);
                    for(Statistic<?,?> stat : stats)
                    {
                        if(!statisticList.contains(stat))
                        {
                            statisticList.add(stat);
                        }
                    }
                }
            }
            if(superclass != null && ConfiguredObject.class.isAssignableFrom(superclass))
            {
                Collection<Attribute<?, ?>> attrs = _allAttributes.get(superclass);
                Collection<Statistic<?, ?>> stats = _allStatistics.get(superclass);
                for(Attribute<?,?> attr : attrs)
                {
                    if(!attributeList.contains(attr))
                    {
                        attributeList.add(attr);
                    }
                }
                for(Statistic<?,?> stat : stats)
                {
                    if(!statisticList.contains(stat))
                    {
                        statisticList.add(stat);
                    }
                }
            }


            for(Method m : clazz.getDeclaredMethods())
            {
                ManagedAttribute annotation = m.getAnnotation(ManagedAttribute.class);
                if(annotation != null)
                {
                    if(m.getParameterTypes().length != 0)
                    {
                        throw new IllegalArgumentException("ManagedAttribute annotation should only be added to no-arg getters");
                    }
                    Class<?> type = getType(m);
                    String name = getName(m, type);
                    Attribute<X,?> newAttr = new Attribute(clazz,name,type,m, annotation);

                }
                else
                {
                    ManagedStatistic statAnnotation = m.getAnnotation(ManagedStatistic.class);
                    if(statAnnotation != null)
                    {
                        if(m.getParameterTypes().length != 0)
                        {
                            throw new IllegalArgumentException("ManagedStatistic annotation should only be added to no-arg getters");
                        }
                        Class<?> type = getType(m);
                        if(!Number.class.isAssignableFrom(type))
                        {
                            throw new IllegalArgumentException("ManagedStatistic annotation should only be added to getters returning a Number type");
                        }
                        String name = getName(m, type);
                        Statistic<X,?> newStat = new Statistic(clazz,name,type,m);
                    }
                }
            }

            Map<String,Attribute<?,?>> attrMap = new HashMap<String, Attribute<?, ?>>();
            Map<String,Field> fieldMap = new HashMap<String, Field>();


            Collection<Attribute<?, ?>> attrCol = _allAttributes.get(clazz);
            for(Attribute<?,?> attr : attrCol)
            {
                attrMap.put(attr.getName(), attr);
                if(attr.getAnnotation().automate())
                {
                    fieldMap.put(attr.getName(), findField(attr, clazz));
                }

            }
            _allAttributeTypes.put(clazz, attrMap);
            _allAutomatedFields.put(clazz, fieldMap);
        }
    }

    private static Field findField(final Attribute<?, ?> attr, Class<?> objClass)
    {
        Class<?> clazz = objClass;
        while(clazz != null)
        {
            for(Field field : clazz.getDeclaredFields())
            {
                if(field.getAnnotation(ManagedAttributeField.class) != null && field.getName().equals("_" + attr.getName().replace('.','_')))
                {
                    field.setAccessible(true);
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
        }
        if(objClass.isInterface() || Modifier.isAbstract(objClass.getModifiers()))
        {
            return null;
        }
        throw new ServerScopedRuntimeException("Unable to find field definition for automated field " + attr.getName() + " in class " + objClass.getName());
    }

    private static String getName(final Method m, final Class<?> type)
    {
        String methodName = m.getName();
        String baseName;

        if(type == Boolean.class )
        {
            if((methodName.startsWith("get") || methodName.startsWith("has")) && methodName.length() >= 4)
            {
                baseName = methodName.substring(3);
            }
            else if(methodName.startsWith("is") && methodName.length() >= 3)
            {
                baseName = methodName.substring(2);
            }
            else
            {
                throw new IllegalArgumentException("Method name " + methodName + " does not conform to the required pattern for ManagedAttributes");
            }
        }
        else
        {
            if(methodName.startsWith("get") && methodName.length() >= 4)
            {
                baseName = methodName.substring(3);
            }
            else
            {
                throw new IllegalArgumentException("Method name " + methodName + " does not conform to the required pattern for ManagedAttributes");
            }
        }

        String name = baseName.length() == 1 ? baseName.toLowerCase() : baseName.substring(0,1).toLowerCase() + baseName.substring(1);
        name = name.replace('_','.');
        return name;
    }

    private static Class<?> getType(final Method m)
    {
        Class<?> type = m.getReturnType();
        if(type.isPrimitive())
        {
            if(type == Boolean.TYPE)
            {
                type = Boolean.class;
            }
            else if(type == Byte.TYPE)
            {
                type = Byte.class;
            }
            else if(type == Short.TYPE)
            {
                type = Short.class;
            }
            else if(type == Integer.TYPE)
            {
                type = Integer.class;
            }
            else if(type == Long.TYPE)
            {
                type = Long.class;
            }
            else if(type == Float.TYPE)
            {
                type = Float.class;
            }
            else if(type == Double.TYPE)
            {
                type = Double.class;
            }
            else if(type == Character.TYPE)
            {
                type = Character.class;
            }
        }
        return type;
    }

    public static <X extends ConfiguredObject> Collection<String> getAttributeNames(Class<X> clazz)
    {
        final Collection<Attribute<? super X, ?>> attrs = getAttributes(clazz);

        return new AbstractCollection<String>()
        {
            @Override
            public Iterator<String> iterator()
            {
                final Iterator<Attribute<? super X, ?>> underlyingIterator = attrs.iterator();
                return new Iterator<String>()
                {
                    @Override
                    public boolean hasNext()
                    {
                        return underlyingIterator.hasNext();
                    }

                    @Override
                    public String next()
                    {
                        return underlyingIterator.next().getName();
                    }

                    @Override
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size()
            {
                return attrs.size();
            }
        };

    }

    protected static <X extends ConfiguredObject> Collection<Attribute<? super X, ?>> getAttributes(final Class<X> clazz)
    {
        if(!_allAttributes.containsKey(clazz))
        {
            processAttributes(clazz);
        }
        final Collection<Attribute<? super X, ?>> attributes = (Collection) _allAttributes.get(clazz);
        return attributes;
    }


    protected static Collection<Statistic> getStatistics(final Class<? extends ConfiguredObject> clazz)
    {
        if(!_allStatistics.containsKey(clazz))
        {
            processAttributes(clazz);
        }
        final Collection<Statistic> statistics = (Collection) _allStatistics.get(clazz);
        return statistics;
    }


    private static Map<String, Attribute<?, ?>> getAttributeTypes(final Class<? extends ConfiguredObject> clazz)
    {
        if(!_allAttributeTypes.containsKey(clazz))
        {
            processAttributes(clazz);
        }
        return _allAttributeTypes.get(clazz);
    }

    private static Map<String, Field> getAutomatedFields(Class<? extends ConfiguredObject> clazz)
    {
        if(!_allAutomatedFields.containsKey(clazz))
        {
            processAttributes(clazz);
        }
        return _allAutomatedFields.get(clazz);
    }

    private static <X extends ConfiguredObject<X>> Collection<X> getReachableObjects(final ConfiguredObject<?> object,
                                                                                     final Class<X> clazz)
    {
        Class<? extends ConfiguredObject> category = getCategory(object.getClass());
        Class<? extends ConfiguredObject> ancestorClass = getAncestorClassWithGivenDescendant(category, clazz);
        if(ancestorClass != null)
        {
            ConfiguredObject ancestor = getAncestor(ancestorClass, category, object);
            if(ancestor != null)
            {
                return getAllDescendants(ancestor, ancestorClass, clazz);
            }
        }
        return null;
    }

    private static <X extends ConfiguredObject<X>> Collection<X> getAllDescendants(final ConfiguredObject ancestor,
                                                                                   final Class<? extends ConfiguredObject> ancestorClass,
                                                                                   final Class<X> clazz)
    {
        Set<X> descendants = new HashSet<X>();
        for(Class<? extends ConfiguredObject> childClass : Model.getInstance().getChildTypes(ancestorClass))
        {
            Collection<? extends ConfiguredObject> children = ancestor.getChildren(childClass);
            if(childClass == clazz)
            {

                if(children != null)
                {
                    descendants.addAll((Collection<X>)children);
                }
            }
            else
            {
                if(children != null)
                {
                    for(ConfiguredObject child : children)
                    {
                        descendants.addAll(getAllDescendants(child, childClass, clazz));
                    }
                }
            }
        }
        return descendants;
    }

    private static ConfiguredObject getAncestor(final Class<? extends ConfiguredObject> ancestorClass,
                                                final Class<? extends ConfiguredObject> category,
                                                final ConfiguredObject<?> object)
    {
        if(ancestorClass.isInstance(object))
        {
            return object;
        }
        else
        {
            for(Class<? extends ConfiguredObject> parentClass : Model.getInstance().getParentTypes(category))
            {
                ConfiguredObject parent = object.getParent(parentClass);
                ConfiguredObject ancestor = getAncestor(ancestorClass, parentClass, parent);
                if(ancestor != null)
                {
                    return ancestor;
                }
            }
        }
        return null;
    }

    private static Class<? extends ConfiguredObject> getAncestorClassWithGivenDescendant(
            final Class<? extends ConfiguredObject> category,
            final Class<? extends ConfiguredObject> descendantClass)
    {
        Model model = Model.getInstance();
        Collection<Class<? extends ConfiguredObject>> candidateClasses =
                Collections.<Class<? extends ConfiguredObject>>singleton(category);
        while(!candidateClasses.isEmpty())
        {
            for(Class<? extends ConfiguredObject> candidate : candidateClasses)
            {
                if(hasDescendant(candidate, descendantClass))
                {
                    return candidate;
                }
            }
            Set<Class<? extends ConfiguredObject>> previous = new HashSet<Class<? extends ConfiguredObject>>(candidateClasses);
            candidateClasses = new HashSet<Class<? extends ConfiguredObject>>();
            for(Class<? extends ConfiguredObject> prev : previous)
            {
                candidateClasses.addAll(model.getParentTypes(prev));
            }
        }
        return null;
    }

    private static boolean hasDescendant(final Class<? extends ConfiguredObject> candidate,
                                         final Class<? extends ConfiguredObject> descendantClass)
    {
        int oldSize = 0;
        Model model = Model.getInstance();

        Set<Class<? extends ConfiguredObject>> allDescendants = new HashSet<Class<? extends ConfiguredObject>>(Collections.singleton(candidate));
        while(allDescendants.size() > oldSize)
        {
            oldSize = allDescendants.size();
            Set<Class<? extends ConfiguredObject>> prev = new HashSet<Class<? extends ConfiguredObject>>(allDescendants);
            for(Class<? extends ConfiguredObject> clazz : prev)
            {
                allDescendants.addAll(model.getChildTypes(clazz));
            }
        }
        return allDescendants.contains(descendantClass);
    }

    private static Class<? extends ConfiguredObject> getCategory(final Class<?> clazz)
    {
        ManagedObject annotation = clazz.getAnnotation(ManagedObject.class);
        if(annotation != null && annotation.category())
        {
            return (Class<? extends ConfiguredObject>) clazz;
        }
        for(Class<?> iface : clazz.getInterfaces() )
        {
            Class<? extends ConfiguredObject> cat = getCategory(iface);
            if(cat != null)
            {
                return cat;
            }
        }
        if(clazz.getSuperclass() != null)
        {
            return getCategory(clazz.getSuperclass());
        }
        return null;
    }


}
