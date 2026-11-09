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

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension4;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Display;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.ui.Icons;

/**
 * A completion proposal of the Groovy editor.
 */
public class ComplProposal implements ICompletionProposal,
                                      ICompletionProposalExtension4,
                                      ICompletionProposalExtension6 {

    private static final Logger LOGGER = new Logger(ComplProposal.class);

    /**
     * Kind of the proposed element, sorted by relevance.
     */
    public enum Kind {
        VARIABLE, PROPERTY, METHOD, CLASS, KEYWORD, WORD
    }

    private final int replaceOffset;
    private final int replaceLength;
    private final String replacement;
    private final int cursorPosition;
    private final String label;
    private final String qualifier;
    private final Kind kind;
    private final int prefixLength;

    /**
     * @param replaceOffset  offset of the replaced text (the typed prefix)
     * @param replaceLength  length of the replaced text
     * @param replacement    inserted text
     * @param cursorPosition position of the cursor after insertion, relative to replaceOffset
     * @param label          displayed text
     * @param qualifier      additional information displayed after the label (type...), may be null
     * @param kind           kind of the element
     * @param prefixLength   number of characters already typed, highlighted in the label
     */
    public ComplProposal(int replaceOffset, int replaceLength, String replacement, int cursorPosition,
                         String label, String qualifier, Kind kind, int prefixLength) {
        this.replaceOffset = replaceOffset;
        this.replaceLength = replaceLength;
        this.replacement = replacement;
        this.cursorPosition = cursorPosition;
        this.label = label;
        this.qualifier = qualifier;
        this.kind = kind;
        this.prefixLength = prefixLength;
    }

    public Kind getKind() {
        return kind;
    }

    public String getReplacement() {
        return replacement;
    }

    @Override
    public void apply(IDocument document) {
        try {
            document.replace(replaceOffset, replaceLength, replacement);
        } catch (BadLocationException e) {
            LOGGER.error("Bad location", e);
        }
    }

    @Override
    public Point getSelection(IDocument document) {
        return new Point(replaceOffset + cursorPosition, 0);
    }

    @Override
    public String getAdditionalProposalInfo() {
        return null;
    }

    @Override
    public String getDisplayString() {
        return qualifier == null ? label : label + " " + qualifier;
    }

    @Override
    public Image getImage() {
        switch (kind) {
            case METHOD:
                return Icons.COMPLETION_METHOD;
            case VARIABLE:
            case PROPERTY:
                return Icons.COMPLETION_VARIABLE;
            default:
                return null;
        }
    }

    @Override
    public IContextInformation getContextInformation() {
        return null;
    }

    @Override
    public boolean isAutoInsertable() {
        return true;
    }

    @Override
    public StyledString getStyledDisplayString() {
        int highlighted = Math.min(prefixLength, label.length());
        StyledString styled = new StyledString()
                .append(label.substring(0, highlighted), new ColorStyler(SWT.COLOR_DARK_CYAN))
                .append(label.substring(highlighted));
        if (qualifier != null) {
            styled.append(" " + qualifier, StyledString.QUALIFIER_STYLER);
        }
        return styled;
    }

    private static class ColorStyler extends StyledString.Styler {
        private final int color;

        ColorStyler(int color) {
            this.color = color;
        }

        @Override
        public void applyStyles(final TextStyle textStyle) {
            textStyle.foreground = Display.getCurrent().getSystemColor(color);
        }
    }
}
