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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;

import java.util.regex.Pattern;

/**
 * Converts the geometries read with the JDBC drivers of DBeaver into JTS geometries.
 *
 * The driver classes ({@code PGgeometry}, {@code PGobject}) come from the class loader of the DBeaver driver,
 * so they are read by name and reflection: a library loaded with @Grab has its own copy of these classes.
 */
public final class GeometryConverter {

    private static final Pattern HEX = Pattern.compile("[0-9A-Fa-f]+");

    private GeometryConverter() {
    }

    /**
     * @param value a value read from a JDBC result set
     * @return the JTS geometry if the value is a PostGIS geometry (with its SRID), the value otherwise
     */
    public static Object toGeometry(Object value) {
        if (value == null || value instanceof Geometry) {
            return value;
        }
        String className = value.getClass().getName();
        try {
            if (className.endsWith(".PGgeometry") || className.endsWith(".PGgeometryLW")) {
                // PostGIS JDBC: toString() gives the EWKT
                return fromText(value.toString());
            }
            if (className.endsWith(".PGobject")) {
                Object type = value.getClass().getMethod("getType").invoke(value);
                if ("geometry".equalsIgnoreCase(String.valueOf(type)) || "geography".equalsIgnoreCase(String.valueOf(type))) {
                    Object text = value.getClass().getMethod("getValue").invoke(value);
                    return text == null ? null : fromText(text.toString());
                }
            }
        } catch (Exception | LinkageError e) {
            // Not a geometry JTS can read (curves, M values...): keep the driver value
        }
        return value;
    }

    /**
     * Read a geometry from its hexadecimal (E)WKB or its (E)WKT ({@code SRID=4326;POINT(1 2)}).
     */
    static Geometry fromText(String text) throws Exception {
        String trimmed = text.trim();
        if (HEX.matcher(trimmed).matches()) {
            // EWKB: the SRID is read by JTS
            return new WKBReader().read(WKBReader.hexToBytes(trimmed));
        }
        int srid = 0;
        if (trimmed.regionMatches(true, 0, "SRID=", 0, 5)) {
            int separator = trimmed.indexOf(';');
            srid = Integer.parseInt(trimmed.substring(5, separator).trim());
            trimmed = trimmed.substring(separator + 1);
        }
        Geometry geometry = new WKTReader().read(trimmed);
        geometry.setSRID(srid);
        return geometry;
    }
}
