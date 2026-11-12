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
package org.orbisgis.h2gis.engine;

import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.orbisgis.h2gis.exporter.TableExport;
import org.orbisgis.h2gis.importer.FileFormat;
import org.orbisgis.h2gis.importer.FileImport;

/**
 * Runs the imports and the exports of files of a kind of connection.
 *
 * The dialogs, the jobs and the summaries of the import and export menus are shared: an engine gives what
 * depends on the database. {@link SqlEngine} runs the H2GIS SQL functions in an H2GIS connection; another
 * plugin (postgis-io) gives an engine for PostgreSQL/PostGIS.
 */
public interface FileIOEngine {

    /**
     * Imports and exports on an open connection.
     */
    interface Session extends AutoCloseable {

        /**
         * @return the result; the errors are in the result, not thrown
         */
        FileImport.Result importFile(FileImport fileImport);

        /**
         * @return the result; the errors are in the result, not thrown
         */
        TableExport.Result exportTable(TableExport export);

        /**
         * Import a file; the import can stop when the monitor is canceled.
         */
        default FileImport.Result importFile(FileImport fileImport, DBRProgressMonitor monitor) {
            return importFile(fileImport);
        }

        /**
         * Export a table; the export can stop when the monitor is canceled.
         */
        default TableExport.Result exportTable(TableExport export, DBRProgressMonitor monitor) {
            return exportTable(export);
        }

        @Override
        void close() throws Exception;
    }

    /**
     * @return true if the engine works with this connection
     */
    boolean accepts(DBPDataSourceContainer container);

    /**
     * Open a session on a connection, connected by the caller.
     */
    Session open(DBPDataSourceContainer container, DBRProgressMonitor monitor) throws Exception;

    /**
     * @return the table name proposed for a file
     */
    default String defaultTableName(String fileName) {
        return FileFormat.defaultTableName(fileName);
    }

    /**
     * @return true if the imports and exports are SQL statements that the View script button can show
     */
    default boolean hasScript() {
        return true;
    }
}
