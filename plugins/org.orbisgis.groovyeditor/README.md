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
| ![classpath](icons/show_classpath.png) | Classpath: libraries of the console (@Grab, added jars and folders) |

Shortcuts: `Ctrl+Enter` executes the whole script, `Ctrl+Shift+Enter` executes the selection (or the current line if
nothing is selected), `Ctrl+Space` opens the completion (it also opens after a `.`).

### Indentation and formatting

- Enter indents the new line after `{`, `[` or `(`; a closing bracket typed at the start of a line is aligned on
  the line of its opening bracket.
- **Format** (`Ctrl+Shift+F`, or right click > Format) indents the selected lines, or the whole script: depth of the
  brackets (one level per line, so `show([` ... `], "Title")` is indented once), method chains starting with a
  point, trailing spaces and repeated blank lines. Only the blanks change; the strings (SQL in `"""..."""`) and
  the comments are kept.
- The indentation (tab width, spaces or tabs) is the one of the text editors: *Window > Preferences > Editors >
  Text Editors* ("Displayed tab width", "Insert spaces for tabs").

### Brackets

- With the cursor next to a parenthesis, a bracket or a brace, the matching one is boxed. The brackets of the strings
  and of the comments are ignored.
- A bracket without a matching one (an extra `]`, a `(` not closed) is marked as an error while typing.
- When a script does not compile because of a bracket, the console shows the bracket without a matching one and
  marks it, instead of the place where Groovy gave up (often the start of the call: `vegalite(`).

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
- The value of the last expression is printed in the output view (`Result: ...`) when the script printed nothing.
- The **Reset the Groovy context** button forgets the variables, imports and libraries.
- Right-click in the output view: Copy (the selection, or the whole output when nothing is selected), Select All
  and Clear. `Ctrl+C` and `Ctrl+A` work too.
- The completion uses the context: run a line, then type `variable.` to see its methods and properties
  (including the Groovy methods such as `each` or `collect`), or `ClassName.` for the static members of a class.

The scripts can use the Groovy modules `groovy-sql`, `groovy-json`, `groovy-xml`, `groovy-templates`,
`groovy-datetime` and `groovy-nio`, the JTS library of DBeaver (`org.locationtech.jts`) and the classes of DBeaver.

### Access to DBeaver: the `dbeaver` variable

Every script has a `dbeaver` variable to use the connections and the navigator of DBeaver. `dbeaver.help()` lists
its methods, and the completion (`dbeaver.`) shows them. A `datasource` is the name of a connection of the Database
Navigator (or its id), a connection object or any database object of that connection.

The completion proposes the names of the connections of the active project in the first argument of the methods
taking a connection (`query`, `execute`, `connection`, `sql`, `datasource`, `connect`, `disconnect`,
`connectionProperties`, `jdbcUrl`, `openSql`): `dbeaver.query("|` (`Ctrl+Space`), with their driver, and their URL
in the description. Only the DBeaver configuration is read: no connection is opened.

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

// PostGIS geometries are returned as JTS geometries, with their SRID
dbeaver.query("Ma base PostGIS", "select the_geom from communes limit 5").each { println it.the_geom.area }

// A library opening its own connection with the DBeaver settings, for instance orbisdata
def postgis = org.orbisgis.data.POSTGIS.open(dbeaver.connectionProperties("Ma base PostGIS"))
postgis.eachRow("select the_geom from communes limit 5") { println it.the_geom.buffer(10) }

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
- `connection()` and `sql()` use the JDBC driver configured in DBeaver. A library loaded with `@Grab` has its own
  copy of the driver and may not use this connection fully: orbisdata, for instance, converts the PostGIS geometries
  to JTS only with its own driver. Give it `dbeaver.connectionProperties(datasource)` (`url`, `user`, `password`)
  instead, so it opens its own connection with the settings of DBeaver.
- `dbeaver.inUI { }` runs code in the UI thread, needed by the UI parts of the Eclipse and DBeaver API. Keep it
  short: DBeaver is frozen while it runs.

### Charts and HTML pages

