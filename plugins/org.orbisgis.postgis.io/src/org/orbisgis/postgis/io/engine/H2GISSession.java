/*
 * postgis-io: import and export of files (GeoJSON, FlatGeobuf, Shapefile...) in the PostgreSQL/PostGIS
 * connections of DBeaver.
 *
 * For more information, please consult: <http://www.h2gis.org/>
 * or contact directly: info_at_h2gis.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.orbisgis.postgis.io.engine;

import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.orbisgis.h2gis.engine.FileIOEngine;
import org.orbisgis.h2gis.exporter.ExportFormat;
import org.orbisgis.h2gis.exporter.TableExport;
import org.orbisgis.h2gis.importer.FileImport;
import org.orbisgis.postgis.io.internal.PostGISIOMessages;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Imports and exports on a connection opened with the postgis-jts driver, with the file drivers of H2GIS (found
 * by reflection in the class loader of the H2GIS libraries).
 *
 * The drivers are called with a progress visitor of H2GIS: canceling the DBeaver job cancels it, and the H2GIS
 * drivers stop (most of them check it, or cancel their statement with JDBCUtilities.attachCancelResultSet). A
 * driver which does not is stopped by closing the connection, a few seconds later.
 */
final class H2GISSession implements FileIOEngine.Session {

    private static final String PROGRESS_VISITOR = "org.h2gis.api.ProgressVisitor";
    private static final String EMPTY_PROGRESS_VISITOR = "org.h2gis.api.EmptyProgressVisitor";
    /** Delay before closing the connection when a driver does not stop after the cancel */
    static long abortDelay = 5000;

    private final ClassLoader loader;
    private final Connection connection;
    private final Object ioMethods;
    private final Method importDriver;
    private final Method exportDriver;
    private final Method importFile;
    private final Method exportTable;
    private final Class<?> progressType;

    H2GISSession(ClassLoader loader, Connection connection) throws Exception {
        this.loader = loader;
        this.connection = connection;
        try {
            Class<?> type = Class.forName(H2GISLibraries.IO_METHODS, true, loader);
            ioMethods = inLoader(loader, () -> type.getConstructor().newInstance());
            // The driver of a file, from its extension
            importDriver = type.getMethod("getImportDriverFromFile", File.class);
            exportDriver = type.getMethod("getExportDriverFromFile", File.class);
            Class<?> driverType = Class.forName("org.h2gis.api.DriverFunction", true, loader);
            progressType = Class.forName(PROGRESS_VISITOR, true, loader);
            // importFile(connection, table, file, options, deleteTables, progress): the names of the tables created
            importFile = driverType.getMethod("importFile", Connection.class, String.class, File.class,
                    String.class, boolean.class, progressType);
            // exportTable(connection, table or (query), file, encoding, deleteFiles, progress)
            exportTable = driverType.getMethod("exportTable", Connection.class, String.class, File.class,
                    String.class, boolean.class, progressType);
        } catch (Exception | LinkageError e) {
            connection.close();
            throw e;
        }
    }

    @Override
    public FileImport.Result importFile(FileImport fileImport) {
        return importFile(fileImport, null);
    }

    @Override
    public TableExport.Result exportTable(TableExport export) {
        return exportTable(export, null);
    }

    @Override
    public FileImport.Result importFile(FileImport fileImport, DBRProgressMonitor monitor) {
        File file = fileImport.getFile();
        Cancel cancel = new Cancel(monitor);
        try {
            Object driver = inLoader(loader, () -> importDriver.invoke(ioMethods, file));
            if (driver == null) {
                return new FileImport.Result(file, fileImport.getTable(), -1, PostGISIOMessages.error_no_file_driver);
            }
            String[] tables = (String[]) cancel.run(progress -> importFile.invoke(driver, connection,
                    fileImport.tableReference(), file, null, fileImport.isReplace(), progress));
            if (fileImport.getFormat().isMultipleTables() || tables == null || tables.length != 1) {
                // GPX, OSM: several tables, prefixed with the name
                return new FileImport.Result(file, fileImport.getTable(), -1, null);
            }
            // The name of the table created, as PostgreSQL wrote it (lower case)
            return new FileImport.Result(file, tables[0], count("SELECT COUNT(*) FROM " + tables[0]), null);
        } catch (Exception e) {
            return new FileImport.Result(file, fileImport.getTable(), -1,
                    cancel.canceled() ? PostGISIOMessages.canceled : message(e));
        }
    }

