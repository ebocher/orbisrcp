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
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.orbisgis.h2gis.exporter.ExportFormat;
import org.orbisgis.h2gis.exporter.TableExport;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The tables to export: the format, the folder, and the name of the file of each table.
 */
public class ExportTablesDialog extends TitleAreaDialog {

    /** Remembered during the DBeaver session */
    private static String lastFolder = System.getProperty("user.home");
    private static ExportFormat lastFormat = ExportFormat.GEOJSON;

    private final List<ExportSource> tables;
    private final List<Text> fileNames = new ArrayList<>();
    private final List<ExportOptions> tableOptions = new ArrayList<>();
    private final List<Label> optionLabels = new ArrayList<>();
    private Combo format;
    private Text folder;
    private Button overwrite;
    private final List<ExportTablesJob.Item> exports = new ArrayList<>();

    public ExportTablesDialog(Shell shell, List<ExportSource> tables) {
        super(shell);
        this.tables = tables;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(H2GISMessages.export_title);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        setTitle(H2GISMessages.export_title);
        setMessage(H2GISMessages.export_message);

        Composite options = new Composite(area, SWT.NONE);
        options.setLayout(new GridLayout(3, false));
        options.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(options, SWT.NONE).setText(H2GISMessages.export_format);
        format = new Combo(options, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (ExportFormat value : ExportFormat.values()) {
            format.add(value.describe());
        }
        format.select(lastFormat.ordinal());
        format.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));
        format.addListener(SWT.Selection, event -> formatChanged());

        new Label(options, SWT.NONE).setText(H2GISMessages.export_folder);
        folder = new Text(options, SWT.BORDER);
        folder.setText(lastFolder);
        folder.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        folder.addModifyListener(e -> validate());
        Button browse = new Button(options, SWT.PUSH);
        browse.setText(H2GISMessages.export_browse);
        browse.addListener(SWT.Selection, event -> {
            DirectoryDialog dialog = new DirectoryDialog(getShell());
            dialog.setFilterPath(folder.getText());
            String path = dialog.open();
            if (path != null) {
                folder.setText(path);
            }
        });

