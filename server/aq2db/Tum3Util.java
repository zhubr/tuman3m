/*
 * Copyright 2011-2021 Nikolai Zhubr <zhubr@rambler.ru>
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


import java.io.*;
import java.nio.*;


public class Tum3Util {

    private final static String const_default_raw_encoding = "ISO-8859-1";
    private final static String const_encoding_1251 = "windows-1251";

    private static byte[] rus_ucs16_to_1251 = {
        63,-88,63,63,63,63,63,63,63,63,63,63,63,63,63,63,-64,-63,-62,-61,-60,
        -59,-58,-57,-56,-55,-54,-53,-52,-51,-50,-49,-48,-47,-46,-45,-44,-43,
        -42,-41,-40,-39,-38,-37,-36,-35,-34,-33,-32,-31,-30,-29,-28,-27,-26,
        -25,-24,-23,-22,-21,-20,-19,-18,-17,-16,-15,-14,-13,-12,-11,-10,-9,
        -8,-7,-6,-5,-4,-3,-2,-1,63,-72
    };


    public static final String getStackTrace(final Throwable throwable) {

        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();

    }

    public static final String getStackTraceAuto() {

        return getStackTrace(new Exception("Stack trace"));

    }

    public static final String StringLenTrim(String s, int limit) {

        if (s == null) return "";
        if (s.length() <= limit) return s;
        return s.substring(0, limit) + "<...>";

    }

    public static final String StrMultilineToOneLine(String s) {

        return s.replace(HandyMisc.crlf, "\n").
                replace("\r", '\\' + "n").
                replace("\n", '\\' + "n");

    }

    public static final String IntToStr2(int i) {

        String result = "" + i;
        if (result.length() < 2) result = "0" + result;
        return result;

    }

    public static final boolean StrNumeric(String s) {
        for (int tmp_i = 0; tmp_i < s.length(); tmp_i++)
            if (((int)s.charAt(tmp_i) > (int)'9') || ((int)s.charAt(tmp_i) < (int)'0'))
                return false;
        return true;
    }

    public static final String JavaStrToTum3Str(String src) {

        try {
            return new String(src.getBytes(const_encoding_1251), const_default_raw_encoding);
        } catch (UnsupportedEncodingException e) {
            //System.out.println("[aq2j] WARNING: JavaStrToTum3Str failed, had to fallback.");
        }
        char[] tmp_arr = new char[src.length()];
        for (int i = 0; i < src.length(); i++) {
            char src_ch = src.charAt(i);
            short ch_uni = (short)src_ch;
            if ((ch_uni < 255) && (ch_uni >= 0)) tmp_arr[i] = src_ch;
            else {
                if ((ch_uni >= 1024) && (ch_uni <= (1023 + rus_ucs16_to_1251.length))) tmp_arr[i] = (char)rus_ucs16_to_1251[ch_uni-1024];
                else tmp_arr[i] = (char)63;
            }
        }
        return new String(tmp_arr); // src;

    }

    public static final byte[] StringToBytesRaw(String src) {

        try {
            return src.getBytes(const_default_raw_encoding);
        } catch (UnsupportedEncodingException e) {
            Tum3Logger.println("WARNING: The expected encoding missing, had to fallback.");
            int tmp_len = src.length();
            byte[] result = new byte[tmp_len];
            for (int i = 0; i < tmp_len; i++) result[i] = (byte)src.charAt(i);
            return result;
        }

    }

    public static final String BytesToStringRaw(byte[] src, int ofs, int count) {

        try {
            return new String(src, ofs, count, const_default_raw_encoding);
        } catch (UnsupportedEncodingException e) {
            Tum3Logger.println("WARNING: The expected encoding missing, had to fallback.");
            int tmp_len = count;
            char[] tmp_arr = new char[tmp_len];
            for (int i = 0; i < tmp_len; i++) tmp_arr[i] = (char)src[i+ofs];
            return new String(tmp_arr, 0, tmp_len);
        }

    }

    public static final String BytesToStringRaw(ByteBuffer src) {

        return BytesToStringRaw(src.array(), src.position(), src.remaining());

    }

    public static final String BytesToStringRaw(byte[] src) {

        return BytesToStringRaw(src, 0, src.length);

    }

    public static final String StrHexDump(String line) {

        StringBuffer tmp_dbg = new StringBuffer();
        for (int k=0; k < line.length(); k++) tmp_dbg.append(" " + Integer.toHexString(0xFF & (byte)line.charAt(k)));
        return tmp_dbg.toString();

    }

    public static final String StringToPasString(String s, int len) throws Exception {
        if (s.length() > len) throw new Exception("StringToPasString error: string does not fit to the specified size");
        StringBuffer tmp_st = new StringBuffer();
        tmp_st.append((char)s.length());
        tmp_st.append(s);
        while (tmp_st.length() < (len+1)) tmp_st.append(" ");
        return tmp_st.toString();
    }

    public static final byte[] StringToByteZ(String s) {
        if (s == null) return null;
        byte[] tmp_buff = new byte[s.length() + 1];
        for (int tmp_j = 0; tmp_j < s.length(); tmp_j++)
            tmp_buff[tmp_j] = (byte)s.charAt(tmp_j);
        tmp_buff[s.length()] = 0;
        return tmp_buff;
    }

    public static final String[] FetchAsStrings(byte[] src, int len, int max_num, int min_len) {

        return FetchAsStrings(src, len, max_num, min_len, 0);

    }

    public static final String[] FetchAsStrings(byte[] src, int len, int max_num, int min_len, int src_offset) {
        int tmp_i = 0;
        int tmp_num = 0;
        StringBuilder tmpStr = new StringBuilder();
        StringList tmp_list = new StringList();
        String[] tmp_result;

        do {
            int tmp_j = 0;
            tmpStr.delete(0, tmpStr.length());
            while (tmp_i < len) {
                if ((src[src_offset + tmp_i] != 0) || (tmp_j < min_len)) { tmpStr.append((char)src[src_offset + tmp_i]); tmp_j++; tmp_i++; }
                else break;
            }
            tmp_num++;
            tmp_list.add(tmpStr.toString());
            if (tmp_i < len) tmp_i++;
        } while ((tmp_num <= max_num) && (tmp_i < len));

        tmp_result = new String[tmp_num];
        for (tmp_i = 0; tmp_i < tmp_num; tmp_i++) tmp_result[tmp_i] = tmp_list.get(tmp_i);
        return tmp_result;
    }

    public static final void SleepExactly(int wait_millis) {
        long tmp_started_waiting_at = System.currentTimeMillis();
        int tmp_ms_remaining;
        boolean tmp_timeout = false;
        do {
            tmp_ms_remaining = (int)(tmp_started_waiting_at + wait_millis - System.currentTimeMillis());
            if (tmp_ms_remaining < 1) 
                tmp_ms_remaining = 1;
            try {
                Thread.sleep(tmp_ms_remaining);
            } catch (InterruptedException e) { }
            if ((System.currentTimeMillis() - tmp_started_waiting_at) > wait_millis)
                tmp_timeout = true;
        } while (!tmp_timeout);
    }

    public static final void WriteIniWithBak(String thePath, String the_ini_name, String theBody) throws Exception {

        WriteIniWithBak(thePath, the_ini_name, theBody, ".in0", ".ini");

    }

    public static final void WriteIniWithBak(String thePath, String the_ini_name, String theBody, String the_bak_ext, String the_new_ext) throws Exception {

        String tmp_name_bak = thePath + the_ini_name + the_bak_ext;
        String tmp_name_new = thePath + the_ini_name + the_new_ext;

        File tmp_file = new File(tmp_name_bak);
        if (tmp_file.exists()) tmp_file.delete();

        tmp_file = new File(tmp_name_new);
        if (tmp_file.exists()) tmp_file.renameTo(new File(tmp_name_bak));

        tmp_file = new File(tmp_name_new);
        FileOutputStream writer = null;
        try {
            writer = new FileOutputStream(tmp_file);
            byte[] tmp_b = StringToBytesRaw(theBody);
            writer.write(tmp_b, 0, tmp_b.length);
        } finally {
            if (writer != null) try {
                writer.close();
            } catch (Exception e) { 
                Tum3Logger.println("WARNING: WriteIniWithBak() raised in close: " + e);
            }
        }
    }

}
