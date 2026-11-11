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
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.orbisgis.h2gis.importer.FileImport;

import java.text.NumberFormat;
import java.util.List;

/**
 * Summary of an import: a row per file, with its table and its number of rows, or its error.
 */
public class ImportResultDialog extends TitleAreaDialog {

    private final ImportTarget target;
    private final List<FileImport.Result> results;
    private final String failure;
    private Text details;

    /**
     * @param failure an error which stopped the import (connection...), or null
     */
    public ImportResultDialog(Shell shell, ImportTarget target, List<FileImport.Result> results, String failure) {
        super(shell);
        this.target = target;
        this.results = results;
        this.failure = failure;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Import files");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        long failed = results.stream().filter(result -> !result.isSuccess()).count();
        long imported = results.size() - failed;
        setTitle("Import in " + target.describe());
        if (failure != null) {
            setMessage("The import stopped: " + failure, IMessageProvider.WARNING);
        } else if (failed == 0) {
            setMessage(imported == 1 ? "1 file imported." : imported + " files imported.", IMessageProvider.INFORMATION);
        } else {
            setMessage(imported + " imported, " + failed + " failed: select a file to read its error.",
                    IMessageProvider.WARNING);
        }

        Table table = new Table(area, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = Math.min(320, 60 + 24 * results.size());
        tableData.widthHint = 620;
        table.setLayoutData(tableData);
        column(table, "File", 220, SWT.LEFT);
        column(table, "Table", 170, SWT.LEFT);
        column(table, "Rows", 80, SWT.RIGHT);
        column(table, "Result", 130, SWT.LEFT);

        NumberFormat numbers = NumberFormat.getIntegerInstance();
        for (FileImport.Result result : results) {
            TableItem item = new TableItem(table, SWT.NONE);
            String rows = result.isSuccess() && result.rows() >= 0 ? numbers.format(result.rows()) : "";
            String tableName = result.isSuccess() && result.rows() < 0 ? result.table() + "_*" : result.table();
            item.setText(new String[]{result.file().getName(), tableName, rows,
                    result.isSuccess() ? "Imported" : "Failed"});
            if (!result.isSuccess()) {
                item.setForeground(3, JFaceColors.getErrorText(table.getDisplay()));
            }
            item.setData(result);
        }

        details = new Text(area, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
        GridData detailsData = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailsData.heightHint = 70;
        details.setLayoutData(detailsData);
        table.addListener(SWT.Selection, event -> showDetails(table));
        // The first failed file is selected, with its error
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).isSuccess()) {
                table.setSelection(i);
                break;
            }
        }
        showDetails(table);
        return area;
    }

    private void showDetails(Table table) {
        TableItem[] selection = table.getSelection();
        if (selection.length == 1 && selection[0].getData() instanceof FileImport.Result result) {
            if (result.isSuccess()) {
                details.setText(result.file().getAbsolutePath() + "\nTable " + result.table()
                        + (result.rows() < 0 ? " and the tables starting with " + result.table() + "_" : ""));
            } else {
                details.setText(result.file().getAbsolutePath() + "\n" + result.error());
            }
        } else {
            details.setText("Select a file to see its details.");
        }
    }

    private static void column(Table table, String text, int width, int alignment) {
        TableColumn column = new TableColumn(table, alignment);
        column.setText(text);
        column.setWidth(width);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.CLOSE_LABEL, true);
    }
}
