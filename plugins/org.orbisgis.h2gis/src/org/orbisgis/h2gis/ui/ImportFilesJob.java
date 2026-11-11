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

/**
 * Import files in an H2GIS database, in the background. The connection is opened if needed; the navigator is
 * refreshed and a summary shown at the end.
 */
public class ImportFilesJob extends AbstractJob {

    private final ImportTarget target;
    private final List<FileImport> imports;

    public ImportFilesJob(ImportTarget target, List<FileImport> imports) {
        super("Import files in " + target.describe());
        this.target = target;
        this.imports = imports;
        setUser(true);
    }

    @Override
    protected IStatus run(DBRProgressMonitor monitor) {
        List<FileImport.Result> results = new ArrayList<>();
        String failure = null;
        monitor.beginTask("Import files", imports.size());
        try {
            DBPDataSourceContainer container = target.container();
            if (!container.isConnected()) {
                monitor.subTask("Connect to " + container.getName());
                container.connect(monitor, true, true);
            }
            DBPDataSource dataSource = container.getDataSource();
            if (dataSource == null) {
                throw new IllegalStateException("Unable to connect to " + container.getName());
            }
            try (JDBCSession session = DBUtils.openUtilSession(monitor, dataSource, "Import files")) {
                for (FileImport fileImport : imports) {
                    if (monitor.isCanceled()) {
                        break;
                    }
                    monitor.subTask(fileImport.getFile().getName());
                    results.add(fileImport.run(session));
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
        new ImportResultDialog(UIUtils.getActiveWorkbenchShell(), target, results, failure).open();
    }
}