    @Override
    public TableExport.Result exportTable(TableExport export, DBRProgressMonitor monitor) {
        String fileError = export.checkFile();
        if (fileError != null) {
            return new TableExport.Result(export.getTableName(), export.getFile(), -1, fileError);
        }
        File file = export.getFile();
        Cancel cancel = new Cancel(monitor);
        try {
            Object driver = inLoader(loader, () -> exportDriver.invoke(ioMethods, file));
            if (driver == null) {
                return new TableExport.Result(export.getTableName(), file, -1, PostGISIOMessages.error_no_file_driver);
            }
            // As IOMethods.exportToFile: the CSV files in UTF-8
            String encoding = export.getFormat() == ExportFormat.CSV ? "charset=UTF-8" : null;
            cancel.run(progress -> exportTable.invoke(driver, connection, export.source(), file, encoding,
                    export.isOverwrite(), progress));
            return new TableExport.Result(export.getTableName(), file, count(export.countQuery()), null);
        } catch (Exception e) {
            return new TableExport.Result(export.getTableName(), file, -1,
                    cancel.canceled() ? PostGISIOMessages.canceled : message(e));
        }
    }

    /**
     * @return the number of rows given by a query, -1 if it fails (the import or export succeeded)
     */
    private long count(String query) {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
            return result.next() ? result.getLong(1) : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    /**
     * A call of an H2GIS driver with a progress visitor, canceled when the DBeaver monitor is.
     */
    private final class Cancel {

        private final DBRProgressMonitor monitor;
        private final AtomicBoolean canceled = new AtomicBoolean();

        Cancel(DBRProgressMonitor monitor) {
            this.monitor = monitor;
        }

        boolean canceled() {
            return canceled.get();
        }

        Object run(DriverCall call) throws Exception {
            Object progress = Class.forName(EMPTY_PROGRESS_VISITOR, true, loader).getConstructor().newInstance();
            Method cancelMethod = progressType.getMethod("cancel");
            AtomicBoolean done = new AtomicBoolean();
            Thread watcher = null;
            if (monitor != null) {
                watcher = new Thread(() -> watch(progress, cancelMethod, done), "postgis-io cancel");
                watcher.setDaemon(true);
                watcher.start();
            }
            try {
                return inLoader(loader, () -> call.invoke(progress));
            } finally {
                done.set(true);
                if (watcher != null) {
                    watcher.interrupt();
                }
            }
        }

        private void watch(Object progress, Method cancelMethod, AtomicBoolean done) {
            long canceledAt = -1;
            try {
                while (!done.get()) {
                    if (monitor.isCanceled()) {
                        if (canceledAt < 0) {
                            canceledAt = System.currentTimeMillis();
                            canceled.set(true);
                            // The H2GIS drivers check it, or cancel their statement (attachCancelResultSet)
                            cancelMethod.invoke(progress);
                        } else if (System.currentTimeMillis() - canceledAt > abortDelay) {
                            // A driver which does not check the cancel: its connection is closed
                            connection.abort(Runnable::run);
                            return;
                        }
                    }
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                // the call ended
            } catch (Exception e) {
                // cancel failed: the call goes on
            }
        }
    }

    @FunctionalInterface
    private interface DriverCall {
        Object invoke(Object progress) throws Exception;
    }

    /**
     * @return the message of an error: the cause tells why (the table exists, a column type...)
     */
    static String message(Throwable error) {
        String message = null;
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            if (!(e instanceof InvocationTargetException) && e.getMessage() != null && !e.getMessage().isBlank()) {
                message = e.getMessage().strip();
            }
        }
        return message == null ? FileImport.message(error) : message;
    }

    /**
     * Run code with the H2GIS class loader as context class loader (some libraries load classes with it).
     */
    static <T> T inLoader(ClassLoader loader, Callable<T> code) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return code.call();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
