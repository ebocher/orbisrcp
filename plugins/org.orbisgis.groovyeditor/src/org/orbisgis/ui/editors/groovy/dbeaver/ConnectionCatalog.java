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

import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceFolder;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.orbisgis.ui.editors.groovy.completion.ConnectionCompletion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The connections of the active project, for the completion of {@code dbeaver.query("|}. Only the DBeaver
 * configuration is read: no connection is opened.
 */
public final class ConnectionCatalog {

    /** Methods of {@link DBeaverFacade} taking a connection as first argument */
    public static final Set<String> CONNECTION_METHODS = Set.of("datasource", "connect", "disconnect",
            "connection", "sql", "query", "execute", "connectionProperties", "jdbcUrl", "openSql");

    private ConnectionCatalog() {
    }

    /**
     * @return the connections of the active project, sorted by name
     */
    public static List<ConnectionCompletion.Connection> connections() {
        List<ConnectionCompletion.Connection> connections = new ArrayList<>();
        DBPProject project = DBWorkbench.getPlatform().getWorkspace().getActiveProject();
        if (project == null || !project.isOpen()) {
            return connections;
        }
        for (DBPDataSourceContainer container : project.getDataSourceRegistry().getDataSources()) {
            String driver = container.getDriver() == null ? "" : container.getDriver().getName();
            StringBuilder details = new StringBuilder(container.getName());
            details.append("\nDriver: ").append(driver);
            DBPConnectionConfiguration configuration = container.getConnectionConfiguration();
            if (configuration != null) {
                String url = configuration.getUrl();
                if (url != null && !url.isEmpty()) {
                    details.append("\nURL: ").append(url);
                } else {
                    append(details, "Host", configuration.getHostName());
                    append(details, "Database", configuration.getDatabaseName());
                }
            }
            DBPDataSourceFolder folder = container.getFolder();
            if (folder != null) {
                details.append("\nFolder: ").append(folder.getFolderPath());
            }
            details.append("\n").append(container.isConnected() ? "Connected" : "Not connected");
            connections.add(new ConnectionCompletion.Connection(container.getName(), driver, container.isConnected(),
                    details.toString()));
        }
        connections.sort(Comparator.comparing(c -> c.name().toLowerCase()));
        return connections;
    }

    private static void append(StringBuilder details, String label, String value) {
        if (value != null && !value.isEmpty()) {
            details.append('\n').append(label).append(": ").append(value);
        }
    }
}
