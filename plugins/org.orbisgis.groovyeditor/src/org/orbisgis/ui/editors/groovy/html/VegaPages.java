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
package org.orbisgis.ui.editors.groovy.html;

import groovy.json.JsonGenerator;
import groovy.json.JsonOutput;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTML pages displaying Vega-Lite (or Vega) specifications with vega-embed.
 */
public final class VegaPages {

    /** Vega-Lite version embedded in the plugin (same as demat) */
    public static final String VEGA_LITE_VERSION = "6.4.3";

    /** Folder of the Vega libraries in the bundle */
    public static final String LIBRARY_FOLDER = "web/vega";

    static final String[] LIBRARIES = {"vega.min.js", "vega-lite.min.js", "vega-embed.min.js"};

    private static final JsonGenerator JSON = new JsonGenerator.Options()
            .addConverter(new ValueConverter())
            .build();

    /**
     * JSON of the values Vega-Lite cannot read as is: geometries as GeoJSON, SQL dates as ISO strings.
     */
    private static final class ValueConverter implements JsonGenerator.Converter {
        @Override
        public boolean handles(Class<?> type) {
            return Geometry.class.isAssignableFrom(type) || java.sql.Timestamp.class.isAssignableFrom(type)
                    || java.sql.Date.class.isAssignableFrom(type) || java.sql.Time.class.isAssignableFrom(type);
        }

        @Override
        public Object convert(Object value, String key) {
            if (value instanceof Geometry geometry) {
                return toGeoJson(geometry);
            }
            if (value instanceof java.sql.Timestamp timestamp) {
                return timestamp.toLocalDateTime().toString();
            }
            if (value instanceof java.sql.Date date) {
                return date.toLocalDate().toString();
            }
            if (value instanceof java.sql.Time time) {
                return time.toLocalTime().toString();
            }
            return value;
        }
    }

    private VegaPages() {
    }

    /**
     * @param spec a specification: a Map (Groovy map, built with demat...) or a JSON string
     * @return the JSON of the specification; the JTS geometries of the data are written as GeoJSON
     */
    public static String toJson(Object spec) {
        if (spec instanceof CharSequence text) {
            String json = text.toString().trim();
            if (!json.startsWith("{")) {
                throw new IllegalArgumentException("A Vega-Lite specification is a map, or a JSON object");
            }
            return json;
        }
        if (!(spec instanceof Map)) {
            throw new IllegalArgumentException("A Vega-Lite specification is a map, or a JSON object, not a "
                    + (spec == null ? "null value" : spec.getClass().getSimpleName()));
        }
        return JSON.toJson(toFeatures(spec));
    }

