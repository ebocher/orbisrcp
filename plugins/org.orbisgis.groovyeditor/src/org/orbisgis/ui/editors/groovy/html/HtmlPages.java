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
package org.orbisgis.ui.editors.groovy.html;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

/**
 * Files of the pages displayed in the HTML views: the pages written by the scripts and the Vega libraries,
 * in a folder of the plugin state.
 */
public final class HtmlPages {

    private final Path folder;
    private final Function<String, URL> bundleEntry;
    private boolean librariesDeployed = false;

    /**
     * @param folder      folder of the pages
     * @param bundleEntry gives the URL of a file of the bundle (for the Vega libraries)
     */
    public HtmlPages(File folder, Function<String, URL> bundleEntry) {
        this.folder = folder.toPath();
        this.bundleEntry = bundleEntry;
    }

    /**
     * Write a page.
     *
     * @param title title of the view, gives the file name
     * @param html  content of the page
     * @return the page file
     */
    public synchronized File write(String title, String html) throws IOException {
        Files.createDirectories(folder);
        Path page = folder.resolve("page-" + VegaPages.fileName(title) + "-" + Integer.toHexString(title.hashCode()) + ".html");
        Files.writeString(page, html, StandardCharsets.UTF_8);
        return page.toFile();
    }

    /**
     * Copy the Vega libraries next to the pages, once.
     *
     * @return the URL of the folder of the libraries, relative to the pages
     */
    public synchronized String deployVegaLibraries() throws IOException {
        Path target = folder.resolve("vega");
        if (!librariesDeployed) {
            Files.createDirectories(target);
            for (String library : VegaPages.LIBRARIES) {
                URL url = bundleEntry.apply(VegaPages.LIBRARY_FOLDER + "/" + library);
                if (url == null) {
                    throw new IOException("Missing library in the plugin: " + library);
                }
                try (InputStream stream = url.openStream()) {
                    Files.copy(stream, target.resolve(library), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            librariesDeployed = true;
        }
        return "vega";
    }
}
