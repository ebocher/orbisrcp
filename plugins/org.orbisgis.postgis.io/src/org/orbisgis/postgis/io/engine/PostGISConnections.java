/*
 * postgis-io: import and export of files (GeoJSON, FlatGeobuf, Shapefile...) in the PostgreSQL/PostGIS
 * connections of DBeaver.
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
package org.orbisgis.postgis.io.engine;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;

import java.util.Map;
import java.util.Properties;

/**
 * The PostgreSQL/PostGIS connections of DBeaver, and how to open a JDBC connection with their settings.
 */
public final class PostGISConnections {

    /** Data source provider of the PostgreSQL connections in DBeaver */
    public static final String PROVIDER = "postgresql";
    /** JDBC driver class: excludes the databases of this provider with other drivers (Redshift, EDB...) */
    public static final String DRIVER_CLASS = "org.postgresql.Driver";

    private PostGISConnections() {
    }

    /**
     * @return true for a PostgreSQL connection (with the PostgreSQL JDBC driver)
     */
    public static boolean accepts(DBPDataSourceContainer container) {
        DBPDriver driver = container.getDriver();
        return driver != null && PROVIDER.equals(driver.getProviderId())
                && DRIVER_CLASS.equals(driver.getDriverClassName());
    }

    /**
     * @return the JDBC URL of a connected connection: it goes through the SSH tunnel opened by DBeaver, if any
     */
    public static String url(DBPDataSourceContainer container) throws DBException {
        DBPConnectionConfiguration configuration = container.getActualConnectionConfiguration();
        String url = configuration.getUrl();
        if (url == null || url.isBlank()) {
            url = container.getDriver().getConnectionURL(configuration);
        }
        return url;
    }

    /**
     * @return the JDBC properties of a connected connection: its driver properties, user and password (asked
     * when connecting if they are not saved)
     */
    public static Properties properties(DBPDataSourceContainer container) {
        DBPConnectionConfiguration configuration = container.getActualConnectionConfiguration();
        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : configuration.getProperties().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                properties.setProperty(entry.getKey(), entry.getValue());
            }
        }
        if (configuration.getUserName() != null && !configuration.getUserName().isEmpty()) {
            properties.setProperty("user", configuration.getUserName());
        }
        if (configuration.getUserPassword() != null) {
            properties.setProperty("password", configuration.getUserPassword());
        }
        properties.putIfAbsent("ApplicationName", "DBeaver postgis-io");
        return properties;
    }
}
