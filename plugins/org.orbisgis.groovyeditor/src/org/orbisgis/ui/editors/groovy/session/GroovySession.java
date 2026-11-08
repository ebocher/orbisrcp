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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.ClassPathHandler;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.Style;
import org.orbisgis.ui.editors.groovy.logger.GroovyLogger;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Groovy execution context of an editor. It is kept between the executions, like a REPL:
 * <ul>
 *     <li>the variables declared at the top level of a script stay available (see {@link ReplCustomizers});</li>
 *     <li>the imports are remembered;</li>
 *     <li>the libraries loaded with {@code @Grab} or added to the classpath stay loaded.</li>
 * </ul>
 * {@link #reset()} starts a new, empty context.
 *
 * The scripts are compiled with the class loader of this bundle as parent: they can use Groovy, the
 * Eclipse and DBeaver bundles required by this bundle and, through {@code DynamicImport-Package}, any
 * package exported by another bundle.
 */
public class GroovySession implements AutoCloseable {

    private static final Logger LOGGER = new Logger(GroovySession.class);

    /** Variables set by the console in every context */
    public static final String VAR_OUT = "out";
    public static final String VAR_LOGGER = "logger";

    /**
     * Adds variables to the context of every console. Called each time a context is created (new editor,
     * reset).
     */
    @FunctionalInterface
    public interface BindingContributor {
        /**
         * @param binding the variables of the new context
         * @return a resource to close when the context is reset or closed, or null
         */
        AutoCloseable contribute(Binding binding);
    }

    private static final List<BindingContributor> CONTRIBUTORS = new CopyOnWriteArrayList<>();

    /**
     * Register a contributor of variables, used by the contexts created afterwards.
     */
    public static void addBindingContributor(BindingContributor contributor) {
        CONTRIBUTORS.add(contributor);
    }

    private final List<AutoCloseable> resources = new ArrayList<>();
    private final Object lock = new Object();
    private final Consumer<URL> classPathListener = this::addClassPath;

    private GroovyShell shell;
    private ConsoleWriter output;
    private ImportCustomizer importCustomizer;
    private ReplCustomizers.ImportCollector importCollector;
    private final Set<Object> knownImports = Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile boolean running = false;

    public GroovySession() {
        createShell();
        ClassPathHandler.addListener(classPathListener);
    }

    private void createShell() {
        Binding binding = new Binding();
        output = new ConsoleWriter(Style.OUTPUT);
        binding.setVariable(VAR_OUT, new PrintWriter(output, true));
        binding.setVariable(VAR_LOGGER, new GroovyLogger(GroovyShell.class));
        for (BindingContributor contributor : CONTRIBUTORS) {
            try {
                AutoCloseable resource = contributor.contribute(binding);
                if (resource != null) {
                    resources.add(resource);
                }
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("Unable to initialize the variables of the Groovy console.", e);
            }
        }

        importCollector = new ReplCustomizers.ImportCollector();
        importCustomizer = new ImportCustomizer();
        knownImports.clear();

        CompilerConfiguration config = new CompilerConfiguration();
        config.setSourceEncoding("UTF-8");
        // The collector must run before the import customizer: it only records the imports written in the script
        config.addCompilationCustomizers(
                importCollector,
                importCustomizer,
                new ReplCustomizers.ScriptVariablesCustomizer(),
                new ASTTransformationCustomizer(ThreadInterrupt.class));

        // GroovyShell uses a GroovyClassLoader parent created with the same configuration as its class loader
        shell = new GroovyShell(new SessionClassLoader(GroovySession.class.getClassLoader(), config), binding, config);
        for (URL url : ClassPathHandler.getUrlsInArray()) {
            shell.getClassLoader().addURL(url);
        }
    }

    /**
     * Compile and run a script in this context. The script runs in the calling thread.
     *
     * @param scriptText the script
     * @param scriptName the name of the script, used in the stack traces
     * @return the value of the last expression of the script
     */
    public Object run(String scriptText, String scriptName) throws Exception {
        synchronized (lock) {
            if (running) {
                throw new IllegalStateException("A script is already running in this console.");
            }
            running = true;
        }
        Thread thread = Thread.currentThread();
        ClassLoader previousContextClassLoader = thread.getContextClassLoader();
        try {
            // Libraries loaded with @Grab or from the user classpath (JDBC drivers, ServiceLoader...) often
            // look for their resources in the context class loader
            thread.setContextClassLoader(shell.getClassLoader());

            GroovyCodeSource codeSource = new GroovyCodeSource(scriptText, scriptName, GroovyShell.DEFAULT_CODE_BASE);
            // Always compile: the customizers must see each execution (imports, @Grab...)
            codeSource.setCachable(false);
            Script script;
            try {
                script = shell.parse(codeSource);
            } catch (Exception e) {
                importCollector.discard();
                throw e;
            }
            importCollector.commit(importCustomizer, knownImports);
            return script.run();
        } finally {
            thread.setContextClassLoader(previousContextClassLoader);
            running = false;
        }
    }

    /**
     * @return the number of characters printed by the scripts of this context (println...)
     */
    public long getPrintedChars() {
        return output.getWrittenChars();
    }

    /**
     * @return true if a script is running in this context
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Forget all the variables, imports and loaded libraries.
     *
     * @return false if the context cannot be reset because a script is running
     */
    public boolean reset() {
        synchronized (lock) {
            if (running) {
                return false;
            }
            closeResources();
            closeClassLoader();
            createShell();
            return true;
        }
    }

    /**
     * @return a copy of the variables of the context
     */
    public Map<String, Object> getVariables() {
        Map<?, ?> variables = shell.getContext().getVariables();
        // The map may be modified by a running script
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Map<String, Object> copy = new LinkedHashMap<>();
                variables.forEach((key, value) -> copy.put(String.valueOf(key), value));
                return copy;
            } catch (ConcurrentModificationException e) {
                // try again
            }
        }
        return Collections.emptyMap();
    }

    /**
     * @return the classes imported in the context: simple name (or alias) to class name
     */
    public Map<String, String> getImportedClasses() {
        synchronized (knownImports) {
            return ReplCustomizers.ImportCollector.importedClasses(knownImports);
        }
    }

    /**
     * @return the packages imported with a star in the context
     */
    public List<String> getImportedPackages() {
        synchronized (knownImports) {
            return ReplCustomizers.ImportCollector.importedPackages(knownImports);
        }
    }

    /**
     * @return the class loader of the scripts (Groovy, the bundle dependencies, @Grab and user libraries)
     */
    public GroovyClassLoader getClassLoader() {
        return shell.getClassLoader();
    }

    /**
     * @return the locations of the libraries of the context (the class loader reads copies of the jars)
     */
    public URL[] getClasspathURLs() {
        return shell.getClassLoader() instanceof SessionClassLoader loader ? loader.getOriginalURLs()
                : shell.getClassLoader().getURLs();
    }

    private void addClassPath(URL url) {
        shell.getClassLoader().addURL(url);
    }

    private void closeResources() {
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                LOGGER.warn("Unable to close a resource of the Groovy console.", e);
            }
        }
        resources.clear();
    }

    private void closeClassLoader() {
        try {
            shell.getClassLoader().close();
        } catch (IOException e) {
            LOGGER.warn("Unable to close the Groovy class loader.", e);
        }
    }

    @Override
    public void close() {
        ClassPathHandler.removeListener(classPathListener);
        synchronized (lock) {
            closeResources();
            if (!running) {
                closeClassLoader();
            }
        }
    }
}
