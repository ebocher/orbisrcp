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

import groovy.grape.Grape;
import groovy.grape.GrapeEngine;
import groovy.grape.GrapeIvy;
import groovy.lang.GroovyClassLoader;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.event.IvyEvent;
import org.apache.ivy.core.event.download.EndArtifactDownloadEvent;
import org.apache.ivy.core.event.download.StartArtifactDownloadEvent;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.Message;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.GroovyConsoleContent;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.Style;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code @Grab} support of the Groovy console.
 * <ul>
 *     <li>The Groovy modules embedded in the console are never downloaded: a library grabbed with its
 *     dependencies (orbisdata...) uses the Groovy of the console. Groovy 2 and 3 modules
 *     ({@code org.codehaus.groovy}) are excluded too: they cannot work with Groovy 4.</li>
 *     <li>Without a personal Grape configuration ({@code ~/.groovy/grapeConfig.xml} or {@code -Dgrape.config}),
 *     the Maven Central snapshots repository is added to the default repositories.</li>
 *     <li>The resolution and the downloads are reported in the output view.</li>
 * </ul>
 * The proxy settings of DBeaver (Preferences &gt; Network Connections) are used: Eclipse publishes them as
 * Java system properties, which Ivy reads.
 */
public final class GrapeSupport {

    private static final Logger LOGGER = new Logger(GrapeSupport.class);

    /** Groovy modules embedded in the console (see the pom.xml of the bundle) */
    static final List<String> EMBEDDED_GROOVY_MODULES = List.of("groovy", "groovy-sql", "groovy-json",
            "groovy-xml", "groovy-templates", "groovy-datetime", "groovy-nio");

    static final String SNAPSHOTS_URL = "https://central.sonatype.com/repository/maven-snapshots/";

