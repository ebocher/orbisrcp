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

import org.orbisgis.core.logger.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Jars and directories added by the user to the classpath of the Groovy scripts (see the Classpath dialog).
 *
 * A path added here is immediately available in the open Groovy sessions. A removed path stays loaded in
 * the open sessions until their context is reset.
 */
public class ClassPathHandler {

	private static final Logger LOGGER = new Logger(ClassPathHandler.class);

	private static final LinkedHashSet<URL> urls = new LinkedHashSet<>();

	private static final List<Consumer<URL>> listeners = new CopyOnWriteArrayList<>();

	private ClassPathHandler() {
	}

	/**
	 * Add a path (jar file or directory) to the list of urls.
	 *
	 * @param path the path of the file or directory chosen by the user, may be null (dialog cancelled)
	 */
	public static void addPathToUrlList(String path) {
		if (path == null || path.isBlank()) {
			return;
		}
		try {
			URL url = new File(path).toURI().toURL();
			boolean added;
			synchronized (urls) {
				added = urls.add(url);
			}
			if (added) {
				listeners.forEach(listener -> listener.accept(url));
			}
		} catch (MalformedURLException e) {
			LOGGER.error("Invalid path '" + path + "'", e);
		}
	}

	/**
	 * @return a copy of the user classpath, in insertion order
	 */
	public static LinkedHashSet<URL> getUrls() {
		synchronized (urls) {
			return new LinkedHashSet<>(urls);
		}
	}

	/**
	 * @return a copy of the user classpath, in insertion order
	 */
	public static URL[] getUrlsInArray() {
		return getUrls().toArray(new URL[0]);
	}

	/**
	 * Remove an url from the user classpath.
	 *
	 * @param url the url to remove
	 */
	public static void removeURL(URL url) {
		synchronized (urls) {
			urls.remove(url);
		}
	}

	/**
	 * Remove the url at the given index of the user classpath.
	 *
	 * @param index the index of the url to remove
	 */
	public static void deleteOneURL(int index) {
		synchronized (urls) {
			List<URL> list = new ArrayList<>(urls);
			if (index >= 0 && index < list.size()) {
				urls.remove(list.get(index));
			}
		}
	}

	/**
	 * Register a listener called each time a new url is added.
	 */
	public static void addListener(Consumer<URL> listener) {
		listeners.add(listener);
	}

	public static void removeListener(Consumer<URL> listener) {
		listeners.remove(listener);
	}
}
