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
package org.orbisgis.ui.editors.groovy.html;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.orbisgis.core.logger.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * View displaying an HTML page (a chart, a report...) produced by a Groovy script. There is one view per
 * title: showing a page with the same title again replaces its content.
 */
public class HtmlView extends ViewPart {

    private static final Logger LOGGER = new Logger(HtmlView.class);

    public static final String VIEW_ID = "org.orbisgis.ui.editors.groovy.htmlView";

    private Browser browser;
    private Label message;
    private String pendingUrl;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());
        try {
            // The browser engine is the one chosen in the DBeaver preferences (Edge or Internet Explorer on Windows)
            browser = new Browser(parent, SWT.NONE);
        } catch (SWTError e) {
            LOGGER.error("Unable to create the web browser.", e);
            message = new Label(parent, SWT.WRAP);
            message.setText("This view needs a web browser, which cannot be created here: " + e.getMessage()
                    + "\nOn Linux, install WebKitGTK (libwebkit2gtk-4.1).");
        }
        String secondaryId = getViewSite().getSecondaryId();
        if (secondaryId != null) {
            setPartName(titleOf(secondaryId));
        }
        if (pendingUrl != null) {
            show(pendingUrl);
        }
    }

    /**
     * Display a page.
     *
     * @param url URL of the page (a file URL)
     */
    public void show(String url) {
        if (browser == null || browser.isDisposed()) {
            pendingUrl = url;
            return;
        }
        // A changing query string forces the reload of a page which was rewritten at the same place
        browser.setUrl(url + (url.startsWith("file:") ? "?t=" + System.nanoTime() : ""));
    }

    @Override
    public void setFocus() {
        if (browser != null && !browser.isDisposed()) {
            browser.setFocus();
        } else if (message != null) {
            message.setFocus();
        }
    }

    /**
     * Open (or bring to front) the view of the given title. Must be called from the UI thread.
     */
    public static HtmlView open(IWorkbenchPage page, String title) throws PartInitException {
        String secondaryId = secondaryIdOf(title);
        IViewReference reference = page.findViewReference(VIEW_ID, secondaryId);
        HtmlView view = reference == null ? null : (HtmlView) reference.getView(true);
        if (view == null) {
            view = (HtmlView) page.showView(VIEW_ID, secondaryId, IWorkbenchPage.VIEW_VISIBLE);
        } else {
            page.bringToTop(view);
        }
        return view;
    }

    /**
     * The title is stored in the secondary id of the view (restored with the workspace), encoded since a
     * secondary id cannot contain some characters (':').
     */
    static String secondaryIdOf(String title) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(title.getBytes(StandardCharsets.UTF_8));
    }

    static String titleOf(String secondaryId) {
        try {
            return new String(Base64.getUrlDecoder().decode(secondaryId), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return secondaryId;
        }
    }
}
