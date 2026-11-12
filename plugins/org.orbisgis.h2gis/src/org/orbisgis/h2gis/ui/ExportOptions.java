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
package org.orbisgis.h2gis.ui;

import org.eclipse.osgi.util.NLS;
import org.orbisgis.h2gis.internal.H2GISMessages;

import java.util.List;

/**
 * The columns and the filter of a table to export.
 *
 * @param columns     the columns, as SQL identifiers; null for all the columns
 * @param filter      an SQL condition (without WHERE), null for all the rows
 * @param hasGeometry false if the columns are chosen without a geometry column
 */
public record ExportOptions(List<String> columns, String filter, boolean hasGeometry) {

    /** All the columns, all the rows */
    public static final ExportOptions ALL = new ExportOptions(null, null, true);

    /**
     * @return "All columns", "3 columns", "All columns, filtered"...
     */
    public String describe() {
        String text = columns == null ? H2GISMessages.export_all_columns
                : NLS.bind(H2GISMessages.export_n_columns, columns.size());
        return filter == null ? text : NLS.bind(H2GISMessages.export_filtered, text);
    }
}
