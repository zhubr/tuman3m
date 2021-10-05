/*
 * Copyright 2011-2021 Nikolai Zhubr <zhubr@mail.ru>
 *
 * This file is provided under the terms of the GNU General Public
 * License version 2. Please see LICENSE file at the uppermost 
 * level of the repository.
 * 
 * Unless required by applicable law or agreed to in writing, this
 * software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OF ANY KIND.
 *
 */
package aq2db;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class StringList extends ArrayList<String> {


    public StringList(String[] src) {

        super();
        for (int i = 0; i < src.length; i++) add(src[i]);

    }

    public StringList() {

        super();

    }

    public static StringList readFromFile(String file_name) {

        File tmp_file = new File(file_name);

        StringList results = null; //new StringList();
        FileInputStream tmp_stream = null;
        try {
            //System.out.println("[aq2j] DEBUG: StringList.readFromFile: <" + file_name + ">");
            tmp_stream = new FileInputStream(tmp_file);
            byte[] buf_b = new byte[4096];
            //char[] buf_c = new char[4096];
            int numRead = 0;
            StringBuffer tmp_sb = new StringBuffer();
            while ((numRead = tmp_stream.read(buf_b)) > 0) {
                //for (int k = 0; k < numRead; k++) buf_c[k] = (char)buf_b[k];
                //String readData = new String(buf_c, 0, numRead);
                //String readData = new String(buf_b, 0, numRead, Charset.forName("ISO-8859-1"));
                String readData = Tum3Util.BytesToStringRaw(buf_b, 0, numRead);
                tmp_sb.append(readData);
            }
            results = new StringList(tmp_sb.toString().split("\r\n"));

            /*
System.out.println("[DEBUG]: readFromFile: lines=" + results.size());
if (results.size() >= 2) {
          String line = results.get(1); // null
System.out.println("[DEBUG]: readFromFile: <" + line + "><" + Tum3Util.StrHexDump(line) + ">");
}
             */
        } catch (IOException e) {
            //System.out.println("[aq2j] DEBUG: readFromFile: error <" + e.toString() + ">");
            //System.err.format("IOException: %s%n", e);
            results = new StringList();
        }
        if (tmp_stream != null) try {
            tmp_stream.close();
        } catch (IOException ignored) { }

        return results;
    }

    public String BuildString() {

        StringBuilder tmp_s = new StringBuilder();
        for (int i = 0; i < size(); i++) tmp_s.append(get(i) + HandyMisc.crlf);
        return tmp_s.toString();

    }

}
