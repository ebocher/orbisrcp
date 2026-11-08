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
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Formatting of Groovy code: indentation by the depth of the brackets ({@code { } [ ] ( )}), method chains
 * (lines starting with a point), trailing spaces and repeated blank lines.
 *
 * Only the blanks change, never the code. The strings and the comments are known by the Groovy partitioning: the
 * brackets they contain are ignored and the lines of a multi-line string are kept as they are. The code may be
 * incomplete or wrong.
 */
public final class GroovyFormatter {

    private static final byte CODE = 0;
    private static final byte COMMENT = 1;
    private static final byte STRING = 2;
    /** Flag of the first character of a comment or a string */
    private static final byte STARTS = 4;

    /** An opening bracket */
    private record Opener(char bracket, int offset, int level, String indent) {
    }

    /**
     * Result of a formatting.
     *
     * @param text      the formatted text of the lines
     * @param lineMap   for each line of the range, its index in the formatted text (a removed blank line has the
     *                  index of the line before it)
     */
    public record Result(String text, int[] lineMap) {
    }

    private final String indentUnit;

    /**
     * @param indentUnit text of one indentation level (a tab, or spaces)
     */
    public GroovyFormatter(String indentUnit) {
        this.indentUnit = indentUnit;
    }

    /**
     * @return the text of one indentation level
     */
    public String getIndentUnit() {
        return indentUnit;
    }

    /**
     * @param tabWidth      width of a tab
     * @param spacesForTabs true to indent with spaces
     * @return the text of one indentation level
     */
    public static String indentUnit(int tabWidth, boolean spacesForTabs) {
        return spacesForTabs ? " ".repeat(Math.max(1, tabWidth)) : "\t";
    }

    /**
     * Format lines of a document.
     *
     * @param document  the document, with the Groovy partitioning
     * @param firstLine first line to format
     * @param lastLine  last line to format
     * @return the new text of the lines, from the start of the first one to the end of the last one (with its line
     * delimiter)
     */
    public Result format(IDocument document, int firstLine, int lastLine) throws BadLocationException {
        byte[] kinds = kinds(document);
        Deque<Opener> stack = new ArrayDeque<>();
        StringBuilder out = new StringBuilder();
        int[] lineMap = new int[lastLine - firstLine + 1];
        int outLine = -1;
        boolean previousBlank = false;
        int statementLevel = 0;
        String statementIndent = "";
        for (int line = 0; line <= lastLine; line++) {
            IRegion region = document.getLineInformation(line);
            int start = region.getOffset();
            int end = start + region.getLength();
            String text = document.get(start, region.getLength());
            String delimiter = document.getLineDelimiter(line);
            int first = firstNonBlank(text);
            int contentStart = start + first;

            boolean continuation = kinds[start] != CODE && (kinds[start] & STARTS) == 0;
            int level = levelOf(document, kinds, stack, contentStart, end);
            String formatted;
            if (continuation) {
                formatted = continuationLine(text, (byte) (kinds[start] & ~STARTS), level);
            } else if (first == text.length()) {
                formatted = "";
            } else {
                boolean keepTrailing = (kinds[end] & ~STARTS) == STRING && (kinds[end] & STARTS) == 0;
                String content = keepTrailing ? text.substring(first) : text.substring(first).stripTrailing();
                formatted = indentUnit.repeat(level) + content;
            }
            // The brackets opened after a multi-line string belong to the statement where the string started
            if (!continuation) {
                statementLevel = level;
                statementIndent = indentOf(text);
            }
            scanLine(document, kinds, stack, start, end, statementLevel, statementIndent);

            if (line < firstLine) {
                continue;
            }
            boolean blank = !continuation && formatted.isEmpty();
            if (blank && previousBlank) {
                lineMap[line - firstLine] = Math.max(outLine, 0);
                continue;
            }
            previousBlank = blank;
            out.append(formatted).append(delimiter == null ? "" : delimiter);
            outLine++;
            lineMap[line - firstLine] = outLine;
        }
        return new Result(out.toString(), lineMap);
    }

    /**
     * Indentation of a new line inserted at an offset (Enter), from the indentation of the lines in the document.
     *
     * @return the indentation, and the indentation of the closing bracket following the offset if Enter is typed
     * between an opening and a closing bracket ({@code {|}}), else null
     */
    public String[] newLineIndent(IDocument document, int offset) throws BadLocationException {
        byte[] kinds = kinds(document);
        Deque<Opener> stack = openersBefore(document, kinds, offset);
        int line = document.getLineOfOffset(offset);
        IRegion region = document.getLineInformation(line);
        String lineText = document.get(region.getOffset(), region.getLength());
        String after = document.get(offset, region.getOffset() + region.getLength() - offset).strip();
        Opener top = stack.peek();
        if (top == null) {
            return new String[]{"", null};
        }
        if (!after.isEmpty() && closerOf(top.bracket()) == after.charAt(0)) {
            String before = document.get(region.getOffset(), offset - region.getOffset()).stripTrailing();
            boolean justOpened = top.offset() >= region.getOffset() && top.offset() == region.getOffset() + before.length() - 1;
            if (justOpened) {
                return new String[]{top.indent() + indentUnit, top.indent()};
            }
            return new String[]{top.indent(), null};
        }
        if (!after.isEmpty() && isCloser(after.charAt(0))) {
            return new String[]{top.indent(), null};
        }
        // A method chain line (.each {...}) has its own indentation: the next line keeps the one of the opener
        return new String[]{top.indent() + indentUnit, null};
    }

