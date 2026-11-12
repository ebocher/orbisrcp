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

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.ui.UIServiceSQL;
import org.jkiss.dbeaver.ui.UIIcon;

import java.util.List;

/**
 * The "View script" window of the import and export dialogs: the SQL statements which will be run, in the
 * DBeaver SQL viewer (the one of "View script" when deleting a table), with its Execute, Copy and
 * Open in SQL editor buttons.
 */
final class ScriptViewer {

    private ScriptViewer() {
    }

    /**
     * Add the statements of an operation to a script, after a comment line.
     */
    static void append(StringBuilder script, String comment, List<String> statements) {
        if (!script.isEmpty()) {
            script.append('\n');
        }
        script.append("-- ").append(comment.replace('\n', ' ')).append('\n');
        for (String statement : statements) {
            script.append(statement).append(";\n");
        }
    }

    /**
     * Show a script.
     *
     * @param container the connection (gives the SQL dialect if it is open), or null
     * @return true if the user chose to execute it
     */
    static boolean show(Shell shell, DBPDataSourceContainer container, String title, String script) {
        UIServiceSQL service = DBWorkbench.getService(UIServiceSQL.class);
        if (service == null) {
            MessageDialog.openInformation(shell, title, script);
            return false;
        }
        DBCExecutionContext context = container == null || !container.isConnected() ? null
                : DBUtils.getDefaultContext(container.getDataSource(), false);
        return service.openSQLViewer(context, title, UIIcon.SQL_PREVIEW, script, true, true)
                == IDialogConstants.PROCEED_ID;
    }
}
