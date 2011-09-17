/*
 * Copyright 2011 - Alistair Rutherford - www.netthreads.co.uk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package com.netthreads.mavenize;

import com.netthreads.mavenize.model.mapping.Mapping;
import java.util.List;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class LoadMappingsTest extends TestCase
{
    private static final String MAP_FILE_PATH = "/mapping.xml";

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite(LoadMappingsTest.class);
    }

    /**
     * Test loading mapping definition file.
     * 
     */
    public void testApp()
    {
        Pommel pommel = new Pommel();

        List<Mapping> mappings = pommel.loadDependencyMappings(MAP_FILE_PATH);

        assertTrue(mappings != null);

        assertTrue(mappings.size() > 0);
    }
}
