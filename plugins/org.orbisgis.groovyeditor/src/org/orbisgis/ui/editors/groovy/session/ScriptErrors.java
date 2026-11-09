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

import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the position, in the script, of the error of an execution.
 */
public final class ScriptErrors {

    /**
     * An error located in the script.
     *
     * @param line      line of the error, starting at 1
     * @param column    column of the error, starting at 1, or 0 when only the line is known
     * @param endLine   last line of the error (same as line when unknown)
     * @param endColumn column after the error, or 0 when unknown
     * @param message   description of the error
     */
    public record ScriptError(int line, int column, int endLine, int endColumn, String message) {
    }

    private ScriptErrors() {
    }

    /**
     * @param error      the error of the execution
     * @param scriptName the name given to the script when it was compiled
     * @return the positions of the error in the script (several for compilation errors), empty if unknown
     */
    public static List<ScriptError> locate(Throwable error, String scriptName) {
        List<ScriptError> errors = new ArrayList<>();
        if (error instanceof MultipleCompilationErrorsException compilation) {
            for (Message message : compilation.getErrorCollector().getErrors()) {
                if (message instanceof SyntaxErrorMessage syntaxError) {
                    SyntaxException cause = syntaxError.getCause();
                    if (cause.getLine() > 0) {
                        errors.add(new ScriptError(cause.getLine(), Math.max(cause.getStartColumn(), 0),
                                Math.max(cause.getEndLine(), cause.getLine()), Math.max(cause.getEndColumn(), 0),
                                cause.getOriginalMessage()));
                    }
                }
            }
            return errors;
        }
        // Runtime error: the deepest frame of the script, in the exception or in its causes
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        for (Throwable t = error; t != null && seen.put(t, Boolean.TRUE) == null; t = t.getCause()) {
            for (StackTraceElement frame : t.getStackTrace()) {
                if (scriptName.equals(frame.getFileName()) && frame.getLineNumber() > 0) {
                    errors.add(new ScriptError(frame.getLineNumber(), 0, frame.getLineNumber(), 0, describe(error)));
                    return errors;
                }
            }
        }
        return errors;
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        String text = error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        // Long messages (MissingMethodException...): the first line is enough for a hover
        int newLine = text.indexOf('\n');
        return newLine > 0 ? text.substring(0, newLine) : text;
    }
}
