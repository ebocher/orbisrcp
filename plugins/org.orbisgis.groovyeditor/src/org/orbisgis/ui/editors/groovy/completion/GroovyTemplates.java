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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code templates of the Groovy editor: loops and control structures.
 *
 * A template is proposed when its name starts with the typed word ({@code for}, {@code ea}...), or at the
 * beginning of an empty line. The templates applying to an object ({@code ${collection}.each {...}}) are also
 * proposed after a point: {@code list.ea} gives {@code list.each { item -> }}.
 *
 * The pattern uses the Eclipse template syntax: {@code ${name}} is a field (the fields with the same name
 * are edited together, Tab goes to the next one) and {@code ${cursor}} is the final position of the cursor.
 */
public final class GroovyTemplates {

    /** A template definition */
    public record Definition(String name, String description, String pattern) {
    }

    /** A template to propose at a position of the script, with its pattern adapted to the position */
    public record Match(String name, String description, String pattern, int replaceOffset, int replaceLength) {
    }

    private static final String INDENT = "    ";

    public static final List<Definition> TEMPLATES = List.of(
            new Definition("for", "loop over a range of integers",
                    "for (${i} in 0..<${n}) {\n\t${cursor}\n}"),
            new Definition("fori", "loop with an index (Java style)",
                    "for (int ${i} = 0; ${i} < ${n}; ${i}++) {\n\t${cursor}\n}"),
            new Definition("forin", "loop over the elements of a collection",
                    "for (${item} in ${collection}) {\n\t${cursor}\n}"),
            new Definition("each", "iterate over a collection",
                    "${collection}.each { ${item} ->\n\t${cursor}\n}"),
            new Definition("eachWithIndex", "iterate over a collection with the index",
                    "${collection}.eachWithIndex { ${item}, ${index} ->\n\t${cursor}\n}"),
            new Definition("eachmap", "iterate over the entries of a map",
                    "${map}.each { ${key}, ${value} ->\n\t${cursor}\n}"),
            new Definition("times", "repeat n times",
                    "${n}.times { ${i} ->\n\t${cursor}\n}"),
            new Definition("upto", "loop from a number up to another (included)",
                    "${from}.upto(${to}) { ${i} ->\n\t${cursor}\n}"),
            new Definition("collect", "transform each element of a collection",
                    "${collection}.collect { ${item} -> ${cursor} }"),
            new Definition("findAll", "keep the elements matching a condition",
                    "${collection}.findAll { ${item} -> ${cursor} }"),
            new Definition("eachRow", "loop over the rows of a query (groovy.sql.Sql)",
                    "${sql}.eachRow(\"${query}\") { ${row} ->\n\t${cursor}\n}"),
            new Definition("eachRow", "loop over the rows of a query on a DBeaver connection",
                    "dbeaver.sql(\"${connection}\").eachRow(\"${query}\") { ${row} ->\n\t${cursor}\n}"),
            new Definition("vegalite", "Vega-Lite chart of rows, shown in a view",
                    "def ${spec} = vegalite(\n\tdata: [values: ${rows}],\n\tmark: [type: \"${bar}\", tooltip: true],\n\tencoding: [\n\t\tx: [field: \"${x}\", type: \"nominal\"],\n\t\ty: [field: \"${y}\", type: \"quantitative\"]\n\t]\n)\ndbeaver.show(${spec}, \"${title}\")${cursor}"),
            new Definition("while", "loop while a condition is true",
                    "while (${condition}) {\n\t${cursor}\n}"),
            new Definition("dowhile", "loop at least once, while a condition is true",
                    "do {\n\t${cursor}\n} while (${condition})"),
            new Definition("if", "condition",
                    "if (${condition}) {\n\t${cursor}\n}"),
            new Definition("ifelse", "condition with an alternative",
                    "if (${condition}) {\n\t${cursor}\n} else {\n\t\n}"),
            new Definition("try", "catch the exceptions",
                    "try {\n\t${cursor}\n} catch (${Exception} e) {\n\te.printStackTrace()\n}")
    );

    /** A pattern applying to an object: ${variable}.rest */
    private static final Pattern RECEIVER_PATTERN = Pattern.compile("^\\$\\{(\\w+)}\\.(.*)$", Pattern.DOTALL);

    private GroovyTemplates() {
    }

    /**
     * @param text   the script
     * @param offset the position of the cursor
     * @return the templates to propose at this position
     */
    public static List<Match> match(String text, int offset) {
        return match(text, offset, receiver -> Optional.empty());
    }

    /**
     * @param text     the script
     * @param offset   the position of the cursor
     * @param receiver gives the class of the object before the point, if it is known
     * @return the templates to propose at this position; after a point, the templates applying to an object
     * of another kind are not proposed ({@code dbeaver.} does not give {@code each})
     */
    public static List<Match> match(String text, int offset, Function<Integer, Optional<Class<?>>> receiver) {
        int start = offset;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        String prefix = text.substring(start, offset);
        boolean afterPoint = start > 0 && text.charAt(start - 1) == '.'
                && !(start > 1 && text.charAt(start - 2) == '.');
        int lineStart = text.lastIndexOf('\n', start - 1) + 1;
        String beforeWord = text.substring(lineStart, start);
        if (!afterPoint && prefix.isEmpty() && !beforeWord.isBlank()) {
            // In the middle of an expression: only when a word is typed
            return List.of();
        }
        String indent = leadingWhitespace(text.substring(lineStart));

        List<Match> matches = new ArrayList<>();
        for (Definition template : TEMPLATES) {
            if (!template.name().regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String pattern = template.pattern();
            if (afterPoint) {
                Matcher matcher = RECEIVER_PATTERN.matcher(pattern);
                if (!matcher.matches()) {
                    continue;
                }
                Optional<Class<?>> type = receiver.apply(offset);
                if (type.isPresent() && !Receivers.acceptsTemplate(matcher.group(1), type.get())) {
                    continue;
                }
                // The object is already written: insert the method call only
                pattern = matcher.group(2);
            }
            matches.add(new Match(template.name(), template.description(), indent(pattern, indent),
                    start, offset - start));
        }
        return matches;
    }

    /**
     * Indent the lines of a pattern after the first one like the current line.
     */
    static String indent(String pattern, String lineIndent) {
        return pattern.replace("\t", INDENT).replace("\n", "\n" + lineIndent);
    }

    private static String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }
}
