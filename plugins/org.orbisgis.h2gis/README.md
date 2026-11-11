Plugin to install the current development version of H2GIS: its drivers ("H2GIS Embedded Snapshot", "H2GIS Server
Snapshot") download the last snapshot build of the H2GIS series written in `plugin.xml` (`2.2.6-SNAPSHOT`) from the
Maven Central snapshots repository. DBeaver has its own H2GIS drivers ("H2GIS Embedded", "H2GIS Server"), which follow
the releases.

DBeaver keeps the downloaded driver files: to get a newer snapshot build, open *Database > Driver Manager*, edit the
driver and click *Download/Update* in the *Libraries* tab.

When the development of a new H2GIS version starts (new version in the H2GIS `pom.xml`), change the version in
`plugin.xml`.

To install the last H2GIS snaptshot version : 

1. Go to the Main menu `Help` -> `Install New Software...`
2. In the `Work with` field, paste the following URL https://raw.githubusercontent.com/orbisgis/orbisrcp/p2-repository/ (which is an extension P2 repository URL) and press `Enter`
3. Check the `OrbisGIS` item and the plugin `H2GIS driver` 
4. Click `Next` -> `Finish` and Restart DBeaver.

![](https://github.com/orbisgis/geoclimate/blob/master/docs/resources/images/for_users/dbeaver_install_plugins.png)

## Import files

Right click on an H2GIS connection, one of its schemas or its *Tables* folder in the Database Navigator, then
**Import files...** (next to *Import Data*). It works with the H2GIS snapshot drivers of this plugin and with the
H2GIS drivers of DBeaver.

Choose one or several files: each one is read by its H2GIS function into a table, in the selected schema (the default
schema from the connection). The table name, proposed from the file name (`Communes 2024.geojson` gives
`COMMUNES_2024`), can be changed. *Replace the existing tables* replaces a table of the same name; without it, the
import of that file fails.

| Format | Extensions | H2GIS function |
|---|---|---|
| GeoJSON | `.geojson`, `.json`, `.geojson.gz` | `GeoJsonRead` |
| FlatGeobuf | `.fgb` | `FGBRead` |
| Shapefile | `.shp` | `SHPRead` |
| dBase | `.dbf`, `.dbf.gz` | `DBFRead` |
| GPX | `.gpx`, `.gpx.gz` | `GPXRead` (several tables, named with the table name as prefix) |
| OpenStreetMap | `.osm`, `.osm.gz`, `.osm.bz2` | `OSMRead` (several tables, named with the table name as prefix) |
| ASCII grid | `.asc`, `.asc.gz` | `AscRead` |
| TSV | `.tsv`, `.tsv.gz` | `TSVRead` |
| CSV | `.csv` | `CSVREAD` of H2 |

The import runs in the background (the connection is opened if needed); the navigator is then refreshed, and a
summary gives the rows of each table, or the error of each file which failed.

