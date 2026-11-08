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
package org.orbisgis.ui.editors.groovy.syntax;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;
import org.jkiss.dbeaver.ui.UIStyles;
import org.orbisgis.ui.editors.groovy.GroovyEditor;
import org.orbisgis.ui.editors.groovy.completion.GroovyCompletionProcessor;
import org.orbisgis.ui.editors.groovy.completion.GroovyTemplateProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration of the Groovy editor: coloring and completion.
 */
public class GroovySourceViewerConfiguration extends TextSourceViewerConfiguration {

    private final GroovyEditor editor;
    private final GroovyColors colors;

    public GroovySourceViewerConfiguration(GroovyEditor editor) {
        this.editor = editor;
        this.colors = GroovyColors.of(isDarkTheme());
    }

    private static boolean isDarkTheme() {
        try {
            return UIStyles.isDarkTheme();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
        return GroovyPartitions.PARTITIONING;
    }

    @Override
    public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
        return GroovyPartitions.CONTENT_TYPES;
    }

    /**
     * Completion in the code (not in the comments and strings): templates first, then the members and the
     * names of the context.
     *
     * @param sourceViewer the editor view
     * @return the completion assistant
     */
    @Override
    public IContentAssistant getContentAssistant(final ISourceViewer sourceViewer) {
        ContentAssistant assistant = new ContentAssistant();
        assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        assistant.setContentAssistProcessor(new CompositeProcessor(
                new GroovyTemplateProcessor(), new GroovyCompletionProcessor(editor::getSession)),
                IDocument.DEFAULT_CONTENT_TYPE);
        assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));
        assistant.enableAutoInsert(true);
        assistant.enableAutoActivation(true);
        assistant.setAutoActivationDelay(300);
        assistant.enableColoredLabels(true);
        assistant.setStatusLineVisible(true);
        assistant.setStatusMessage("Enter inserts the selected proposal, Tab moves to the next field of a template");
        return assistant;
    }

    @Override
    public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
        return null;
    }

    @Override
    public IPresentationReconciler getPresentationReconciler(ISourceViewer viewer) {
        PresentationReconciler reconciler = new PresentationReconciler();
        reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(viewer));
        register(reconciler, IDocument.DEFAULT_CONTENT_TYPE, GroovyCodeScanner.createCodeScanner(colors));
        register(reconciler, GroovyPartitions.COMMENT, GroovyCodeScanner.createCommentScanner(colors.comment));
        register(reconciler, GroovyPartitions.DOC_COMMENT, GroovyCodeScanner.createCommentScanner(colors.docComment));
        register(reconciler, GroovyPartitions.STRING, GroovyCodeScanner.createStringScanner(colors));
        register(reconciler, GroovyPartitions.GSTRING, GroovyCodeScanner.createGStringScanner(colors));
        return reconciler;
    }

    private static void register(PresentationReconciler reconciler, String contentType, ITokenScanner scanner) {
        DefaultDamagerRepairer damagerRepairer = new DefaultDamagerRepairer(scanner);
        reconciler.setDamager(damagerRepairer, contentType);
        reconciler.setRepairer(damagerRepairer, contentType);
    }

    /**
     * Merges the proposals of several processors.
     */
    private static final class CompositeProcessor implements IContentAssistProcessor {
        private final GroovyTemplateProcessor templates;
        private final GroovyCompletionProcessor completion;

        CompositeProcessor(GroovyTemplateProcessor templates, GroovyCompletionProcessor completion) {
            this.templates = templates;
            this.completion = completion;
        }

        @Override
        public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
            List<ICompletionProposal> proposals = new ArrayList<>(templates.computeTemplateProposals(viewer, offset));
            proposals.addAll(Arrays.asList(completion.computeCompletionProposals(viewer, offset)));
            return proposals.toArray(new ICompletionProposal[0]);
        }

        @Override
        public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
            return null;
        }

        @Override
        public char[] getCompletionProposalAutoActivationCharacters() {
            return completion.getCompletionProposalAutoActivationCharacters();
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
}
