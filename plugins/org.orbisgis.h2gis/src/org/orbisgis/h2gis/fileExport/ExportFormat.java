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
package org.orbisgis.h2gis.exporter;

import java.util.List;

/**
 * The file formats H2GIS can write, with their write function.
 */
public enum ExportFormat {

    GEOJSON("GeoJSON", "GeoJsonWrite", "geojson", "geojson.gz"),
    FLATGEOBUF("FlatGeobuf", "FGBWrite", "fgb"),
    SHAPEFILE("Shapefile", "SHPWrite", "shp"),
    KML("KML", "KMLWrite", "kml", "kmz"),
    DBF("dBase", "DBFWrite", "dbf"),
    /** The rows as JSON objects (not GeoJSON) */
    JSON("JSON", "JsonWrite", "json", "json.gz"),
    TSV("TSV", "TSVWrite", "tsv", "tsv.gz"),
    /** Written by the CSVWRITE function of H2 */
    CSV("CSV", null, "csv");

    private final String label;
    private final String function;
    private final List<String> extensions;

    ExportFormat(String label, String function, String... extensions) {
        this.label = label;
        this.function = function;
        this.extensions = List.of(extensions);
    }

    public String getLabel() {
        return label;
    }

    /**
     * @return the H2GIS function writing the files, null for CSV (written with CSVWRITE)
     */
    public String getFunction() {
        return function;
    }

    /**
     * @return the file extensions; the first one is the default
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * @return the default extension
     */
    public String getExtension() {
        return extensions.get(0);
    }

    /**
     * @return true for the formats storing geometries (a geometry column is expected)
     */
    public boolean isSpatial() {
        return this == GEOJSON || this == FLATGEOBUF || this == SHAPEFILE || this == KML;
    }

    /**
     * @return the label shown in a list: "GeoJSON (.geojson)"
     */
    public String describe() {
        return label + " (." + getExtension() + ")";
    }
}
