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
package org.orbisgis.h2gis.importer;

import org.orbisgis.h2gis.internal.H2GISMessages;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * The file formats H2GIS can import, with their read function.
 */
public enum FileFormat {

    GEOJSON("GeoJSON", "GeoJsonRead", false, "geojson.gz", "geojson", "json"),
    FLATGEOBUF("FlatGeobuf", "FGBRead", false, "fgb"),
    SHAPEFILE("Shapefile", "SHPRead", false, "shp"),
    DBF("dBase", "DBFRead", false, "dbf.gz", "dbf"),
    GPX("GPX", "GPXRead", true, "gpx.gz", "gpx"),
    OSM("OpenStreetMap", "OSMRead", true, "osm.gz", "osm.bz2", "osm"),
    ASCII_GRID("ASCII grid", "AscRead", false, "asc.gz", "asc"),
    TSV("TSV", "TSVRead", false, "tsv.gz", "tsv"),
    /** Read by the CSVREAD function of H2 */
    CSV("CSV", null, false, "csv");

    private final String label;
    private final String function;
    private final boolean multipleTables;
    private final List<String> extensions;

    FileFormat(String label, String function, boolean multipleTables, String... extensions) {
        this.label = label;
        this.function = function;
        this.multipleTables = multipleTables;
        this.extensions = List.of(extensions);
    }

    /**
     * @return the name of the format
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return the H2GIS function reading the files, null for CSV (read with CSVREAD)
     */
    public String getFunction() {
        return function;
    }

    /**
     * @return true if the import creates several tables, named with the table name as prefix (GPX: waypoints,
     * routes, tracks...; OSM: nodes, ways, relations...)
     */
    public boolean isMultipleTables() {
        return multipleTables;
    }

    /**
     * @return the file extensions, the longest first (geojson.gz before geojson)
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * @return the format of a file, from its extension, or null if H2GIS cannot import it
     */
    public static FileFormat of(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        FileFormat found = null;
        int length = 0;
        for (FileFormat format : values()) {
            for (String extension : format.extensions) {
                if (name.endsWith("." + extension) && extension.length() > length) {
                    found = format;
                    length = extension.length();
                }
            }
        }
        return found;
    }

    /**
     * @return the name of a file without its format extension (communes.geojson.gz gives communes)
     */
    public static String baseName(String fileName) {
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        FileFormat format = of(name);
        if (format != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String extension : format.extensions) {
                if (lower.endsWith("." + extension)) {
                    return name.substring(0, name.length() - extension.length() - 1);
                }
            }
        }
        int point = name.lastIndexOf('.');
        return point > 0 ? name.substring(0, point) : name;
    }

    /**
     * @return a table name for a file: its name in capitals (as H2 stores the names), the other characters
     * replaced by '_' ("Communes 2024.geojson" gives COMMUNES_2024), without accents
     */
    public static String defaultTableName(String fileName) {
        // accents removed: bâtiments gives BATIMENTS
        String plain = Normalizer.normalize(baseName(fileName), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String name = plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        if (name.isEmpty()) {
            return "IMPORTED";
        }
        return Character.isDigit(name.charAt(0)) ? "T_" + name : name;
    }

    /**
     * @return the filter extensions of a file dialog: all the formats, then each format
     */
    public static String[][] dialogFilters() {
        StringBuilder all = new StringBuilder();
        String[] names = new String[values().length + 2];
        String[] patterns = new String[values().length + 2];
        int i = 1;
        for (FileFormat format : values()) {
            StringBuilder pattern = new StringBuilder();
            for (String extension : format.extensions) {
                pattern.append(pattern.length() == 0 ? "" : ";").append("*.").append(extension);
            }
            all.append(all.length() == 0 ? "" : ";").append(pattern);
            names[i] = format.label + " (" + pattern.toString().replace(";", ", ") + ")";
            patterns[i] = pattern.toString();
            i++;
        }
        names[0] = H2GISMessages.import_filter_supported;
        patterns[0] = all.toString();
        names[i] = H2GISMessages.import_filter_all;
        patterns[i] = "*";
        return new String[][]{names, patterns};
    }
}
