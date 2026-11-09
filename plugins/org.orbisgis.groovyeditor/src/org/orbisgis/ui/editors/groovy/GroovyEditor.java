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

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.source.DefaultCharacterPairMatcher;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.jkiss.dbeaver.ui.UIStyles;
import org.orbisgis.ui.editors.groovy.syntax.BracketChecker;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.editors.text.ForwardingDocumentProvider;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.controls.VerticalButton;
import org.jkiss.dbeaver.ui.controls.VerticalFolder;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.GroovyConsoleContent;
import org.orbisgis.ui.editors.groovy.GroovyOutputConsole.Style;
import org.orbisgis.ui.editors.groovy.session.GroovySession;
import org.orbisgis.ui.editors.groovy.session.ScriptErrors.ScriptError;
import org.orbisgis.ui.editors.groovy.syntax.GroovyFormatter;
import org.orbisgis.ui.editors.groovy.syntax.GroovyPartitions;
import org.orbisgis.ui.editors.groovy.syntax.GroovySourceViewerConfiguration;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;

public class GroovyEditor extends AbstractDecoratedTextEditor implements ISaveablePart2 {

    private static final Logger LOGGER = new Logger(GroovyEditor.class);

    private static final int SIDE_TOOLBAR_MARGIN_TOP = 3;
    private static final int SIDE_TOOLBAR_MARGIN_BOTTOM = 10;
    private static final int SIDE_TOOLBAR_VERTICAL_SPACING = 3;

    private static final String CONTENT_ASSIST_ACTION = "ContentAssistProposal";

    private static final String FORMAT_ACTION = "GroovyFormat";

    /** Command of the formatting (Ctrl+Shift+F) */
    public static final String FORMAT_COMMAND = "org.orbisgis.ui.editors.groovy.format";

    /** Standard error annotation: red underline, marker in the ruler and the overview ruler */
    private static final String ERROR_ANNOTATION_TYPE = "org.eclipse.ui.workbench.texteditor.error";

    /** Errors of the last execution shown in the editor */
    private final List<Annotation> errorAnnotations = new ArrayList<>();

    /** Brackets without a matching one, marked while typing */
    private final List<Annotation> bracketAnnotations = new ArrayList<>();

    /** Delay of the bracket check after the last change (ms) */
    private static final int BRACKET_CHECK_DELAY = 500;

    private final Runnable bracketCheck = this::checkBrackets;

    private final IDocumentListener bracketListener = new IDocumentListener() {
        @Override
        public void documentAboutToBeChanged(DocumentEvent event) {
        }

        @Override
        public void documentChanged(DocumentEvent event) {
            Display display = Display.getCurrent();
            if (display != null) {
                display.timerExec(-1, bracketCheck);
                display.timerExec(BRACKET_CHECK_DELAY, bracketCheck);
            }
        }
    };

    private IDocument checkedDocument;

    /** Preferences of the bracket highlighting, before the ones of the text editors */
    private static final String MATCHING_BRACKETS = "org.orbisgis.groovyeditor.matchingBrackets";
    private static final String MATCHING_BRACKETS_COLOR = "org.orbisgis.groovyeditor.matchingBracketsColor";
    private static final String BRACKET_AT_CARET = "org.orbisgis.groovyeditor.highlightBracketAtCaret";
    private static final String ENCLOSING_BRACKETS = "org.orbisgis.groovyeditor.enclosingBrackets";

    private GroovyJob job;

    /** Groovy context of this editor, kept between the executions */
    private final GroovySession session = new GroovySession();

    public GroovyEditor(){
        super();
        setKeyBindingScopes(new String[]{"org.orbisgis.ui.editors.groovy"});
        // The documents get the Groovy partitioner (comments, strings) used by the coloring and the completion
        setDocumentProvider(new ForwardingDocumentProvider(GroovyPartitions.PARTITIONING,
                GroovyPartitions::setup, new TextFileDocumentProvider()));
        setSourceViewerConfiguration(new GroovySourceViewerConfiguration(this));
    }

    /**
     * @return the Groovy context of this editor
     */
    public GroovySession getSession() {
        return session;
    }

