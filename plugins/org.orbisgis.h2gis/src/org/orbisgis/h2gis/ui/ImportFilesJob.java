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
import org.jkiss.dbeaver.ui.navigator.actions.NavigatorHandlerRefresh;
import org.orbisgis.h2gis.importer.FileImport;

import java.util.ArrayList;
import java.util.List;
import org.orbisgis.h2gis.engine.SqlEngine;
import org.orbisgis.h2gis.engine.FileIOEngine;

/**
 * Import files in an H2GIS database, in the background. The connection is opened if needed; the navigator is
 * refreshed and a summary shown at the end.
 */
public class ImportFilesJob extends AbstractJob {

    private final FileIOEngine engine;
    private final ImportTarget target;
    private final List<FileImport> imports;

    public ImportFilesJob(ImportTarget target, List<FileImport> imports) {
        this(SqlEngine.INSTANCE, target, imports);
    }

    /**
     * @param engine runs the imports
     */
    public ImportFilesJob(FileIOEngine engine, ImportTarget target, List<FileImport> imports) {
        super(NLS.bind(H2GISMessages.import_job, target.describe()));
        this.engine = engine;
        this.target = target;
        this.imports = imports;
        setUser(true);
    }

    @Override
    protected IStatus run(DBRProgressMonitor monitor) {
        List<FileImport.Result> results = new ArrayList<>();
        String failure = null;
        monitor.beginTask(H2GISMessages.import_task, imports.size());
        try {
            DBPDataSourceContainer container = target.container();
            if (!container.isConnected()) {
                monitor.subTask(NLS.bind(H2GISMessages.job_connect, container.getName()));
                container.connect(monitor, true, true);
            }
            try (FileIOEngine.Session session = engine.open(container, monitor)) {
                for (FileImport fileImport : imports) {
                    if (monitor.isCanceled()) {
                        break;
                    }
                    monitor.subTask(fileImport.getFile().getName());
                    results.add(session.importFile(fileImport, monitor));
                    monitor.worked(1);
                }
            }
        } catch (Exception e) {
            failure = e.getMessage() == null ? e.toString() : e.getMessage();
        } finally {
            monitor.done();
        }
        String error = failure;
        UIUtils.asyncExec(() -> {
            if (!results.isEmpty()) {
                NavigatorHandlerRefresh.refreshNavigator(List.of(target.node()));
            }
            showSummary(results, error);
        });
        return Status.OK_STATUS;
    }

    private void showSummary(List<FileImport.Result> results, String failure) {
        List<ResultDialog.Row> rows = new ArrayList<>();
        for (FileImport.Result result : results) {
            boolean several = result.isSuccess() && result.rows() < 0;
            rows.add(new ResultDialog.Row(result.file().getName(), result.table() + (several ? "_*" : ""),
                    result.rows(), result.error(), result.file().getAbsolutePath() + (several
                    ? "\n" + NLS.bind(H2GISMessages.import_details_several, result.table()) : "")));
        }
        new ResultDialog(UIUtils.getActiveWorkbenchShell(), NLS.bind(H2GISMessages.import_summary_title, target.describe()),
                H2GISMessages.import_summary_verb, H2GISMessages.result_imported,
                new String[]{H2GISMessages.import_column_file, H2GISMessages.import_column_table}, rows, failure).open();
    }
}
