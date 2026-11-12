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

import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.eclipse.osgi.util.NLS;
import org.orbisgis.h2gis.exporter.TableExport;
import org.orbisgis.h2gis.importer.FileImport;
import org.orbisgis.h2gis.internal.H2GISMessages;
import org.orbisgis.h2gis.ui.ImportTarget;

import java.sql.Connection;

/**
 * The H2GIS engine: the SQL functions of H2GIS (GeoJsonRead, FGBWrite...), run in the connection of DBeaver.
 */
public final class SqlEngine implements FileIOEngine {

    public static final SqlEngine INSTANCE = new SqlEngine();

    private SqlEngine() {
    }

    @Override
    public boolean accepts(DBPDataSourceContainer container) {
        return ImportTarget.isH2GIS(container);
    }

    /**
     * Cancel the H2 session: the statement of the H2GIS function, and the SQL commands it runs itself (the
     * inserts of the rows read...), which the cancel of the statement does not stop. Only an embedded database:
     * the remote sessions of H2 have no cancel.
     */
    static void cancelH2Session(JDBCSession session) {
        try {
            Connection connection = session.getOriginal();
            Object h2Session = connection.getClass().getMethod("getSession").invoke(connection);
            h2Session.getClass().getMethod("cancel").invoke(h2Session);
        } catch (Exception | LinkageError e) {
            // not an H2 connection, or no cancel: the statement cancel only
        }
    }

    @Override
    public Session open(DBPDataSourceContainer container, DBRProgressMonitor monitor) throws Exception {
        DBPDataSource dataSource = container.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException(NLS.bind(H2GISMessages.job_connect_error, container.getName()));
        }
        JDBCSession session = DBUtils.openUtilSession(monitor, dataSource, "H2GIS files");
        return new Session() {
            @Override
            public FileImport.Result importFile(FileImport fileImport) {
                return importFile(fileImport, null);
            }

            @Override
            public TableExport.Result exportTable(TableExport export) {
                return exportTable(export, null);
            }

            @Override
            public FileImport.Result importFile(FileImport fileImport, DBRProgressMonitor monitor) {
                try (CancelWatcher watcher = new CancelWatcher(monitor, () -> cancelH2Session(session))) {
                    FileImport.Result result = fileImport.run(session, watcher::watch);
                    return watcher.isCanceled() && !result.isSuccess()
                            ? new FileImport.Result(result.file(), result.table(), -1, H2GISMessages.canceled)
                            : result;
                }
            }

            @Override
            public TableExport.Result exportTable(TableExport export, DBRProgressMonitor monitor) {
                try (CancelWatcher watcher = new CancelWatcher(monitor, () -> cancelH2Session(session))) {
                    TableExport.Result result = export.run(session, watcher::watch);
                    return watcher.isCanceled() && !result.isSuccess()
                            ? new TableExport.Result(result.table(), result.file(), -1, H2GISMessages.canceled)
                            : result;
                }
            }

            @Override
            public void close() {
                session.close();
            }
        };
    }
}
