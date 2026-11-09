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
