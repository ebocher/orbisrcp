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
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.orbisgis.core.logger.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
            installDownloads(browser);
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
     * The embedded browser cannot download files: the download links of the pages (Save as PNG of the charts,
     * also in the pages written by demat) go through {@link DownloadBridge} to a Save As dialog.
     */
    private void installDownloads(Browser browser) {
        new BrowserFunction(browser, DownloadBridge.FUNCTION) {
            @Override
            public Object function(Object[] arguments) {
                if (arguments.length >= 2 && arguments[0] instanceof String name && arguments[1] instanceof String url) {
                    // Not in the JavaScript call: a modal dialog opened there can block the browser engine
                    browser.getDisplay().asyncExec(() -> {
                        if (!browser.isDisposed()) {
                            save(name, url);
                        }
                    });
                }
                return null;
            }
        };
        browser.addProgressListener(new ProgressAdapter() {
            @Override
            public void completed(ProgressEvent event) {
                browser.execute(DownloadBridge.SCRIPT);
            }
        });
    }

    private void save(String name, String dataUrl) {
        FileDialog dialog = new FileDialog(getSite().getShell(), SWT.SAVE);
        dialog.setFileName(name);
        String extension = DownloadBridge.extension(name);
        if (!extension.isEmpty()) {
            dialog.setFilterExtensions(new String[]{"*." + extension, "*"});
        }
        dialog.setOverwrite(true);
        String path = dialog.open();
        if (path == null) {
            return;
        }
        try {
            Files.write(Path.of(path), DownloadBridge.decode(dataUrl));
        } catch (Exception e) {
            LOGGER.error("Unable to save " + path, e);
            MessageDialog.openError(getSite().getShell(), "Save", "Unable to save " + path + ": " + e.getMessage());
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
