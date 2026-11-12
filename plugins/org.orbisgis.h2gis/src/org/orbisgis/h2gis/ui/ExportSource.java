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
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.struct.DBSEntity;

/**
 * A table (or a view) of an H2GIS connection, selected in the navigator to be exported.
 *
 * @param container the connection
 * @param name      the name of the table
 * @param reference the table in SQL, with its catalog/schema and quotes
 * @param entity    the table, to read its columns
 */
public record ExportSource(DBPDataSourceContainer container, String name, String reference, DBSEntity entity) {

    /**
     * @return the table of a selected navigator node, null if it is not a table of an H2GIS connection
     */
    public static ExportSource of(Object element) {
        if (!(element instanceof DBNDatabaseNode node) || !(node.getObject() instanceof DBSEntity entity)) {
            return null;
        }
        DBPDataSourceContainer container = node.getDataSourceContainer();
        if (container == null || !ImportTarget.isH2GIS(container)) {
            return null;
        }
        return new ExportSource(container, entity.getName(), DBUtils.getObjectFullName(entity, DBPEvaluationContext.DML),
                entity);
    }
}
