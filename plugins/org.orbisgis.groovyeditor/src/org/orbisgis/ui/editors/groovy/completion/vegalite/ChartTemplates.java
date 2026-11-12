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
import org.orbisgis.ui.editors.groovy.completion.GroovyTemplates;
import org.orbisgis.ui.editors.groovy.syntax.CodeKinds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chart templates proposed in an empty {@code vegalite(|)}: a complete specification (data, mark, encoding) of a
 * kind of chart, whose fields (the rows, the columns) are filled with Tab as in the Groovy templates.
 *
 * They are proposed at the root of the specification while no property but {@code data} is written; with
 * {@code data} already written, the template does not repeat it.
 */
public final class ChartTemplates {

    /**
     * A chart.
     *
     * @param name        name typed to select it
     * @param description shown in the list
     * @param lines       the properties of the specification; {@code \t} is a level of indentation
     */
    public record Chart(String name, String description, List<String> lines) {
    }

    private static final String INDENT = "    ";
    private static final String DATA = "data: [values: ${rows}],";

    public static final List<Chart> CHARTS = List.of(
            chart("bar", "Bar chart: a value per category",
                    "mark: [type: \"bar\", tooltip: true],",
                    "encoding: [",
                    "\tx: [field: \"${category}\", type: \"nominal\"],",
                    "\ty: [field: \"${value}\", type: \"quantitative\"]",
                    "]"),
            chart("stackedbar", "Stacked bars: a value per category and group",
                    "mark: [type: \"bar\", tooltip: true],",
                    "encoding: [",
                    "\tx: [field: \"${category}\", type: \"nominal\"],",
                    "\ty: [field: \"${value}\", type: \"quantitative\", aggregate: \"sum\"],",
                    "\tcolor: [field: \"${group}\", type: \"nominal\"]",
                    "]"),
            chart("line", "Line chart: a value over time",
                    "mark: [type: \"line\", point: true, tooltip: true],",
                    "encoding: [",
                    "\tx: [field: \"${date}\", type: \"temporal\"],",
                    "\ty: [field: \"${value}\", type: \"quantitative\"]",
                    "]"),
            chart("area", "Area chart: a value over time",
                    "mark: [type: \"area\", tooltip: true],",
                    "encoding: [",
                    "\tx: [field: \"${date}\", type: \"temporal\"],",
                    "\ty: [field: \"${value}\", type: \"quantitative\"]",
                    "]"),
            chart("scatter", "Scatter plot: a quantity against another one",
                    "mark: [type: \"point\", tooltip: true],",
                    "encoding: [",
                    "\tx: [field: \"${x}\", type: \"quantitative\"],",
                    "\ty: [field: \"${y}\", type: \"quantitative\"]",
                    "]"),
            chart("pie", "Pie chart: the shares of the categories",
                    "mark: [type: \"arc\", tooltip: true],",
                    "encoding: [",
                    "\ttheta: [field: \"${value}\", type: \"quantitative\"],",
                    "\tcolor: [field: \"${category}\", type: \"nominal\"]",
                    "]"),
            chart("histogram", "Histogram: the distribution of a quantity",
                    "mark: [type: \"bar\", tooltip: true],",
                    "encoding: [",
                    "\tx: [field: \"${value}\", type: \"quantitative\", bin: true],",
                    "\ty: [aggregate: \"count\", type: \"quantitative\"]",
                    "]"),
            chart("heatmap", "Heat map: a value per pair of categories",
                    "mark: [type: \"rect\", tooltip: true],",
                    "encoding: [",
                    "\tx: [field: \"${column}\", type: \"nominal\"],",
                    "\ty: [field: \"${row}\", type: \"nominal\"],",
                    "\tcolor: [field: \"${value}\", type: \"quantitative\"]",
                    "]"),
            chart("boxplot", "Box plot: the distribution of a quantity per category",
                    "mark: \"boxplot\",",
                    "encoding: [",
                    "\tx: [field: \"${category}\", type: \"nominal\"],",
                    "\ty: [field: \"${value}\", type: \"quantitative\"]",
                    "]"),
            chart("geomap", "Map of rows with a geometry, colored by a value (longitudes and latitudes)",
                    "mark: [type: \"geoshape\", tooltip: true],",
                    "encoding: [",
                    "\tcolor: [field: \"${value}\", type: \"quantitative\"]",
                    "]"),
            chart("geomapxy", "Map of rows with a geometry in projected coordinates (metres...)",
                    "mark: [type: \"geoshape\", tooltip: true],",
                    "projection: [type: \"identity\", reflectY: true],",
                    "encoding: [",
                    "\tcolor: [field: \"${value}\", type: \"quantitative\"]",
                    "]"));

