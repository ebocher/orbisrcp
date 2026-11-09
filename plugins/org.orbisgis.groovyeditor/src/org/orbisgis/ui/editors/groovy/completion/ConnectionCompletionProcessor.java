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

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.orbisgis.ui.editors.groovy.session.GroovySession;
import org.orbisgis.ui.editors.groovy.ui.Icons;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Content assist of the names of the DBeaver connections: {@code dbeaver.query("|}.
 */
public class ConnectionCompletionProcessor implements IContentAssistProcessor {

    private final Supplier<GroovySession> session;

    public ConnectionCompletionProcessor(Supplier<GroovySession> session) {
        this.session = session;
    }

    /**
     * @return the proposals, or null if the position is not the connection argument of a {@code dbeaver} method
     */
    public ConnectionCompletion.Result result(ITextViewer viewer, int offset) {
        GroovySession context = session.get();
        if (context == null) {
            return null;
        }
        try {
            Map<String, Object> variables = context.getVariables();
            return ConnectionCompletion.compute(viewer.getDocument(), offset, variables);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * @return the proposals of a result
     */
    public static ICompletionProposal[] proposals(ConnectionCompletion.Result result) {
        List<ConnectionCompletion.Suggestion> suggestions = result.suggestions();
        ICompletionProposal[] proposals = new ICompletionProposal[suggestions.size()];
        for (int i = 0; i < proposals.length; i++) {
            ConnectionCompletion.Suggestion s = suggestions.get(i);
            ConnectionCompletion.Connection connection = s.connection();
            String display = connection.name() + " - " + connection.driver()
                    + (connection.connected() ? " (connected)" : "");
            proposals[i] = new CompletionProposal(s.replacement(), s.offset(), s.length(), s.cursor(),
                    Icons.COMPLETION_VARIABLE, display, null, connection.description());
        }
        return proposals;
    }

    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
        ConnectionCompletion.Result result = result(viewer, offset);
        return result == null ? new ICompletionProposal[0] : proposals(result);
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
