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

import java.text.NumberFormat;
import java.util.List;

/**
 * Summary of an import or an export: a row per file (or table), with its rows, or its error.
 */
public class ResultDialog extends TitleAreaDialog {

    /**
     * A row of the summary.
     *
     * @param source  the file imported, or the table exported
     * @param target  the table created, or the file written
     * @param rows    number of rows, -1 if unknown
     * @param error   the error, null if it succeeded
     * @param details shown below the table when the row is selected
     */
    public record Row(String source, String target, long rows, String error, String details) {

        boolean isSuccess() {
            return error == null;
        }
    }

    private final String title;
    private final String verb;
    private final String doneLabel;
    private final String[] columns;
    private final List<Row> rows;
    private final String failure;
    private Text details;

    /**
     * @param title   title: "Import in geo, schema PUBLIC"
     * @param verb    what was done to each row: "imported", "exported"
     * @param doneLabel result of a row which succeeded: "Imported", "Exported"
     * @param columns names of the source and target columns: "File", "Table"
     * @param rows    the rows
     * @param failure an error which stopped the whole operation (connection...), or null
     */
    public ResultDialog(Shell shell, String title, String verb, String doneLabel, String[] columns, List<Row> rows,
                        String failure) {
        super(shell);
        this.title = title;
        this.verb = verb;
        this.doneLabel = doneLabel;
        this.columns = columns;
        this.rows = rows;
        this.failure = failure;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        long failed = rows.stream().filter(row -> !row.isSuccess()).count();
        long done = rows.size() - failed;
        setTitle(title);
        if (failure != null) {
            setMessage(NLS.bind(H2GISMessages.result_stopped, failure), IMessageProvider.WARNING);
        } else if (failed == 0) {
            setMessage(done == 1 ? NLS.bind(H2GISMessages.result_done_one, verb) : NLS.bind(H2GISMessages.result_done, done, verb),
                    IMessageProvider.INFORMATION);
        } else {
            setMessage(NLS.bind(H2GISMessages.result_partial, new Object[]{done, verb, failed}), IMessageProvider.WARNING);
        }

        Table table = new Table(area, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = Math.min(320, 60 + 24 * rows.size());
        tableData.widthHint = 620;
        table.setLayoutData(tableData);
        column(table, columns[0], 220, SWT.LEFT);
        column(table, columns[1], 170, SWT.LEFT);
        column(table, H2GISMessages.result_column_rows, 80, SWT.RIGHT);
        column(table, H2GISMessages.result_column_result, 130, SWT.LEFT);

        NumberFormat numbers = NumberFormat.getIntegerInstance();
        for (Row row : rows) {
            TableItem item = new TableItem(table, SWT.NONE);
            String count = row.isSuccess() && row.rows() >= 0 ? numbers.format(row.rows()) : "";
            item.setText(new String[]{row.source(), row.target(), count, row.isSuccess() ? doneLabel : H2GISMessages.result_failed});
            if (!row.isSuccess()) {
                item.setForeground(3, JFaceColors.getErrorText(table.getDisplay()));
            }
            item.setData(row);
        }

        details = new Text(area, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.READ_ONLY | SWT.V_SCROLL);
        GridData detailsData = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailsData.heightHint = 70;
        details.setLayoutData(detailsData);
        table.addListener(SWT.Selection, event -> showDetails(table));
        // The first failed row is selected, with its error
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).isSuccess()) {
                table.setSelection(i);
                break;
            }
        }
        showDetails(table);
        return area;
    }

    private void showDetails(Table table) {
        TableItem[] selection = table.getSelection();
        if (selection.length == 1 && selection[0].getData() instanceof Row row) {
            details.setText(row.details() + (row.isSuccess() ? "" : "\n" + row.error()));
        } else {
            details.setText(H2GISMessages.result_select);
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