    /** Default Grape configuration of Groovy, plus the Maven Central snapshots repository */
    static final String GRAPE_CONFIG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Grape configuration of the DBeaver Groovy console.
                 Generated: create ~/.groovy/grapeConfig.xml to use your own configuration. -->
            <ivysettings>
              <settings defaultResolver="downloadGrapes"/>
              <caches lockStrategy="artifact-lock-nio"/>
              <resolvers>
                <chain name="downloadGrapes" returnFirst="true">
                  <filesystem name="cachedGrapes">
                    <ivy pattern="${user.home}/.groovy/grapes/[organisation]/[module]/ivy-[revision].xml"/>
                    <artifact pattern="${user.home}/.groovy/grapes/[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]"/>
                  </filesystem>
                  <ibiblio name="localm2" root="${user.home.url}/.m2/repository/" checkmodified="true" changingPattern=".*" changingMatcher="regexp" m2compatible="true"/>
                  <ibiblio name="ibiblio" m2compatible="true"/>
                  <ibiblio name="central-snapshots" root="%s" m2compatible="true" checkmodified="true" changingPattern=".*-SNAPSHOT" changingMatcher="regexp"/>
                </chain>
              </resolvers>
            </ivysettings>
            """.formatted(SNAPSHOTS_URL);

    /** Ivy error for an artifact which cannot be downloaded: "download failed: org#module;revision!artifact.ext" */
    private static final Pattern DOWNLOAD_FAILED = Pattern.compile("download failed: ([^#\\s]+)#([^;\\s]+);([^!\\s]+)!");

    private static boolean installed = false;

    /** True while a grab reported in the output view is running */
    private static volatile boolean reportingDownloads = false;

    private GrapeSupport() {
    }

    /**
     * Install the Grape engine of the console. Does nothing if it is already installed.
     *
     * @param stateDirectory directory where the default Grape configuration can be written
     */
    public static synchronized void install(File stateDirectory) {
        if (installed) {
            return;
        }
        installed = true;
        try {
            configure(stateDirectory);
            GrapeEngine engine = GrapeAccess.current();
            if (engine == null) {
                engine = new GrapeIvy();
            }
            // The downloads are reported in the output view, not with dots on the standard output
            Message.getDefaultLogger().setShowProgress(false);
            GrapeAccess.set(new ConsoleGrapeEngine(engine));
        } catch (Throwable e) {
            LOGGER.error("Unable to configure @Grab, the default Groovy configuration is used.", e);
        }
    }

    /**
     * Use the configuration of the console unless the user has a personal Grape configuration.
     */
    private static void configure(File stateDirectory) throws IOException {
        if (System.getProperty("grape.config") != null) {
            return;
        }
        String root = System.getProperty("grape.root", System.getProperty("groovy.root",
                new File(System.getProperty("user.home"), ".groovy").getPath()));
        if (new File(root, "grapeConfig.xml").exists() || stateDirectory == null) {
            return;
        }
        File config = new File(stateDirectory, "grapeConfig.xml");
        Files.createDirectories(stateDirectory.toPath());
        Files.writeString(config.toPath(), GRAPE_CONFIG, StandardCharsets.UTF_8);
        System.setProperty("grape.config", config.getAbsolutePath());
    }

    /**
     * Gives access to the protected engine instance of {@link Grape}.
     */
    private static final class GrapeAccess extends Grape {
        static GrapeEngine current() {
            return instance;
        }

        static void set(GrapeEngine engine) {
            instance = engine;
        }
    }

    /**
     * Grape engine adding the exclusions and the progress messages of the console to the Groovy engine.
     */
    static final class ConsoleGrapeEngine implements GrapeEngine {

        private final GrapeEngine delegate;
        private Ivy listenedIvy;

        ConsoleGrapeEngine(GrapeEngine delegate) {
            this.delegate = delegate;
        }

        GrapeEngine getDelegate() {
            return delegate;
        }

        @Override
        public Object grab(String endorsedModule) {
            return delegate.grab(endorsedModule);
        }

        @Override
        public Object grab(Map args) {
            Map<String, Object> dependency = new HashMap<>(args);
            dependency.putIfAbsent(CALLEE_DEPTH, DEFAULT_CALLEE_DEPTH + 1);
            return grab(dependency, dependency);
        }

        @Override
        public Object grab(Map args, Map... dependencies) {
            Map<String, Object> grabArgs = prepare(args);
            // The script compilation gives its class loader: report this grab, not the one made again
            // (silently, from the cache) when the script class is initialized
            boolean report = args.containsKey("classLoader");
            long start = System.currentTimeMillis();
            if (report) {
                listenDownloads();
                GroovyConsoleContent.appendLine("@Grab " + describe(dependencies) + "...", Style.INFO);
                refreshLocalSnapshots(dependencies);
            }
            Object result;
            reportingDownloads = report;
            try {
                try {
                    result = delegate.grab(grabArgs, dependencies);
                } catch (RuntimeException e) {
                    if (!removeIncompleteCacheEntries(e)) {
                        throw e;
                    }
                    // GrapeIvy consumes some arguments (classLoader...): prepare them again
                    result = delegate.grab(prepare(args), dependencies);
                }
            } finally {
                reportingDownloads = false;
            }
            if (report) {
                GroovyConsoleContent.appendLine("@Grab done (" + (System.currentTimeMillis() - start) + " ms).",
                        Style.INFO);
            }
            return result;
        }

        @Override
        public Map<String, Map<String, List<String>>> enumerateGrapes() {
            return delegate.enumerateGrapes();
        }

        @Override
        public URI[] resolve(Map args, Map... dependencies) {
            return delegate.resolve(prepare(args), dependencies);
        }

        @Override
        public URI[] resolve(Map args, List depsInfo, Map... dependencies) {
            return delegate.resolve(prepare(args), depsInfo, dependencies);
        }

        @Override
        public Map[] listDependencies(ClassLoader classLoader) {
            return delegate.listDependencies(classLoader);
        }

        @Override
        public void addResolver(Map<String, Object> args) {
            delegate.addResolver(args);
        }

        /**
         * Copy the arguments, add the exclusions and find the target class loader.
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> prepare(Map args) {
            Map<String, Object> prepared = new HashMap<>(args);
            List<Object> excludes = new ArrayList<>();
            if (args.get("excludes") instanceof List<?> list) {
                excludes.addAll(list);
            }
            for (String module : EMBEDDED_GROOVY_MODULES) {
                excludes.add(Map.of("group", "org.apache.groovy", "module", module));
            }
            excludes.add(Map.of("group", "org.codehaus.groovy", "module", "*"));
            prepared.put("excludes", excludes);

            if (!prepared.containsKey("classLoader") && !prepared.containsKey("refObject")) {
                // Grab made when a script class is initialized: the console runs the scripts with their
                // class loader as context class loader
                ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
                if (contextLoader instanceof GroovyClassLoader) {
                    prepared.put("classLoader", contextLoader);
                } else {
                    // Otherwise Grape looks for the caller in the stack: count the frame of this engine
                    Object depth = prepared.get(CALLEE_DEPTH);
                    prepared.put(CALLEE_DEPTH, (depth instanceof Integer d ? d : DEFAULT_CALLEE_DEPTH) + 1);
                }
            }
            return prepared;
        }

        /**
         * Report the downloads in the output view. The Ivy instance changes when a resolver is added
         * (@GrabResolver), so the listener is added to each new instance.
         */
        private void listenDownloads() {
            if (!(delegate instanceof GrapeIvy grapeIvy)) {
                return;
            }
            Ivy ivy = grapeIvy.getIvyInstance();
            if (ivy == null || ivy == listenedIvy) {
                return;
            }
            listenedIvy = ivy;
            ivy.getEventManager().addIvyListener(GrapeSupport::reportDownload);
            // GrapeIvy creates Ivy from its own settings, so the resolvers do not get the event manager
            // and the downloads would not be notified
            for (DependencyResolver resolver : ivy.getSettings().getResolvers()) {
                if (resolver instanceof AbstractResolver abstractResolver && abstractResolver.getEventManager() == null) {
                    abstractResolver.setEventManager(ivy.getEventManager());
                }
            }
        }

        /**
         * A -SNAPSHOT version installed in the local Maven repository ({@code mvn install}) is copied once in the
         * Grape cache, which is searched first: a newer build would never be seen. When the jar of the local
         * repository is newer than the cached one, the cache entry is removed, so the grab copies the new build.
         */
        private void refreshLocalSnapshots(Map[] dependencies) {
            if (!(delegate instanceof GrapeIvy grapeIvy)) {
                return;
            }
            File localRepository = new File(System.getProperty("maven.repo.local",
                    new File(System.getProperty("user.home"), ".m2/repository").getPath()));
            for (Map dependency : dependencies) {
                String group = value(dependency, "group", "groupId");
                String module = value(dependency, "module", "artifactId");
                String version = value(dependency, "version", "revision");
                if (group == null || module == null || version == null || !version.endsWith("-SNAPSHOT")) {
                    continue;
                }
                File localJar = new File(localRepository, group.replace('.', '/') + "/" + module + "/" + version
                        + "/" + module + "-" + version + ".jar");
                File cacheDir = new File(new File(grapeIvy.getGrapeCacheDir(), group), module);
                File cachedJar = null;
                for (String type : List.of("jars", "bundles")) {
                    File candidate = new File(new File(cacheDir, type), module + "-" + version + ".jar");
                    if (candidate.isFile()) {
                        cachedJar = candidate;
                    }
                }
                if (!localJar.isFile() || cachedJar == null || localJar.lastModified() <= cachedJar.lastModified()) {
                    continue;
                }
                for (String name : List.of("ivy-" + version + ".xml", "ivy-" + version + ".xml.original",
                        "ivydata-" + version + ".properties")) {
                    new File(cacheDir, name).delete();
                }
                cachedJar.delete();
                GroovyConsoleContent.appendLine("  newer local build of " + group + ":" + module + ":" + version
                        + " found in " + localRepository + ", using it", Style.INFO);
            }
        }

        private static String value(Map dependency, String key, String alternativeKey) {
            Object value = dependency.containsKey(key) ? dependency.get(key) : dependency.get(alternativeKey);
            return value == null ? null : value.toString();
        }

        /**
         * Grape looks for a module in its cache ({@code ~/.groovy/grapes}) before the repositories. If the
         * module descriptor is in the cache but not the requested jar, the resolution fails with
         * "download failed" instead of downloading the jar. It happens with the modules published with the
         * {@code bundle} packaging (orbisdata, H2GIS...): when they are grabbed as a dependency, their jar is
         * stored in {@code bundles/}, while a direct @Grab looks for it in {@code jars/}. It also happens
         * after an interrupted download.
         *
         * The descriptors of the failed modules are removed from the cache, so the next resolution gets them
         * again from the repositories, with their jar.
         *
         * @return true if cache entries were removed and the grab can be retried
         */
        private boolean removeIncompleteCacheEntries(RuntimeException error) {
            if (!(delegate instanceof GrapeIvy grapeIvy) || error.getMessage() == null) {
                return false;
            }
            Matcher matcher = DOWNLOAD_FAILED.matcher(error.getMessage());
            File cacheDir = grapeIvy.getGrapeCacheDir();
            boolean removed = false;
            while (matcher.find()) {
                String organisation = matcher.group(1);
                String module = matcher.group(2);
                String revision = matcher.group(3);
                File moduleDir = new File(new File(cacheDir, organisation), module);
                boolean moduleRemoved = false;
                for (String name : List.of("ivy-" + revision + ".xml", "ivy-" + revision + ".xml.original",
                        "ivydata-" + revision + ".properties")) {
                    File file = new File(moduleDir, name);
                    if (file.isFile() && file.delete()) {
                        moduleRemoved = true;
                    }
                }
                if (moduleRemoved) {
                    removed = true;
                    GroovyConsoleContent.appendLine("  incomplete cache entry removed for " + organisation + ":"
                            + module + ":" + revision + ", resolving it again...", Style.INFO);
                }
            }
            return removed;
        }

        private static String describe(Map[] dependencies) {
            StringJoiner joiner = new StringJoiner(", ");
            for (Map dependency : dependencies) {
                Object group = dependency.containsKey("group") ? dependency.get("group") : dependency.get("groupId");
                Object module = dependency.containsKey("module") ? dependency.get("module") : dependency.get("artifactId");
                Object version = dependency.containsKey("version") ? dependency.get("version") : dependency.get("revision");
                joiner.add(group + ":" + module + ":" + (version == null ? "*" : version));
            }
            return joiner.toString();
        }
    }

    private static void reportDownload(IvyEvent event) {
        if (!reportingDownloads) {
            return;
        }
        if (event instanceof StartArtifactDownloadEvent start) {
            GroovyConsoleContent.appendLine("  downloading " + artifactName(start.getArtifact()), Style.INFO);
        } else if (event instanceof EndArtifactDownloadEvent end && end.getReport() != null
                && end.getReport().getSize() > 0) {
            GroovyConsoleContent.appendLine("  downloaded " + artifactName(end.getArtifact()) + " ("
                    + Math.max(1, end.getReport().getSize() / 1024) + " KB)", Style.INFO);
        }
    }

    private static String artifactName(Artifact artifact) {
        return artifact.getModuleRevisionId().getOrganisation() + ":" + artifact.getName() + ":"
                + artifact.getModuleRevisionId().getRevision() + " (" + artifact.getExt() + ")";
    }
}
