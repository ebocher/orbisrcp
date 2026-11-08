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
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;

import java.util.function.Supplier;

/**
 * Indentation while typing:
 * <ul>
 * <li>Enter indents the new line after an opening bracket, and puts a closing bracket following the caret on its
 * own line ({@code {|}});</li>
 * <li>a closing bracket typed at the start of a line is aligned on the line of its opening bracket.</li>
 * </ul>
 * Inside a multi-line string or comment, Enter keeps the indentation of the line.
 */
public class GroovyAutoIndentStrategy implements IAutoEditStrategy {

    private final Supplier<GroovyFormatter> formatter;
    private final DefaultIndentLineAutoEditStrategy defaultStrategy = new DefaultIndentLineAutoEditStrategy();

    /**
     * @param formatter gives the formatter with the current indentation settings
     */
    public GroovyAutoIndentStrategy(Supplier<GroovyFormatter> formatter) {
        this.formatter = formatter;
    }

    @Override
    public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
        if (command.text == null || !command.doit) {
            return;
        }
        try {
            if (command.length == 0 && TextUtilities.equals(document.getLegalLineDelimiters(), command.text) != -1) {
                newLine(document, command);
            } else if (command.length == 0 && command.text.length() == 1 && "}])".indexOf(command.text.charAt(0)) >= 0) {
                closer(document, command);
            }
        } catch (BadLocationException e) {
            // keep the command as it is
        }
    }

    private void newLine(IDocument document, DocumentCommand command) throws BadLocationException {
        if (insideMultiLinePartition(document, command.offset)) {
            defaultStrategy.customizeDocumentCommand(document, command);
            return;
        }
        String[] indents = formatter.get().newLineIndent(document, command.offset);
        IRegion line = document.getLineInformationOfOffset(command.offset);
        // The blanks between the caret and the rest of the line are replaced by the indentation
        int end = line.getOffset() + line.getLength();
        int blanks = 0;
        while (command.offset + blanks < end && Character.isWhitespace(document.getChar(command.offset + blanks))) {
            blanks++;
        }
        String delimiter = command.text;
        if (indents[1] != null) {
            command.text = delimiter + indents[0] + delimiter + indents[1];
            command.caretOffset = command.offset + delimiter.length() + indents[0].length();
            command.shiftsCaret = false;
        } else {
            command.text = delimiter + indents[0];
        }
        command.length = blanks;
    }

    private void closer(IDocument document, DocumentCommand command) throws BadLocationException {
        IRegion line = document.getLineInformationOfOffset(command.offset);
        String before = document.get(line.getOffset(), command.offset - line.getOffset());
        if (!before.isBlank() || insideMultiLinePartition(document, command.offset)) {
            return;
        }
        String indent = formatter.get().closerIndent(document, command.offset, command.text.charAt(0));
        if (indent == null) {
            return;
        }
        command.text = indent + command.text;
        command.length += command.offset - line.getOffset();
        command.offset = line.getOffset();
    }

    /**
     * true inside a string or a comment started on a previous line (a single line comment ends at the line end).
     */
    private static boolean insideMultiLinePartition(IDocument document, int offset) throws BadLocationException {
        try {
            var partition = TextUtilities.getPartition(document, GroovyPartitions.PARTITIONING, offset, false);
            if (IDocument.DEFAULT_CONTENT_TYPE.equals(partition.getType())
                    || offset == partition.getOffset() + partition.getLength()) {
                return false;
            }
            int lineStart = document.getLineInformationOfOffset(offset).getOffset();
            return partition.getOffset() < lineStart || !isSingleLine(document, partition.getOffset());
        } catch (BadLocationException e) {
            return false;
        }
    }

    private static boolean isSingleLine(IDocument document, int partitionOffset) throws BadLocationException {
        return document.getLength() >= partitionOffset + 2 && document.get(partitionOffset, 2).equals("//");
    }
}