    /**
     * Indentation of a closing bracket typed at an offset, from the indentation of the line of its opening bracket.
     *
     * @return the indentation, or null if the bracket closes nothing
     */
    public String closerIndent(IDocument document, int offset, char closer) throws BadLocationException {
        byte[] kinds = kinds(document);
        for (Opener opener : openersBefore(document, kinds, offset)) {
            if (closerOf(opener.bracket()) == closer) {
                return opener.indent();
            }
        }
        return null;
    }

    private Deque<Opener> openersBefore(IDocument document, byte[] kinds, int offset) throws BadLocationException {
        Deque<Opener> stack = new ArrayDeque<>();
        int lastLine = document.getLineOfOffset(offset);
        String statementIndent = "";
        for (int line = 0; line <= lastLine; line++) {
            IRegion region = document.getLineInformation(line);
            int start = region.getOffset();
            int end = line == lastLine ? offset : start + region.getLength();
            if (kinds[start] == CODE || (kinds[start] & STARTS) != 0) {
                statementIndent = indentOf(document.get(start, region.getLength()));
            }
            scanLine(document, kinds, stack, start, end, 0, statementIndent);
        }
        return stack;
    }

    /**
     * Level of a line: the level of the line of the bracket closed by a closing bracket at its start, else one
     * more than the line of the innermost open bracket. A method chain line ({@code .each {}}) has one more level.
     */
    private int levelOf(IDocument document, byte[] kinds, Deque<Opener> stack, int contentStart, int end)
            throws BadLocationException {
        Opener top = stack.peek();
        if (contentStart < end && kinds[contentStart] == CODE) {
            char c = document.getChar(contentStart);
            if (isCloser(c)) {
                for (Opener opener : stack) {
                    if (closerOf(opener.bracket()) == c) {
                        return opener.level();
                    }
                }
                return top == null ? 0 : top.level();
            }
            int level = top == null ? 0 : top.level() + 1;
            if (isChain(document, contentStart, end)) {
                level++;
            }
            return level;
        }
        return top == null ? 0 : top.level() + 1;
    }

    private static boolean isChain(IDocument document, int offset, int end) throws BadLocationException {
        String start = document.get(offset, Math.min(3, end - offset));
        return (start.startsWith(".") && !start.startsWith("..")) || start.startsWith("?.") || start.startsWith("*.")
                || start.startsWith("&.");
    }

    private static void scanLine(IDocument document, byte[] kinds, Deque<Opener> stack, int start, int end, int level,
                                 String indent) throws BadLocationException {
        for (int offset = start; offset < end; offset++) {
            if (kinds[offset] != CODE) {
                continue;
            }
            char c = document.getChar(offset);
            if (c == '{' || c == '[' || c == '(') {
                stack.push(new Opener(c, offset, level, indent));
            } else if (isCloser(c)) {
                for (Opener opener : stack) {
                    if (closerOf(opener.bracket()) == c) {
                        while (stack.pop() != opener) {
                            // brackets left open inside the closed ones
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * A line inside a multi-line comment or string. The lines of a comment starting with a star are aligned on
     * the code, the other ones are kept.
     */
    private String continuationLine(String text, byte kind, int level) {
        String content = text.strip();
        if (kind == COMMENT && content.startsWith("*")) {
            return indentUnit.repeat(level) + " " + content;
        }
        return kind == COMMENT ? text.stripTrailing() : text;
    }

    /**
     * Kind of each character of the document: code, comment or string.
     */
    private static byte[] kinds(IDocument document) {
        byte[] kinds = new byte[document.getLength() + 1];
        try {
            ITypedRegion[] regions = TextUtilities.computePartitioning(document, GroovyPartitions.PARTITIONING, 0,
                    document.getLength(), false);
            for (ITypedRegion region : regions) {
                byte kind = switch (region.getType()) {
                    case GroovyPartitions.COMMENT, GroovyPartitions.DOC_COMMENT -> COMMENT;
                    case GroovyPartitions.STRING, GroovyPartitions.GSTRING -> STRING;
                    default -> CODE;
                };
                if (kind == CODE) {
                    continue;
                }
                int end = region.getOffset() + region.getLength();
                for (int offset = region.getOffset(); offset < end; offset++) {
                    kinds[offset] = kind;
                }
                kinds[region.getOffset()] |= STARTS;
            }
        } catch (BadLocationException e) {
            // no partitioning: all code
        }
        return kinds;
    }

    private static int firstNonBlank(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static String indentOf(String text) {
        return text.substring(0, firstNonBlank(text));
    }

    private static boolean isCloser(char c) {
        return c == '}' || c == ']' || c == ')';
    }

    private static char closerOf(char opener) {
        return switch (opener) {
            case '{' -> '}';
            case '[' -> ']';
            default -> ')';
        };
    }
}
