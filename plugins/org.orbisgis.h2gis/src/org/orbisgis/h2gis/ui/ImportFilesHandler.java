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
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.orbisgis.h2gis.importer.FileFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.orbisgis.h2gis.engine.SqlEngine;
import org.orbisgis.h2gis.engine.FileIOEngine;

/**
 * Import files (GeoJSON, FlatGeobuf, Shapefile...) in the H2GIS connection selected in the navigator.
 */
public class ImportFilesHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) {
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        Object element = selection instanceof IStructuredSelection structured ? structured.getFirstElement() : null;
        FileIOEngine engine = engine();
        ImportTarget target = ImportTarget.of(element, engine::accepts);
        if (target == null) {
            return null;
        }
        Shell shell = HandlerUtil.getActiveShell(event);
        FileDialog fileDialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
        fileDialog.setText(NLS.bind(H2GISMessages.import_file_dialog, target.describe()));
        String[][] filters = FileFormat.dialogFilters();
        fileDialog.setFilterNames(filters[0]);
        fileDialog.setFilterExtensions(filters[1]);
        if (fileDialog.open() == null) {
            return null;
        }
        List<File> files = new ArrayList<>();
        for (String name : fileDialog.getFileNames()) {
            files.add(new File(fileDialog.getFilterPath(), name));
        }
        ImportFilesDialog dialog = new ImportFilesDialog(shell, engine, target, files);
        if (dialog.open() == Window.OK && !dialog.getImports().isEmpty()) {
            new ImportFilesJob(engine, target, dialog.getImports()).schedule();
        }
        return null;
    }

    /**
     * @return the engine running the imports: the H2GIS SQL functions; overridden for other databases
     */
    protected FileIOEngine engine() {
        return SqlEngine.INSTANCE;
    }
}
