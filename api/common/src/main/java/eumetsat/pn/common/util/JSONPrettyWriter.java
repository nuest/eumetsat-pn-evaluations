/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eumetsat.pn.common.util;

import java.io.StringWriter;

/**
* Based on:
* 
* https://code.google.com/p/json-simple/issues/detail?id=22
* * https://code.google.com/p/json-simple/issues/attachmentText?id=22&aid=220009000&name=JSONPrettyWriter.java&token=ABZ6GAf65wpbYnb4gsKa7ZilNBon2VqOJw%3A1416392298768
* 
* 
* @author Elad Tabak
* @since 28-Nov-2011
* @version 0.1
* 
* Changed class name from JSONWriter.
*
*/
public class JSONPrettyWriter extends StringWriter {

    private int indent = 0;

    @Override
    public void write(int c) {
        if (((char)c) == '[' || ((char)c) == '{') {
            super.write(c);
            super.write('\n');
            indent++;
            writeIndentation();
        } else if (((char)c) == ',') {
            super.write(c);
            super.write('\n');
            writeIndentation();
        } else if (((char)c) == ']' || ((char)c) == '}') {
            super.write('\n');
            indent--;
            writeIndentation();
            super.write(c);
        } else {
            super.write(c);
        }

    }

    private void writeIndentation() {
        for (int i = 0; i < indent; i++) {
            super.write("   ");
        }
    }
}
