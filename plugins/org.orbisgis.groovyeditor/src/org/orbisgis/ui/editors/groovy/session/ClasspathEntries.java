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
 */
package org.orbisgis.ui.editors.groovy.session;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The libraries of a Groovy console: the jars loaded with @Grab and the jars or folders added by the user.
 */
public final class ClasspathEntries {

    /** Where a library comes from */
    public enum Origin {
        /** Jar or folder added with the Classpath dialog */
        ADDED("Added"),
        /** Library loaded with @Grab (or one of its dependencies) */
        GRAB("@Grab"),
        /** Still loaded but removed from the classpath, or added by a script */
        OTHER("Loaded");

        private final String label;

        Origin(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * A library.
     *
     * @param name    jar or folder name, or module name for @Grab
     * @param version version of a @Grab library, empty otherwise
     * @param group   group of a @Grab library, empty otherwise
     * @param origin  where it comes from
     * @param url     location
     * @param loaded  true if the library is loaded in the console
     */
    public record Entry(String name, String version, String group, Origin origin, URL url, boolean loaded) {

        /**
         * @return the file path of the library
         */
        public String path() {
            try {
                return new File(url.toURI()).getPath();
            } catch (Exception e) {
                return url.toString();
            }
        }
    }

    private ClasspathEntries() {
    }

    /**
     * @param loaded the URLs of the class loader of the console
     * @param added  the jars and folders added by the user
     * @return the libraries: the added ones first, then the @Grab ones sorted by group and name, then the others
     */
    public static List<Entry> list(URL[] loaded, Collection<URL> added) {
        Set<String> loadedKeys = new LinkedHashSet<>();
        for (URL url : loaded) {
            loadedKeys.add(key(url));
        }
        Set<String> addedKeys = new LinkedHashSet<>();
        List<Entry> entries = new ArrayList<>();
        for (URL url : added) {
            addedKeys.add(key(url));
            entries.add(new Entry(fileName(url), "", "", Origin.ADDED, url, loadedKeys.contains(key(url))));
        }
        List<Entry> others = new ArrayList<>();
        for (URL url : loaded) {
            if (addedKeys.contains(key(url))) {
                continue;
            }
            Entry grab = grabEntry(url);
            others.add(grab != null ? grab : new Entry(fileName(url), "", "", Origin.OTHER, url, true));
        }
        others.sort(Comparator.comparing(Entry::origin)
                .thenComparing(Entry::group, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        entries.addAll(others);
        return entries;
    }

    /**
     * A jar of the Grape cache: .../grapes/{group}/{module}/{jars|bundles}/{artifact}-{version}.jar
     */
    static Entry grabEntry(URL url) {
        String path = url.getPath().replace('\\', '/');
        String[] segments = path.split("/");
        for (int i = segments.length - 5; i >= 0; i--) {
            if (segments[i].equals("grapes")) {
                String group = segments[i + 1];
                String module = segments[i + 2];
                String file = segments[segments.length - 1];
                String base = file.endsWith(".jar") ? file.substring(0, file.length() - 4) : file;
                String version = base.startsWith(module + "-") ? base.substring(module.length() + 1) : "";
                return new Entry(module, version, group, Origin.GRAB, url, true);
            }
        }
        return null;
    }

    private static String fileName(URL url) {
        String path = url.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        return url.getPath().endsWith("/") ? name + "/" : name;
    }

    private static String key(URL url) {
        try {
            return URI.create(url.toString()).normalize().toString();
        } catch (IllegalArgumentException e) {
            return url.toString();
        }
    }
}
