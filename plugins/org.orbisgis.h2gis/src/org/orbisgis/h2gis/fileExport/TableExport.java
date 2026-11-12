/*
 * H2GIS ecplise plugin to register a H2GIS spatial database to 
 * DBeaver, the  Universal Database Manager
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
package org.orbisgis.h2gis.exporter;

import org.eclipse.osgi.util.NLS;
import org.orbisgis.h2gis.internal.H2GISMessages;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Export of a table of an H2GIS database to a file, with the write function of its format.
 */
public final class TableExport {

    /**
     * Result of the export of a table.
     *
     * @param table the table
     * @param file  the file
     * @param rows  number of rows exported, -1 if unknown
     * @param error the error, null if the export succeeded
     */
    public record Result(String table, File file, long rows, String error) {

        public boolean isSuccess() {
            return error == null;
        }
    }

    private final String tableName;
    private final String tableReference;
    private final File file;
    private final ExportFormat format;
    private final boolean overwrite;
    private final List<String> columns;
    private final String filter;

    /**
     * @param tableName      the name of the table, to show
     * @param tableReference the table in SQL, with its schema and quotes: PUBLIC.COMMUNES
     * @param file           the file to write
     * @param format         the format of the file
     * @param overwrite      true to replace an existing file
     */
    public TableExport(String tableName, String tableReference, File file, ExportFormat format, boolean overwrite) {
        this(tableName, tableReference, file, format, overwrite, null, null);
    }

    /**
     * @param columns the columns to export, as SQL identifiers (quoted if needed); null for all the columns
     * @param filter  an SQL condition on the rows (without WHERE), null or blank for all the rows
     */
    public TableExport(String tableName, String tableReference, File file, ExportFormat format, boolean overwrite,
                       List<String> columns, String filter) {
        this.tableName = tableName;
        this.tableReference = tableReference;
        this.file = file;
        this.format = format;
        this.overwrite = overwrite;
        this.columns = columns == null || columns.isEmpty() ? null : List.copyOf(columns);
        this.filter = cleanFilter(filter);
    }

    /**
     * @return a condition without the WHERE keyword nor a final semicolon, null if blank
     */
    static String cleanFilter(String filter) {
        if (filter == null) {
            return null;
        }
        String condition = filter.strip();
        if (condition.regionMatches(true, 0, "WHERE ", 0, 6)) {
            condition = condition.substring(6).strip();
        }
        while (condition.endsWith(";")) {
            condition = condition.substring(0, condition.length() - 1).strip();
        }
        return condition.isEmpty() ? null : condition;
    }

    /**
     * @return the query of the rows exported: SELECT columns FROM table WHERE filter
     */
    public String selectQuery() {
        return "SELECT " + (columns == null ? "*" : String.join(", ", columns)) + " FROM " + tableReference
                + whereClause();
    }

    /**
     * @return what the H2GIS function exports: the table, or the query in parentheses when columns or a filter are
     * chosen (the H2GIS write functions accept '(SELECT ...)')
     */
    public String source() {
        if (columns == null && filter == null) {
            return tableReference;
        }
        // A new line before the parenthesis: a '--' comment in the filter does not hide it
        return "(" + selectQuery() + "\n)";
    }

    private String whereClause() {
        return filter == null ? "" : " WHERE " + filter;
    }

    /**
     * @return the query counting the rows exported
     */
    public String countQuery() {
        return "SELECT COUNT(*) FROM " + tableReference + (filter == null ? "" : " WHERE " + filter + "\n");
    }

    public String getTableName() {
        return tableName;
    }

    public File getFile() {
        return file;
    }

    public ExportFormat getFormat() {
        return format;
    }

    /**
     * @return true to replace an existing file
     */
    public boolean isOverwrite() {
        return overwrite;
    }

    /**
     * @return why the file cannot be written (it exists, its folder does not), null if it can
     */
    public String checkFile() {
        if (file.exists() && !overwrite) {
            return H2GISMessages.export_error_exists;
        }
        File folder = file.getAbsoluteFile().getParentFile();
        if (folder != null && !folder.isDirectory()) {
            return NLS.bind(H2GISMessages.export_error_no_folder, folder);
        }
        return null;
    }

    /**
     * @return a file name for a table: its name in lower case, with the extension of the format
     */
    public static String defaultFileName(String tableName, ExportFormat format) {
        String name = tableName.toLowerCase(Locale.ROOT).replaceAll("[\\\\/:*?\"<>|]+", "_");
        return (name.isBlank() ? "export" : name) + "." + format.getExtension();
    }

    /**
     * @return the SQL statements of the export
     */
    public List<String> statements() {
        String path = literal(file.getAbsolutePath());
        if (format == ExportFormat.CSV) {
            return List.of("CALL CSVWRITE(" + path + ", " + literal(selectQuery() + (filter == null ? "" : "\n"))
                    + ", 'charset=UTF-8')");
        }
        // The table reference (or the query) is given to H2GIS as a text, which it parses as SQL (quotes kept)
        return List.of("CALL " + format.getFunction() + "(" + path + ", " + literal(source()) + ", "
                + (overwrite ? "TRUE" : "FALSE") + ")");
    }

    /**
     * Run the export.
     *
     * @return the result; the errors are in the result, not thrown
     */
    public Result run(Connection connection) {
        return run(connection, statement -> {
        });
    }

    /**
     * Run the export.
     *
     * @param started called with the statement before it runs: it can be canceled from another thread
     * @return the result; the errors are in the result, not thrown
     */
    public Result run(Connection connection, Consumer<Statement> started) {
        String fileError = checkFile();
        if (fileError != null) {
            return new Result(tableName, file, -1, fileError);
        }
        try (Statement statement = connection.createStatement()) {
            started.accept(statement);
            for (String sql : statements()) {
                statement.execute(sql);
            }
            long rows;
            try (ResultSet count = statement.executeQuery(countQuery())) {
                rows = count.next() ? count.getLong(1) : -1;
            }
            return new Result(tableName, file, rows, null);
        } catch (SQLException e) {
            return new Result(tableName, file, -1, message(e));
        }
    }

    public static String message(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                return e.getMessage().strip();
            }
        }
        return error.toString();
    }

    static String literal(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
