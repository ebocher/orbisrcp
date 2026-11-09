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
package org.orbisgis.ui.editors.groovy;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.orbisgis.core.CoreActivator;
import org.orbisgis.ui.editors.groovy.completion.ConnectionCompletion;
import org.orbisgis.ui.editors.groovy.dbeaver.ConnectionCatalog;
import org.orbisgis.ui.editors.groovy.dbeaver.DBeaverFacade;
import org.orbisgis.ui.editors.groovy.html.HtmlPages;
import org.orbisgis.ui.editors.groovy.html.VegaLiteFunction;
import org.orbisgis.ui.editors.groovy.shell.ShellFunction;
import org.orbisgis.ui.editors.groovy.session.GrapeSupport;
import org.orbisgis.ui.editors.groovy.session.SessionClassLoader;
import org.orbisgis.ui.editors.groovy.session.GroovySession;
import org.osgi.framework.BundleContext;

import java.io.File;

public class GroovyActivator extends AbstractUIPlugin {

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        CoreActivator.getInstance().getCoreWorkspace().extend(new GroovyWorkspaceExtension());
        // @Grab: exclusion of the embedded Groovy modules, snapshots repository, download reports
        GrapeSupport.install(getStateLocation().toFile());
        SessionClassLoader.setCopyRoot(new File(getStateLocation().toFile(), "context-jars"));
        // Pages of the HTML views (charts...), in the plugin state
        HtmlPages htmlPages = new HtmlPages(new File(getStateLocation().toFile(), "html"), path -> getBundle().getEntry(path));
        // 'dbeaver' variable: access to DBeaver from the scripts
        GroovySession.addBindingContributor(binding -> {
            DBeaverFacade facade = new DBeaverFacade(htmlPages);
            binding.setVariable(DBeaverFacade.VARIABLE, facade);
            return facade;
        });
        // 'sh(...)': run a command (a Python script...), its output in the console; stopped with the script
        GroovySession.addBindingContributor(binding -> {
            ShellFunction shell = new ShellFunction(binding);
            binding.setVariable(ShellFunction.NAME, shell);
            return shell;
        });
        // Completion of the connection names: dbeaver.query("|
        ConnectionCompletion.configure(DBeaverFacade.class, ConnectionCatalog.CONNECTION_METHODS,
                ConnectionCatalog::connections);
        // 'vegalite(...)': Vega-Lite specifications, with their completion in the editor
        GroovySession.addBindingContributor(binding -> {
            binding.setVariable(VegaLiteFunction.NAME, new VegaLiteFunction());
            return null;
        });
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        super.stop(context);
    }
}
