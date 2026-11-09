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

import org.eclipse.jface.text.IDocument;
import org.orbisgis.ui.editors.groovy.syntax.CodeKinds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Completion of the names of the DBeaver connections, in the first argument of the methods of the
 * {@code dbeaver} object taking a connection: {@code dbeaver.query("|}.
 *
 * The connections are the ones of the DBeaver configuration (no connection is opened): their list is given by the
 * plugin with {@link #configure}.
 */
public final class ConnectionCompletion {

    /**
     * A connection.
     *
     * @param name        name, as in the Database Navigator
     * @param driver      name of the driver
     * @param connected   true if the connection is open
     * @param description details (URL, folder...)
     */
    public record Connection(String name, String driver, boolean connected, String description) {
    }

    /**
     * A proposal.
     *
     * @param connection the connection
     * @param replacement text inserted
     * @param offset      start of the replaced text
     * @param length      length of the replaced text
     * @param cursor      position of the cursor in the replacement
     */
    public record Suggestion(Connection connection, String replacement, int offset, int length, int cursor) {
    }

    /**
     * Proposals at a position.
     *
     * @param suggestions the connections
     * @param inString    true in a string: only the connections are proposed; in code ({@code dbeaver.query(|}),
     *                    the Groovy proposals (variables...) are also useful
     */
    public record Result(List<Suggestion> suggestions, boolean inString) {
    }

    private static volatile Class<?> facadeClass;
    private static volatile Set<String> methods = Set.of();
    private static volatile Supplier<List<Connection>> catalog = Collections::emptyList;

    private ConnectionCompletion() {
    }

    /**
     * @param facade            class of the {@code dbeaver} object
     * @param connectionMethods its methods taking a connection as first argument
     * @param connections       gives the connections
     */
    public static void configure(Class<?> facade, Set<String> connectionMethods,
                                 Supplier<List<Connection>> connections) {
        facadeClass = facade;
        methods = Set.copyOf(connectionMethods);
        catalog = connections;
    }

    /**
     * @param document  the script, with the Groovy partitioning
     * @param offset    the position of the cursor
     * @param variables the variables of the context
     * @return the proposals, or null if the position is not the first argument of a method taking a connection
     */
    public static Result compute(IDocument document, int offset, Map<String, Object> variables) {
        String text = document.get();
        byte[] kinds = CodeKinds.of(document);

        // The word typed, maybe in a string: "geo| or geo|
        int prefixStart = offset;
        while (prefixStart > 0 && text.charAt(prefixStart - 1) != '"' && text.charAt(prefixStart - 1) != '\''
                && text.charAt(prefixStart - 1) != '\n' && text.charAt(prefixStart - 1) != '('
                && (CodeKinds.kind(kinds, prefixStart - 1) == CodeKinds.STRING
                || Character.isJavaIdentifierPart(text.charAt(prefixStart - 1)))) {
            prefixStart--;
        }
        char quote = 0;
        int beforeArgument = prefixStart - 1;
        if (prefixStart > 0 && (text.charAt(prefixStart - 1) == '"' || text.charAt(prefixStart - 1) == '\'')
                && CodeKinds.kind(kinds, prefixStart - 1) != CodeKinds.COMMENT) {
            quote = text.charAt(prefixStart - 1);
            // not a triple quoted string, and an opening quote
            if (prefixStart >= 3 && text.startsWith(String.valueOf(quote).repeat(3), prefixStart - 3)) {
                return null;
            }
            if (CodeKinds.kind(kinds, prefixStart - 1) == CodeKinds.STRING
                    && (kinds[prefixStart - 1] & CodeKinds.STARTS) == 0) {
                return null;
            }
            beforeArgument = prefixStart - 2;
        } else if (CodeKinds.kind(kinds, Math.max(0, offset - 1)) == CodeKinds.STRING && offset > 0) {
            return null;
        }
        String prefix = text.substring(prefixStart, offset);

        // dbeaver.method(
        int parenthesis = previousCode(text, kinds, beforeArgument);
        if (parenthesis < 0 || text.charAt(parenthesis) != '(') {
            return null;
        }
        int methodEnd = previousCode(text, kinds, parenthesis - 1);
        String method = identifierEndingAt(text, methodEnd);
        if (method == null || !methods.contains(method)) {
            return null;
        }
        int dot = previousCode(text, kinds, methodEnd - method.length());
        if (dot < 0 || text.charAt(dot) != '.') {
            return null;
        }
        String receiver = identifierEndingAt(text, previousCode(text, kinds, dot - 1));
        Object value = receiver == null ? null : variables.get(receiver);
        if (value == null || facadeClass == null || !facadeClass.isInstance(value)) {
            return null;
        }

        boolean closed = quote != 0 && offset < text.length() && text.charAt(offset) == quote;
        List<Suggestion> suggestions = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<Connection> connections = catalog.get();
        // The names starting with the prefix, then the ones containing it
        for (int pass = 0; pass < 2; pass++) {
            for (Connection connection : connections) {
                String name = connection.name().toLowerCase(Locale.ROOT);
                boolean matches = pass == 0 ? name.startsWith(lower) : !name.startsWith(lower) && name.contains(lower);
                if (matches) {
                    suggestions.add(suggestion(connection, quote, closed, prefix, prefixStart));
                }
            }
        }
        if (quote == 0 && suggestions.isEmpty()) {
            // a variable being typed: the Groovy completion
            return null;
        }
        return new Result(suggestions, quote != 0);
    }

    private static Suggestion suggestion(Connection connection, char quote, boolean closed, String prefix,
                                         int prefixStart) {
        String name = connection.name();
        if (quote != 0) {
            String escaped = name.replace("\\", "\\\\").replace(String.valueOf(quote), "\\" + quote);
            if (quote == '"') {
                escaped = escaped.replace("$", "\\$");
            }
            String replacement = escaped + (closed ? "" : String.valueOf(quote));
            return new Suggestion(connection, replacement, prefixStart, prefix.length(),
                    replacement.length() + (closed ? 1 : 0));
        }
        // double quotes, as usual; single quotes if the name has a '$' (GString interpolation) or a '"'
        char q = name.indexOf('$') >= 0 || name.indexOf('"') >= 0 ? '\'' : '"';
        String replacement = q + name.replace("\\", "\\\\").replace(String.valueOf(q), "\\" + q) + q;
        return new Suggestion(connection, replacement, prefixStart, prefix.length(), replacement.length());
    }

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

    private static String identifierEndingAt(String text, int end) {
        if (end < 0 || !Character.isJavaIdentifierPart(text.charAt(end))) {
            return null;
        }
        int start = end;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, end + 1);
    }
}
