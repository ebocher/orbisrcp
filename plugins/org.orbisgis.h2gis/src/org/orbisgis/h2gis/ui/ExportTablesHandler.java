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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import java.util.ArrayList;
import java.util.List;
import org.orbisgis.h2gis.engine.SqlEngine;
import org.orbisgis.h2gis.engine.FileIOEngine;

/**
 * Export the tables selected in the navigator (H2GIS connections) to files: GeoJSON, FlatGeobuf, Shapefile...
 */
public class ExportTablesHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) {
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (!(selection instanceof IStructuredSelection structured)) {
            return null;
        }
        FileIOEngine engine = engine();
        List<ExportSource> tables = new ArrayList<>();
        for (Object element : structured.toList()) {
            ExportSource source = ExportSource.of(element, engine::accepts);
            if (source != null) {
                tables.add(source);
            }
        }
        if (tables.isEmpty()) {
            return null;
        }
        Shell shell = HandlerUtil.getActiveShell(event);
        ExportTablesDialog dialog = new ExportTablesDialog(shell, engine, tables);
        if (dialog.open() == Window.OK && !dialog.getExports().isEmpty()) {
            new ExportTablesJob(engine, dialog.getExports()).schedule();
        }
        return null;
    }

    /**
     * @return the engine running the exports: the H2GIS SQL functions; overridden for other databases
     */
    protected FileIOEngine engine() {
        return SqlEngine.INSTANCE;
    }
}
