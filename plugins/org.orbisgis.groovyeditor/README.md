Groovy Editor (GE) is a UI console to run Groovy script in an RCP eclipse environment.
Groovy Editor (GE) can be used in the [DBeaver tool](https://dbeaver.io/).

[DBeaver](https://dbeaver.io/) is a free and opensource multi-platform database tool to query, explore and manage data. Since the 6.2.2 version,the OrbisGIS team collaborates with the DBeaver team to integrate its own components : database, scripting console, map viewer...


For example, DBeaver supports the [H2GIS](http://h2gis.org/)-[H2 database](https://www.h2database.com/) engine. 


To install the Groovy console

1. Go to the Main menu `Help` -> `Install New Software...`
2. In the `Work with` field, paste the following URL https://raw.githubusercontent.com/orbisgis/orbisrcp/p2-repository/ (which is an extension P2 repository URL) and press `Enter`
3. Check the `OrbisGIS` item  and check  the `Groovy Editor` plugin.  You can select the `H2GIS driver` too.
4. Click `Next` -> `Finish` and Restart DBeaver.

![](https://github.com/orbisgis/geoclimate/blob/master/docs/resources/images/for_users/dbeaver_install_plugins.png)





------

### Remarks

#### Accept the license

You may be asked to accept the open license ([LGPL v3](https://www.gnu.org/licenses/lgpl-3.0.en.html)) of the `Groovy Editor` and the `h2gis_driver` . If so, check on `I accept the terms of the licence agreement` and click on `Finish`.

![dbeaver_accept_licence](https://github.com/orbisgis/geoclimate/blob/master/docs/resources/images/for_users/dbeaver_accept_licence.png)



#### Security warning

You also may have a `Security Warning` since these two "plugins" are not officially developed by the DBeaver team. So you are asked to confirm the installation by clicking on `Install anyway`.

![dbeaver_install_anyway](https://github.com/orbisgis/geoclimate/blob/master/docs/resources/images/for_users/dbeaver_install_anyway.png)

------



Once DBeaver has restarted, select the main menu `Groovy Editor`, click on `Open editor`, then you will have a Groovy Console.

![](https://github.com/orbisgis/geoclimate/blob/master/docs/resources/images/for_users/dbeaver_groovy_console_text.png)



|                             Icon                             |              Action               |
| :----------------------------------------------------------: | :-------------------------------: |
| ![dbeaver_groovy_console_execute](https://github.com/orbisgis/geoclimate/blob/master/docs/resources/images/for_users/dbeaver_groovy_console_execute.png) | Execute the selected instructions |
| ![dbeaver_groovy_console_execute_all](https://github.com/orbisgis/geoclimate/blob/master/docs/resources/images/for_users/dbeaver_groovy_console_execute_all.png) |      Execute all the console      |
| ![dbeaver_groovy_console_erase](https://github.com/orbisgis/geoclimate/blob/master/docs/resources/images/for_users/dbeaver_groovy_console_erase.png) |         Clear the console         |
| ![reset](icons/reset.png) | Reset the Groovy context |

Shortcuts: `Ctrl+Enter` executes the whole script, `Ctrl+Shift+Enter` executes the selection (or the current line if
nothing is selected), `Ctrl+Space` opens the completion (it also opens after a `.`).

### Templates

Type the beginning of a template name and press `Ctrl+Space`: the template is inserted, `Tab` moves to the next
field to fill, `Enter` ends the edition. On an empty line, `Ctrl+Space` lists all the templates. After a point,
the templates applying to an object are proposed: `list.ea` + `Ctrl+Space` gives `list.each { item -> }`.

| Name | Inserts |
| --- | --- |
| `for` | `for (i in 0..<n) { }` |
| `fori` | `for (int i = 0; i < n; i++) { }` |
| `forin` | `for (item in collection) { }` |
| `each` | `collection.each { item -> }` |
| `eachWithIndex` | `collection.eachWithIndex { item, index -> }` |
| `eachmap` | `map.each { key, value -> }` |
| `times` | `n.times { i -> }` |
| `upto` | `from.upto(to) { i -> }` |
| `collect`, `findAll` | `collection.collect { item -> }`, `collection.findAll { item -> }` |
| `eachRow` | `sql.eachRow("query") { row -> }` or `dbeaver.sql("connection").eachRow("query") { row -> }` |
| `while`, `dowhile` | `while (condition) { }`, `do { } while (condition)` |
| `if`, `ifelse`, `try` | `if`, `if/else`, `try/catch` blocks |

### A console that keeps its context

Each editor keeps its Groovy context between the executions, like a REPL. You can run a script piece by piece:

```groovy
import java.time.LocalDate     // the imports are remembered
def start = LocalDate.now()    // the variables declared at the top level are kept
square = { it * it }           // closures too: use them instead of methods to reuse a function
```

then, in a later execution:

```groovy
println start.plusDays(square(3))
```

- The variables declared at the top level of a script (`def x = ...`, `String s = ...`, `var v = ...`) are kept.
  The declarations inside a block (closure, loop, `if`...), the multiple assignments (`def (a, b) = ...`) and the
  annotated declarations (`@Field`) stay local to the execution.
- The libraries loaded with `@Grab` or added to the classpath stay loaded.
- The value of the last expression is printed in the output view (`Result: ...`).
- The **Reset the Groovy context** button forgets the variables, imports and libraries.
- The completion uses the context: run a line, then type `variable.` to see its methods and properties
  (including the Groovy methods such as `each` or `collect`), or `ClassName.` for the static members of a class.

The scripts can use the Groovy modules `groovy-sql`, `groovy-json`, `groovy-xml`, `groovy-templates`,
`groovy-datetime` and `groovy-nio`, the JTS library of DBeaver (`org.locationtech.jts`) and the classes of DBeaver.

### Access to DBeaver: the `dbeaver` variable

Every script has a `dbeaver` variable to use the connections and the navigator of DBeaver. `dbeaver.help()` lists
its methods, and the completion (`dbeaver.`) shows them. A `datasource` is the name of a connection of the Database
Navigator (or its id), a connection object or any database object of that connection.

```groovy
println dbeaver.datasourceNames                       // connections of the active project

// Query a connection: the password saved in DBeaver is used
dbeaver.query("Ma base PostGIS", "select nom, population from communes limit 5").each {
    println "${it.nom}: ${it.population}"
}
dbeaver.query("Ma base PostGIS", "select * from communes where insee = ?", ["56260"])
dbeaver.execute("Ma base PostGIS", "create table test as select 1 as id")
dbeaver.refresh("Ma base PostGIS")                    // show the new table in the navigator

// Several statements on the same connection: groovy.sql.Sql
def sql = dbeaver.sql("Ma base PostGIS")
sql.eachRow("select nom from communes") { println it.nom }

// A JDBC connection for a library, for instance orbisdata
def postgis = org.orbisgis.data.POSTGIS.open(dbeaver.connection("Ma base PostGIS"))

// The Database Navigator
dbeaver.selection.each { println "${it.class.simpleName}: ${it.name}" }
def ds = dbeaver.selectedDatasource                   // connection of the selected object
dbeaver.openSql(ds, "select * from communes")         // open a SQL console

// Raw DBeaver API
dbeaver.platform, dbeaver.project, dbeaver.monitor
dbeaver.inUI { /* code using the UI (views, dialogs...) */ }
```

- `connection()` and `sql()` open a new connection, isolated from the SQL editors (like a new SQL editor). They are
  closed when the context of the console is reset or when the editor is closed. `query()` and `execute()` close
  their connection themselves.
- The connection is created by the JDBC driver configured in DBeaver. A library using its own copy of the driver
  (for instance to read PostGIS geometries with `PGConnection`) may not accept it: open its own connection then.
- `dbeaver.inUI { }` runs code in the UI thread, needed by the UI parts of the Eclipse and DBeaver API. Keep it
  short: DBeaver is frozen while it runs.

### Libraries with @Grab

Libraries published on Maven can be loaded with `@Grab`, for example [orbisdata](https://github.com/orbisgis/orbisdata):

```groovy
@Grab(group='org.orbisgis.data', module='h2gis', version='2.1.2')
import org.orbisgis.data.H2GIS

h2gis = H2GIS.open('/tmp/myh2gis_db')
h2gis.execute("DROP TABLE IF EXISTS pts; CREATE TABLE pts AS SELECT 'POINT(1 2)'::GEOMETRY AS the_geom")
h2gis.getSpatialTable("pts").each { println it.the_geom.buffer(10).area }
```

- The resolution and the downloads are shown in the output view. The libraries are cached in `~/.groovy/grapes`.
- The library stays loaded, and its imports remembered, in the next executions of the same editor
  (until the context is reset).
- The Groovy modules embedded in the console are never downloaded: the libraries use the Groovy of the console.
  Groovy 2 and 3 modules (`org.codehaus.groovy`) are excluded, since they cannot work with Groovy 4.
- JTS comes from DBeaver (version 1.20): the geometries of a library and those of DBeaver are the same objects.
- Repositories: the local Maven repository (`~/.m2/repository`), Maven Central and the Maven Central snapshots
  (`-SNAPSHOT` versions). Add another one with `@GrabResolver(name='my-repo', root='https://...')`, or use your own
  Ivy configuration in `~/.groovy/grapeConfig.xml`.
- The proxy settings of DBeaver (`Preferences` > `Network Connections`) are used.
- A cached `-SNAPSHOT` version is not updated automatically: delete its folder in `~/.groovy/grapes` to get a newer one.
- When the Grape cache is incomplete (a jar missing, a library first found in `~/.m2` and removed since...), the
  console removes the cache entry and resolves the library again from the repositories.





