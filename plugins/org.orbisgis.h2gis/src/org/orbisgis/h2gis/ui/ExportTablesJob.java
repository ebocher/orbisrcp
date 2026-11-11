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
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.ui.UIUtils;
import org.orbisgis.h2gis.exporter.TableExport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Export tables of H2GIS databases to files, in the background, then show a summary.
 */
public class ExportTablesJob extends AbstractJob {

    /**
     * A table to export.
     *
     * @param source the table
     * @param export its export
     */
    public record Item(ExportSource source, TableExport export) {
    }

    private final List<Item> items;

    public ExportTablesJob(List<Item> items) {
        super(H2GISMessages.export_job);
        this.items = items;
        setUser(true);
    }

    @Override
    protected IStatus run(DBRProgressMonitor monitor) {
        List<TableExport.Result> results = new ArrayList<>();
        String failure = null;
        // The tables of each connection with one session
        Map<DBPDataSourceContainer, List<TableExport>> byConnection = new LinkedHashMap<>();
        for (Item item : items) {
            byConnection.computeIfAbsent(item.source().container(), c -> new ArrayList<>()).add(item.export());
        }
        monitor.beginTask(H2GISMessages.export_job, items.size());
        try {
            for (Map.Entry<DBPDataSourceContainer, List<TableExport>> entry : byConnection.entrySet()) {
                DBPDataSourceContainer container = entry.getKey();
                if (!container.isConnected()) {
                    monitor.subTask(NLS.bind(H2GISMessages.job_connect, container.getName()));
                    container.connect(monitor, true, true);
                }
                DBPDataSource dataSource = container.getDataSource();
                if (dataSource == null) {
                    throw new IllegalStateException(NLS.bind(H2GISMessages.job_connect_error, container.getName()));
                }
                try (JDBCSession session = DBUtils.openUtilSession(monitor, dataSource, "Export to files")) {
                    for (TableExport export : entry.getValue()) {
                        if (monitor.isCanceled()) {
                            break;
                        }
                        monitor.subTask(export.getTableName() + " → " + export.getFile().getName());
                        results.add(export.run(session));
                        monitor.worked(1);
                    }
                }
            }
        } catch (Exception e) {
            failure = e.getMessage() == null ? e.toString() : e.getMessage();
        } finally {
            monitor.done();
        }
        String error = failure;
        UIUtils.asyncExec(() -> showSummary(results, error));
        return Status.OK_STATUS;
    }

    private void showSummary(List<TableExport.Result> results, String failure) {
        List<ResultDialog.Row> rows = new ArrayList<>();
        for (TableExport.Result result : results) {
            rows.add(new ResultDialog.Row(result.table(), result.file().getName(), result.rows(), result.error(),
                    result.file().getAbsolutePath()));
        }
        new ResultDialog(UIUtils.getActiveWorkbenchShell(), H2GISMessages.export_title, H2GISMessages.export_summary_verb,
                H2GISMessages.result_exported,
                new String[]{H2GISMessages.export_column_table, H2GISMessages.export_column_file}, rows, failure).open();
    }
}
