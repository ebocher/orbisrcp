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
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enter in the block comments, as in the Java editor:
 * <ul>
 * <li>after {@code /**} or {@code /*}: the comment is closed, the cursor on a {@code  * } line; a documentation
 * comment ({@code /**}) above a method or a closure gets its {@code @param} and {@code @return} tags;</li>
 * <li>in a block comment: the new line starts with {@code  * }.</li>
 * </ul>
 */
public final class CommentEditing {

    private static final Set<String> MODIFIERS = Set.of("public", "protected", "private", "static", "final",
            "abstract", "synchronized", "def");
    private static final Set<String> KEYWORDS = Set.of("if", "else", "for", "while", "switch", "catch", "return",
            "new", "throw", "try", "do", "assert", "synchronized", "in", "instanceof", "class", "interface", "enum",
            "trait", "record", "println", "print");

    /** A method: modifiers and/or a type, a name, the parameters */
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*((?:(?:public|protected|private|static|final|abstract|synchronized|def)\\s+)*)"
                    + "(?:([\\w.$]+(?:\\s*<[^()]*?>)?(?:\\s*\\[\\s*])*)\\s+)?(\\w+)\\s*\\(([^)]*)\\)", Pattern.DOTALL);

    /** A closure in a variable: def name = { a, b -> */
    private static final Pattern CLOSURE = Pattern.compile("^\\s*(?:def\\s+|final\\s+)?\\w+\\s*=\\s*\\{\\s*([\\w\\s,]*?)\\s*->");

    private CommentEditing() {
    }

    /**
     * Change a new line command typed in or just after the opening of a block comment.
     *
     * @return true if the command was changed
     */
    public static boolean newLine(IDocument document, DocumentCommand command) throws BadLocationException {
        int offset = command.offset;
        IRegion line = document.getLineInformationOfOffset(offset);
        int lineStart = line.getOffset();
        int lineEnd = lineStart + line.getLength();
        String before = document.get(lineStart, offset - lineStart);
        String after = document.get(offset, lineEnd - offset);
        String delimiter = command.text;
        byte[] kinds = CodeKinds.of(document);
        String text = document.get();

        int opening = openingOnLine(text, kinds, lineStart, offset);
        if (opening >= 0 && after.isBlank() && before.substring(opening - lineStart).trim().matches("/\\*\\*?")) {
            // Enter after /** or /*
            String prefix = before.substring(0, opening - lineStart);
            String indent = prefix.isBlank() ? prefix : leadingBlanks(before);
            boolean doc = text.startsWith("/**", opening) && !text.startsWith("/**/", opening);
            StringBuilder insert = new StringBuilder(delimiter).append(indent).append(" * ");
            int caret = offset + insert.length();
            if (!isClosed(text, offset)) {
                if (doc) {
                    for (String tag : tags(text.substring(lineEnd))) {
                        insert.append(delimiter).append(indent).append(" * ").append(tag);
                    }
                }
                insert.append(delimiter).append(indent).append(" */");
            }
            command.text = insert.toString();
            command.length = after.length();
            command.caretOffset = caret;
            command.shiftsCaret = false;
            return true;
        }
        if (!insideBlockComment(text, kinds, offset)) {
            return false;
        }
        // In a block comment: the lines start with " * ", aligned on the star of the line
        String trimmed = before.stripLeading();
        String starIndent;
        if (trimmed.startsWith("*")) {
            starIndent = leadingBlanks(before);
        } else if (opening >= 0) {
            String prefix = before.substring(0, opening - lineStart);
            starIndent = (prefix.isBlank() ? prefix : leadingBlanks(before)) + " ";
        } else {
            // a line of the comment without star: keep its indentation
            command.text = delimiter + leadingBlanks(before);
            return true;
        }
        String rest = after.stripLeading();
        int blanks = after.length() - rest.length();
        if (rest.startsWith("*/")) {
            command.text = delimiter + starIndent;
        } else {
            command.text = delimiter + starIndent + "* ";
        }
        command.length = blanks;
        return true;
    }

    /**
     * @return the offset of the opening of a block comment started on the line before the offset, or -1
     */
    private static int openingOnLine(String text, byte[] kinds, int lineStart, int offset) {
        for (int i = offset - 2; i >= lineStart; i--) {
            if (text.charAt(i) == '/' && text.charAt(i + 1) == '*' && CodeKinds.kind(kinds, i) == CodeKinds.COMMENT
                    && (kinds[i] & CodeKinds.STARTS) != 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * true if the offset is in a block comment (not after its end)
     */
    private static boolean insideBlockComment(String text, byte[] kinds, int offset) {
        if (offset == 0 || CodeKinds.kind(kinds, offset - 1) != CodeKinds.COMMENT) {
            return false;
        }
        int start = offset - 1;
        while (start > 0 && (kinds[start] & CodeKinds.STARTS) == 0) {
            start--;
        }
        if (!text.startsWith("/*", start)) {
            return false;
        }
        int end = start + 1;
        while (end < text.length() && CodeKinds.kind(kinds, end) == CodeKinds.COMMENT
                && (kinds[end] & CodeKinds.STARTS) == 0) {
            end++;
        }
        boolean closed = end - start >= 4 && text.startsWith("*/", end - 2);
        return offset < end || !closed;
    }

    /**
     * true if a block comment opened before the offset is closed after it, before another comment opens
     */
    private static boolean isClosed(String text, int offset) {
        int close = text.indexOf("*/", offset);
        int open = text.indexOf("/*", offset);
        return close >= 0 && (open < 0 || close < open);
    }

    /**
     * The tags of the documentation of the declaration following a comment.
     *
     * @param code the code after the comment
     * @return {@code @param} of each parameter and {@code @return}, for a method or a closure in a variable
     */
    static List<String> tags(String code) {
        String declaration = declaration(code);
        List<String> tags = new ArrayList<>();
        if (declaration == null) {
            return tags;
        }
        Matcher closure = CLOSURE.matcher(declaration);
        if (closure.find()) {
            for (String parameter : closure.group(1).split(",")) {
                String name = lastIdentifier(parameter);
                if (name != null) {
                    tags.add("@param " + name);
                }
            }
            tags.add("@return");
            return tags;
        }
        Matcher method = METHOD.matcher(declaration);
        if (!method.find()) {
            return tags;
        }
        String modifiers = method.group(1).trim();
        String type = method.group(2);
        String name = method.group(3);
        if (KEYWORDS.contains(name) || (type != null && (KEYWORDS.contains(type) || MODIFIERS.contains(type)
                && !type.equals("def")))) {
            return tags;
        }
        if (modifiers.isEmpty() && type == null) {
            // a call (foo(x)), not a declaration
            return tags;
        }
        for (String parameter : splitParameters(method.group(4))) {
            String parameterName = lastIdentifier(withoutDefault(parameter));
            if (parameterName != null) {
                tags.add("@param " + parameterName);
            }
        }
        if (!"void".equals(type)) {
            tags.add("@return");
        }
        return tags;
    }

    /**
     * @return the code following the comment (the patterns are anchored at its start); null if there is none
     */
    private static String declaration(String code) {
        String text = code.stripLeading();
        if (text.isEmpty() || text.startsWith("/*") || text.startsWith("//")) {
            return null;
        }
        return text.substring(0, Math.min(text.length(), 500));
    }

    private static List<String> splitParameters(String parameters) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : parameters.toCharArray()) {
            if (c == '<' || c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                result.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.toString().isBlank()) {
            result.add(current.toString());
        }
        return result;
    }

    private static String withoutDefault(String parameter) {
        int depth = 0;
        for (int i = 0; i < parameter.length(); i++) {
            char c = parameter.charAt(i);
            if (c == '<' || c == '(' || c == '[') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']') {
                depth--;
            } else if (c == '=' && depth == 0) {
                return parameter.substring(0, i);
            }
        }
        return parameter;
    }

    private static String lastIdentifier(String text) {
        Matcher matcher = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*$").matcher(text.strip());
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String leadingBlanks(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(0, i);
    }
}
