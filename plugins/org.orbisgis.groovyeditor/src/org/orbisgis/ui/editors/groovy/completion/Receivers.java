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
package org.orbisgis.ui.editors.groovy.completion;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * What can be done with an object written before a point, for the completion.
 *
 * Groovy adds the iteration methods (each, collect, findAll...) to all the objects: on an object which is not a
 * collection, each calls the closure once with the object. They are proposed only on the objects which can be
 * iterated.
 */
final class Receivers {

    /** Groovy extension methods of Object iterating over the object */
    static final Set<String> ITERATION_METHODS = Set.of("each", "eachWithIndex", "collect", "collectMany",
            "collectEntries", "findAll", "find", "findResult", "findResults", "findIndexOf", "findIndexValues",
            "findLastIndexOf", "any", "every", "inject", "grep", "split", "sum", "count", "iterator", "withIndex",
            "indexed", "countBy", "groupBy", "toList", "asList", "min", "max", "toUnique",
            "unique", "flatten", "join", "first", "last", "head", "tail", "reverseEach", "eachPermutation",
            "combinations", "subsequences");

    private Receivers() {
    }

    /**
     * @return true if the object can be iterated (collection, map, array, iterator...)
     */
    static boolean isIterable(Object value) {
        return value != null && isIterable(value.getClass());
    }

    /**
     * @return true if the objects of a class can be iterated (collection, map, array, iterator...)
     */
    static boolean isIterable(Class<?> type) {
        return Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)
                || Iterator.class.isAssignableFrom(type) || Enumeration.class.isAssignableFrom(type)
                || type.isArray();
    }

    /**
     * @param placeholder the name of the object in a template applying to an object ({@code ${collection}.each})
     * @param type        the class of the object
     * @return true if the template applies to the object
     */
    static boolean acceptsTemplate(String placeholder, Class<?> type) {
        return switch (placeholder) {
            case "collection" -> isIterable(type);
            case "map" -> Map.class.isAssignableFrom(type);
            case "n", "from" -> Number.class.isAssignableFrom(type) || type == int.class || type == long.class;
            case "sql" -> isSql(type);
            default -> true;
        };
    }

    private static boolean isSql(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals("groovy.sql.Sql")) {
                return true;
            }
        }
        return false;
    }
}
