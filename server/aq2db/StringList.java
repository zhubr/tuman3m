/*
 * Copyright 2011-2023 Nikolai Zhubr <zhubr@rambler.ru>
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Collections;


public class StringList extends ArrayList<String> {

    private final static ShotNumComparator shotNumComp = new ShotNumComparator();

    private static class ShotNumComparator implements Comparator<String> {

        public int compare(String o1, String o2) {

            //return o1.compareTo(o2);
            return compareAsShots(o1, o2); // YYY

        }
    }

    public static final int compareAsShots(String s1, String s2) {
        // compareAsShots(bup_last_seen_syn_shot, tmp_name)
        //return bup_last_seen_syn_shot.compareTo(tmp_name);

        //return s1.compareTo(s2);

        // Use custom compare to correctly sort also for longer shot numbers.
        if (s1.length() < s2.length()) return -1; // YYY
        else if (s1.length() > s2.length()) return 1; // YYY
        else return s1.compareTo(s2);

    }

    public void SortAsShots() { // YYY

        //Collections.sort(this);
        sort(shotNumComp); // YYY

    }

    public StringList(String[] src, boolean trim_lf) {

        super();
        for (int i = 0; i < src.length; i++) {
            String tmp_st = src[i];
            if (trim_lf && tmp_st.endsWith("\r")) tmp_st = tmp_st.substring(0, tmp_st.length() - 1); // YYY
            add(tmp_st);
        }

    }

    public StringList(String[] src) {

        this(src, false);

    }


    public StringList dup() {

        StringList tmp_new = new StringList();
        for (int i = 0; i < size(); i++) tmp_new.add(get(i));
        return tmp_new;

    }

    public StringList() {

        super();

    }

    public static StringList readFromFile(String file_name) {

        return readFromFile(new File(file_name));

    }

    public static StringList readFromFile(File tmp_file) {

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
            results = new StringList(tmp_sb.toString().split("\n"), true); // YYY

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

    public boolean writeToFile(String tmp_name_new) throws Exception {

        return writeToFile(new File(tmp_name_new));

    }

    public boolean writeToFile(File tmp_file) throws Exception {

        boolean tmp_ok = false;
        FileOutputStream writer = null;
        try {
            writer = new FileOutputStream(tmp_file);
            byte[] tmp_b = Tum3Util.StringToBytesRaw(BuildString());
            writer.write(tmp_b, 0, tmp_b.length);
        } finally {
            if (writer != null) try {
                writer.close();
                tmp_ok = true;
            } catch (Exception ignored) {}
        }
        return tmp_ok;

    }

    public String BuildString() {

        StringBuilder tmp_s = new StringBuilder();
        for (int i = 0; i < size(); i++) tmp_s.append(get(i) + HandyMisc.crlf);
        return tmp_s.toString();

    }

}
