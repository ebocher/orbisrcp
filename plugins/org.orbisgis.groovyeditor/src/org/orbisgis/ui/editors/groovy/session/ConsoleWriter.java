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
package org.orbisgis.ui.editors.groovy.session;

import org.eclipse.swt.widgets.Display;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.GroovyConsoleContent;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.Style;

import java.io.Writer;

/**
 * Writer sending the text written by a script (println...) to the Groovy output view while the script runs.
 *
 * The written text is buffered and sent to the UI thread in batches, so a script printing a lot of lines
 * does not flood the UI thread with one task per line.
 */
public class ConsoleWriter extends Writer {

    private final StringBuilder buffer = new StringBuilder();
    private final Style style;
    private boolean drainScheduled = false;
    private volatile long writtenChars = 0;

    public ConsoleWriter(Style style) {
        this.style = style;
    }

    @Override
    public void write(char[] chars, int offset, int length) {
        synchronized (buffer) {
            writtenChars += length;
            buffer.append(chars, offset, length);
            if (!drainScheduled) {
                drainScheduled = true;
                Display.getDefault().asyncExec(this::drain);
            }
        }
    }

    /**
     * @return the number of characters written since the creation of the writer
     */
    public long getWrittenChars() {
        return writtenChars;
    }

    private void drain() {
        String text;
        synchronized (buffer) {
            text = buffer.toString();
            buffer.setLength(0);
            drainScheduled = false;
        }
        // Already in the UI thread: append now to keep the order with the other console messages
        GroovyConsoleContent.appendInUIThread(text, style);
    }

    @Override
    public void flush() {
        // The text is sent asynchronously, nothing to do
    }

    @Override
    public void close() {
        // The console stays open
    }
}
