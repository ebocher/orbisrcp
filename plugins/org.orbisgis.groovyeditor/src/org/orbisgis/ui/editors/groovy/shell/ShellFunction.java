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

import groovy.lang.Binding;
import groovy.lang.Closure;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.Style;
import org.orbisgis.ui.editors.groovy.session.ConsoleWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The {@code sh(...)} function of the console: run a command (a Python script...), its output shown in the
 * console while it runs.
 *
 * <pre>
 * sh "python3 analyse.py communes.csv"                  // output in the console
 * sh(["python3", "analyse.py", "my file.csv"])          // arguments as a list: no quoting
 * def r = sh("python3 stats.py", quiet: true)           // r.text: the output, r.exitCode
 * sh("ls *.csv | wc -l", shell: true)                    // pipes, redirections, wildcards
 * </pre>
 *
 * Options: {@code dir} (working directory, the home directory by default), {@code env} (additional environment
 * variables), {@code input} (text sent to the standard input), {@code shell} (run with bash -c, or cmd /c on
 * Windows), {@code quiet} (do not show the output), {@code timeout} (in seconds), {@code charset} (of the output,
 * UTF-8 by default).
 *
 * The Stop button of the console, the timeout, Reset and closing the console stop the command and its
 * sub-processes: they are asked to stop, then killed after 5 seconds.
 */
public class ShellFunction extends Closure<ShellResult> implements AutoCloseable {

    /** Name of the function in the scripts */
    public static final String NAME = "sh";

    /** Delay between the request to stop and the kill */
    static final long STOP_DELAY_SECONDS = 5;

    private static final Set<String> OPTIONS = Set.of("dir", "env", "input", "shell", "quiet", "timeout", "charset");
    private static final int MAX_CAPTURED = 16 * 1024 * 1024;
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private final Binding binding;
    private final Set<Process> running = ConcurrentHashMap.newKeySet();

    /**
     * @param binding the variables of the context: its {@code out} writer receives the output
     */
    public ShellFunction(Binding binding) {
        super(null);
        this.binding = binding;
    }

    public ShellResult doCall(Object command) throws IOException, InterruptedException {
        return doCall(Map.of(), command);
    }

    public ShellResult doCall(Map<?, ?> options, Object command) throws IOException, InterruptedException {
        for (Object key : options.keySet()) {
            if (!OPTIONS.contains(String.valueOf(key))) {
                throw new IllegalArgumentException("Unknown option '" + key + "' of sh. Options: "
                        + String.join(", ", new java.util.TreeSet<>(OPTIONS)));
            }
        }
        if (command instanceof Map) {
            throw new IllegalArgumentException("sh: no command, for example sh('python3 script.py', quiet: true)");
        }
        boolean shell = isTrue(options.get("shell"));
        List<String> arguments = commandLine(command, shell);
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("sh: no command");
        }
        ProcessBuilder builder = new ProcessBuilder(arguments);
        builder.directory(directory(options.get("dir")));
        Map<String, String> environment = builder.environment();
        // Python: the output while the script runs, in UTF-8
        environment.putIfAbsent("PYTHONUNBUFFERED", "1");
        environment.putIfAbsent("PYTHONIOENCODING", "utf-8");
        if (options.get("env") instanceof Map<?, ?> variables) {
            variables.forEach((name, value) -> {
                if (value == null) {
                    environment.remove(String.valueOf(name));
                } else {
                    environment.put(String.valueOf(name), String.valueOf(value));
                }
            });
        }
        Charset charset = options.get("charset") == null ? StandardCharsets.UTF_8
                : Charset.forName(String.valueOf(options.get("charset")));
        boolean quiet = isTrue(options.get("quiet"));
        long timeout = options.get("timeout") instanceof Number number ? number.longValue() : 0;
        String label = String.join(" ", arguments);

