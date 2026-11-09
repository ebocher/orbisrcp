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

import org.eclipse.jface.text.IDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Completion of the Vega-Lite specifications written in {@code vegalite(...)}:
 * <ul>
 * <li>at a key position, the properties of the schema at this place, not yet written;</li>
 * <li>at a value position, the constant values of the property ({@code type: "nominal"...});</li>
 * <li>elsewhere (values of any form: {@code values: rows}, {@code width: w}...), nothing: the Groovy completion
 * applies.</li>
 * </ul>
 */
public final class VegaLiteCompletion {

    /**
     * A proposal.
     *
     * @param display     text shown in the list
     * @param type        accepted values, shown after the text
     * @param replacement text inserted
     * @param offset      start of the replaced text
     * @param length      length of the replaced text
     * @param cursor      position of the cursor in the replacement
     * @param description documentation (Markdown), may be empty
     * @param key         true for a property, false for a value
     */
    public record Suggestion(String display, String type, String replacement, int offset, int length, int cursor,
                             String description, boolean key) {
    }

    private final Supplier<VegaLiteSchema> schema;

    /**
     * @param schema gives the schema (loaded on the first use)
     */
    public VegaLiteCompletion(Supplier<VegaLiteSchema> schema) {
        this.schema = schema;
    }

    /**
     * @return the proposals, or null if the position is not handled by the Vega-Lite completion (out of a
     * specification, or a value written in Groovy): the Groovy completion applies. An empty list means a position
     * of the specification with nothing to propose.
     */
    public List<Suggestion> compute(IDocument document, int offset) {
        SpecContext context = SpecContext.at(document, offset);
        if (context == null) {
            return null;
        }
        VegaLiteSchema vl = schema.get();
        if (vl == null) {
            return null;
        }
        List<String> path = context.path();
        if (context.key() != null) {
            List<String> valuePath = new ArrayList<>(path);
            valuePath.add(context.key());
            return values(vl, valuePath, context);
        }
        // A key, or an element of a list
        VegaLiteSchema.Values container = vl.values(path);
        boolean list = context.innerIsBracket() && container.array() && !container.object()
                && (context.emptyContainer() || context.existingKeys().isEmpty());
        if (list) {
            List<String> itemPath = new ArrayList<>(path);
            itemPath.add(VegaLiteSchema.ITEM);
            return values(vl, itemPath, context);
        }
        Map<String, VegaLiteSchema.Property> properties = vl.properties(path);
        if (properties.isEmpty()) {
            // free form object (data rows...): Groovy
            return null;
        }
        List<Suggestion> suggestions = new ArrayList<>();
        String prefix = context.prefix().toLowerCase(Locale.ROOT);
        for (VegaLiteSchema.Property property : properties.values()) {
            if (context.existingKeys().contains(property.name())
                    || !property.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                continue;
            }
            suggestions.add(keySuggestion(property, context));
        }
        return suggestions;
    }

    private static Suggestion keySuggestion(VegaLiteSchema.Property property, SpecContext context) {
        String name = property.name();
        if (context.quote() != 0) {
            // "name": the key is written as a string
            String replacement = name + (context.closedQuote() ? "" : String.valueOf(context.quote()));
            return new Suggestion(name, property.type(), replacement, context.prefixOffset(),
                    context.prefix().length(), replacement.length() + (context.closedQuote() ? 1 : 0),
                    property.description(), true);
        }
        // single quotes: "$schema" would be a GString reading a variable
        String key = isIdentifier(name) ? name : "'" + name + "'";
        String replacement;
        int cursor;
        if (property.objectOnly() || property.arrayOnly()) {
            replacement = key + ": []";
            cursor = replacement.length() - 1;
        } else {
            replacement = key + ": ";
            cursor = replacement.length();
        }
        return new Suggestion(name, property.type(), replacement, context.prefixOffset(), context.prefix().length(),
                cursor, property.description(), true);
    }

    private static List<Suggestion> values(VegaLiteSchema vl, List<String> path, SpecContext context) {
        VegaLiteSchema.Values values = vl.values(path);
        List<Suggestion> suggestions = new ArrayList<>();
        String prefix = context.prefix().toLowerCase(Locale.ROOT);
        for (Object value : values.values()) {
            if (context.quote() != 0 && !(value instanceof String)) {
                continue;
            }
            String text = String.valueOf(value);
            if (!text.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                continue;
            }
            String replacement;
            int cursor;
            if (context.quote() != 0) {
                replacement = text + (context.closedQuote() ? "" : String.valueOf(context.quote()));
                cursor = replacement.length() + (context.closedQuote() ? 1 : 0);
            } else {
                replacement = value instanceof String ? "\"" + text + "\"" : text;
                cursor = replacement.length();
            }
            suggestions.add(new Suggestion(value instanceof String ? "\"" + text + "\"" : text, "",
                    replacement, context.prefixOffset(), context.prefix().length(), cursor, "", false));
        }
        boolean structured = (values.object() || values.array()) && !values.primitive();
        if (context.quote() == 0 && context.prefix().isEmpty() && structured) {
            // [field: ...] or [[...], ...]
            String label = values.object() ? "[ ... ]  map" : "[ ... ]  list";
            suggestions.add(new Suggestion(label, "", "[]", context.prefixOffset(), 0, 1, "", false));
        }
        if (suggestions.isEmpty() && context.quote() == 0) {
            // a value written in Groovy: rows, a variable, a number...
            return null;
        }
        return suggestions;
    }

    private static boolean isIdentifier(String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0)) || name.charAt(0) == '$') {
            return false;
        }
        return name.chars().allMatch(Character::isJavaIdentifierPart);
    }
}