`dbeaver.show(spec, title)` displays a [Vega-Lite](https://vega.github.io/vega-lite/) chart in a view of DBeaver.
The specification is a Groovy map (or a JSON string), written in the script or built with
[demat](https://github.com/orbisgis/demat): the same Vega 6.2 and Vega-Lite 6.4 libraries are embedded, so the
charts look the same, offline. Showing a chart with the same title again replaces it.

```groovy
dbeaver.show([
    data    : [values: dbeaver.query("geoclimate_db", "select nom, altitude from stations")],
    mark    : "bar",
    encoding: [x: [field: "nom", type: "nominal"], y: [field: "altitude", type: "quantitative"]]
], "Stations")
```

Maps: the rows with a JTS geometry (from `dbeaver.query()`) become GeoJSON features, drawn with the `geoshape`
mark; the other columns keep their name for the encodings. Lines need `mark: [type: "geoshape", filled: false]`.
The default projection expects longitudes and latitudes (EPSG:4326): for projected coordinates, add
`projection: [type: "identity", reflectY: true]`.

```groovy
dbeaver.show([
    data    : [values: dbeaver.query("geoclimate_db", "select nom, the_geom from communes")],
    mark    : "geoshape",
    encoding: [color: [field: "nom", type: "nominal"]]
], "Communes")
```

#### Writing a specification: `vegalite(...)`

Write the specification in `vegalite(...)`, as named arguments (or as a map: `vegalite([...])`). It returns the
specification as a map:

```groovy
def spec = vegalite(
    data: [values: rows],
    mark: [type: "bar", tooltip: true],
    encoding: [
        x: [field: "name", type: "nominal"],
        y: [field: "area", type: "quantitative"]
    ]
)
dbeaver.show(spec, "Communes")
```

In the parentheses of `vegalite(...)`, the completion (`Ctrl+Space`) follows the
[Vega-Lite schema](https://vega.github.io/vega-lite/docs/) (version 6.4.3, as demat), as the Vega editor:

- at a key, the properties allowed at this place (`encoding: [x: [|` gives `field`, `type`, `aggregate`,
  `axis`...), with their documentation, except the ones already written;
- at a value with fixed choices, these choices: `type: |` gives `"quantitative"`, `"nominal"`...; also inside
  the quotes (`mark: "ba|"`);
- at a value written in Groovy (`values: rows`, `field: column`, a closure, a method call...), the Groovy
  completion.

The Vega-Lite and the Groovy proposals are never mixed, and out of `vegalite(...)` (a map given directly to
`dbeaver.show`...) there is no Vega-Lite completion. The schema is loaded on the first use (about 3 MB in memory,
shared by the consoles). The `vegalite` template writes a complete chart.

In an empty specification (`vegalite(|)`, or with only `data` written), `Ctrl+Space` first proposes chart
templates, then the properties. A template writes the `data`, `mark` and `encoding` of a kind of chart; `Tab` goes
from a field to the next one (the rows, then the columns), `Enter` ends the edition. Type the beginning of a name to
choose (`vegalite(h` + `Ctrl+Space` gives `histogram` and `heatmap`).

In the list, the Groovy templates have an orange **T** icon and the chart templates a purple bar chart icon.

| Name | Chart |
| --- | --- |
| `bar` | bars: a value per category |
| `stackedbar` | stacked bars: a value per category and group |
| `line`, `area` | a value over time |
| `scatter` | a quantity against another one |
| `pie` | the shares of the categories |
| `histogram` | the distribution of a quantity |
| `heatmap` | a value per pair of categories |
| `boxplot` | the distribution of a quantity per category |
| `geomap` | rows with a geometry, colored by a value (longitudes and latitudes) |
| `geomapxy` | the same in projected coordinates (`projection: [type: "identity", reflectY: true]`) |

Other pages:
- `dbeaver.html(new File("/tmp/chart.html"))`: an HTML file, for instance written by demat with `toHTML`;
- `dbeaver.html("<h1>Report</h1>...", "Report")`: HTML content;
- `dbeaver.browse("https://...", "Title")`: a web page.

The `...` menu of a chart saves it as PNG or SVG: DBeaver asks where to write the file. The download links of the
other pages (demat pages...) open the same dialog.

On Windows, the pages need the Edge browser engine (the default of DBeaver on Windows 10 and 11).

### Running commands: `sh(...)`

`sh(...)` runs a command (a Python script, a tool...): its output is shown in the console while it runs, the errors
in red.

```groovy
sh "python3 analyse.py communes.csv"                    // the output in the console
sh(["python3", "analyse.py", "my file.csv"])            // arguments as a list: no quoting problem
def r = sh("python3 stats.py", quiet: true)             // r.text: the output, r.exitCode, r.error
def stats = new groovy.json.JsonSlurper().parseText(r.text)
sh("ls *.csv | wc -l", shell: true)                      // pipes, redirections, wildcards
sh("python3 long.py", dir: "/data", env: [MODE: "fast"], timeout: 600)
```

| Option | |
|---|---|
| `dir` | working directory (the home directory by default) |
| `env` | environment variables added to the ones of DBeaver |
| `input` | text sent to the standard input |
| `shell` | run with `bash -c` (`cmd /c` on Windows): pipes, redirections, wildcards |
| `quiet` | do not show the output (it is in the result) |
| `timeout` | maximum duration, in seconds |
| `charset` | encoding of the output (UTF-8 by default) |

- The result has `exitCode`, `text` (standard output), `error` (error output), `timedOut` and `success`.
  A non-zero exit code is shown in the console; the script goes on.
- The Python scripts show their output as they run (`PYTHONUNBUFFERED`), in UTF-8.
- The **Stop** button stops the command and its sub-processes: they are asked to stop, and killed after 5 seconds
  (immediately on Windows). The `timeout` and closing the console do the same. A command which detaches itself
  (`nohup ... &`, a daemon) is not stopped.

### Libraries with @Grab

Libraries published on Maven can be loaded with `@Grab`, for example [orbisdata](https://github.com/orbisgis/orbisdata):

```groovy
@Grab(group='org.orbisgis.data', module='h2gis', version='2.1.2')
import org.orbisgis.data.H2GIS

h2gis = H2GIS.open('/tmp/myh2gis_db')
h2gis.execute("DROP TABLE IF EXISTS pts; CREATE TABLE pts AS SELECT 'POINT(1 2)'::GEOMETRY AS the_geom")
h2gis.getSpatialTable("pts").each { println it.the_geom.buffer(10).area }
```

## Languages

The user interface (menus, dialogs, views) is in English and French: DBeaver shows the language of its user interface
(*Window > Preferences > User Interface*, language). The texts are in `OSGI-INF/l10n/bundle*.properties` (plugin.xml)
and in the `*Messages*.properties` files of the `internal` package (dialogs); another language is a copy of the
`_fr` files with its language code (`_de`, `_es`...), non-ASCII characters written `\uXXXX`. The output of the
scripts, the logs and the error messages of the databases stay in English.
