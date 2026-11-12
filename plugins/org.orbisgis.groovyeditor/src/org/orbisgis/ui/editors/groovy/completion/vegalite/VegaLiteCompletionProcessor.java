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
package org.orbisgis.ui.editors.groovy.completion.vegalite;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.orbisgis.ui.editors.groovy.completion.GroovyTemplateProcessor;
import org.orbisgis.ui.editors.groovy.ui.Icons;

import java.util.ArrayList;
import java.util.List;

/**
 * Content assist of the Vega-Lite specifications ({@code vegalite(...)}). Inside a specification, its proposals
 * replace the Groovy ones; elsewhere {@link #proposals} returns null and the Groovy completion applies.
 */
public class VegaLiteCompletionProcessor implements IContentAssistProcessor {

    private static final int MAX_DESCRIPTION = 1500;

    private final VegaLiteCompletion completion = new VegaLiteCompletion(VegaLiteSchema::get);
    private final GroovyTemplateProcessor templates = new GroovyTemplateProcessor();

    /**
     * @return the proposals, or null if the position is not in a specification (or is a value written in Groovy)
     */
    public ICompletionProposal[] proposals(ITextViewer viewer, int offset) {
        List<VegaLiteCompletion.Suggestion> suggestions = completion.compute(viewer.getDocument(), offset);
        if (suggestions == null) {
            return null;
        }
        // In an empty specification, the chart templates first, then the properties
        List<ICompletionProposal> proposals = new ArrayList<>(
                templates.proposals(viewer, ChartTemplates.match(viewer.getDocument(), offset), Icons.COMPLETION_CHART));
        for (VegaLiteCompletion.Suggestion s : suggestions) {
            String display = s.type().isEmpty() ? s.display() : s.display() + " : " + s.type();
            proposals.add(new CompletionProposal(s.replacement(), s.offset(), s.length(), s.cursor(),
                    s.key() ? Icons.COMPLETION_VARIABLE : null, display, null, describe(s.description())));
        }
        return proposals.toArray(new ICompletionProposal[0]);
    }

    private static String describe(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return null;
        }
        String text = markdown.replace("`", "").replace("__", "")
                .replaceAll("\\[([^]]+)]\\((https?://[^)]+)\\)", "$1");
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) + "..." : text;
    }

    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
        ICompletionProposal[] proposals = proposals(viewer, offset);
        return proposals == null ? new ICompletionProposal[0] : proposals;
    }

    @Override
    public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
        return null;
    }

    @Override
    public char[] getCompletionProposalAutoActivationCharacters() {
        return null;
    }

    @Override
    public char[] getContextInformationAutoActivationCharacters() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public IContextInformationValidator getContextInformationValidator() {
        return null;
    }
}
