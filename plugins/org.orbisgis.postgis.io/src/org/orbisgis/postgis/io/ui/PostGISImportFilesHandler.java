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

import org.orbisgis.h2gis.engine.FileIOEngine;
import org.orbisgis.h2gis.ui.ImportFilesHandler;
import org.orbisgis.postgis.io.engine.H2GISFunctionsEngine;

/**
 * The import of files in a PostgreSQL/PostGIS connection: the dialogs of the H2GIS plugin,
 * with the engine of this plugin.
 */
public class PostGISImportFilesHandler extends ImportFilesHandler {

    @Override
    protected FileIOEngine engine() {
        return H2GISFunctionsEngine.INSTANCE;
    }
}