    private ChartTemplates() {
    }

    private static Chart chart(String name, String description, String... lines) {
        List<String> all = new ArrayList<>();
        all.add(DATA);
        all.addAll(List.of(lines));
        return new Chart(name, description, all);
    }

    /**
     * @param document the script, with the Groovy partitioning
     * @param offset   the position of the cursor
     * @return the chart templates to propose at this position (empty out of an empty specification)
     */
    public static List<GroovyTemplates.Match> match(IDocument document, int offset) {
        SpecContext context = SpecContext.at(document, offset);
        if (context == null || context.key() != null || context.quote() != 0 || !context.path().isEmpty()) {
            return List.of();
        }
        boolean hasData = context.existingKeys().contains("data");
        if (context.existingKeys().size() > (hasData ? 1 : 0)) {
            return List.of();
        }
        String text = document.get();
        int start = context.prefixOffset();
        String prefix = context.prefix().toLowerCase(Locale.ROOT);

        // The container of the specification: the parentheses of the call, or the brackets of vegalite([...])
        int opener = openerBefore(text, CodeKinds.of(document), start);
        if (opener < 0) {
            return List.of();
        }
        String outerIndent = leadingWhitespace(text, lineStart(text, opener));
        boolean sameLine = text.lastIndexOf('\n', start - 1) < opener;
        String indent;
        String head;
        String tail = "";
        // The replaced text is the typed word only: the proposals are filtered on it while typing
        int replaceOffset = start;
        if (sameLine) {
            // vegalite(|): one property per line, and the closing parenthesis on its own line
            indent = outerIndent + INDENT;
            head = "\n" + indent;
            int next = offset;
            while (next < text.length() && text.charAt(next) == ' ') {
                next++;
            }
            if (next < text.length() && (text.charAt(next) == ')' || text.charAt(next) == ']')) {
                tail = "\n" + outerIndent;
            }
        } else {
            indent = leadingWhitespace(text, lineStart(text, start));
            head = "";
        }

        List<GroovyTemplates.Match> matches = new ArrayList<>();
        for (Chart chart : CHARTS) {
            if (!chart.name().startsWith(prefix)) {
                continue;
            }
            StringBuilder pattern = new StringBuilder(head);
            boolean first = true;
            for (String line : chart.lines()) {
                if (hasData && line.equals(DATA)) {
                    continue;
                }
                if (!first) {
                    pattern.append('\n').append(indent);
                }
                pattern.append(line.replace("\t", INDENT));
                first = false;
            }
            pattern.append("${cursor}").append(tail);
            matches.add(new GroovyTemplates.Match(chart.name(), chart.description(), pattern.toString(),
                    replaceOffset, offset - replaceOffset));
        }
        return matches;
    }

    /**
     * The '(' of the call or the '[' of the root map, before the word typed: the innermost opener at depth 0.
     */
    private static int openerBefore(String text, byte[] kinds, int from) {
        int depth = 0;
        for (int i = from - 1; i >= 0; i--) {
            if (CodeKinds.kind(kinds, i) != CodeKinds.CODE) {
                continue;
            }
            char c = text.charAt(i);
            if (c == ')' || c == ']' || c == '}') {
                depth++;
            } else if (c == '(' || c == '[' || c == '{') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    private static int lineStart(String text, int offset) {
        return text.lastIndexOf('\n', offset - 1) + 1;
    }

    private static String leadingWhitespace(String text, int lineStart) {
        int i = lineStart;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(lineStart, i);
    }
}
