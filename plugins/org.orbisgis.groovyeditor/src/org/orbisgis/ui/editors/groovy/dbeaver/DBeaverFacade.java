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
package org.orbisgis.ui.editors.groovy.dbeaver;

import groovy.lang.Closure;
import groovy.sql.GroovyRowResult;
import groovy.sql.Sql;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.app.DBPPlatform;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.editors.sql.SQLEditor;
import org.jkiss.dbeaver.ui.editors.sql.handlers.SQLEditorHandlerOpenEditor;
import org.jkiss.dbeaver.ui.editors.sql.handlers.SQLNavigatorContext;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;
import org.jkiss.dbeaver.ui.navigator.database.DatabaseNavigatorView;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.GroovyConsoleContent;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.Style;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Callable;

/**
 * Access to DBeaver from the Groovy scripts, available as the {@code dbeaver} variable.
 *
 * Every method taking a {@code datasource} accepts the name (or id) of a connection of the Database Navigator,
 * a {@link DBPDataSourceContainer} or any database object ({@link DBSObject}) of that connection.
 *
 * The methods can be called from the script: the calls which must run in the UI thread are done in the UI
 * thread. The JDBC connections opened by the script are closed when the context of the console is reset or
 * when the editor is closed.
 */
public class DBeaverFacade implements AutoCloseable {

    private static final Logger LOGGER = new Logger(DBeaverFacade.class);

    /** Name of the variable in the scripts */
    public static final String VARIABLE = "dbeaver";

    private static final String CONTEXT_PURPOSE = "Groovy console";

    private final List<DBCExecutionContext> openedContexts = Collections.synchronizedList(new ArrayList<>());

    // ---------------------------------------------------------------- platform

    /**
     * @return the DBeaver platform, entry point of the whole DBeaver API
     */
    public DBPPlatform getPlatform() {
        return DBWorkbench.getPlatform();
    }

    /**
     * @return the active project
     */
    public DBPProject getProject() {
        return DBWorkbench.getPlatform().getWorkspace().getActiveProject();
    }

    /**
     * @return all the projects of the workspace
     */
    public List<DBPProject> getProjects() {
        return new ArrayList<>(DBWorkbench.getPlatform().getWorkspace().getProjects());
    }

    /**
     * @return a progress monitor for the DBeaver methods which need one
     */
    public DBRProgressMonitor getMonitor() {
        return new VoidProgressMonitor();
    }

    // ---------------------------------------------------------------- connections

