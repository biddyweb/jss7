/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.indicator;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javolution.xml.XMLObjectReader;
import javolution.xml.XMLObjectWriter;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author amit bhayani
 *
 */
public class AddressIndicatorTest {

    /**
	 *
	 */
    public AddressIndicatorTest() {
        // TODO Auto-generated constructor stub
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @BeforeMethod
    public void setUp() {
    }

    @AfterMethod
    public void tearDown() {
    }

    @Test(groups = { "functional.decode", "indicator" })
    public void testDecode() throws Exception {
        byte b = 0x42;
        AddressIndicator ai = new AddressIndicator(b);
        assertFalse(ai.pcPresent());
        assertTrue(ai.ssnPresent());
        assertEquals(ai.getGlobalTitleIndicator(), GlobalTitleIndicator.NO_GLOBAL_TITLE_INCLUDED);
        assertEquals(ai.getRoutingIndicator(), RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN);
    }

    @Test(groups = { "functional.encode", "indicator" })
    public void testEncode() throws Exception {
        AddressIndicator ai = new AddressIndicator(false, true, RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN,
                GlobalTitleIndicator.NO_GLOBAL_TITLE_INCLUDED);
        assertEquals((int) ai.getValue(), 66);
    }

    @Test(groups = { "functional.encode", "indicator" })
    public void testSerialize() throws Exception {
        AddressIndicator ai = new AddressIndicator(false, true, RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN,
                GlobalTitleIndicator.NO_GLOBAL_TITLE_INCLUDED);

        // Writes
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XMLObjectWriter writer = XMLObjectWriter.newInstance(output);
        writer.setIndentation("\t"); // Optional (use tabulation for
        // indentation).
        writer.write(ai, "AddressIndicator", AddressIndicator.class);
        writer.close();

        System.out.println(output.toString());

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        XMLObjectReader reader = XMLObjectReader.newInstance(input);
        AddressIndicator aiOut = reader.read("AddressIndicator", AddressIndicator.class);

        assertFalse(aiOut.pcPresent());
        assertTrue(aiOut.ssnPresent());
        assertEquals(aiOut.getGlobalTitleIndicator(), GlobalTitleIndicator.NO_GLOBAL_TITLE_INCLUDED);
        assertEquals(aiOut.getRoutingIndicator(), RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN);
    }

}