        ScrolledComposite scrolled = new ScrolledComposite(area, SWT.V_SCROLL | SWT.H_SCROLL);
        GridData scrolledData = new GridData(SWT.FILL, SWT.FILL, true, true);
        scrolledData.heightHint = Math.min(360, 40 + 30 * tables.size());
        scrolledData.widthHint = 760;
        scrolled.setLayoutData(scrolledData);
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);
        Composite grid = new Composite(scrolled, SWT.NONE);
        grid.setLayout(new GridLayout(4, false));
        new Label(grid, SWT.NONE).setText(H2GISMessages.export_column_table);
        new Label(grid, SWT.NONE).setText(H2GISMessages.export_column_file);
        new Label(grid, SWT.NONE).setText(H2GISMessages.export_column_rows);
        new Label(grid, SWT.NONE);
        for (ExportSource table : tables) {
            Label name = new Label(grid, SWT.NONE);
            name.setText(table.name());
            name.setToolTipText(table.reference() + " (" + table.container().getName() + ")");
            Text file = new Text(grid, SWT.BORDER);
            GridData fileData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            fileData.widthHint = 260;
            file.setLayoutData(fileData);
            file.setText(TableExport.defaultFileName(table.name(), lastFormat));
            file.addModifyListener(e -> validate());
            fileNames.add(file);

            int index = tableOptions.size();
            tableOptions.add(ExportOptions.ALL);
            Label summary = new Label(grid, SWT.NONE);
            summary.setText(ExportOptions.ALL.describe());
            GridData summaryData = new GridData(SWT.FILL, SWT.CENTER, false, false);
            summaryData.widthHint = 160;
            summary.setLayoutData(summaryData);
            optionLabels.add(summary);
            Button choose = new Button(grid, SWT.PUSH);
            choose.setText(H2GISMessages.export_columns_button);
            choose.addListener(SWT.Selection, event -> chooseOptions(index));
        }
        scrolled.setContent(grid);
        scrolled.setMinSize(grid.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        overwrite = new Button(area, SWT.CHECK);
        overwrite.setText(H2GISMessages.export_replace);
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.DETAILS_ID, H2GISMessages.view_script_button, false);
        createButton(parent, IDialogConstants.OK_ID, H2GISMessages.export_button, true);
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
     * Show the SQL statements of the exports; the script window can also run them.
     */
    private void viewScript() {
        List<ExportTablesJob.Item> items = collect();
        Set<String> connections = new HashSet<>();
        for (ExportTablesJob.Item item : items) {
            connections.add(item.source().container().getName());
        }
        StringBuilder script = new StringBuilder();
        for (ExportTablesJob.Item item : items) {
            TableExport export = item.export();
            String comment = item.source().name() + " -> " + export.getFile().getAbsolutePath();
            if (connections.size() > 1) {
                comment = item.source().container().getName() + " : " + comment;
            }
            ScriptViewer.append(script, comment, export.statements());
        }
        // Several connections: the script is shown without the dialect of one of them
        if (ScriptViewer.show(getShell(), connections.size() == 1 ? tables.get(0).container() : null,
                H2GISMessages.export_script_title, script.toString())) {
            okPressed();
        }
    }

    /**
     * Choose the columns and the filter of a table.
     */
    private void chooseOptions(int index) {
        ColumnsFilterDialog dialog = new ColumnsFilterDialog(getShell(), tables.get(index), tableOptions.get(index));
        if (dialog.open() == OK && dialog.getOptions() != null) {
            tableOptions.set(index, dialog.getOptions());
            optionLabels.get(index).setText(dialog.getOptions().describe());
            optionLabels.get(index).setToolTipText(dialog.getOptions().filter());
            validate();
        }
    }

    private ExportFormat selectedFormat() {
        return ExportFormat.values()[Math.max(0, format.getSelectionIndex())];
    }

    /**
     * The extension of the file names follows the format.
     */
    private void formatChanged() {
        ExportFormat selected = selectedFormat();
        for (Text file : fileNames) {
            file.setText(withExtension(file.getText(), selected));
        }
        validate();
    }

    /**
     * @return a file name with the extension of a format, instead of the extension of another format
     */
    static String withExtension(String fileName, ExportFormat format) {
        String name = fileName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        List<String> known = new ArrayList<>();
        for (ExportFormat value : ExportFormat.values()) {
            known.addAll(value.getExtensions());
        }
        known.sort(Comparator.comparingInt(String::length).reversed());
        for (String extension : known) {
            if (lower.endsWith("." + extension)) {
                if (format.getExtensions().contains(extension)) {
                    return name;
                }
                name = name.substring(0, name.length() - extension.length() - 1);
                break;
            }
        }
        return name + "." + format.getExtension();
    }

    private void validate() {
        String error = null;
        File directory = new File(folder.getText().trim());
        if (folder.getText().isBlank() || !directory.isDirectory()) {
            error = H2GISMessages.export_error_folder;
        }
        Set<String> names = new HashSet<>();
        for (Text file : fileNames) {
            String name = file.getText().trim();
            if (name.isEmpty()) {
                error = H2GISMessages.export_error_name;
            } else if (name.contains("/") || name.contains("\\")) {
                error = NLS.bind(H2GISMessages.export_error_path, name);
            } else if (!names.add(name.toLowerCase(Locale.ROOT))) {
                error = NLS.bind(H2GISMessages.export_error_duplicate, name);
            }
        }
        setErrorMessage(error);
        // A geographic format without the geometry column of a table: allowed, but probably a mistake
        String withoutGeometry = null;
        if (format != null && selectedFormat().isSpatial()) {
            for (int i = 0; i < tableOptions.size(); i++) {
                if (!tableOptions.get(i).hasGeometry()) {
                    withoutGeometry = tables.get(i).name();
                    break;
                }
            }
        }
        if (withoutGeometry != null) {
            setMessage(NLS.bind(H2GISMessages.export_warning_geometry, withoutGeometry), IMessageProvider.WARNING);
        } else {
            setMessage(H2GISMessages.export_message);
        }
        for (int id : new int[]{IDialogConstants.OK_ID, IDialogConstants.DETAILS_ID}) {
            Button button = getButton(id);
            if (button != null) {
                button.setEnabled(error == null);
            }
        }
    }

    @Override
    protected void okPressed() {
        exports.clear();
        exports.addAll(collect());
        lastFolder = new File(folder.getText().trim()).getAbsolutePath();
        lastFormat = selectedFormat();
        super.okPressed();
    }

    /**
     * The exports described by the dialog.
     */
    private List<ExportTablesJob.Item> collect() {
        ExportFormat selected = selectedFormat();
        File directory = new File(folder.getText().trim());
        List<ExportTablesJob.Item> list = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            ExportSource table = tables.get(i);
            File file = new File(directory, fileNames.get(i).getText().trim());
            ExportOptions chosen = tableOptions.get(i);
            list.add(new ExportTablesJob.Item(table, new TableExport(table.name(), table.reference(), file,
                    selected, overwrite.getSelection(), chosen.columns(), chosen.filter())));
        }
        return list;
    }

    /**
     * @return the exports to run (after OK)
     */
    public List<ExportTablesJob.Item> getExports() {
        return exports;
    }
}
