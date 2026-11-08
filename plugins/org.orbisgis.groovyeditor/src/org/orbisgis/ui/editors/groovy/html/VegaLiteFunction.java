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

import groovy.lang.Closure;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code vegalite(...)} function of the console: a Vega-Lite specification, written as named arguments
 * ({@code vegalite(mark: "bar", encoding: [...])}) or as a map ({@code vegalite([...])}).
 *
 * It returns the specification as a map, for {@code dbeaver.show(spec)} or anything accepting a map. In the
 * editor, the completion proposes the Vega-Lite properties and values inside its parentheses.
 */
public class VegaLiteFunction extends Closure<Map<String, Object>> {

    /** Name of the function in the scripts */
    public static final String NAME = "vegalite";

    public VegaLiteFunction() {
        super(null);
    }

    public Map<String, Object> doCall(Map<?, ?> spec) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (spec != null) {
            spec.forEach((key, value) -> copy.put(String.valueOf(key), value));
        }
        return copy;
    }

    public Map<String, Object> doCall() {
        return new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        return "vegalite(spec): a Vega-Lite specification, as named arguments or a map";
    }
}
