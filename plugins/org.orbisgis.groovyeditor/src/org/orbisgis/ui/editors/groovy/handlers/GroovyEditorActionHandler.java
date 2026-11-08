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
package org.orbisgis.ui.editors.groovy.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.ui.navigator.database.DatabaseNavigatorView;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.ClassPathHandler;
import org.orbisgis.ui.editors.groovy.GroovyEditor;
import org.orbisgis.ui.editors.groovy.GroovyEditorCommands;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.GroovyConsoleContent;

public class GroovyEditorActionHandler extends AbstractHandler {

    private static final Logger LOGGER = new Logger(GroovyEditorActionHandler.class);

    @Override
    public Object execute(ExecutionEvent executionEvent) {
        String actionId = executionEvent.getCommand().getId();
        // These commands do not depend on an editor (they are also available in the output view)
        switch (actionId) {
            case GroovyEditorCommands.CMD_GROOVY_DELETE_OUTPUT:
                GroovyConsoleContent.clear();
                return null;
            case GroovyEditorCommands.CMD_LIST_CLASSPATHS:
                ClassPathHandler.showClassPaths();
                return null;
            case GroovyEditorCommands.CMD_ADD_DIR_TO_CLASSPATH:
                ClassPathHandler.addDirPathToPathList();
                return null;
            case GroovyEditorCommands.CMD_ADD_JAR_TO_CLASSPATH:
                ClassPathHandler.addJarPathToPathList();
                return null;
            default:
                break;
        }
        IEditorPart activeEditor = HandlerUtil.getActiveEditor(executionEvent);
        GroovyEditor editor = activeEditor == null ? null : RuntimeUtils.getObjectAdapter(activeEditor, GroovyEditor.class);
        if (editor == null) {
            LOGGER.warn("No active Groovy editor found");
            return null;
        }
        switch (actionId) {
            case GroovyEditorCommands.CMD_CLEAR:
                editor.clear();
                break;
            case GroovyEditorCommands.CMD_EXECUTE_SCRIPT:
                editor.execute();
                break;
            case GroovyEditorCommands.CMD_EXECUTE_SELECTION:
                editor.executeSelection();
                break;
            case GroovyEditorCommands.CMD_STOP_PROCESS:
                editor.stop();
                break;
            case GroovyEditorCommands.CMD_RESET_CONTEXT:
                editor.resetContext();
                break;
            default:
                LOGGER.error("Unsupported Groovy editor command: " + actionId);
                break;
        }

        return null;
    }

    private List<DBPDataSourceContainer> getContainers(ExecutionEvent event) {
        List<DBPDataSourceContainer> list = new ArrayList<>();
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        if (window != null) {
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                IViewPart nav = window.getActivePage().findView(DatabaseNavigatorView.VIEW_ID);
                if (nav instanceof DatabaseNavigatorView) {
                    for (TreeItem item : ((DatabaseNavigatorView) nav).getNavigatorViewer().getTree().getItems()) {
                        if (item != null) {
                            Object data = item.getData();
                            if (data instanceof DBNDataSource) {
                                list.add(((DBNDataSource) data).getDataSourceContainer());
                            } else {
                                LOGGER.warn("No data source found");
                            }
                        } else {
                            LOGGER.warn("No tree item found");
                        }
                    }
                } else {
                    LOGGER.warn("No DatabaseNavigatorView found");
                }
            } else {
                LOGGER.warn("No IWorkbenchPage found");
            }
        } else {
            LOGGER.warn("No IWorkbenchWindow found");
        }
        return list;
    }
}
