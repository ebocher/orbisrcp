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
package org.orbisgis.ui.editors.groovy.syntax;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Finds the brackets ({@code ( ) [ ] { }}) without a matching one. The brackets of the strings and of the comments
 * are ignored.
 */
public final class BracketChecker {

    /**
     * A bracket without a matching one.
     *
     * @param offset  offset of the bracket
     * @param line    line of the bracket (from 1)
     * @param column  column of the bracket (from 1)
     * @param message description
     */
    public record Problem(int offset, int line, int column, String message) {
    }

    private BracketChecker() {
    }

    /**
     * @param script a Groovy script
     * @return the brackets without a matching one, in the order of the script
     */
    public static List<Problem> check(String script) {
        Document document = new Document(script);
        GroovyPartitions.setup(document);
        return check(document);
    }

    /**
     * @param document a document with the Groovy partitioning
     * @return the brackets without a matching one, in the order of the document
     */
    public static List<Problem> check(IDocument document) {
        byte[] kinds = CodeKinds.of(document);
        String text = document.get();
        Deque<Integer> openers = new ArrayDeque<>();
        List<Integer> unclosed = new ArrayList<>();
        List<Integer> extra = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            if (CodeKinds.kind(kinds, i) != CodeKinds.CODE) {
                continue;
            }
            char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                openers.push(i);
            } else if (c == ')' || c == ']' || c == '}') {
                char opener = openerOf(c);
                boolean matched = false;
                for (int index : openers) {
                    if (text.charAt(index) == opener) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    extra.add(i);
                    continue;
                }
                // The brackets opened inside are not closed
                while (text.charAt(openers.peek()) != opener) {
                    unclosed.add(openers.pop());
                }
                openers.pop();
            }
        }
        unclosed.addAll(openers);

        List<Problem> problems = new ArrayList<>();
        for (int offset : extra) {
            problems.add(problem(document, offset, "'" + text.charAt(offset) + "' has no opening bracket"));
        }
        for (int offset : unclosed) {
            problems.add(problem(document, offset, "'" + text.charAt(offset) + "' is not closed"));
        }
        problems.sort((a, b) -> Integer.compare(a.offset(), b.offset()));
        return problems;
    }

    private static Problem problem(IDocument document, int offset, String message) {
        try {
            int line = document.getLineOfOffset(offset);
            return new Problem(offset, line + 1, offset - document.getLineOffset(line) + 1, message);
        } catch (BadLocationException e) {
            return new Problem(offset, 0, 0, message);
        }
    }

    private static char openerOf(char closer) {
        return switch (closer) {
            case ')' -> '(';
            case ']' -> '[';
            default -> '{';
        };
    }
}
