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
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;

/**
 * Kind of each character of a Groovy document (code, comment or string), from the Groovy partitioning.
 */
public final class CodeKinds {

    public static final byte CODE = 0;
    public static final byte COMMENT = 1;
    public static final byte STRING = 2;
    /** Flag of the first character of a comment or a string */
    public static final byte STARTS = 4;

    private CodeKinds() {
    }

    /**
     * @return the kind of each character, plus one for the end of the document (code); the first character of a
     * comment or a string has the {@link #STARTS} flag
     */
    public static byte[] of(IDocument document) {
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

    /**
     * @return the kind of a character, without the flag
     */
    public static byte kind(byte[] kinds, int offset) {
        return (byte) (kinds[offset] & ~STARTS);
    }
}
