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
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.ui.UIUtils;
import org.orbisgis.h2gis.exporter.TableExport;
import org.orbisgis.h2gis.internal.H2GISMessages;

import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The columns and the filter of a table to export.
 */
public class ColumnsFilterDialog extends TitleAreaDialog {

    private final ExportSource source;
    private final ExportOptions initial;
    private final List<DBSEntityAttribute> attributes = new ArrayList<>();
    private Table columns;
    private Text filter;
    private Label count;
    private ExportOptions options;

    public ColumnsFilterDialog(Shell shell, ExportSource source, ExportOptions initial) {
        super(shell);
        this.source = source;
        this.initial = initial;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(H2GISMessages.columns_title);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        setTitle(NLS.bind(H2GISMessages.columns_heading, source.name()));
        setMessage(H2GISMessages.columns_message);

        Composite content = new Composite(area, SWT.NONE);
        content.setLayout(new GridLayout(2, false));
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        columns = new Table(content, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION | SWT.V_SCROLL);
        columns.setHeaderVisible(true);
        GridData columnsData = new GridData(SWT.FILL, SWT.FILL, true, true);
        columnsData.heightHint = 220;
        columnsData.widthHint = 380;
        columns.setLayoutData(columnsData);
        TableColumn name = new TableColumn(columns, SWT.LEFT);
        name.setText(H2GISMessages.columns_column);
        name.setWidth(220);
        TableColumn type = new TableColumn(columns, SWT.LEFT);
        type.setText(H2GISMessages.columns_type);
        type.setWidth(140);
        try {
            attributes.addAll(source.entity().getAttributes(new VoidProgressMonitor()));
        } catch (Exception e) {
            setErrorMessage(e.getMessage());
        }
        for (DBSEntityAttribute attribute : attributes) {
            TableItem item = new TableItem(columns, SWT.NONE);
            item.setText(new String[]{attribute.getName(), attribute.getTypeName()});
            item.setChecked(initial.columns() == null || initial.columns().contains(quoted(attribute)));
            item.setData(attribute);
        }
        columns.addListener(SWT.Selection, event -> validate());

        Composite buttons = new Composite(content, SWT.NONE);
        buttons.setLayout(new GridLayout(1, true));
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        button(buttons, H2GISMessages.columns_all, () -> checkAll(true));
        button(buttons, H2GISMessages.columns_none, () -> checkAll(false));

        Label filterLabel = new Label(content, SWT.NONE);
        filterLabel.setText(H2GISMessages.columns_filter);
        filterLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        filter = new Text(content, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData filterData = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1);
        filterData.heightHint = 50;
        filter.setLayoutData(filterData);
        filter.setText(initial.filter() == null ? "" : initial.filter());
        filter.setToolTipText(H2GISMessages.columns_filter_tooltip);

        Composite countLine = new Composite(content, SWT.NONE);
        countLine.setLayout(new GridLayout(2, false));
        countLine.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        button(countLine, H2GISMessages.columns_count, this::countRows);
        count = new Label(countLine, SWT.WRAP);
        count.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        validate();
        return area;
    }

    private void button(Composite parent, String text, Runnable action) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        button.addListener(SWT.Selection, event -> action.run());
    }

    private void checkAll(boolean checked) {
        for (TableItem item : columns.getItems()) {
            item.setChecked(checked);
        }
        validate();
    }

    private static String quoted(DBSEntityAttribute attribute) {
        return DBUtils.getQuotedIdentifier(attribute);
    }

    private static boolean isGeometry(DBSEntityAttribute attribute) {
        String type = attribute.getTypeName();
        return type != null && type.toUpperCase(Locale.ROOT).contains("GEOMETRY");
    }

    /**
     * @return the checked columns; null if all of them are checked
     */
    private List<String> checkedColumns() {
        List<String> checked = new ArrayList<>();
        boolean all = true;
        for (TableItem item : columns.getItems()) {
            if (item.getChecked()) {
                checked.add(quoted((DBSEntityAttribute) item.getData()));
            } else {
                all = false;
            }
        }
        return all ? null : checked;
    }

    private boolean checkedGeometry() {
        for (TableItem item : columns.getItems()) {
            if (item.getChecked() && isGeometry((DBSEntityAttribute) item.getData())) {
                return true;
            }
        }
        return false;
    }

    private void validate() {
        List<String> checked = checkedColumns();
        Button ok = getButton(IDialogConstants.OK_ID);
        boolean none = checked != null && checked.isEmpty();
        if (none) {
            setErrorMessage(H2GISMessages.columns_error_none);
        } else {
            setErrorMessage(null);
            if (!checkedGeometry() && attributes.stream().anyMatch(ColumnsFilterDialog::isGeometry)) {
                setMessage(H2GISMessages.columns_warning_geometry, IMessageProvider.WARNING);
            } else {
                setMessage(H2GISMessages.columns_message);
            }
        }
        if (ok != null) {
            ok.setEnabled(!none);
        }
    }

    /**
     * Count the rows of the filter, to check it before the export.
     */
    private void countRows() {
        TableExport export = new TableExport(source.name(), source.reference(), null, null, false,
                null, filter.getText());
        String query = export.countQuery();
        DBPDataSourceContainer container = source.container();
        long[] rows = {-1};
        String[] error = {null};
        try {
            UIUtils.runInProgressDialog(monitor -> {
                try {
                    if (!container.isConnected()) {
                        container.connect(monitor, true, true);
                    }
                    DBPDataSource dataSource = container.getDataSource();
                    try (JDBCSession session = DBUtils.openUtilSession(monitor, dataSource, "Count rows");
                         Statement statement = session.createStatement();
                         ResultSet result = statement.executeQuery(query)) {
                        rows[0] = result.next() ? result.getLong(1) : 0;
                    }
                } catch (Exception e) {
                    throw new InvocationTargetException(e);
                }
            });
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            error[0] = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        }
        if (error[0] != null) {
            count.setText(error[0]);
            count.setForeground(JFaceColors.getErrorText(count.getDisplay()));
        } else if (rows[0] >= 0) {
            count.setText(NLS.bind(H2GISMessages.columns_count_result, NumberFormat.getIntegerInstance().format(rows[0])));
            count.setForeground(null);
        }
        count.getParent().layout();
    }

    @Override
    protected void okPressed() {
        String condition = filter.getText().strip();
        options = new ExportOptions(checkedColumns(), condition.isEmpty() ? null : condition, checkedGeometry()
                || attributes.stream().noneMatch(ColumnsFilterDialog::isGeometry));
        super.okPressed();
    }

    /**
     * @return the options chosen (after OK)
     */
    public ExportOptions getOptions() {
        return options;
    }
}
