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
package org.orbisgis.core.logger;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.orbisgis.core.CoreActivator;

public class Logger implements ILogger {

    private final String id;

    public Logger(Class<?> clazz) {
        this.id = clazz.getName();
    }

    /**
     * The Eclipse log is resolved on each call: a Logger may be created (as a static field) before the
     * core bundle is started.
     */
    private static ILog getEclipseLog() {
        CoreActivator instance = CoreActivator.getInstance();
        try {
            return instance == null ? null : instance.getLog();
        } catch (Throwable e) {
            return null;
        }
    }

    protected void log(int severity, String message){
        log(severity, message, null);
    }

    protected void log(int severity, String message, Throwable throwable){
        IStatus status;
        if(throwable != null){
            status = new Status(severity, id, message, throwable);
        }
        else {
            status = new Status(severity, id, message);
        }
        ILog eclipseLog = getEclipseLog();
        if (eclipseLog != null) {
            eclipseLog.log(status);
        } else {
            System.err.println("[" + id + "] " + message);
            if (throwable != null) {
                throwable.printStackTrace();
            }
        }
    }

    @Override
    public void trace(String message) {
        log(IStatus.OK, message);
    }

    @Override
    public void trace(String message, Throwable throwable) {
        log(IStatus.OK, message, throwable);
    }

    @Override
    public void debug(String message) {
        log(IStatus.CANCEL, message);
    }

    @Override
    public void debug(String message, Throwable throwable) {
        log(IStatus.CANCEL, message, throwable);
    }

    @Override
    public void info(String message) {
        log(IStatus.INFO, message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        log(IStatus.INFO, message, throwable);
    }

    @Override
    public void warn(String message) {
        log(IStatus.WARNING, message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        log(IStatus.WARNING, message, throwable);
    }

    @Override
    public void error(String message) {
        log(IStatus.ERROR, message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        log(IStatus.ERROR, message, throwable);
    }
}
