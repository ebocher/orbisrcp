# PostGIS files import and export

Import and export of files (GeoJSON, FlatGeobuf, Shapefile, KML, CSV...) in the PostgreSQL/PostGIS connections of
DBeaver, from the Database Navigator. The menus and the dialogs are the ones of the H2GIS plugin
(`org.orbisgis.h2gis`): see its README for their use (table names, columns and filter of the exports...).

## Menus

- Right-click on a PostgreSQL connection, a database or a schema: **Import files (H2GIS drivers)...**. Without a
  schema selected, the tables are created in the default schema of the connection (`public`). The proposed table
  names are in lower case, as PostgreSQL writes the names without quotes.
- Right-click on one or several tables: **Export to files (H2GIS drivers)...**.

There is no *View script* button, because the plugin uses internal H2GIS IO classes.

## How it works

The files are read and written by the file drivers of H2GIS (`IOMethods.importFile` and `IOMethods.exportToFile`).

- the files are on this computer, even when the PostgreSQL server is remote; the data go through JDBC;
- the drivers work on a dedicated JDBC connection, opened with the settings of the DBeaver connection (URL through
  its SSH tunnel, user, password, driver properties) and closed at the end;
- this connection goes through the `postgis-jts` driver of H2GIS, which reads and writes the geometries as JTS
  objects.


## Stop an import or an export

The import and export run in a DBeaver job. You can stop it from the progress view (red square). 
The H2GIS drivers get a custom progress visitor which can be canceled using the `JDBCUtilities.attachCancelResultSet`.
A file partly imported stays in the database.

