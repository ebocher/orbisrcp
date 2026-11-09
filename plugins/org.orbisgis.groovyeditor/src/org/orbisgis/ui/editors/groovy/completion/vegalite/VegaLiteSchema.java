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
package org.orbisgis.ui.editors.groovy.completion.vegalite;

import groovy.json.JsonSlurper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The JSON schema of Vega-Lite, read to propose the properties and the values of a specification.
 *
 * A position in a specification is a path of property names from the root; {@link #ITEM} is an element of an
 * array ({@code layer: [[...], [...]]}). The alternatives of the schema ({@code anyOf}...) are merged: the
 * properties of a position are the ones of all the possible definitions.
 *
 * The schema (about 3 MB in memory) is loaded on the first use, once for all the consoles, and the memory can be
 * released by the JVM when it lacks memory: it is then loaded again.
 */
public final class VegaLiteSchema {

    /** Path step of an element of an array */
    public static final String ITEM = "[]";

    /** Location of the schema in the bundle */
    public static final String RESOURCE = "schema/vega-lite-schema.json";

    private static final int MAX_DEPTH = 40;

    /**
     * A property.
     *
     * @param name        name
     * @param description description (Markdown), may be empty
     * @param type        short description of the accepted values: "string | object"...
     * @param objectOnly  true if the value can only be an object (a map)
     * @param arrayOnly   true if the value can only be an array (a list)
     */
    public record Property(String name, String description, String type, boolean objectOnly, boolean arrayOnly) {
    }

    /**
     * Possible values at a position.
     *
     * @param values      the constant values (enumerations): strings and booleans
     * @param object      true if an object (a map) is accepted
     * @param array       true if an array (a list) is accepted
     * @param primitive   true if other values (numbers, any string...) are accepted
     */
    public record Values(List<Object> values, boolean object, boolean array, boolean primitive) {
    }

    private static volatile SoftReference<VegaLiteSchema> instance = new SoftReference<>(null);
    private static CompletableFuture<VegaLiteSchema> loading;

    private final Map<?, ?> root;
    private final Map<?, ?> definitions;
    private final Map<String, List<Map<?, ?>>> cache = new ConcurrentHashMap<>();

    VegaLiteSchema(Map<?, ?> root) {
        this.root = root;
        Object definitions = root.get("definitions");
        this.definitions = definitions instanceof Map<?, ?> map ? map : Map.of();
    }

    /**
     * Start loading the schema in the background, if it is not loaded.
     */
    public static synchronized CompletableFuture<VegaLiteSchema> load() {
        VegaLiteSchema schema = instance.get();
        if (schema != null) {
            return CompletableFuture.completedFuture(schema);
        }
        if (loading == null || loading.isCompletedExceptionally()) {
            loading = CompletableFuture.supplyAsync(VegaLiteSchema::read);
            loading.thenAccept(loaded -> {
                synchronized (VegaLiteSchema.class) {
                    instance = new SoftReference<>(loaded);
                    loading = null;
                }
            });
        }
        return loading;
    }

    /**
     * @return the schema, waiting for its loading (a fraction of a second the first time); null if it cannot be read
     */
    public static VegaLiteSchema get() {
        try {
            return load().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private static VegaLiteSchema read() {
        try (InputStream in = VegaLiteSchema.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found");
            }
            return of(in);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the Vega-Lite schema", e);
        }
    }

    /**
     * @return the schema read from a stream
     */
    public static VegaLiteSchema of(InputStream in) {
        Object json = new JsonSlurper().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        if (!(json instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Not a JSON schema");
        }
        return new VegaLiteSchema(map);
    }

    /**
     * @return the properties of the objects at a position, sorted by name
     */
    public Map<String, Property> properties(List<String> path) {
        Map<String, List<Map<?, ?>>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (Map<?, ?> schema : at(path)) {
            if (schema.get("properties") instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    String name = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Map<?, ?> property) {
                        byName.computeIfAbsent(name, n -> new ArrayList<>()).add(property);
                        if (!descriptions.containsKey(name) && property.get("description") instanceof String text) {
                            descriptions.put(name, text);
                        }
                    }
                }
            }
        }
        Map<String, Property> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<?, ?>>> entry : byName.entrySet()) {
            List<Map<?, ?>> alternatives = new ArrayList<>();
            for (Map<?, ?> property : entry.getValue()) {
                alternatives.addAll(expand(property));
            }
            Values values = valuesOf(alternatives);
            String description = descriptions.getOrDefault(entry.getKey(), "");
            if (description.isEmpty()) {
                for (Map<?, ?> alternative : alternatives) {
                    if (alternative.get("description") instanceof String text) {
                        description = text;
                        break;
                    }
                }
            }
            result.put(entry.getKey(), new Property(entry.getKey(), description, typeLabel(values),
                    values.object() && !values.array() && !values.primitive() && values.values().isEmpty(),
                    values.array() && !values.object() && !values.primitive() && values.values().isEmpty()));
        }
        return result;
    }

    /**
     * @return the possible values at a position (the path ends with the name of the property)
     */
    public Values values(List<String> path) {
        return valuesOf(at(path));
    }

    /**
     * @return true if the schema knows the position
     */
    public boolean exists(List<String> path) {
        return !at(path).isEmpty();
    }

    /**
     * The definitions possible at a position, with the references and the alternatives expanded.
     */
    List<Map<?, ?>> at(List<String> path) {
        String key = String.join("\u0000", path);
        List<Map<?, ?>> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<Map<?, ?>> current = expand(root);
        for (String step : path) {
            List<Map<?, ?>> next = new ArrayList<>();
            for (Map<?, ?> schema : current) {
                if (ITEM.equals(step)) {
                    if (schema.get("items") instanceof Map<?, ?> items) {
                        next.addAll(expand(items));
                    }
                } else if (schema.get("properties") instanceof Map<?, ?> properties
                        && properties.get(step) instanceof Map<?, ?> property) {
                    next.addAll(expand(property));
                } else if (schema.get("additionalProperties") instanceof Map<?, ?> additional
                        && !(schema.get("properties") instanceof Map<?, ?> p && !p.isEmpty())) {
                    // a dictionary (datasets...)
                    next.addAll(expand(additional));
                }
            }
            current = distinct(next);
            if (current.isEmpty()) {
                break;
            }
        }
        cache.put(key, current);
        return current;
    }

    /**
     * The concrete definitions of a schema: references followed, alternatives (anyOf, oneOf, allOf) flattened.
     */
    List<Map<?, ?>> expand(Map<?, ?> schema) {
        List<Map<?, ?>> result = new ArrayList<>();
        expand(schema, result, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        return distinct(result);
    }

    private void expand(Map<?, ?> schema, List<Map<?, ?>> result, Set<Object> visited, int depth) {
        if (depth > MAX_DEPTH || !visited.add(schema)) {
            return;
        }
        boolean alternatives = false;
        if (schema.get("$ref") instanceof String ref) {
            Map<?, ?> target = resolve(ref);
            if (target != null) {
                expand(target, result, visited, depth + 1);
            }
            alternatives = true;
        }
        for (String keyword : List.of("anyOf", "oneOf", "allOf")) {
            if (schema.get(keyword) instanceof List<?> list) {
                alternatives = true;
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        expand(map, result, visited, depth + 1);
                    }
                }
            }
        }
        if (!alternatives || schema.containsKey("properties") || schema.containsKey("enum")
                || schema.containsKey("type")) {
            result.add(schema);
        }
    }

    private Map<?, ?> resolve(String ref) {
        String prefix = "#/definitions/";
        if (!ref.startsWith(prefix)) {
            return null;
        }
        String name = ref.substring(prefix.length());
        if (name.contains("%")) {
            name = java.net.URLDecoder.decode(name.replace("+", "%2B"), StandardCharsets.UTF_8);
        }
        return definitions.get(name) instanceof Map<?, ?> map ? map : null;
    }

    private static Values valuesOf(List<Map<?, ?>> schemas) {
        Set<Object> values = new LinkedHashSet<>();
        boolean object = false;
        boolean array = false;
        boolean primitive = false;
        for (Map<?, ?> schema : schemas) {
            if (schema.get("enum") instanceof List<?> list) {
                for (Object value : list) {
                    if (value instanceof String || value instanceof Boolean) {
                        values.add(value);
                    }
                }
                continue;
            }
            if (schema.containsKey("const")) {
                Object value = schema.get("const");
                if (value instanceof String || value instanceof Boolean) {
                    values.add(value);
                }
                continue;
            }
            Object type = schema.get("type");
            List<?> types = type instanceof List<?> list ? list : type == null ? List.of() : List.of(type);
            if (types.isEmpty() && schema.containsKey("properties")) {
                object = true;
            }
            for (Object t : types) {
                switch (String.valueOf(t)) {
                    case "object" -> object = true;
                    case "array" -> array = true;
                    case "boolean" -> {
                        values.add(true);
                        values.add(false);
                    }
                    case "null" -> {
                        // not proposed
                    }
                    default -> primitive = true;
                }
            }
        }
        return new Values(new ArrayList<>(values), object, array, primitive);
    }

    private static String typeLabel(Values values) {
        List<String> parts = new ArrayList<>();
        if (!values.values().isEmpty()) {
            List<Object> shown = values.values().subList(0, Math.min(4, values.values().size()));
            StringBuilder text = new StringBuilder();
            for (Object value : shown) {
                text.append(text.length() == 0 ? "" : " | ").append(value instanceof String ? "\"" + value + "\"" : value);
            }
            if (values.values().size() > shown.size()) {
                text.append(" | ...");
            }
            parts.add(text.toString());
        }
        if (values.primitive()) {
            parts.add("value");
        }
        if (values.object()) {
            parts.add("[map]");
        }
        if (values.array()) {
            parts.add("[list]");
        }
        return String.join(" | ", parts);
    }

    private static List<Map<?, ?>> distinct(List<Map<?, ?>> schemas) {
        Set<Map<?, ?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Map<?, ?>> result = new ArrayList<>();
        for (Map<?, ?> schema : schemas) {
            if (seen.add(schema)) {
                result.add(schema);
            }
        }
        return result;
    }
}