    /**
     * @return the connections of the active project
     */
    public List<DBPDataSourceContainer> getDatasources() {
        DBPProject project = getProject();
        if (project == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(project.getDataSourceRegistry().getDataSources());
    }

    /**
     * @return the names of the connections of the active project
     */
    public List<String> getDatasourceNames() {
        List<String> names = new ArrayList<>();
        for (DBPDataSourceContainer container : getDatasources()) {
            names.add(container.getName());
        }
        return names;
    }

    /**
     * Find a connection of the Database Navigator.
     *
     * @param datasource name or id of the connection (the active project is searched first), a connection or a
     *                   database object
     * @return the connection, connected or not
     */
    public DBPDataSourceContainer datasource(Object datasource) {
        if (datasource instanceof DBPDataSourceContainer container) {
            return container;
        }
        if (datasource instanceof DBSObject object && object.getDataSource() != null) {
            return object.getDataSource().getContainer();
        }
        if (datasource instanceof DBNDatabaseNode node && node.getObject() != null) {
            return datasource(node.getObject());
        }
        if (datasource instanceof CharSequence) {
            String name = datasource.toString();
            List<DBPProject> projects = getProjects();
            DBPProject active = getProject();
            if (active != null) {
                projects.remove(active);
                projects.add(0, active);
            }
            // name first, then id, then case-insensitive name
            for (DBPProject project : projects) {
                if (!project.isOpen()) {
                    continue;
                }
                DBPDataSourceContainer container = project.getDataSourceRegistry().findDataSourceByName(name);
                if (container == null) {
                    container = project.getDataSourceRegistry().getDataSource(name);
                }
                if (container != null) {
                    return container;
                }
            }
            for (DBPProject project : projects) {
                if (!project.isOpen()) {
                    continue;
                }
                for (DBPDataSourceContainer container : project.getDataSourceRegistry().getDataSources()) {
                    if (container.getName().equalsIgnoreCase(name)) {
                        return container;
                    }
                }
            }
            throw new IllegalArgumentException("No connection named '" + name + "'. Available connections: "
                    + String.join(", ", getDatasourceNames()));
        }
        throw new IllegalArgumentException("Not a connection: " + datasource
                + ". Use the name of a connection of the Database Navigator.");
    }

    /**
     * Connect a connection of the Database Navigator, as the Connect button does (the password is asked if
     * needed).
     *
     * @return the connection
     */
    public DBPDataSourceContainer connect(Object datasource) throws DBException {
        DBPDataSourceContainer container = datasource(datasource);
        if (!container.isConnected()) {
            container.connect(getMonitor(), true, true);
        }
        if (container.getDataSource() == null) {
            throw new DBException("Unable to connect to '" + container.getName() + "'.");
        }
        return container;
    }

    /**
     * Disconnect a connection of the Database Navigator.
     */
    public void disconnect(Object datasource) throws DBException {
        DBPDataSourceContainer container = datasource(datasource);
        if (container.isConnected()) {
            container.disconnect(getMonitor());
        }
    }

    /**
     * Open a JDBC connection on a connection of the Database Navigator, without asking for the password again.
     *
     * The connection is a new, isolated, connection (like the one of a new SQL editor): its transactions do
     * not interfere with DBeaver. It is closed when the context of the console is reset or when the editor is
     * closed, it can also be closed by the script.
     *
     * The connection is created by the JDBC driver configured in DBeaver. A library which needs the classes
     * of the driver (for instance {@code PGConnection}) may not accept it if it uses its own copy of the
     * driver.
     *
     * @return the JDBC connection
     */
    public Connection connection(Object datasource) throws DBException, SQLException {
        DBPDataSourceContainer container = connect(datasource);
        DBPDataSource dataSource = container.getDataSource();
        DBRProgressMonitor monitor = getMonitor();
        DBCExecutionContext context = dataSource.getDefaultInstance().openIsolatedContext(monitor, CONTEXT_PURPOSE, null);
        if (!(context instanceof JDBCExecutionContext jdbcContext)) {
            closeQuietly(context);
            throw new DBException("'" + container.getName() + "' is not a JDBC connection.");
        }
        openedContexts.add(context);
        return jdbcContext.getConnection(monitor);
    }

    /**
     * Open a {@link Sql} (Groovy SQL) on a new JDBC connection, see {@link #connection(Object)}.
     */
    public Sql sql(Object datasource) throws DBException, SQLException {
        return new Sql(connection(datasource));
    }

    /**
     * Run a query on a connection of the Database Navigator.
     *
     * The PostGIS geometries are returned as JTS geometries (with their SRID): {@code row.the_geom.area}.
     *
     * @return the rows; the values can be read by column name: {@code row.name} or {@code row['name']}
     */
    public List<GroovyRowResult> query(Object datasource, String query) throws DBException, SQLException {
        Connection connection = connection(datasource);
        try {
            return convertGeometries(new Sql(connection).rows(query));
        } finally {
            closeConnection(connection);
        }
    }

    /**
     * Run a query with parameters ({@code ?} or {@code :name} placeholders) on a connection of the Database
     * Navigator.
     */
    public List<GroovyRowResult> query(Object datasource, String query, List<Object> params) throws DBException, SQLException {
        Connection connection = connection(datasource);
        try {
            return convertGeometries(new Sql(connection).rows(query, params));
        } finally {
            closeConnection(connection);
        }
    }

    private static List<GroovyRowResult> convertGeometries(List<GroovyRowResult> rows) {
        for (GroovyRowResult row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                entry.setValue(GeometryConverter.toGeometry(entry.getValue()));
            }
        }
        return rows;
    }

    /**
     * Connection parameters of a connection of the Database Navigator: {@code url}, {@code user} and
     * {@code password} (the password saved in DBeaver, or typed when connecting).
     *
     * A library can open its own connection with them, with its own JDBC driver. For instance, orbisdata
     * converts the PostGIS geometries to JTS only with its own driver:
     * {@code POSTGIS.open(dbeaver.connectionProperties("my base"))}.
     *
     * When the connection uses an SSH tunnel, the URL goes through the tunnel opened by DBeaver: DBeaver must
     * stay connected.
     */
    public Map<String, String> connectionProperties(Object datasource) throws DBException {
        // Connecting resolves the credentials (asked if they are not saved) and the tunnels
        DBPDataSourceContainer container = connect(datasource);
        DBPConnectionConfiguration configuration = container.getActualConnectionConfiguration();
        String url = configuration.getUrl();
        if (url == null || url.isBlank()) {
            url = container.getDriver().getConnectionURL(configuration);
        }
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("url", url);
        if (configuration.getUserName() != null && !configuration.getUserName().isEmpty()) {
            properties.put("user", configuration.getUserName());
        }
        if (configuration.getUserPassword() != null) {
            properties.put("password", configuration.getUserPassword());
        }
        return properties;
    }