    /**
     * The rows with a geometry ({@code dbeaver.query(...)}...) become GeoJSON features, so geoshape marks can draw
     * them and Vega-Lite can fit the map on them. The columns stay readable by their name ({@code field: "name"},
     * as with the other rows) and as {@code properties.name}.
     */
    static Object toFeatures(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, toFeatures(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(item instanceof Map<?, ?> row && !row.containsKey("type") ? toFeature(row) : toFeatures(item));
            }
            return copy;
        }
        return value;
    }

    private static Object toFeature(Map<?, ?> row) {
        Geometry geometry = null;
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : row.entrySet()) {
            if (geometry == null && entry.getValue() instanceof Geometry g) {
                geometry = g;
            } else {
                properties.put(String.valueOf(entry.getKey()), toFeatures(entry.getValue()));
            }
        }
        if (geometry == null) {
            return toFeatures(row);
        }
        Map<String, Object> feature = new LinkedHashMap<>(properties);
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", properties);
        return feature;
    }

    /**
     * @param specJson     JSON of the specification
     * @param title        title of the chart, used for the exported files
     * @param libraryUrl   URL of the folder of the Vega libraries, relative to the page or absolute
     * @param dark         true for the dark theme
     * @return the HTML page
     */
    public static String page(String specJson, String title, String libraryUrl, boolean dark) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n<title>")
                .append(escapeHtml(title)).append("</title>\n<style>\n")
                .append("html, body { margin: 0; padding: 0; background: ").append(dark ? "#2b2b2b" : "#ffffff")
                .append("; color: ").append(dark ? "#dddddd" : "#222222").append("; font-family: sans-serif; }\n")
                .append("#vis { padding: 8px; }\n")
                .append(".error { color: #d32f2f; padding: 8px; white-space: pre-wrap; font-family: monospace; }\n")
                .append("</style>\n");
        for (String library : LIBRARIES) {
            html.append("<script src=\"").append(libraryUrl).append('/').append(library).append("\"></script>\n");
        }
        html.append("</head>\n<body>\n<div id=\"vis\"></div>\n<script>\n")
                .append("const spec = ").append(specJson.replace("</", "<\\/")).append(";\n")
                .append("const options = { renderer: 'svg', downloadFileName: ")
                .append(JsonOutput.toJson(fileName(title)))
                .append(", scaleFactor: { png: 2 }")
                // View Source and View Compiled open new windows, which the embedded browser does not support
                .append(", actions: { export: true, source: false, compiled: false, editor: false }")
                .append(dark ? ", theme: 'dark'" : "").append(" };\n")
                .append("vegaEmbed('#vis', spec, options).catch(function (error) {\n")
                .append("  const message = document.createElement('div');\n")
                .append("  message.className = 'error';\n")
                .append("  message.textContent = 'Invalid specification: ' + error.message;\n")
                .append("  document.getElementById('vis').replaceChildren(message);\n")
                .append("});\n")
                .append(DownloadBridge.SCRIPT)
                .append("</script>\n</body>\n</html>\n");
        return html.toString();
    }

    /**
     * A JTS geometry as a GeoJSON geometry (for the geoshape marks).
     */
    public static Map<String, Object> toGeoJson(Geometry geometry) {
        Map<String, Object> json = new LinkedHashMap<>();
        if (geometry instanceof Point point) {
            json.put("type", "Point");
            json.put("coordinates", point.isEmpty() ? List.of() : coordinate(point.getCoordinate()));
        } else if (geometry instanceof LineString line) {
            json.put("type", "LineString");
            json.put("coordinates", coordinates(line.getCoordinates()));
        } else if (geometry instanceof Polygon polygon) {
            json.put("type", "Polygon");
            json.put("coordinates", rings(polygon));
        } else if (geometry instanceof MultiPoint || geometry instanceof MultiLineString || geometry instanceof MultiPolygon) {
            json.put("type", geometry.getGeometryType());
            List<Object> parts = new ArrayList<>();
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                parts.add(toGeoJson(geometry.getGeometryN(i)).get("coordinates"));
            }
            json.put("coordinates", parts);
        } else if (geometry instanceof GeometryCollection) {
            json.put("type", "GeometryCollection");
            List<Object> geometries = new ArrayList<>();
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                geometries.add(toGeoJson(geometry.getGeometryN(i)));
            }
            json.put("geometries", geometries);
        } else {
            throw new IllegalArgumentException("Unsupported geometry: " + geometry.getGeometryType());
        }
        return json;
    }

    /**
     * The rings in the orientation of the Vega map projections (d3-geo): exterior ring clockwise, holes
     * counter-clockwise. With the other orientation, a polygon is drawn as the rest of the world.
     */
    private static List<Object> rings(Polygon polygon) {
        List<Object> rings = new ArrayList<>();
        rings.add(coordinates(oriented(polygon.getExteriorRing().getCoordinates(), false)));
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            rings.add(coordinates(oriented(polygon.getInteriorRingN(i).getCoordinates(), true)));
        }
        return rings;
    }

    private static Coordinate[] oriented(Coordinate[] ring, boolean counterClockwise) {
        if (ring.length < 4 || Orientation.isCCW(ring) == counterClockwise) {
            return ring;
        }
        Coordinate[] reversed = ring.clone();
        java.util.Collections.reverse(java.util.Arrays.asList(reversed));
        return reversed;
    }

    private static List<Object> coordinates(Coordinate[] coordinates) {
        List<Object> list = new ArrayList<>();
        for (Coordinate c : coordinates) {
            list.add(coordinate(c));
        }
        return list;
    }

    private static List<Double> coordinate(Coordinate c) {
        return Double.isNaN(c.getZ()) ? List.of(c.getX(), c.getY()) : List.of(c.getX(), c.getY(), c.getZ());
    }

    static String fileName(String title) {
        String name = title.replaceAll("[^\\p{L}\\p{N}_-]+", "_").replaceAll("^_+|_+$", "");
        return name.isEmpty() ? "chart" : name;
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
