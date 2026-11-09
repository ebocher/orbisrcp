# OrbisRCP

OrbisRCP is the next generation of OrbisGIS components. The new components are RCP eclipse based and must be used inside [DBeaver](https://dbeaver.io/).

[DBeaver](https://dbeaver.io/) is a free and opensource multi-platform database tool to query, explore and manage data. Since the 6.2.2 version,the OrbisGIS team collaborates with the DBeaver team to integrate its own components : database, scripting console, map viewer...

OrbisRCP plugins have only been supported since version 26.2.4 of Dbeaver.

Please look at the readme file available in the directory of the plugins you want to install.

If you have any questions, comments or troubles with the plugins, feel free to push an issue. 
We will do our best to address them.

## Build

OrbisRCP is built against the master branch of DBeaver with Java 21, Maven and Tycho. The build uses `dbeaver-common` as parent, so the Tycho version, the Java version and the p2 repositories
(Eclipse release and DBeaver third-party bundles) always follow DBeaver.

DBeaver and its dependencies must be cloned next to OrbisRCP, then installed in the local Maven repository:

```bash
export JAVA_HOME=/path/to/the/jdk/21
mkdir dbeaver-dev && cd dbeaver-dev
git clone https://github.com/dbeaver/dbeaver-common
git clone https://github.com/dbeaver/datadam-api
git clone https://github.com/dbeaver/dbeaver
git clone https://github.com/orbisgis/orbisrcp

# Build and install DBeaver
mvn install -f dbeaver/product/aggregate/pom.xml -T1C -DskipTests

# Build OrbisRCP: the p2 repository is created in orbisrcp/p2/target/repository
mvn clean verify -f orbisrcp/pom.xml
```

To build against a released DBeaver instead of the master branch, use the `dbeaver-release` profile
(`dbeaver-common` must still be cloned next to OrbisRCP, at the tag of the DBeaver version):

```bash
mvn clean verify -f orbisrcp/pom.xml -Pdbeaver-release -Ddbeaver.update.url=https://dbeaver.io/update/latest/
```

To test the plugins, install the p2 repository `orbisrcp/p2/target/repository` in DBeaver
(`Help` > `Install New Software...` > `Add...` > `Local...`).

## Plugins installation

The repository to install the OrbisRCP plugins is  : https://orbisgis.github.io/orbisrcp/
It contains  the current snaphot versions (deployed after each PR).

In DBeaver :
 - Go to menu `help/Install New Software...`.
 - Add the repository by clicking on `Add...`
   - Put the name you want (like `OrbisRCP`) 
   - Put the location `https://orbisgis.github.io/orbisrcp/`
   - Click on `Add`.
  - Once back on the `Available Software` window, verify the newly add repository is selected in `Work with:`.
    - Check then the box at the left of `OrbisGIS` software. 
    - Click then on `Next`.
  - On the next window click on `Finish`.
  - A `Security Warning` will be show, click on `Install Anyway` 
  - On the next popup, click on `Restart Now`

  - TaDaa, you have the plugins installed.

