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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.ViewPart;
import org.jkiss.dbeaver.ui.ActionUtils;
import org.jkiss.dbeaver.ui.UIUtils;
import org.orbisgis.core.logger.Logger;

/**
 * View displaying the output of the Groovy scripts.
 */
public class GroovyOutputConsole extends ViewPart {

	private static final Logger LOGGER = new Logger(GroovyOutputConsole.class);

	/** Id of the view, see plugin.xml (kept unchanged for the existing workspaces) */
	public static final String VIEW_ID = "org.jkiss.dbeaver.groovy.output";
	public static final String CLEAR_CMD_ID = GroovyEditorCommands.CMD_GROOVY_DELETE_OUTPUT;

	@Override
	public void createPartControl(Composite parent) {
		Composite group = UIUtils.createPlaceholder(parent, 1);
		group.setLayout(new FillLayout());
		GroovyConsoleContent.initialize(group);
		createContextMenu(GroovyConsoleContent.text);
	}

	@Override
	public void setFocus() {
		if (GroovyConsoleContent.isAvailable()) {
			GroovyConsoleContent.text.setFocus();
		}
	}

	/**
	 * Context menu of the output: copy, select all, clear. Copy and Select All are also bound to the usual
	 * shortcuts and to the Edit menu of DBeaver while the view is active.
	 */
	private void createContextMenu(StyledText control) {
		ISharedImages images = PlatformUI.getWorkbench().getSharedImages();

		IAction copyAction = new Action("Copy") {
			@Override
			public void run() {
				if (control.getSelectionCount() > 0) {
					control.copy();
				} else {
					// Nothing selected: copy the whole output
					String all = control.getText();
					if (!all.isEmpty()) {
						Clipboard clipboard = new Clipboard(control.getDisplay());
						try {
							clipboard.setContents(new Object[]{all}, new Transfer[]{TextTransfer.getInstance()});
						} finally {
							clipboard.dispose();
						}
					}
				}
			}
		};
		copyAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_COPY);
		copyAction.setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_COPY));

		IAction selectAllAction = new Action("Select All") {
			@Override
			public void run() {
				control.selectAll();
			}
		};
		selectAllAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_SELECT_ALL);

		IActionBars actionBars = getViewSite().getActionBars();
		actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(), copyAction);
		actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), selectAllAction);
		actionBars.updateActionBars();

		final MenuManager menuMgr = new MenuManager();
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(manager -> {
			copyAction.setText(control.getSelectionCount() > 0 ? "Copy" : "Copy All");
			copyAction.setEnabled(control.getCharCount() > 0);
			selectAllAction.setEnabled(control.getCharCount() > 0);
			manager.add(copyAction);
			manager.add(selectAllAction);
			manager.add(new Separator());
			manager.add(ActionUtils.makeCommandContribution(getSite(), CLEAR_CMD_ID));
		});
		control.setMenu(menuMgr.createContextMenu(control));
	}

	/**
	 * Open (or bring to front) the output view, without stealing the focus from the editor.
	 * Must be called from the UI thread.
	 */
	public static void show() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null || window.getActivePage() == null) {
			return;
		}
		try {
			window.getActivePage().showView(VIEW_ID, null, IWorkbenchPage.VIEW_VISIBLE);
		} catch (PartInitException e) {
			LOGGER.error("Unable to open the Groovy output view.", e);
		}
	}

	/**
	 * Style of a text written into the console.
	 */
	public enum Style {
		/** Standard output of the script */
		OUTPUT(SWT.NONE),
		/** Informative message from the console (script name, duration...) */
		INFO(SWT.COLOR_DARK_GRAY),
		/** Value returned by the script */
		RESULT(SWT.COLOR_DARK_BLUE),
		/** Successful end of a script */
		SUCCESS(SWT.COLOR_DARK_GREEN),
		/** Errors */
		ERROR(SWT.COLOR_RED);

		private final int systemColor;

		Style(int systemColor) {
			this.systemColor = systemColor;
		}
	}

	/**
	 * Content of the console. Every method can be called from any thread: the text is appended
	 * asynchronously in the UI thread, in the order of the calls.
	 */
	public static class GroovyConsoleContent {

		/** Maximum number of characters kept in the console, the oldest output is removed first */
		private static final int MAX_LENGTH = 1_000_000;

		static StyledText text = null;

		/**
		 * Initialize the console content.
		 *
		 * @param group A given composite
		 */
		public static void initialize(Composite group) {
			text = new StyledText(group, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
			text.setFont(JFaceResources.getTextFont());
		}

		static boolean isAvailable() {
			return text != null && !text.isDisposed();
		}

		/**
		 * Append a text into the console. Can be called from any thread.
		 *
		 * @param message the text to append
		 * @param style   the style of the text
		 */
		public static void append(String message, Style style) {
			if (message == null || message.isEmpty()) {
				return;
			}
			Display.getDefault().asyncExec(() -> appendInUIThread(message, style));
		}

		/**
		 * Append a text into the console immediately. Must be called from the UI thread.
		 */
		public static void appendInUIThread(String message, Style style) {
			if (message == null || message.isEmpty() || !isAvailable()) {
				return;
			}
			int start = text.getCharCount();
			text.append(message);
			if (style.systemColor != SWT.NONE) {
				StyleRange range = new StyleRange(start, message.length(),
						text.getDisplay().getSystemColor(style.systemColor), null);
				if (style == Style.ERROR || style == Style.SUCCESS) {
					range.fontStyle = SWT.BOLD;
				}
				text.setStyleRange(range);
			}
			int overflow = text.getCharCount() - MAX_LENGTH;
			if (overflow > 0) {
				text.replaceTextRange(0, overflow, "");
			}
			text.setTopIndex(text.getLineCount() - 1);
		}

		/**
		 * Append a line into the console.
		 */
		public static void appendLine(String message, Style style) {
			append(message + "\n", style);
		}

		/**
		 * Remove the console content.
		 */
		public static void clear() {
			Display.getDefault().asyncExec(() -> {
				if (isAvailable()) {
					text.setText("");
				}
			});
		}
	}
}