    /**
     * @return the JDBC URL of a connection of the Database Navigator
     */
    public String jdbcUrl(Object datasource) throws DBException {
        return connectionProperties(datasource).get("url");
    }

    /**
     * Run a SQL statement (DDL, insert, update...) on a connection of the Database Navigator.
     *
     * @return the number of updated rows (0 for a DDL statement)
     */
    public int execute(Object datasource, String statement) throws DBException, SQLException {
        Connection connection = connection(datasource);
        try {
            return new Sql(connection).executeUpdate(statement);
        } finally {
            closeConnection(connection);
        }
    }

    // ---------------------------------------------------------------- navigator

    /**
     * @return the database objects selected in the Database Navigator (connections, schemas, tables,
     * columns...)
     */
    public List<DBSObject> getSelection() throws Exception {
        return inUI(() -> {
            ISelection selection = getNavigatorSelection();
            return selection == null ? new ArrayList<DBSObject>() : new ArrayList<DBSObject>(NavigatorUtils.getSelectedObjects(selection));
        });
    }

    /**
     * @return the nodes selected in the Database Navigator (including the folders and the other non database
     * nodes)
     */
    public List<DBNNode> getSelectedNodes() throws Exception {
        return inUI(() -> {
            ISelection selection = getNavigatorSelection();
            return selection == null ? new ArrayList<DBNNode>() : new ArrayList<DBNNode>(NavigatorUtils.getSelectedNodes(selection));
        });
    }

    /**
     * @return the first database object selected in the Database Navigator, or null
     */
    public DBSObject getSelectedObject() throws Exception {
        List<DBSObject> selection = getSelection();
        return selection.isEmpty() ? null : selection.get(0);
    }

    /**
     * @return the connection of the first object selected in the Database Navigator, or null
     */
    public DBPDataSourceContainer getSelectedDatasource() throws Exception {
        DBSObject object = getSelectedObject();
        if (object == null) {
            return null;
        }
        if (object instanceof DBPDataSourceContainer container) {
            return container;
        }
        return object.getDataSource() == null ? null : object.getDataSource().getContainer();
    }

    private static ISelection getNavigatorSelection() {
        IWorkbenchWindow window = getWindow();
        return window == null ? null : window.getSelectionService().getSelection(DatabaseNavigatorView.VIEW_ID);
    }

    /**
     * Refresh a connection or a database object in the Database Navigator, for instance after a script created
     * tables.
     *
     * @param target name of a connection, connection or database object
     */
    public void refresh(Object target) throws DBException {
        DBSObject object;
        if (target instanceof DBSObject dbsObject) {
            object = dbsObject;
        } else if (target instanceof DBNDatabaseNode node) {
            node.refreshNode(getMonitor(), this);
            return;
        } else {
            object = datasource(target);
        }
        DBRProgressMonitor monitor = getMonitor();
        DBNDatabaseNode node = DBWorkbench.getPlatform().getNavigatorModel().getNodeByObject(monitor, object, true);
        if (node == null) {
            throw new DBException("'" + object.getName() + "' is not in the Database Navigator.");
        }
        node.refreshNode(monitor, this);
    }

    // ---------------------------------------------------------------- editors

    /**
     * Open a SQL console on a connection, with the given SQL text.
     *
     * @return the SQL editor
     */
    public SQLEditor openSql(Object datasource, String sqlText) throws Exception {
        DBPDataSourceContainer container = datasource(datasource);
        return inUI(() -> SQLEditorHandlerOpenEditor.openSQLConsole(getWindow(),
                new SQLNavigatorContext(container, null), container.getName(), sqlText == null ? "" : sqlText));
    }

    /**
     * Open an empty SQL console on a connection.
     */
    public SQLEditor openSql(Object datasource) throws Exception {
        return openSql(datasource, "");
    }

    // ---------------------------------------------------------------- UI thread

    /**
     * Run a closure in the UI thread and return its result. Needed to use the UI parts of the Eclipse and
     * DBeaver API (views, editors, dialogs...). Do not run long tasks in the UI thread: DBeaver would freeze.
     */
    public Object inUI(Closure<?> closure) throws Exception {
        return inUI(closure::call);
    }

