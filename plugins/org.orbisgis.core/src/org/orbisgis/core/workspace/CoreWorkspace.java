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
package org.orbisgis.core.workspace;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.orbisgis.core.logger.Logger;

import java.io.ByteArrayInputStream;

public class CoreWorkspace implements ICoreWorkspace {

    private static final Logger LOGGER = new Logger(CoreWorkspace.class);

    private static final IWorkspace eclipseWorkspace = ResourcesPlugin.getWorkspace();

    @Override
    public void extend(ICoreWorkspaceExtension extension){
        for(String path : extension.getPaths()){
            if(!path.contains(".")){
                try {
                    IFolder folder = getFolder(path);
                    if(folder == null){
                        LOGGER.error("The folder path '" + path + "' is invalid");
                        return;
                    }
                    if(!folder.exists()) {
                        folder.create(true, true, null);
                    }
                } catch (CoreException e) {
                    LOGGER.error("Unable to create the folder '"+path+"'.", e);
                }
            }
            else{
                try {
                    IFile file = getFile(path);
                    if(file == null){
                        LOGGER.error("The file path '" + path + "' is invalid");
                        return;
                    }
                    if(!file.exists()) {
                        file.create(new ByteArrayInputStream(new byte[]{}), true, null);
                    }
                } catch (CoreException e) {
                    LOGGER.error("Unable to create the file '"+path+"'.", e);
                }
            }
        }
    }

    @Override
    public IProject getOpenProject(){
        if(eclipseWorkspace == null){
            LOGGER.error("No Eclipse workspace found.");
            return null;
        }
        if(eclipseWorkspace.getRoot() == null){
            LOGGER.error("No Eclipse workspace root found.");
            return null;
        }
        if(eclipseWorkspace.getRoot().getProjects() == null){
            LOGGER.error("No Eclipse workspace projects.");
            return null;
        }
        if(eclipseWorkspace.getRoot().getProjects().length == 0){
            LOGGER.error("No Eclipse workspace projects found.");
            return null;
        }
        for(IProject project : eclipseWorkspace.getRoot().getProjects()){
            if(project.isOpen()){
                return project;
            }
        }
        LOGGER.error("No open project found.");
        return null;
    }

    @Override
    public IFolder getFolder(String path){
        IProject project = getOpenProject();
        if(project != null){
            return project.getFolder(path);
        }
        LOGGER.error("No open project found");
        return null;
    }

    @Override
    public IFile getFile(String path){
        IProject project = getOpenProject();
        if(project != null){
            return project.getFile(path);
        }
        LOGGER.error("No open project found");
        return null;
    }
}
