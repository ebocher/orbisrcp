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
package org.orbisgis.ui.editors.groovy.completion;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.templates.GlobalTemplateVariables;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateCompletionProcessor;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.swt.graphics.Image;

import java.util.ArrayList;
import java.util.List;

/**
 * Proposes the templates of {@link GroovyTemplates}. Inserting a template starts the linked mode: the fields
 * are edited together and Tab goes to the next one, Enter ends the edition.
 */
public class GroovyTemplateProcessor extends TemplateCompletionProcessor {

    public static final String CONTEXT_TYPE_ID = "org.orbisgis.ui.editors.groovy.templates";

    private static final TemplateContextType CONTEXT_TYPE = createContextType();

    private static TemplateContextType createContextType() {
        TemplateContextType contextType = new TemplateContextType(CONTEXT_TYPE_ID, "Groovy");
        contextType.addResolver(new GlobalTemplateVariables.Cursor());
        return contextType;
    }

    /**
     * @return the template proposals for the given position
     */
    public List<ICompletionProposal> computeTemplateProposals(ITextViewer viewer, int offset) {
        List<ICompletionProposal> proposals = new ArrayList<>();
        for (GroovyTemplates.Match match : GroovyTemplates.match(viewer.getDocument().get(), offset)) {
            Template template = new Template(match.name(), match.description(), CONTEXT_TYPE_ID, match.pattern(), false);
            IRegion region = new Region(match.replaceOffset(), match.replaceLength());
            TemplateContext context = createContext(viewer, region);
            if (context != null) {
                proposals.add(createProposal(template, context, region, 90));
            }
        }
        return proposals;
    }

    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
        return computeTemplateProposals(viewer, offset).toArray(new ICompletionProposal[0]);
    }

    @Override
    protected Template[] getTemplates(String contextTypeId) {
        return new Template[0];
    }

    @Override
    protected TemplateContextType getContextType(ITextViewer viewer, IRegion region) {
        return CONTEXT_TYPE;
    }

    @Override
    protected Image getImage(Template template) {
        return null;
    }
}
