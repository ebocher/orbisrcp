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
package org.orbisgis.ui.editors.groovy.internal;

import org.eclipse.osgi.util.NLS;

/**
 * Texts of the user interface: GroovyMessages.properties (English), GroovyMessages_fr.properties (French).
 */
public final class GroovyMessages extends NLS {

    private static final String BUNDLE_NAME = "org.orbisgis.ui.editors.groovy.internal.GroovyMessages";

    public static String browser_unavailable;
    public static String save_title;
    public static String save_error;
    public static String classpath_title;
    public static String classpath_header;
    public static String classpath_column_library;
    public static String classpath_column_version;
    public static String classpath_column_group;
    public static String classpath_column_origin;
    public static String classpath_column_path;
    public static String classpath_add_jar;
    public static String classpath_add_folder;
    public static String classpath_remove;
    public static String classpath_copy;
    public static String classpath_note;
    public static String classpath_origin_added;
    public static String classpath_origin_grab;
    public static String classpath_origin_other;
    public static String output_copy;
    public static String output_copy_all;
    public static String output_select_all;
    public static String editor_content_assist;
    public static String editor_format;
    public static String editor_format_tooltip;

    static {
        NLS.initializeMessages(BUNDLE_NAME, GroovyMessages.class);
    }

    private GroovyMessages() {
    }
}
