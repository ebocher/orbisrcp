/*
 * Groovy Editor (GE) is a library that brings a groovy console to the Eclipse RCP.
 * GE is developed by CNRS http://www.cnrs.fr/.
 *
 * GE is part of the OrbisGIS project. GE is free software;
 * you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * GE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details http://www.gnu.org/licenses.
 *
 *
 *For more information, please consult: http://www.orbisgis.org
 *or contact directly: info_at_orbisgis.org
 *
 */
package org.orbisgis.ui.editors.groovy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.orbisgis.ui.editors.groovy.ClassPathHandler.*;

class ClassPathHandlerTest {

    private static URL url(String path) throws MalformedURLException {
        return new File(path).toURI().toURL();
    }

    @AfterEach
    void afterEach() {
        for (URL url : getUrls()) {
            removeURL(url);
        }
    }

    @Test
    void addDirectoryTest() throws MalformedURLException {
        addPathToUrlList("/notAJar");
        assertEquals(List.of(url("/notAJar")), new ArrayList<>(getUrls()));
    }

    @Test
    void addJarTest() throws MalformedURLException {
        addPathToUrlList("/aJar.jar");
        assertEquals(List.of(url("/aJar.jar")), new ArrayList<>(getUrls()));
    }

    @Test
    void addNullOrDuplicateTest() {
        addPathToUrlList(null);
        addPathToUrlList("/aJar.jar");
        addPathToUrlList("/aJar.jar");
        assertEquals(1, getUrlsInArray().length);
    }

    @Test
    void getUrlsInArrayIfEmptyTest() {
        assertEquals(0, getUrlsInArray().length);
    }

    @Test
    void deleteOneURLTest() throws MalformedURLException {
        addPathToUrlList("/notAJar");
        addPathToUrlList("/aJar.jar");
        deleteOneURL(1);
        assertEquals(1, getUrlsInArray().length);
        assertEquals(url("/notAJar"), getUrlsInArray()[0]);
    }

    @Test
    void listenerTest() throws MalformedURLException {
        List<URL> added = new ArrayList<>();
        java.util.function.Consumer<URL> listener = added::add;
        addListener(listener);
        try {
            addPathToUrlList("/aJar.jar");
        } finally {
            removeListener(listener);
        }
        assertEquals(List.of(url("/aJar.jar")), added);
    }
}
