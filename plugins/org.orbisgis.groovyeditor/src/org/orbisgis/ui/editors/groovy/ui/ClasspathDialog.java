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
package org.orbisgis.ui.editors.groovy.ui;

import org.eclipse.osgi.util.NLS;
import org.orbisgis.ui.editors.groovy.internal.GroovyMessages;
import groovy.lang.GroovySystem;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.orbisgis.ui.editors.groovy.ClassPathHandler;
import org.orbisgis.ui.editors.groovy.session.ClasspathEntries;
import org.orbisgis.ui.editors.groovy.session.ClasspathEntries.Entry;
import org.orbisgis.ui.editors.groovy.session.ClasspathEntries.Origin;
import org.orbisgis.ui.editors.groovy.session.GroovySession;

import java.util.List;
import java.util.StringJoiner;

/**
 * Libraries of a Groovy console: the jars loaded with @Grab and the jars or folders added by the user, which
 * can be added and removed here.
 */
public class ClasspathDialog extends Dialog {

    private static final int COPY_ID = IDialogConstants.CLIENT_ID + 1;

    private final GroovySession session;
    private final String scriptName;
    private Table table;
    private Button removeButton;
    private List<Entry> entries = List.of();

    /**
     * @param parent     parent shell
     * @param session    context of the console
     * @param scriptName name of the script of the console
     */
    public ClasspathDialog(Shell parent, GroovySession session, String scriptName) {
        super(parent);
        this.session = session;
        this.scriptName = scriptName;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(NLS.bind(GroovyMessages.classpath_title, scriptName));
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(2, false));

        Label header = new Label(area, SWT.WRAP);
        header.setText(NLS.bind(GroovyMessages.classpath_header, GroovySystem.getVersion()));
        GridData headerData = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
        headerData.widthHint = 700;
        header.setLayoutData(headerData);

        table = new Table(area, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.heightHint = 320;
        tableData.widthHint = 700;
        table.setLayoutData(tableData);
        for (String title : new String[]{GroovyMessages.classpath_column_library, GroovyMessages.classpath_column_version,
                GroovyMessages.classpath_column_group, GroovyMessages.classpath_column_origin,
                GroovyMessages.classpath_column_path}) {
            new TableColumn(table, SWT.LEFT).setText(title);
        }
        table.addListener(SWT.Selection, e -> updateButtons());

        Composite buttons = new Composite(area, SWT.NONE);
        buttons.setLayout(new GridLayout(1, true));
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        createButton(buttons, GroovyMessages.classpath_add_jar, this::addJar);
        createButton(buttons, GroovyMessages.classpath_add_folder, this::addFolder);
        removeButton = createButton(buttons, GroovyMessages.classpath_remove, this::removeSelection);

        Label note = new Label(area, SWT.WRAP);
        note.setText(GroovyMessages.classpath_note);
        GridData noteData = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
        noteData.widthHint = 700;
        note.setLayoutData(noteData);

        refresh();
        return area;
    }

    private Button createButton(Composite parent, String text, Runnable action) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        button.addListener(SWT.Selection, e -> action.run());
        return button;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, COPY_ID, GroovyMessages.classpath_copy, false);
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == COPY_ID) {
            copyList();
        } else if (buttonId == IDialogConstants.CLOSE_ID) {
            close();
        } else {
            super.buttonPressed(buttonId);
        }
    }

    private void refresh() {
        entries = ClasspathEntries.list(session.getClasspathURLs(), ClassPathHandler.getUrls());
        table.removeAll();
        for (Entry entry : entries) {
            TableItem item = new TableItem(table, SWT.NONE);
            item.setText(new String[]{entry.name(), entry.version(), entry.group(), originLabel(entry.origin()),
                    entry.path()});
        }
        for (TableColumn column : table.getColumns()) {
            column.pack();
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean removable = table.getSelectionCount() > 0;
        for (int index : table.getSelectionIndices()) {
            removable &= entries.get(index).origin() == Origin.ADDED;
        }
        removeButton.setEnabled(removable);
    }

    private void addJar() {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN | SWT.MULTI);
        dialog.setFilterExtensions(new String[]{"*.jar", "*"});
        if (dialog.open() != null) {
            for (String name : dialog.getFileNames()) {
                ClassPathHandler.addPathToUrlList(dialog.getFilterPath() + java.io.File.separator + name);
            }
            refresh();
        }
    }

    private void addFolder() {
        String path = new DirectoryDialog(getShell()).open();
        if (path != null) {
            ClassPathHandler.addPathToUrlList(path);
            refresh();
        }
    }

    private void removeSelection() {
        for (int index : table.getSelectionIndices()) {
            ClassPathHandler.removeURL(entries.get(index).url());
        }
        refresh();
    }

    private static String originLabel(Origin origin) {
        return switch (origin) {
            case ADDED -> GroovyMessages.classpath_origin_added;
            case GRAB -> GroovyMessages.classpath_origin_grab;
            case OTHER -> GroovyMessages.classpath_origin_other;
        };
    }

    private void copyList() {
        StringJoiner text = new StringJoiner("\n");
        text.add("Groovy " + GroovySystem.getVersion());
        for (Entry entry : entries) {
            text.add(String.join("\t", entry.origin().getLabel(),
                    entry.group().isEmpty() ? entry.name() : entry.group() + ":" + entry.name() + ":" + entry.version(),
                    entry.path()));
        }
        Clipboard clipboard = new Clipboard(getShell().getDisplay());
        try {
            clipboard.setContents(new Object[]{text.toString()}, new Transfer[]{TextTransfer.getInstance()});
        } finally {
            clipboard.dispose();
        }
    }
}
