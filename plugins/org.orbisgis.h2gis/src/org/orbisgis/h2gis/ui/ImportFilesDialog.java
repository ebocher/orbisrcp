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
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.orbisgis.h2gis.importer.FileFormat;
import org.orbisgis.h2gis.importer.FileImport;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.orbisgis.h2gis.engine.SqlEngine;
import org.orbisgis.h2gis.engine.FileIOEngine;

/**
 * The files to import, with their format and the name of their table.
 */
public class ImportFilesDialog extends TitleAreaDialog {

    private final FileIOEngine engine;
    private final ImportTarget target;
    private final List<File> files;
    private final List<Text> tableNames = new ArrayList<>();
    private Button replace;
    private final List<FileImport> imports = new ArrayList<>();

    public ImportFilesDialog(Shell shell, ImportTarget target, List<File> files) {
        this(shell, SqlEngine.INSTANCE, target, files);
    }

    /**
     * @param engine runs the imports: gives the default table names, and if a script can be shown
     */
    public ImportFilesDialog(Shell shell, FileIOEngine engine, ImportTarget target, List<File> files) {
        super(shell);
        this.engine = engine;
        this.target = target;
        this.files = files;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(H2GISMessages.import_shell);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        setTitle(H2GISMessages.import_title);
        setMessage(NLS.bind(H2GISMessages.import_message, target.describe()));

        ScrolledComposite scrolled = new ScrolledComposite(area, SWT.V_SCROLL | SWT.H_SCROLL);
        GridData scrolledData = new GridData(SWT.FILL, SWT.FILL, true, true);
        scrolledData.heightHint = Math.min(400, 40 + 30 * files.size());
        scrolledData.widthHint = 640;
        scrolled.setLayoutData(scrolledData);
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);

        Composite grid = new Composite(scrolled, SWT.NONE);
        grid.setLayout(new GridLayout(3, false));
        header(grid, H2GISMessages.import_column_file);
        header(grid, H2GISMessages.import_column_format);
        header(grid, H2GISMessages.import_column_table);
        for (File file : files) {
            FileFormat format = FileFormat.of(file.getName());
            Label name = new Label(grid, SWT.NONE);
            name.setText(file.getName());
            name.setToolTipText(file.getAbsolutePath());
            name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            Label formatLabel = new Label(grid, SWT.NONE);
            formatLabel.setText(format == null ? H2GISMessages.import_format_unsupported : format.isMultipleTables()
                    ? NLS.bind(H2GISMessages.import_format_several, format.getLabel()) : format.getLabel());
            Text table = new Text(grid, SWT.BORDER);
            GridData tableData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            tableData.widthHint = 200;
            table.setLayoutData(tableData);
            if (format == null) {
                table.setEnabled(false);
            } else {
                table.setText(engine.defaultTableName(file.getName()));
                if (format.isMultipleTables()) {
                    table.setToolTipText(H2GISMessages.import_table_prefix_tooltip);
                }
            }
            table.addModifyListener(e -> validate());
            tableNames.add(table);
        }
        scrolled.setContent(grid);
        scrolled.setMinSize(grid.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        replace = new Button(area, SWT.CHECK);
        replace.setText(H2GISMessages.import_replace);
        replace.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        if (engine.hasScript()) {
            createButton(parent, IDialogConstants.DETAILS_ID, H2GISMessages.view_script_button, false);
        }
        createButton(parent, IDialogConstants.OK_ID, H2GISMessages.import_button, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        validate();
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.DETAILS_ID) {
            viewScript();
        } else {
            super.buttonPressed(buttonId);
        }
    }

    /**
     * Show the SQL statements of the imports; the script window can also run them.
     */
    private void viewScript() {
        StringBuilder script = new StringBuilder();
        for (FileImport fileImport : collect()) {
            ScriptViewer.append(script, fileImport.getFile().getName() + " -> " + fileImport.tableReference(),
                    fileImport.statements());
        }
        if (ScriptViewer.show(getShell(), target.container(), H2GISMessages.import_script_title,
                script.toString())) {
            okPressed();
        }
    }

    private static void header(Composite parent, String text) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(text);
    }

    private void validate() {
        String error = null;
        Set<String> names = new HashSet<>();
        int count = 0;
        for (Text table : tableNames) {
            if (!table.isEnabled()) {
                continue;
            }
            String name = table.getText().trim();
            if (name.isEmpty()) {
                error = H2GISMessages.import_error_name;
            } else if (!names.add(name.toUpperCase(Locale.ROOT))) {
                error = NLS.bind(H2GISMessages.import_error_duplicate, name);
            }
            count++;
        }
        if (error == null && count == 0) {
            error = H2GISMessages.import_error_none;
        }
        setErrorMessage(error);
        for (int id : new int[]{IDialogConstants.OK_ID, IDialogConstants.DETAILS_ID}) {
            Button button = getButton(id);
            if (button != null) {
                button.setEnabled(error == null);
            }
        }
    }

    @Override
    protected void okPressed() {
        imports.clear();
        imports.addAll(collect());
        super.okPressed();
    }

    /**
     * The imports described by the dialog.
     */
    private List<FileImport> collect() {
        List<FileImport> list = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            FileFormat format = FileFormat.of(file.getName());
            Text table = tableNames.get(i);
            if (format != null && table.isEnabled() && !table.getText().isBlank()) {
                list.add(new FileImport(file, format, target.schema(), table.getText().trim(),
                        replace.getSelection()));
            }
        }
        return list;
    }

    /**
     * @return the imports to run (after OK)
     */
    public List<FileImport> getImports() {
        return imports;
    }
}
