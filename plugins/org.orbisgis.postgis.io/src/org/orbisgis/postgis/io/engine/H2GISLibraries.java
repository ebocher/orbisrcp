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
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.connection.DBPDriverLoader;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.orbisgis.postgis.io.internal.PostGISIOMessages;

/**
 * The H2GIS libraries, taken from the H2GIS snapshot driver of the plugin org.orbisgis.h2gis: H2GIS, JTS,
 * postgis-jts and the PostgreSQL JDBC driver
 *
 * Their classes are used by reflection, in the class loader of this driver: they are not in this plugin.
 */
final class H2GISLibraries {

    /** The H2GIS snapshot driver (provider:driver) */
    static final String DRIVER = "h2gis_snapshot:h2gis_embedded_snapshot";

    static final String IO_METHODS = "org.h2gis.functions.io.utility.IOMethods";
    /** JDBC driver wrapping the PostgreSQL driver: the geometries are read and written as JTS geometries */
    static final String POSTGIS_JTS_DRIVER = "org.h2gis.postgis_jts.Driver";

    private static final String[] REQUIRED = {IO_METHODS, POSTGIS_JTS_DRIVER, "org.postgresql.Driver",
            "net.postgis.jdbc.PGboxbase", "org.locationtech.jts.geom.Geometry"};

    private H2GISLibraries() {
    }

    /**
     * @return the class loader of the H2GIS libraries, downloaded if needed
     */
    static ClassLoader classLoader(DBRProgressMonitor monitor) throws DBException {
        DBPDriver driver = DBWorkbench.getPlatform().getDataSourceProviderRegistry().findDriver(DRIVER);
        if (driver == null) {
            throw new DBException(PostGISIOMessages.error_no_h2gis_driver);
        }
        DBPDriverLoader loader = driver.getDefaultDriverLoader();
        // Downloads the missing libraries (the first time, or when the list of the driver changed)
        loader.loadDriver(monitor);
        ClassLoader classLoader = loader.getClassLoader();
        if (classLoader == null) {
            throw new DBException(PostGISIOMessages.error_no_h2gis_driver);
        }
        for (String name : REQUIRED) {
            try {
                Class.forName(name, false, classLoader);
            } catch (ClassNotFoundException | LinkageError e) {
                throw new DBException(NLS.bind(PostGISIOMessages.error_missing_library, name, driver.getName()));
            }
        }
        return classLoader;
    }
}
