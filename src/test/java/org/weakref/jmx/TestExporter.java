/**
 *  Copyright 2009 Martin Traverso
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.weakref.jmx;

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.MBeanRegistrationException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.NotBoundException;

public class TestExporter
{
    private MBeanServer server;
    private ObjectName objectName;
    private SimpleObject object;

   
    @BeforeTest
    private void setup()
            throws IOException, MalformedObjectNameException, NotBoundException
    {
        server = ManagementFactory.getPlatformMBeanServer();

        objectName = Util.getUniqueObjectName();
        object = new SimpleObject();

        MBeanExporter exporter = new MBeanExporter(ManagementFactory.getPlatformMBeanServer());
        exporter.export(objectName.getCanonicalName(), object);
    }

    @AfterTest
    public void teardown()
            throws IOException, InstanceNotFoundException, MBeanRegistrationException
    {
        server.unregisterMBean(objectName);
    }

    @Test(dataProvider = "fixtures")
    public void testGet(String attribute, boolean isIs, Object[] values, Class clazz)
            throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        String methodName = "set" + attribute;
        Method setter = object.getClass().getMethod(methodName, clazz);

        for (Object value : values) {
            setter.invoke(object, value);

            Assert.assertEquals(server.getAttribute(objectName, attribute), value);
        }
    }


    @Test(dataProvider = "fixtures")
    public void testSet(String attribute, boolean isIs, Object[] values, Class clazz)
            throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InvalidAttributeValueException
    {
        String methodName = (isIs ? "is" : "get") + attribute;
        Method getter = object.getClass().getMethod(methodName);

        for (Object value : values) {
            server.setAttribute(objectName, new javax.management.Attribute(attribute, value));

            Assert.assertEquals(getter.invoke(object), value);
        }
    }

    @Test
    public void testSetFailsOnNotManaged() throws InstanceNotFoundException, IOException, InvalidAttributeValueException, ReflectionException, AttributeNotFoundException, MBeanException
    {
        object.setNotManaged(1);
        try {
            server.setAttribute(objectName, new javax.management.Attribute("NotManaged", 2));
            Assert.fail("Should not allow setting unmanaged attribute");
        }
        catch (AttributeNotFoundException e) {
            // ignore
        }

        Assert.assertEquals(object.getNotManaged(), 1);
    }

    @Test
    public void testGetFailsOnNotManaged() throws InstanceNotFoundException, IOException, InvalidAttributeValueException, ReflectionException, AttributeNotFoundException, MBeanException
    {
        try {
            server.getAttribute(objectName, "NotManaged");
            Assert.fail("Should not allow getting unmanaged attribute");
        }
        catch (AttributeNotFoundException e) {
            // ignore
        }
    }

    @Test
    public void testGetFailsOnWriteOnly() throws InstanceNotFoundException, IOException, ReflectionException, MBeanException
    {
        try {
            server.getAttribute(objectName, "WriteOnly");
            Assert.fail("Should not allow getting write-only attribute");
        }
        catch (AttributeNotFoundException e) {
            // ignore
        }
    }

    @Test
    public void testSetFailsOnReadOnly() throws InstanceNotFoundException, IOException, ReflectionException, MBeanException, InvalidAttributeValueException
    {
        object.setReadOnly(1);
        try {
            server.setAttribute(objectName, new javax.management.Attribute("ReadOnly", 2));
            Assert.fail("Should not allow setting read-only attribute");
        }
        catch (AttributeNotFoundException e) {
            // ignore
        }

        Assert.assertEquals(object.getReadOnly(), 1);
    }

    @Test
    public void testDescription()
    {
        // TODO: make sure description is parsed properly and makes it into the remove mbean    
    }

    @Test(dataProvider = "fixtures")
    public void testOperation(String attribute, boolean isIs, Object[] values, Class clazz)
            throws InstanceNotFoundException, IOException, ReflectionException, MBeanException
    {
        for (Object value : values) {
            Assert.assertEquals(server.invoke(objectName, "echo", new Object[]{value},
                    new String[]{Object.class.getName()}), value);
        }
    }

    @DataProvider(name = "fixtures")
    private Object[][] getFixtures()
    {
        return new Object[][]{

                new Object[]{"BooleanValue", true, new Object[]{true, false}, Boolean.TYPE},
                new Object[]{"BooleanBoxedValue", true, new Object[]{true, false, null}, Boolean.class},
                new Object[]{"ByteValue", false, new Object[]{Byte.MAX_VALUE, Byte.MIN_VALUE, (byte) 0}, Byte.TYPE},
                new Object[]{"ByteBoxedValue", false, new Object[]{Byte.MAX_VALUE, Byte.MIN_VALUE, (byte) 0, null}, Byte.class},

                new Object[]{"ShortValue", false, new Object[]{Short.MAX_VALUE, Short.MIN_VALUE, (short) 0}, Short.TYPE},
                new Object[]{"ShortBoxedValue", false, new Object[]{Short.MAX_VALUE, Short.MIN_VALUE, (short) 0, null}, Short.class},

                new Object[]{"IntegerValue", false, new Object[]{Integer.MAX_VALUE, Integer.MIN_VALUE, 0}, Integer.TYPE},
                new Object[]{"IntegerBoxedValue", false, new Object[]{Integer.MAX_VALUE, Integer.MIN_VALUE, 0, null}, Integer.class},

                new Object[]{"LongValue", false, new Object[]{Long.MAX_VALUE, Long.MIN_VALUE, 0L}, Long.TYPE},
                new Object[]{"LongBoxedValue", false, new Object[]{Long.MAX_VALUE, Long.MIN_VALUE, 0L, null}, Long.class},

                new Object[]{"FloatValue", false, new Object[]{-Float.MIN_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, 0f, Float.NaN}, Float.TYPE},
                new Object[]{"FloatBoxedValue", false, new Object[]{-Float.MIN_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, 0f, Float.NaN, null}, Float.class},

                new Object[]{"DoubleValue", false, new Object[]{-Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MIN_VALUE, 0.0, Double.NaN}, Double.TYPE},
                new Object[]{"DoubleBoxedValue", false, new Object[]{-Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MIN_VALUE, 0.0, Double.NaN}, Double.class},

                new Object[]{"StringValue", false, new Object[]{null, "hello there"}, String.class},

                new Object[]{"ObjectValue", false, new Object[]{"random object", 1, true}, Object.class}

        };
    }
}


