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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Downloads of the pages displayed in the HTML views (Save as PNG / SVG of the charts...).
 *
 * The browser embedded in DBeaver cannot download files: clicking a download link can freeze DBeaver. A script
 * installed in each page intercepts the download links and gives the file to Java ({@code orbisSave}), which
 * asks where to save it.
 */
public final class DownloadBridge {

    /** Name of the JavaScript function implemented in Java */
    public static final String FUNCTION = "orbisSave";

    /**
     * Script intercepting the clicks on the download links ({@code <a download>}). vega-embed sets the address of
     * its export links asynchronously, when the mouse button is pressed: the script waits for it. Blob addresses
     * (SVG export) are read in the page, since Java cannot read them.
     */
    public static final String SCRIPT = """
            (function () {
              if (window.__orbisDownloads) { return; }
              window.__orbisDownloads = true;
              function send(name, url) {
                if (url.startsWith('data:')) { window.orbisSave(name, url); return; }
                fetch(url).then(function (r) { return r.blob(); }).then(function (blob) {
                  var reader = new FileReader();
                  reader.onload = function () { window.orbisSave(name, reader.result); };
                  reader.readAsDataURL(blob);
                });
              }
              document.addEventListener('click', function (event) {
                var link = event.target && event.target.closest ? event.target.closest('a[download]') : null;
                if (!link || typeof window.orbisSave !== 'function') { return; }
                event.preventDefault();
                event.stopPropagation();
                var attempts = 0;
                (function wait() {
                  var url = link.getAttribute('href') || '';
                  if (url && url !== '#') { send(link.download || 'download', link.href); }
                  else if (attempts++ < 100) { setTimeout(wait, 50); }
                })();
              }, true);
            })();
            """;

    private DownloadBridge() {
    }

    /**
     * @param dataUrl a data URL: {@code data:[type][;base64],content}
     * @return the content of the URL
     */
    public static byte[] decode(String dataUrl) {
        if (!dataUrl.startsWith("data:")) {
            throw new IllegalArgumentException("Not a data URL");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Invalid data URL");
        }
        String header = dataUrl.substring(5, comma);
        String content = dataUrl.substring(comma + 1);
        if (header.endsWith(";base64")) {
            return Base64.getDecoder().decode(content);
        }
        return URLDecoder.decode(content, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return the extension of a file name (without the point), or an empty string
     */
    public static String extension(String fileName) {
        int point = fileName.lastIndexOf('.');
        return point < 0 ? "" : fileName.substring(point + 1);
    }
}
