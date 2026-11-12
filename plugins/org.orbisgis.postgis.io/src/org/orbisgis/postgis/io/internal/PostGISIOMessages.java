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
package org.orbisgis.postgis.io.internal;

import org.eclipse.osgi.util.NLS;

/**
 * Texts of the plugin, translated in the PostGISIOMessages_xx.properties files.
 */
public class PostGISIOMessages extends NLS {

    private static final String BUNDLE_NAME = "org.orbisgis.postgis.io.internal.PostGISIOMessages";

    public static String error_no_h2gis_driver;
    public static String error_missing_library;
    public static String error_url;
    public static String error_no_file_driver;
    public static String canceled;

    static {
        NLS.initializeMessages(BUNDLE_NAME, PostGISIOMessages.class);
    }

    private PostGISIOMessages() {
    }
}