    /**
     * Checks if the cursor is between simple quotation marks
     * @param caretOffset the caret
     * @param text the text
     * @param keyCode the keyCode
     * @return true if the caret is between simple quotation marks
     */
    public boolean cursorIsBetweenSimpleQuotationMarks(int caretOffset, String text, int keyCode){
        boolean doubleMarksInSimpleMarks = false;
        ArrayList<Integer> simpleQuotePositions = findPositions(text).get(0);
        if(simpleQuotePositions.size() > 1) {
            for (int i = 0; i < simpleQuotePositions.size();i = i + 2){
                if(i+1 < simpleQuotePositions.size()) {
                    // checks if the cursor is between simple quotation marks
                    int a = simpleQuotePositions.get(i + 1);
                    int b = a + 1;
                    if (caretOffset > simpleQuotePositions.get(i) && caretOffset < b && keyCode != 8) {
                        doubleMarksInSimpleMarks = true;
                    }
                }
            }
        }
        return doubleMarksInSimpleMarks;
    }

    /**
     * Find the positions of a character in a text and returns an ArrayList of these positions
     * @param text the text
     * @return an ArrayList containing the positions of the character in the text
     */
    public ArrayList<ArrayList<Integer>> findPositions(String text) {
        ArrayList<ArrayList<Integer>> quotationMarksList = new ArrayList<>();
        ArrayList<Integer> simpleQuotePositions = new ArrayList<>();
        ArrayList<Integer> doubleQuotePositions = new ArrayList<>();
        for (int i = 0; i < text.length(); i++){
            if (text.charAt(i) == "'".charAt(0)) {
                simpleQuotePositions.add(i);
            }
            if (text.charAt(i) == "\"".charAt(0)) {
                doubleQuotePositions.add(i);
            }
        }
        ArrayList<Integer> doubleQuotePositionsToDelete = new ArrayList<>();
        if(simpleQuotePositions.size() > 1) {
            for (int i = 0; i < simpleQuotePositions.size();i = i + 2){
                for (Integer doubleQuotePosition : doubleQuotePositions) {
                    // check if a double quotation marks is not between simple quotation marks
                    if(i+1 < simpleQuotePositions.size()) {
                        if (doubleQuotePosition > simpleQuotePositions.get(i) && doubleQuotePosition < simpleQuotePositions.get(i + 1)) {
                            doubleQuotePositionsToDelete.add(doubleQuotePosition);
                        }
                    }
                }
            }
        }
        doubleQuotePositions.removeAll(doubleQuotePositionsToDelete);
        quotationMarksList.add(simpleQuotePositions);
        quotationMarksList.add(doubleQuotePositions);
        return quotationMarksList;
    }

