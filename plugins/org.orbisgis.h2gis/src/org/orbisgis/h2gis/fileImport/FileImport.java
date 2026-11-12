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
package org.orbisgis.h2gis.importer;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.function.Consumer;

/**
 * Import of a file in an H2GIS database, with the read function of its format.
 */
public final class FileImport {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    /**
     * Result of the import of a file.
     *
     * @param file  the file
     * @param table the table (the prefix of the tables for GPX and OSM)
     * @param rows  number of rows of the table, -1 if unknown (several tables)
     * @param error the error, null if the import succeeded
     */
    public record Result(File file, String table, long rows, String error) {

        public boolean isSuccess() {
            return error == null;
        }
    }

    private final File file;
    private final FileFormat format;
    private final String schema;
    private final String table;
    private final boolean replace;

    /**
     * @param file    the file
     * @param format  its format
     * @param schema  the schema of the table, null for the default one
     * @param table   the name of the table
     * @param replace true to replace an existing table
     */
    public FileImport(File file, FileFormat format, String schema, String table, boolean replace) {
        this.file = file;
        this.format = format;
        this.schema = schema == null || schema.isBlank() ? null : schema;
        this.table = table;
        this.replace = replace;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }

    public FileFormat getFormat() {
        return format;
    }

    /**
     * @return the name of the table, as written by the user
     */
    public String getTable() {
        return table;
    }

    /**
     * @return true to replace an existing table
     */
    public boolean isReplace() {
        return replace;
    }

    /**
     * @return the table, with its schema: SCHEMA.TABLE
     */
    public String tableReference() {
        return schema == null ? identifier(table) : identifier(schema) + "." + identifier(table);
    }

    /**
     * @return the SQL statements of the import
     */
    public List<String> statements() {
        List<String> statements = new ArrayList<>();
        String path = literal(file.getAbsolutePath());
        if (format == FileFormat.CSV) {
            if (replace) {
                statements.add("DROP TABLE IF EXISTS " + tableReference());
            }
            statements.add("CREATE TABLE " + tableReference() + " AS SELECT * FROM CSVREAD(" + path
                    + ", NULL, 'charset=UTF-8')");
        } else {
            // The table reference is given to H2GIS as a text, which it parses as SQL (quotes kept)
            statements.add("CALL " + format.getFunction() + "(" + path + ", " + literal(tableReference()) + ", "
                    + (replace ? "TRUE" : "FALSE") + ")");
        }
        return statements;
    }

    /**
     * Run the import.
     *
     * @return the result; the errors are in the result, not thrown
     */
    public Result run(Connection connection) {
        return run(connection, statement -> {
        });
    }

    /**
     * Run the import.
     *
     * @param started called with the statement before it runs: it can be canceled from another thread
     * @return the result; the errors are in the result, not thrown
     */
    public Result run(Connection connection, Consumer<Statement> started) {
        try (Statement statement = connection.createStatement()) {
            started.accept(statement);
            for (String sql : statements()) {
                statement.execute(sql);
            }
            long rows = -1;
            if (!format.isMultipleTables()) {
                try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM " + tableReference())) {
                    rows = count.next() ? count.getLong(1) : -1;
                }
            }
            return new Result(file, table, rows, null);
        } catch (SQLException e) {
            return new Result(file, table, -1, message(e));
        }
    }

    /**
     * @return the message of an error: the first one of the chain with a text
     */
    public static String message(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                return e.getMessage().strip();
            }
        }
        return error.toString();
    }

    /**
     * @return a name as an SQL identifier: as it is if it is a simple name (H2 puts it in capitals), else quoted
     */
    static String identifier(String name) {
        if (IDENTIFIER.matcher(name).matches()) {
            return name;
        }
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    static String literal(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