    /**
     * Run a task in the UI thread and return its result, the exceptions are thrown to the caller.
     */
    @SuppressWarnings("unchecked")
    public static <T> T inUI(Callable<T> task) throws Exception {
        Display display = PlatformUI.isWorkbenchRunning() ? PlatformUI.getWorkbench().getDisplay() : Display.getDefault();
        if (Display.getCurrent() == display) {
            return task.call();
        }
        Object[] result = new Object[1];
        Exception[] error = new Exception[1];
        display.syncExec(() -> {
            try {
                result[0] = task.call();
            } catch (Exception e) {
                error[0] = e;
            }
        });
        if (error[0] != null) {
            throw error[0];
        }
        return (T) result[0];
    }

    /**
     * @return the active workbench window, or the first one when DBeaver is not the active application.
     * Must be called from the UI thread.
     */
    private static IWorkbenchWindow getWindow() {
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        if (window == null && workbench.getWorkbenchWindows().length > 0) {
            window = workbench.getWorkbenchWindows()[0];
        }
        return window;
    }

    // ---------------------------------------------------------------- help

    /**
     * Print the list of the available methods in the output view.
     */
    public void help() {
        StringJoiner help = new StringJoiner("\n", "\n", "\n");
        help.add("dbeaver: access to DBeaver from the scripts. 'datasource' is the name of a connection of the");
        help.add("Database Navigator, a connection or a database object.");
        help.add("");
        help.add("  dbeaver.datasources              connections of the active project");
        help.add("  dbeaver.datasourceNames          names of these connections");
        help.add("  dbeaver.datasource(name)         find a connection");
        help.add("  dbeaver.connect(datasource)      connect it (as the Connect button)");
        help.add("  dbeaver.disconnect(datasource)   disconnect it");
        help.add("  dbeaver.connection(datasource)   new JDBC connection (closed on reset)");
        help.add("  dbeaver.sql(datasource)          groovy.sql.Sql on a new JDBC connection");
        help.add("  dbeaver.query(datasource, sql)   rows of a query, e.g. .each { println it.name }");
        help.add("                                   (PostGIS geometries are returned as JTS geometries)");
        help.add("  dbeaver.query(datasource, sql, [params])");
        help.add("  dbeaver.execute(datasource, sql) run a statement, returns the number of updated rows");
        help.add("  dbeaver.connectionProperties(datasource)  [url, user, password] for a library, e.g.");
        help.add("                                   POSTGIS.open(dbeaver.connectionProperties(datasource))");
        help.add("  dbeaver.jdbcUrl(datasource)      JDBC URL of the connection");
        help.add("  dbeaver.selection                objects selected in the Database Navigator");
        help.add("  dbeaver.selectedObject           first selected object");
        help.add("  dbeaver.selectedDatasource       connection of the selection");
        help.add("  dbeaver.selectedNodes            selected navigator nodes");
        help.add("  dbeaver.refresh(target)          refresh a connection or an object in the navigator");
        help.add("  dbeaver.openSql(datasource, sql) open a SQL console");
        help.add("  dbeaver.inUI { ... }             run code in the UI thread");
        help.add("  dbeaver.platform / project / projects / monitor   raw DBeaver API");
        GroovyConsoleContent.append(help.toString(), Style.OUTPUT);
    }

    @Override
    public String toString() {
        return "DBeaver (dbeaver.help() lists the available methods)";
    }

    // ---------------------------------------------------------------- resources

    /**
     * Close a connection opened by {@link #connection(Object)} and its DBeaver context.
     */
    private void closeConnection(Connection connection) {
        synchronized (openedContexts) {
            for (DBCExecutionContext context : new ArrayList<>(openedContexts)) {
                try {
                    if (context instanceof JDBCExecutionContext jdbcContext
                            && jdbcContext.getConnection(getMonitor(), false) == connection) {
                        openedContexts.remove(context);
                        closeQuietly(context);
                        return;
                    }
                } catch (SQLException e) {
                    // Not this one
                }
            }
        }
    }

    private static void closeQuietly(DBCExecutionContext context) {
        try {
            context.close();
        } catch (Exception e) {
            LOGGER.warn("Unable to close a connection of the Groovy console.", e);
        }
    }

    /**
     * Close the connections opened by the scripts.
     */
    @Override
    public void close() {
        List<DBCExecutionContext> contexts;
        synchronized (openedContexts) {
            contexts = new ArrayList<>(openedContexts);
            openedContexts.clear();
        }
        contexts.forEach(DBeaverFacade::closeQuietly);
    }
}