    /**
     * Catch the last pressed key, if it is an opening symbol like ([{"', the corresponding closing symbol is written
     * automatically after the cursor.
     * If the deleted key is pressed : if the deleted character is an opening symbol like ([{"' and if the following
     * character is its corresponding closing symbol, so this corresponding closing symbol is also deleted.
     * @param parent the composite
     * @param ruler the iVerticalRuler
     * @param styles the styles
     * @return the possibly edited view
     */
    @Override
    public ISourceViewer createSourceViewer(Composite parent, IVerticalRuler ruler, int styles) {
        ISourceViewer viewer = super.createSourceViewer(parent, ruler, styles);
        if(viewer instanceof ITextViewerExtension) {

            ((ITextViewerExtension) viewer).appendVerifyKeyListener(verifyEvent -> {
                String lastCharacter = String.valueOf(verifyEvent.character);
                StyledText styledText = (StyledText) verifyEvent.getSource();
                String source = styledText.getText();

                ArrayList<Integer> doubleQuotationMarkPositions = findPositions(source).get(1);
                int caretOffset = styledText.getCaretOffset();
                boolean cursorInSimpleMarks = cursorIsBetweenSimpleQuotationMarks(caretOffset, source, verifyEvent.keyCode);

                int nbQuotationMarkBeforeCaret = 0;
                for (Integer quotationMarkPosition : doubleQuotationMarkPositions) {
                    if (quotationMarkPosition < caretOffset) {
                        nbQuotationMarkBeforeCaret++;
                    } else {
                        break;
                    }
                }
                // if the quotation mark number before the caret is even
                if((nbQuotationMarkBeforeCaret % 2) == 0 && !cursorInSimpleMarks) {
                    switch (lastCharacter) {
                        case "(":
                            styledText.insert(")");
                            break;
                        case "[":
                            styledText.insert("]");
                            break;
                        case "{":
                            styledText.insert("}");
                            break;
                        case "\"":
                            styledText.insert("\"");
                            break;
                        case "'":
                            styledText.insert("'");
                            break;
                    }
                    // if the delete key is pressed
                    if (verifyEvent.keyCode == 8) {
                        // if there is a character after the cursor and if the delete key is not pressed at the first
                        // possible position of the caret
                        if (caretOffset < source.length() && caretOffset != 0) {
                            String deletedCharacter = String.valueOf(source.charAt(caretOffset - 1));
                            String followingCharacter = String.valueOf(source.charAt(caretOffset));
                            if (deletedCharacter.equals("(") && followingCharacter.equals(")")
                                    || deletedCharacter.equals("[") && followingCharacter.equals("]")
                                    || deletedCharacter.equals("{") && followingCharacter.equals("}")
                                    || deletedCharacter.equals("'") && followingCharacter.equals("'")) {
                                styledText.replaceTextRange(caretOffset, 1, "");
                            }
                        }
                    }
                    if (caretOffset < source.length() && caretOffset != 0) {
                        String followingCharacter = String.valueOf(source.charAt(caretOffset));
                        if(followingCharacter.equals(lastCharacter) && followingCharacter.equals(")")
                        || followingCharacter.equals(lastCharacter) && followingCharacter.equals("]")
                        || followingCharacter.equals(lastCharacter) && followingCharacter.equals("}")){
                            styledText.replaceTextRange(caretOffset, 1, "");
                        }
                    }
                }else {
                    // if we type a double quotation mark and if the following character is also a double quotation mark,
                    // the cursor will go to the next position without inserting anything more.
                    if (caretOffset < source.length() && caretOffset != 0) {
                        String followingCharacter = String.valueOf(source.charAt(caretOffset));
                        if(followingCharacter.equals(lastCharacter) && followingCharacter.equals("\"")){
                            styledText.replaceTextRange(caretOffset, 1, "");
                        }
                    }
                    // if the delete key is pressed
                    if (verifyEvent.keyCode == 8) {
                        // if there is a character after the cursor and if the delete key is not pressed at the first
                        // possible position of the caret
                        if (caretOffset < source.length() && caretOffset != 0) {
                            String deletedCharacter = String.valueOf(source.charAt(caretOffset - 1));
                            String followingCharacter = String.valueOf(source.charAt(caretOffset));
                            if (deletedCharacter.equals("\"") && followingCharacter.equals("\"")) {
                                styledText.replaceTextRange(caretOffset, 1, "");
                            }
                        }
                    }
                }
            });
        }
        return viewer;
    }

