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

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;

/**
 * Colors of the Groovy editor, for the light and the dark themes of DBeaver. The plain code uses the
 * default text color of the editor.
 */
public final class GroovyColors {

    public final TextAttribute keyword;
    public final TextAttribute string;
    public final TextAttribute interpolation;
    public final TextAttribute number;
    public final TextAttribute comment;
    public final TextAttribute docComment;
    public final TextAttribute annotation;

    private GroovyColors(boolean dark) {
        if (dark) {
            keyword = new TextAttribute(new Color(204, 120, 50), null, SWT.BOLD);
            string = new TextAttribute(new Color(106, 171, 115));
            interpolation = new TextAttribute(new Color(204, 120, 50));
            number = new TextAttribute(new Color(104, 151, 187));
            comment = new TextAttribute(new Color(128, 128, 128));
            docComment = new TextAttribute(new Color(98, 151, 85));
            annotation = new TextAttribute(new Color(187, 181, 41));
        } else {
            keyword = new TextAttribute(new Color(127, 0, 85), null, SWT.BOLD);
            string = new TextAttribute(new Color(42, 0, 255));
            interpolation = new TextAttribute(new Color(127, 0, 85));
            number = new TextAttribute(new Color(18, 120, 120));
            comment = new TextAttribute(new Color(63, 127, 95));
            docComment = new TextAttribute(new Color(63, 95, 191));
            annotation = new TextAttribute(new Color(100, 100, 100));
        }
    }

    /**
     * @param dark true for the dark theme
     */
    public static GroovyColors of(boolean dark) {
        return new GroovyColors(dark);
    }
}