        Writer out = quiet ? null : outputWriter();
        Writer err = quiet ? null : new ConsoleWriter(Style.ERROR);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("Unable to run " + arguments.get(0) + ": " + e.getMessage(), e);
        }
        running.add(process);
        StringBuilder text = new StringBuilder();
        StringBuilder error = new StringBuilder();
        Thread outReader = reader(process.getInputStream(), charset, text, out, "sh stdout");
        Thread errReader = reader(process.getErrorStream(), charset, error, err, "sh stderr");
        writeInput(process, options.get("input"), charset);

        boolean timedOut = false;
        try {
            long deadline = timeout > 0 ? System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout) : Long.MAX_VALUE;
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() > deadline) {
                    timedOut = true;
                    stop(process, true);
                    break;
                }
            }
        } catch (InterruptedException e) {
            // Stop button
            stop(process, true);
            join(outReader, errReader);
            message(err, "Stopped: " + label);
            throw e;
        } finally {
            running.remove(process);
        }
        join(outReader, errReader);
        int exitCode = process.exitValue();
        if (timedOut) {
            message(err, "Timeout after " + timeout + " s, stopped: " + label);
        } else if (exitCode != 0) {
            message(err, arguments.get(0) + " exited with code " + exitCode);
        }
        return new ShellResult(exitCode, text.toString(), error.toString(), timedOut);
    }

    /**
     * Stop the running commands (Reset, console closed).
     */
    @Override
    public void close() {
        for (Process process : new ArrayList<>(running)) {
            stop(process, false);
        }
        running.clear();
    }

    /**
     * Stop a process and its sub-processes: asked to stop, killed after {@link #STOP_DELAY_SECONDS}.
     *
     * @param wait true to wait for the end of the processes, false to kill them later in the background
     */
    static void stop(Process process, boolean wait) {
        List<ProcessHandle> processes = new ArrayList<>(process.descendants().toList());
        processes.add(0, process.toHandle());
        processes.forEach(ProcessHandle::destroy);
        Runnable kill = () -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STOP_DELAY_SECONDS);
            while (processes.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }
            processes.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        };
        if (wait) {
            boolean interrupted = Thread.interrupted();
            kill.run();
            try {
                process.waitFor(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } else {
            Thread thread = new Thread(kill, "sh stop");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * @return the processes running (for the tests)
     */
    Collection<Process> running() {
        return running;
    }

    private Writer outputWriter() {
        Object out = binding.hasVariable("out") ? binding.getVariable("out") : null;
        return out instanceof Writer writer ? writer : new ConsoleWriter(Style.OUTPUT);
    }

    private static Thread reader(InputStream stream, Charset charset, StringBuilder captured, Writer console,
                                 String name) {
        Thread thread = new Thread(() -> {
            char[] buffer = new char[8192];
            try (Reader reader = new InputStreamReader(stream, charset)) {
                int length;
                while ((length = reader.read(buffer)) >= 0) {
                    synchronized (captured) {
                        if (captured.length() < MAX_CAPTURED) {
                            captured.append(buffer, 0, Math.min(length, MAX_CAPTURED - captured.length()));
                        }
                    }
                    if (console != null) {
                        console.write(buffer, 0, length);
                        console.flush();
                    }
                }
            } catch (IOException e) {
                // the process was stopped
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void writeInput(Process process, Object input, Charset charset) {
        if (input == null) {
            closeQuietly(process.getOutputStream());
            return;
        }
        byte[] bytes = String.valueOf(input).getBytes(charset);
        Thread thread = new Thread(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(bytes);
            } catch (IOException e) {
                // the process does not read its input
            }
        }, "sh stdin");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Wait for the end of the output; a sub-process left running may keep it open: not more than 2 seconds.
     */
    private static void join(Thread... readers) {
        for (Thread reader : readers) {
            try {
                reader.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void message(Writer err, String message) {
        if (err == null) {
            return;
        }
        try {
            err.write(message + "\n");
            err.flush();
        } catch (IOException e) {
            // console closed
        }
    }

    private static void closeQuietly(OutputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private static File directory(Object dir) {
        if (dir == null) {
            return new File(System.getProperty("user.home"));
        }
        File file = dir instanceof File f ? f : dir instanceof Path path ? path.toFile() : new File(String.valueOf(dir));
        if (!file.isDirectory()) {
            throw new IllegalArgumentException("sh: no directory " + file);
        }
        return file;
    }

    private static boolean isTrue(Object value) {
        return value instanceof Boolean b ? b : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * @return the program and its arguments: a list as it is, a string split on the blanks (quotes kept
     * together), or a string given to the shell
     */
    static List<String> commandLine(Object command, boolean shell) {
        if (command instanceof Collection<?> collection) {
            List<String> arguments = new ArrayList<>();
            for (Object item : collection) {
                arguments.add(String.valueOf(item));
            }
            if (shell) {
                return shell(String.join(" ", arguments));
            }
            return arguments;
        }
        if (command == null) {
            return List.of();
        }
        String text = String.valueOf(command);
        return shell ? shell(text) : split(text);
    }

    private static List<String> shell(String command) {
        return WINDOWS ? List.of("cmd", "/c", command) : List.of("bash", "-c", command);
    }

    /**
     * Split a command on the blanks, as a shell: quotes ("a b", 'a b') keep a text together, a backslash escapes
     * a character (outside single quotes).
     */
    static List<String> split(String command) {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inArgument = false;
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else if (c == '\\' && quote == '"' && i + 1 < command.length()
                        && (command.charAt(i + 1) == '"' || command.charAt(i + 1) == '\\')) {
                    current.append(command.charAt(++i));
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inArgument = true;
            } else if (c == '\\' && i + 1 < command.length() && !WINDOWS) {
                current.append(command.charAt(++i));
                inArgument = true;
            } else if (Character.isWhitespace(c)) {
                if (inArgument) {
                    arguments.add(current.toString());
                    current.setLength(0);
                    inArgument = false;
                }
            } else {
                current.append(c);
                inArgument = true;
            }
        }
        if (inArgument) {
            arguments.add(current.toString());
        }
        return arguments;
    }

    @Override
    public String toString() {
        return "sh(command): run a command, its output in the console";
    }
}