    /**
     * Register the content assist action, so Ctrl+Space opens the completion.
     */
    @Override
    protected void createActions() {
        super.createActions();
        ResourceBundle noResources = new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[0][];
            }
        };
        IAction action = new TextOperationAction(noResources, "ContentAssistProposal.", this,
                ISourceViewer.CONTENTASSIST_PROPOSALS);
        action.setText("Content Assist");
        action.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
        setAction(CONTENT_ASSIST_ACTION, action);
        markAsStateDependentAction(CONTENT_ASSIST_ACTION, true);

        IAction format = new Action("Format") {
            @Override
            public void run() {
                format();
            }
        };
        format.setToolTipText("Indent the selected lines, or the whole script");
        format.setActionDefinitionId(FORMAT_COMMAND);
        setAction(FORMAT_ACTION, format);
        markAsStateDependentAction(FORMAT_ACTION, true);
    }

    @Override
    protected void editorContextMenuAboutToShow(IMenuManager menu) {
        super.editorContextMenuAboutToShow(menu);
        addAction(menu, ITextEditorActionConstants.GROUP_EDIT, FORMAT_ACTION);
    }

    /**
     * Indent the selected lines, or the whole script when nothing is selected. Only the blanks change.
     */
    public void format() {
        ISourceViewer viewer = getSourceViewer();
        if (viewer == null || !isEditable()) {
            return;
        }
        IDocument document = viewer.getDocument();
        ITextSelection selection = (ITextSelection) getSelectionProvider().getSelection();
        try {
            int firstLine = 0;
            int lastLine = document.getNumberOfLines() - 1;
            if (selection.getLength() > 0) {
                firstLine = selection.getStartLine();
                lastLine = selection.getEndLine();
                // A selection ending at the start of a line does not include this line
                if (lastLine > firstLine && document.getLineOffset(lastLine) == selection.getOffset() + selection.getLength()) {
                    lastLine--;
                }
            }
            GroovyFormatter.Result result = ((GroovySourceViewerConfiguration) getSourceViewerConfiguration())
                    .createFormatter().format(document, firstLine, lastLine);
            int start = document.getLineOffset(firstLine);
            int end = document.getLineOffset(lastLine) + document.getLineLength(lastLine);
            if (result.text().equals(document.get(start, end - start))) {
                return;
            }
            // The caret stays on its line, at the same place in the text of the line
            int caretLine = document.getLineOfOffset(selection.getOffset());
            IRegion caretRegion = document.getLineInformation(caretLine);
            String caretLineText = document.get(caretRegion.getOffset(), caretRegion.getLength());
            int indent = caretLineText.length() - caretLineText.stripLeading().length();
            int inText = Math.max(0, selection.getOffset() - caretRegion.getOffset() - indent);

            document.replace(start, end - start, result.text());

            if (selection.getLength() > 0) {
                int newEnd = start + result.text().length();
                selectAndReveal(start, newEnd - start);
            } else if (caretLine >= firstLine && caretLine <= lastLine) {
                int newLine = firstLine + result.lineMap()[caretLine - firstLine];
                IRegion region = document.getLineInformation(Math.min(newLine, document.getNumberOfLines() - 1));
                String text = document.get(region.getOffset(), region.getLength());
                int newIndent = text.length() - text.stripLeading().length();
                selectAndReveal(region.getOffset() + Math.min(newIndent + inText, text.length()), 0);
            }
        } catch (BadLocationException e) {
            LOGGER.error("Unable to format the script.", e);
        }
    }

    @Override
    public int promptToSaveOnClose() {
        return ISaveablePart2.YES;
    }

    @Override
    public void createPartControl(Composite parent) {
        Composite groovyEditor = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout(3, false);
        gl.verticalSpacing = 0;
        gl.horizontalSpacing = 0;
        gl.marginHeight = 0;
        gl.marginWidth = 0;
        groovyEditor.setLayout(gl);

        createControlsBar(groovyEditor);

        GroovyEditorControl editorControl = new GroovyEditorControl(groovyEditor, this);
        super.createPartControl(editorControl);
        editorControl.setLayoutData(new GridData(GridData.FILL_BOTH));

        checkedDocument = getDocument();
        if (checkedDocument != null) {
            checkedDocument.addDocumentListener(bracketListener);
            checkBrackets();
        }
    }

    @Override
    protected void initializeEditor() {
        super.initializeEditor();
        // The bracket highlighting has its own settings; the other ones are those of the text editors
        setPreferenceStore(new ChainedPreferenceStore(new IPreferenceStore[]{bracketPreferences(),
                EditorsUI.getPreferenceStore()}));
    }

    private static IPreferenceStore bracketPreferences() {
        PreferenceStore store = new PreferenceStore();
        store.setDefault(MATCHING_BRACKETS, true);
        store.setDefault(BRACKET_AT_CARET, true);
        store.setDefault(ENCLOSING_BRACKETS, false);
        boolean dark;
        try {
            dark = UIStyles.isDarkTheme();
        } catch (RuntimeException | LinkageError e) {
            dark = false;
        }
        PreferenceConverter.setDefault(store, MATCHING_BRACKETS_COLOR, dark ? new RGB(150, 150, 150) : new RGB(128, 128, 128));
        return store;
    }

    /**
     * The bracket matching the one at the cursor is boxed. The brackets of the strings and of the comments are
     * ignored (Groovy partitioning).
     */
    @Override
    protected void configureSourceViewerDecorationSupport(SourceViewerDecorationSupport support) {
        support.setCharacterPairMatcher(new DefaultCharacterPairMatcher(
                new char[]{'(', ')', '[', ']', '{', '}'}, GroovyPartitions.PARTITIONING, true));
        support.setMatchingCharacterPainterPreferenceKeys(MATCHING_BRACKETS, MATCHING_BRACKETS_COLOR,
                BRACKET_AT_CARET, ENCLOSING_BRACKETS);
        super.configureSourceViewerDecorationSupport(support);
    }

    /**
     * Mark the brackets without a matching one (a missing or extra bracket).
     */
    private void checkBrackets() {
        IAnnotationModel model = getAnnotationModel();
        IDocument document = getDocument();
        if (model == null || document == null || getSourceViewer() == null) {
            return;
        }
        bracketAnnotations.forEach(model::removeAnnotation);
        bracketAnnotations.clear();
        for (BracketChecker.Problem problem : BracketChecker.check(document)) {
            Annotation annotation = new Annotation(ERROR_ANNOTATION_TYPE, false, problem.message());
            model.addAnnotation(annotation, new Position(problem.offset(), 1));
            bracketAnnotations.add(annotation);
        }
    }

    private void createControlsBar(Composite editorPanel) {
        try {
            ImageDescriptor imageDescriptor = ImageDescriptor.createFromURL(new URL("platform:/plugin/org.orbisgis.groovyeditor/icons/groovy_script.png"));
            setTitleImage(imageDescriptor.createImage());
        } catch (MalformedURLException e) {
            LOGGER.warn("Unable to get Groovy script icon.", e);
        }

        VerticalFolder sideToolBar = new VerticalFolder(editorPanel, SWT.LEFT);
        sideToolBar.setCheckCommandEnablement(true);
        ((GridLayout)sideToolBar.getLayout()).marginTop = 3;
        ((GridLayout)sideToolBar.getLayout()).marginBottom = 10;
        ((GridLayout)sideToolBar.getLayout()).verticalSpacing = 3;

        VerticalButton.create(sideToolBar, SWT.LEFT | SWT.PUSH, getSite(), GroovyEditorCommands.CMD_EXECUTE_SELECTION, false);
        VerticalButton.create(sideToolBar, SWT.LEFT | SWT.PUSH, getSite(), GroovyEditorCommands.CMD_EXECUTE_SCRIPT, false);
        VerticalButton.create(sideToolBar, SWT.LEFT | SWT.PUSH, getSite(), GroovyEditorCommands.CMD_CLEAR, false);
        VerticalButton.create(sideToolBar, SWT.LEFT | SWT.PUSH, getSite(), GroovyEditorCommands.CMD_RESET_CONTEXT, false);
        UIUtils.createEmptyLabel(sideToolBar, 1, 1).setLayoutData(new GridData(GridData.FILL_VERTICAL));
        VerticalButton.create(sideToolBar, SWT.LEFT | SWT.PUSH, getSite(), GroovyEditorCommands.CMD_LIST_CLASSPATHS, false);

        GridLayout layout = (GridLayout)sideToolBar.getLayout();
        layout.marginTop = SIDE_TOOLBAR_MARGIN_TOP;
        layout.marginBottom = SIDE_TOOLBAR_MARGIN_BOTTOM;
        layout.verticalSpacing = SIDE_TOOLBAR_VERTICAL_SPACING;

        sideToolBar.setLayoutData(new GridData(GridData.FILL_VERTICAL | GridData.VERTICAL_ALIGN_BEGINNING));
    }

    private IDocument getDocument(){
        IDocumentProvider documentProvider = getDocumentProvider();
        if(documentProvider != null){
            return documentProvider.getDocument(getEditorInput());
        }
        return null;
    }

    public void clear(){
        IDocument document = getDocument();
        if(document != null){
            document.set("");
        }
    }

    /**
     * Execute the whole script.
     */
    public void execute(){
        IDocument document = getDocument();
        if(document != null){
            runScript(document.get(), 0, 0);
        }
    }

    /**
     * Execute the selected text, or the current line if nothing is selected.
     */
    public void executeSelection() {
        IDocument document = getDocument();
        if(document == null){
            return;
        }
        ITextSelection selection = (ITextSelection) getSelectionProvider().getSelection();
        String text = selection.getText();
        int start = selection.getOffset();
        try {
            if (text == null || text.isEmpty()) {
                IRegion line = document.getLineInformationOfOffset(selection.getOffset());
                text = document.get(line.getOffset(), line.getLength());
                start = line.getOffset();
            }
            if (!text.isBlank()) {
                int firstLine = document.getLineOfOffset(start);
                runScript(text, firstLine, start - document.getLineOffset(firstLine));
            }
        } catch (BadLocationException e) {
            LOGGER.warn("Unable to get the text to execute.", e);
        }
    }

    /**
     * @param script      the text to execute
     * @param firstLine   line of the document where the text starts (from 0), to locate the errors
     * @param firstColumn column of the document where the text starts (from 0)
     */
    private void runScript(String script, int firstLine, int firstColumn) {
        this.doSave(new NullProgressMonitor());
        clearErrors();
        GroovyOutputConsole.show();
        if (session.isRunning()) {
            GroovyConsoleContent.appendLine("A script is already running in " + getScriptName()
                    + ": stop it before running another one.", Style.ERROR);
            return;
        }
        job = new GroovyJob(session, getScriptName(), script);
        job.setErrorListener(errors -> Display.getDefault().asyncExec(() -> markErrors(errors, firstLine, firstColumn)));
        job.schedule();
    }

    /**
     * Underline the errors of the last execution in the editor and show the first one.
     */
    private void markErrors(List<ScriptError> errors, int firstLine, int firstColumn) {
        IDocument document = getDocument();
        IAnnotationModel model = getAnnotationModel();
        if (document == null || model == null) {
            return;
        }
        clearErrors();
        Position first = null;
        for (ScriptError error : errors) {
            try {
                int line = firstLine + error.line() - 1;
                if (line < 0 || line >= document.getNumberOfLines()) {
                    continue;
                }
                IRegion lineRegion = document.getLineInformation(line);
                int offset;
                int length;
                if (error.column() > 0) {
                    // Syntax error: from its column (the first line of a selection may not start at column 0)
                    int column = error.column() - 1 + (error.line() == 1 ? firstColumn : 0);
                    offset = lineRegion.getOffset() + Math.min(column, lineRegion.getLength());
                    int end = error.endLine() == error.line() && error.endColumn() > error.column()
                            ? offset + error.endColumn() - error.column()
                            : lineRegion.getOffset() + lineRegion.getLength();
                    length = Math.max(1, Math.min(end, lineRegion.getOffset() + lineRegion.getLength()) - offset);
                } else {
                    // Runtime error: the whole line, without its indentation
                    String text = document.get(lineRegion.getOffset(), lineRegion.getLength());
                    int indent = text.length() - text.stripLeading().length();
                    offset = lineRegion.getOffset() + indent;
                    length = text.strip().length();
                }
                Position position = new Position(offset, length);
                if (isBracketMarked(model, offset)) {
                    first = first == null ? position : first;
                    continue;
                }
                Annotation annotation = new Annotation(ERROR_ANNOTATION_TYPE, false, error.message());
                model.addAnnotation(annotation, position);
                errorAnnotations.add(annotation);
                if (first == null) {
                    first = position;
                }
            } catch (BadLocationException e) {
                LOGGER.trace("Error outside of the document", e);
            }
        }
        if (first != null && getSourceViewer() != null) {
            getSourceViewer().revealRange(first.getOffset(), first.getLength());
        }
    }

    private boolean isBracketMarked(IAnnotationModel model, int offset) {
        for (Annotation annotation : bracketAnnotations) {
            Position position = model.getPosition(annotation);
            if (position != null && position.getOffset() == offset) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove the errors of the last execution from the editor.
     */
    private void clearErrors() {
        IAnnotationModel model = getAnnotationModel();
        if (model != null) {
            errorAnnotations.forEach(model::removeAnnotation);
        }
        errorAnnotations.clear();
    }

    private IAnnotationModel getAnnotationModel() {
        return getDocumentProvider() == null ? null : getDocumentProvider().getAnnotationModel(getEditorInput());
    }

    private String getScriptName() {
        return getEditorInput() != null ? getEditorInput().getName() : getPartName();
    }

    /**
     * Stop the running script.
     */
    public void stop(){
        if (job != null) {
            job.cancel();
        }
    }

    /**
     * Forget the variables, imports and libraries of the Groovy context.
     */
    public void resetContext() {
        GroovyOutputConsole.show();
        clearErrors();
        if (session.reset()) {
            GroovyConsoleContent.appendLine("\n--- " + getScriptName() + ": context reset (variables, imports and @Grab libraries).", Style.INFO);
        } else {
            GroovyConsoleContent.appendLine("The context of " + getScriptName()
                    + " cannot be reset while a script is running.", Style.ERROR);
        }
    }

    public GroovyJob getRunningJob() {
        return job;
    }

    @Override
    public void dispose() {
        if (checkedDocument != null) {
            checkedDocument.removeDocumentListener(bracketListener);
        }
        Display display = Display.getCurrent();
        if (display != null) {
            display.timerExec(-1, bracketCheck);
        }
        stop();
        session.close();
        super.dispose();
    }

}
