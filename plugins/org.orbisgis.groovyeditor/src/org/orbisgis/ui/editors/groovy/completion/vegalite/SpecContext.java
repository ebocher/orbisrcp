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

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.orbisgis.ui.editors.groovy.syntax.CodeKinds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Position of the cursor in the specification written in the parentheses of {@code vegalite(...)}.
 *
 * @param containers  the maps and lists from the root to the cursor: for each one, the path step leading to it
 *                    (a property name, {@link VegaLiteSchema#ITEM} for an element of a list, or null for the root)
 * @param key         at a value position ({@code key: |}), the name of the property; null at a key position
 * @param prefix      the text typed before the cursor
 * @param prefixOffset offset of the prefix
 * @param quote       the quote opening the prefix ({@code "} or {@code '}) when the prefix is in a string, else 0
 * @param closedQuote true if the string of the prefix is closed after the cursor
 * @param existingKeys the keys already written in the map of the cursor
 * @param emptyContainer true if the innermost container has nothing written but the prefix ({@code [|]})
 * @param innerIsBracket true if the innermost container is a {@code [}, false for the parentheses of the call
 */
public record SpecContext(List<String> containers, String key, String prefix, int prefixOffset, char quote,
                          boolean closedQuote, Set<String> existingKeys, boolean emptyContainer,
                          boolean innerIsBracket) {

    /** Name of the function of the specifications */
    public static final String FUNCTION = "vegalite";

    /**
     * @return the path of the innermost container
     */
    public List<String> path() {
        List<String> path = new ArrayList<>();
        for (String step : containers) {
            if (step != null) {
                path.add(step);
            }
        }
        return path;
    }

    /**
     * @return the position of the cursor in a {@code vegalite(...)} call, or null if it is not in a specification or
     * if it is in Groovy code inside the specification (a closure, a method call...)
     */
    public static SpecContext at(IDocument document, int offset) {
        try {
            return find(document, offset);
        } catch (BadLocationException | RuntimeException e) {
            return null;
        }
    }

    private static SpecContext find(IDocument document, int offset) throws BadLocationException {
        String text = document.get();
        if (!text.contains(FUNCTION)) {
            return null;
        }
        byte[] kinds = CodeKinds.of(document);

        // The word being typed, maybe in a string
        int prefixStart = offset;
        while (prefixStart > 0 && isWordChar(text.charAt(prefixStart - 1))
                && CodeKinds.kind(kinds, prefixStart - 1) != CodeKinds.COMMENT) {
            prefixStart--;
        }
        char quote = 0;
        int before = prefixStart;
        if (prefixStart > 0 && (text.charAt(prefixStart - 1) == '"' || text.charAt(prefixStart - 1) == '\'')
                && CodeKinds.kind(kinds, prefixStart - 1) != CodeKinds.COMMENT
                && isOpeningQuote(kinds, text, prefixStart - 1)) {
            quote = text.charAt(prefixStart - 1);
            before = prefixStart - 1;
        } else if (CodeKinds.kind(kinds, Math.max(0, offset - 1)) == CodeKinds.STRING && offset > 0
                && (kinds[offset] & CodeKinds.STARTS) == 0 && CodeKinds.kind(kinds, offset) == CodeKinds.STRING) {
            // in a string, not after a word: "a b|"
            return null;
        }
        String prefix = text.substring(prefixStart, offset);
        if (quote == 0 && !prefix.isEmpty() && prefix.chars().anyMatch(c -> c == '-')) {
            return null;
        }
        boolean closedQuote = quote != 0 && offset < text.length() && text.charAt(offset) == quote;

        // What precedes the word: ':' for a value, ',' '[' '(' for a key or an element
        int previous = previousCode(text, kinds, before - 1);
        if (previous < 0) {
            return null;
        }
        char p = text.charAt(previous);
        String key = null;
        if (p == ':') {
            key = keyBefore(text, kinds, previous - 1);
            if (key == null) {
                return null;
            }
        } else if (p != ',' && p != '[' && p != '(') {
            return null;
        }

        // The containers, from the cursor to the call
        List<Integer> openers = new ArrayList<>();
        int depth = 0;
        int callParenthesis = -1;
        for (int i = before - 1; i >= 0; i--) {
            if (CodeKinds.kind(kinds, i) != CodeKinds.CODE) {
                continue;
            }
            char c = text.charAt(i);
            if (c == ']' || c == ')' || c == '}') {
                depth++;
            } else if (c == '[' || c == '(' || c == '{') {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                if (c == '{') {
                    return null;
                }
                if (c == '(') {
                    if (FUNCTION.equals(identifierBefore(text, kinds, i - 1))) {
                        callParenthesis = i;
                        break;
                    }
                    return null;
                }
                openers.add(i);
            }
        }
        if (callParenthesis < 0) {
            return null;
        }
        Collections.reverse(openers);

        List<String> containers = new ArrayList<>();
        containers.add(null);
        for (int opener : openers) {
            int before2 = previousCode(text, kinds, opener - 1);
            char c = before2 < 0 ? 0 : text.charAt(before2);
            if (c == ':') {
                String name = keyBefore(text, kinds, before2 - 1);
                if (name == null) {
                    return null;
                }
                containers.add(name);
            } else if (c == '(' && before2 == callParenthesis) {
                // vegalite([...]): the map is the root
                containers.add(null);
            } else if (c == '[' || c == ',') {
                containers.add(VegaLiteSchema.ITEM);
            } else {
                return null;
            }
        }

        int inner = openers.isEmpty() ? callParenthesis : openers.get(openers.size() - 1);
        Set<String> existing = new LinkedHashSet<>();
        boolean empty = collectKeys(text, kinds, inner, prefixStart, offset, existing);
        return new SpecContext(containers, key, prefix, prefixStart, quote, closedQuote, existing, empty,
                text.charAt(inner) == '[');
    }

    /**
     * Collect the keys written at the first level of a container.
     *
     * @return true if nothing but the prefix is written in the container
     */
    private static boolean collectKeys(String text, byte[] kinds, int opener, int prefixStart, int offset,
                                       Set<String> keys) {
        int depth = 0;
        boolean empty = true;
        for (int i = opener + 1; i < text.length(); i++) {
            if (i >= prefixStart && i < offset) {
                continue;
            }
            byte kind = CodeKinds.kind(kinds, i);
            char c = text.charAt(i);
            if (kind == CodeKinds.COMMENT || Character.isWhitespace(c)) {
                continue;
            }
            if (kind == CodeKinds.CODE && (c == '[' || c == '(' || c == '{')) {
                depth++;
            } else if (kind == CodeKinds.CODE && (c == ']' || c == ')' || c == '}')) {
                if (depth == 0) {
                    break;
                }
                depth--;
            } else if (depth == 0 && kind == CodeKinds.CODE && c == ':') {
                String key = keyBefore(text, kinds, i - 1);
                if (key != null) {
                    keys.add(key);
                }
            }
            empty = false;
        }
        return empty;
    }

    /**
     * @return the name of a key before a ':' ({@code name:}, {@code "name":} or {@code 'name':})
     */
    private static String keyBefore(String text, byte[] kinds, int from) {
        int end = previousCode(text, kinds, from);
        if (end < 0) {
            return null;
        }
        char c = text.charAt(end);
        if (c == '"' || c == '\'') {
            int start = text.lastIndexOf(c, end - 1);
            return start < 0 ? null : text.substring(start + 1, end);
        }
        return identifierBefore(text, kinds, end);
    }

    private static String identifierBefore(String text, byte[] kinds, int from) {
        int end = previousCode(text, kinds, from);
        if (end < 0 || !Character.isJavaIdentifierPart(text.charAt(end))) {
            return null;
        }
        int start = end;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))
                && CodeKinds.kind(kinds, start - 1) == CodeKinds.CODE) {
            start--;
        }
        return text.substring(start, end + 1);
    }

    /**
     * @return the offset of the last code character at or before an offset, skipping blanks and comments; -1 if none
     */
    private static int previousCode(String text, byte[] kinds, int from) {
        for (int i = from; i >= 0; i--) {
            byte kind = CodeKinds.kind(kinds, i);
            if (kind == CodeKinds.COMMENT || (kind == CodeKinds.CODE && Character.isWhitespace(text.charAt(i)))) {
                continue;
            }
            return i;
        }
        return -1;
    }

    /**
     * true if a quote opens a string: it starts a string partition, or it is not closing one.
     */
    private static boolean isOpeningQuote(byte[] kinds, String text, int index) {
        if ((kinds[index] & CodeKinds.STARTS) != 0) {
            return true;
        }
        // An unclosed quote is not a string partition: it is code
        return CodeKinds.kind(kinds, index) == CodeKinds.CODE;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-';
    }
}
