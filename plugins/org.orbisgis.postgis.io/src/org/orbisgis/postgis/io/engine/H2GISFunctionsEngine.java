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

import org.eclipse.osgi.util.NLS;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.orbisgis.h2gis.engine.FileIOEngine;
import org.orbisgis.h2gis.importer.FileFormat;
import org.orbisgis.postgis.io.internal.PostGISIOMessages;

import java.sql.Connection;
import java.sql.Driver;
import java.util.Locale;

/**
 * Imports and exports in a PostgreSQL/PostGIS connection with the file drivers of H2GIS.
 *
 * The H2GIS drivers work on a dedicated connection opened with the settings of the DBeaver connection, through
 * the postgis-jts wrapper which gives the geometries as JTS objects.
 *
 * {@link FileIOEngine}.
 */
public final class H2GISFunctionsEngine implements FileIOEngine {

    public static final H2GISFunctionsEngine INSTANCE = new H2GISFunctionsEngine();

    private static final String POSTGRESQL_PREFIX = "jdbc:postgresql:";
    /** Protocol of the postgis-jts driver */
    private static final String POSTGIS_JTS_PREFIX = "jdbc:postgresql_h2:";

    private H2GISFunctionsEngine() {
    }

    @Override
    public boolean accepts(DBPDataSourceContainer container) {
        return PostGISConnections.accepts(container);
    }

    @Override
    public Session open(DBPDataSourceContainer container, DBRProgressMonitor monitor) throws Exception {
        ClassLoader loader = H2GISLibraries.classLoader(monitor);
        String url = PostGISConnections.url(container);
        if (url == null || !url.startsWith(POSTGRESQL_PREFIX)) {
            throw new DBException(NLS.bind(PostGISIOMessages.error_url, url));
        }
        Connection connection = H2GISSession.inLoader(loader, () -> {
            Driver driver = (Driver) Class.forName(H2GISLibraries.POSTGIS_JTS_DRIVER, true, loader)
                    .getConstructor().newInstance();
            return driver.connect(POSTGIS_JTS_PREFIX + url.substring(POSTGRESQL_PREFIX.length()),
                    PostGISConnections.properties(container));
        });
        if (connection == null) {
            throw new DBException(NLS.bind(PostGISIOMessages.error_url, url));
        }
        return new H2GISSession(loader, connection);
    }

    /**
     * PostgreSQL writes the names without quotes in lower case: the proposed names are in lower case.
     */
    @Override
    public String defaultTableName(String fileName) {
        return FileFormat.defaultTableName(fileName).toLowerCase(Locale.ROOT);
    }

    /**
     * No SQL script because the H2GIS drivers are called in from H2GIS classes.
     */
    @Override
    public boolean hasScript() {
        return false;
    }
}
