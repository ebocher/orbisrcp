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

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.GroovyConsoleContent;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.Style;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Class loader of a console context. It loads private copies of the jars.
 *
 * A jar must not change while a class loader reads it: the classes and resources not loaded yet would be read
 * from a different file ("JAR entry ... not found", "invalid LOC header"...). The jars of the Grape cache are
 * rewritten by Ivy (local -SNAPSHOT builds, refreshed modules...) and the jars added by the user are rebuilt.
 * The copies are deleted when the context is reset or closed. A jar changed on disk is used after a reset.
 */
public class SessionClassLoader extends GroovyClassLoader {

    private static final Logger LOGGER = new Logger(SessionClassLoader.class);

    private static volatile File copyRoot = new File(System.getProperty("java.io.tmpdir"), "orbisrcp-groovy-jars");

    private record LoadedJar(File copy, long size, long lastModified) {
    }

    private final Map<String, LoadedJar> jars = new LinkedHashMap<>();
    private final List<URL> originalUrls = new ArrayList<>();
    private final List<String> reportedChanges = new ArrayList<>();
    private File copyDirectory;

    public SessionClassLoader(ClassLoader parent, CompilerConfiguration config) {
        super(parent, config);
    }

    /**
     * Set the folder of the copies and delete the copies left by a previous DBeaver session.
     */
    public static void setCopyRoot(File root) {
        copyRoot = root;
        deleteTree(root.toPath());
    }

    @Override
    public void addURL(URL url) {
        File jar = jarFile(url);
        if (jar == null) {
            addOriginal(url, url);
            return;
        }
        synchronized (jars) {
            String key = jar.getAbsolutePath();
            LoadedJar loaded = jars.get(key);
            if (loaded != null) {
                reportChange(jar, loaded);
                return;
            }
            URL target = url;
            File copy = null;
            try {
                copy = copy(jar);
                target = copy.toURI().toURL();
            } catch (IOException e) {
                LOGGER.warn("Unable to copy " + jar + ", it is loaded from its location.", e);
            }
            jars.put(key, new LoadedJar(copy, jar.length(), jar.lastModified()));
            addOriginal(url, target);
        }
    }

    private void addOriginal(URL original, URL target) {
        synchronized (originalUrls) {
            if (!originalUrls.contains(original)) {
                originalUrls.add(original);
            }
        }
        super.addURL(target);
    }

    /**
     * @return the locations of the libraries of the context (the jars as given, not their copies)
     */
    public URL[] getOriginalURLs() {
        synchronized (originalUrls) {
            return originalUrls.toArray(new URL[0]);
        }
    }

    private void reportChange(File jar, LoadedJar loaded) {
        if (!jar.isFile() || reportedChanges.contains(jar.getAbsolutePath())) {
            return;
        }
        boolean changed = jar.length() != loaded.size();
        if (!changed && jar.lastModified() != loaded.lastModified() && loaded.copy() != null) {
            try {
                changed = Files.mismatch(jar.toPath(), loaded.copy().toPath()) != -1;
            } catch (IOException e) {
                changed = true;
            }
        }
        if (changed) {
            reportedChanges.add(jar.getAbsolutePath());
            GroovyConsoleContent.appendLine("  " + jar.getName() + " changed since it was loaded: Reset the "
                    + "context to use the new version.", Style.INFO);
        }
    }

    private File copy(File jar) throws IOException {
        if (copyDirectory == null) {
            Files.createDirectories(copyRoot.toPath());
            copyDirectory = Files.createTempDirectory(copyRoot.toPath(), "context").toFile();
        }
        // The name and the path end of the Grape cache are kept: the Classpath dialog reads them
        Path relative = grapePath(jar);
        Path target = copyDirectory.toPath().resolve(relative != null ? relative
                : Path.of("added", String.valueOf(jars.size()), jar.getName()));
        Files.createDirectories(target.getParent());
        Files.copy(jar.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toFile();
    }

    private static Path grapePath(File jar) {
        Path path = jar.toPath().toAbsolutePath();
        for (int i = path.getNameCount() - 5; i >= 0; i--) {
            if (path.getName(i).toString().equals("grapes")) {
                return path.subpath(i, path.getNameCount());
            }
        }
        return null;
    }

    private static File jarFile(URL url) {
        if (!"file".equals(url.getProtocol())) {
            return null;
        }
        try {
            File file = new File(url.toURI());
            return file.isFile() && file.getName().toLowerCase().endsWith(".jar") ? file : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            if (copyDirectory != null) {
                deleteTree(copyDirectory.toPath());
            }
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            LOGGER.warn("Unable to delete " + root, e);
        }
    }
}
