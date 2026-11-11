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

import org.eclipse.core.expressions.PropertyTester;

/**
 * {@code org.orbisgis.h2gis.isH2GIS}: true for a navigator node of an H2GIS connection (the connection, a schema,
 * a folder...). Shows the H2GIS menu entries.
 */
public class H2GISPropertyTester extends PropertyTester {

    public static final String NAMESPACE = "org.orbisgis.h2gis";
    public static final String PROP_IS_H2GIS = "isH2GIS";

    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
        return PROP_IS_H2GIS.equals(property) && ImportTarget.of(receiver) != null;
    }
}
