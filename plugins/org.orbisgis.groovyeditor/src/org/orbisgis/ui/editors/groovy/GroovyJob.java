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
package org.orbisgis.ui.editors.groovy;

import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.ExceptionMessage;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.runtime.FormatHelper;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.codehaus.groovy.syntax.SyntaxException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.GroovyConsoleContent;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.Style;
import org.orbisgis.ui.editors.groovy.session.GroovySession;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Job running a script in the Groovy session of an editor.
 *
 * The script runs in its own thread: cancelling the job interrupts it. The scripts are compiled with
 * {@code @ThreadInterrupt}, so loops stop at the next iteration; a blocking call (JDBC query...) stops
 * only if it reacts to the interruption.
 */
public class GroovyJob extends Job {

    private static final Logger LOGGER = new Logger(GroovyJob.class);

    private static final int MAX_RESULT_LENGTH = 2000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GroovySession session;
    private final String script;
    private final String scriptName;

    public GroovyJob(GroovySession session, String scriptName, String script) {
        super("Groovy: " + scriptName);
        this.session = session;
        this.script = script;
        this.scriptName = scriptName;
        setUser(false);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Running " + scriptName, IProgressMonitor.UNKNOWN);
        GroovyConsoleContent.appendLine("\n--- " + scriptName + " (" + LocalTime.now().format(TIME_FORMAT) + ")", Style.INFO);

        long printedBefore = session.getPrintedChars();
        Runner runner = new Runner();
        Thread thread = new Thread(runner, "Groovy script " + scriptName);
        thread.setDaemon(true);
        long start = System.currentTimeMillis();
        thread.start();
        boolean cancelled = false;
        try {
            while (thread.isAlive()) {
                thread.join(200);
                if (monitor.isCanceled() && !cancelled) {
                    cancelled = true;
                    GroovyConsoleContent.appendLine("Stopping the script...", Style.INFO);
                    thread.interrupt();
                }
            }
        } catch (InterruptedException e) {
            thread.interrupt();
            Thread.currentThread().interrupt();
        } finally {
            monitor.done();
        }
        String duration = formatDuration(System.currentTimeMillis() - start);

        if (runner.error != null) {
            if (cancelled) {
                GroovyConsoleContent.appendLine("Script stopped (" + duration + ").", Style.ERROR);
            } else {
                GroovyConsoleContent.append(formatError(runner.error), Style.ERROR);
                GroovyConsoleContent.appendLine("Error while executing the Groovy script (" + duration + ").", Style.ERROR);
            }
            return cancelled ? Status.CANCEL_STATUS : Status.OK_STATUS;
        }
        // The value of the last expression is shown when the script printed nothing: a script ending with
        // list.each { println it } does not need the list again
        if (runner.result != null && session.getPrintedChars() == printedBefore) {
            GroovyConsoleContent.appendLine("Result: " + formatResult(runner.result), Style.RESULT);
        }
        GroovyConsoleContent.appendLine("Groovy script successfully executed (" + duration + ").", Style.SUCCESS);
        return Status.OK_STATUS;
    }

    private static String formatResult(Object result) {
        String text = FormatHelper.format(result, false, MAX_RESULT_LENGTH, true);
        return text.length() > MAX_RESULT_LENGTH ? text.substring(0, MAX_RESULT_LENGTH) + "..." : text;
    }

    static String formatError(Throwable error) {
        if (error instanceof MultipleCompilationErrorsException compilationErrors) {
            return formatCompilationErrors(compilationErrors);
        }
        StringWriter sw = new StringWriter();
        StackTraceUtils.deepSanitize(error).printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * One line per compilation error, without the stack traces of the exceptions raised during the
     * compilation (an unresolved @Grab...).
     */
    static String formatCompilationErrors(MultipleCompilationErrorsException exception) {
        StringBuilder sb = new StringBuilder();
        boolean grabError = false;
        for (Message message : exception.getErrorCollector().getErrors()) {
            String text;
            if (message instanceof SyntaxErrorMessage syntaxError) {
                SyntaxException cause = syntaxError.getCause();
                text = cause.getOriginalMessage() + " (line " + cause.getLine() + ", column " + cause.getStartColumn() + ")";
            } else if (message instanceof ExceptionMessage exceptionMessage) {
                Exception cause = exceptionMessage.getCause();
                text = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                grabError |= text.contains("Error grabbing Grapes");
            } else if (message instanceof SimpleMessage simpleMessage) {
                text = simpleMessage.getMessage();
            } else {
                StringWriter sw = new StringWriter();
                message.write(new PrintWriter(sw));
                text = sw.toString().trim();
            }
            sb.append(text).append('\n');
        }
        if (grabError) {
            sb.append("Check the coordinates of the library, the network and proxy settings, or add its repository with @GrabResolver.\n");
        }
        return sb.length() > 0 ? sb.toString() : exception.getMessage() + "\n";
    }

    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        long seconds = millis / 1000;
        if (seconds < 60) {
            return String.format("%.1f s", millis / 1000.0);
        }
        return String.format("%d min %02d s", seconds / 60, seconds % 60);
    }

    private class Runner implements Runnable {
        private volatile Object result;
        private volatile Throwable error;

        @Override
        public void run() {
            try {
                result = session.run(script, scriptName);
            } catch (Throwable e) {
                error = e;
                LOGGER.trace("Error in the Groovy script " + scriptName, e);
            }
        }
    }
}
