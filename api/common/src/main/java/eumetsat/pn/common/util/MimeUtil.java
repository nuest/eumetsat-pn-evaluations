/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.common.util;

import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author gaubert?
 */
public class MimeUtil {

    /**
     * Get Mime type from a Filename
     *
     * @param aFilename
     * @return Mime type as a String
     */
    static final public String getMimeType(String aFilename) {
        String mime;

        if (aFilename == null) {
            throw new IllegalArgumentException("aFilename is null");
        }

        String extension = FilenameUtils.getExtension(aFilename);
        if (extension.equalsIgnoreCase("css")) {
            mime = "text/css";
        } else if (extension.equalsIgnoreCase("js")) {
            mime = "text/javascript";
        } else if (extension.equalsIgnoreCase("ico")) {
            mime = "image/x-icon";
        } else if (extension.equalsIgnoreCase(".svg")) {
            mime = "image/svg+xml";
        } else {
            mime = "application/octet-stream";
        }

        return mime;
    }

}
