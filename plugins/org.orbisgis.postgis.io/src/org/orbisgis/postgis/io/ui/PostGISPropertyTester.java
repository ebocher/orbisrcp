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
package org.orbisgis.postgis.io.ui;

import org.eclipse.core.expressions.PropertyTester;
import org.orbisgis.h2gis.ui.ExportSource;
import org.orbisgis.h2gis.ui.ImportTarget;
import org.orbisgis.postgis.io.engine.PostGISConnections;

/**
 * Tests of the navigator selection, for the menus: a node of a PostgreSQL connection (where files can be
 * imported), a table of a PostgreSQL connection (which can be exported).
 */
public class PostGISPropertyTester extends PropertyTester {

    public static final String NAMESPACE = "org.orbisgis.postgis.io";
    public static final String PROP_IS_POSTGIS = "isPostGIS";
    public static final String PROP_IS_POSTGIS_TABLE = "isPostGISTable";

    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
        return switch (property) {
            case PROP_IS_POSTGIS -> ImportTarget.of(receiver, PostGISConnections::accepts) != null;
            case PROP_IS_POSTGIS_TABLE -> ExportSource.of(receiver, PostGISConnections::accepts) != null;
            default -> false;
        };
    }
}
