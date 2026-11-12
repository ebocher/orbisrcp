/*
 * H2GIS ecplise plugin to register a H2GIS spatial database to 
 * DBeaver, the  Universal Database Manager
 *
 * For more information, please consult: <http://www.h2gis.org/>
 * or contact directly: info_at_h2gis.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.orbisgis.h2gis.internal;

import org.eclipse.osgi.util.NLS;

/**
 * Texts of the user interface: H2GISMessages.properties (English), H2GISMessages_fr.properties (French).
 */
public final class H2GISMessages extends NLS {

    private static final String BUNDLE_NAME = "org.orbisgis.h2gis.internal.H2GISMessages";

    public static String connection_schema;
    public static String import_title;
    public static String import_shell;
    public static String import_message;
    public static String import_file_dialog;
    public static String import_column_file;
    public static String import_column_format;
    public static String import_column_table;
    public static String import_format_unsupported;
    public static String import_format_several;
    public static String import_table_prefix_tooltip;
    public static String import_replace;
    public static String import_button;
    public static String import_error_name;
    public static String import_error_duplicate;
    public static String import_error_none;
    public static String import_job;
    public static String import_task;
    public static String import_summary_title;
    public static String import_summary_verb;
    public static String import_details_several;
    public static String export_title;
    public static String export_message;
    public static String export_format;
    public static String export_folder;
    public static String export_browse;
    public static String export_column_table;
    public static String export_column_file;
    public static String export_replace;
    public static String export_button;
    public static String canceled;
    public static String view_script_button;
    public static String import_script_title;
    public static String export_script_title;
    public static String export_error_folder;
    public static String export_error_name;
    public static String export_error_path;
    public static String export_error_duplicate;
    public static String export_error_exists;
    public static String export_error_no_folder;
    public static String export_job;
    public static String export_summary_verb;
    public static String job_connect;
    public static String job_connect_error;
    public static String result_stopped;
    public static String result_done_one;
    public static String result_done;
    public static String result_partial;
    public static String result_column_rows;
    public static String result_column_result;
    public static String result_imported;
    public static String result_exported;
    public static String result_failed;
    public static String result_select;
    public static String import_filter_supported;
    public static String import_filter_all;
    public static String export_column_rows;
    public static String export_columns_button;
    public static String export_all_columns;
    public static String export_n_columns;
    public static String export_filtered;
    public static String export_warning_geometry;
    public static String columns_title;
    public static String columns_heading;
    public static String columns_message;
    public static String columns_column;
    public static String columns_type;
    public static String columns_all;
    public static String columns_none;
    public static String columns_filter;
    public static String columns_filter_tooltip;
    public static String columns_count;
    public static String columns_count_result;
    public static String columns_error_none;
    public static String columns_warning_geometry;

    static {
        NLS.initializeMessages(BUNDLE_NAME, H2GISMessages.class);
    }

    private H2GISMessages() {
    }
}
