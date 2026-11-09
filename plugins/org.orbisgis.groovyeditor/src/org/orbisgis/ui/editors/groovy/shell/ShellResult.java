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
package org.orbisgis.ui.editors.groovy.shell;

/**
 * Result of a command run with {@code sh(...)}.
 */
public class ShellResult {

    private final int exitCode;
    private final String text;
    private final String error;
    private final boolean timedOut;

    public ShellResult(int exitCode, String text, String error, boolean timedOut) {
        this.exitCode = exitCode;
        this.text = text;
        this.error = error;
        this.timedOut = timedOut;
    }

    /**
     * @return the exit code of the command: 0 when it succeeded
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * @return what the command wrote on its standard output
     */
    public String getText() {
        return text;
    }

    /**
     * @return what the command wrote on its error output
     */
    public String getError() {
        return error;
    }

    /**
     * @return true if the command was stopped because it lasted longer than its timeout
     */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * @return true if the command succeeded (exit code 0)
     */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    /**
     * @return the standard output: {@code println sh("cmd", quiet: true)}
     */
    @Override
    public String toString() {
        return text;
    }
}
