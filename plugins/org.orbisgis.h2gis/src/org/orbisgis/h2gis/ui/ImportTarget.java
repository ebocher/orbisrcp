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
package org.orbisgis.h2gis.ui;

import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;

import java.util.Locale;
import java.util.Set;

/**
 * Where files are imported: an H2GIS connection, and a schema when a schema (or one of its folders) is selected.
 *
 * @param container the connection
 * @param schema    the schema, null for the default one
 * @param node      the node to refresh after the import: the schema, or the connection
 */
public record ImportTarget(DBPDataSourceContainer container, String schema, DBNNode node) {

    /** Data source providers of H2GIS: the snapshot drivers of this plugin, the H2GIS drivers of DBeaver */
    private static final Set<String> PROVIDERS = Set.of("h2gis_snapshot", "h2gis");

    /**
     * @return the target of a selected navigator node, null if it is not in an H2GIS connection
     */
    public static ImportTarget of(Object element) {
        if (!(element instanceof DBNDatabaseNode node)) {
            return null;
        }
        DBPDataSourceContainer container = node.getDataSourceContainer();
        if (container == null || !isH2GIS(container)) {
            return null;
        }
        DBNNode connection = node;
        for (DBNNode n = node; n != null; n = n.getParentNode()) {
            if (n instanceof DBNDatabaseNode databaseNode) {
                DBSObject object = databaseNode.getObject();
                if (object instanceof DBSSchema schema) {
                    return new ImportTarget(container, schema.getName(), databaseNode);
                }
            }
            if (n instanceof DBNDataSource) {
                connection = n;
                break;
            }
        }
        return new ImportTarget(container, null, connection);
    }

    /**
     * @return true for a connection with an H2GIS driver
     */
    public static boolean isH2GIS(DBPDataSourceContainer container) {
        DBPDriver driver = container.getDriver();
        if (driver == null) {
            return false;
        }
        return PROVIDERS.contains(driver.getProviderId())
                || driver.getId().toLowerCase(Locale.ROOT).contains("h2gis");
    }

    /**
     * @return a description: "connection" or "connection, schema PUBLIC"
     */
    public String describe() {
        return container.getName() + (schema == null ? "" : ", schema " + schema);
    }
}
