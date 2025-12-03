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
package aq2j;


import java.io.File;
import java.io.FileInputStream;

import aq2db.*;


public class Tum3AppUpdateHelper {

    private final static int const_prog_fsize_mb_limit = 50;
    private String RealPath;
    private String RealBaseFName;
    private Object CheckLock = new Object();

    private boolean TriedToLoad = false;
    private long CurrStamp = 0;
    private int CurrVersion = 0;
    private byte[] CurrBody = null;

    private final static String TUM3_CFG_update_path_look4 = "update_path_look4";
    private final static String TUM3_CFG_update_path_look64 = "update_path_look64";
    private final static String TUM3_CFG_update_path_full = "update_path_full";
    private final static String TUM3_CFG_update_path_full64 = "update_path_full64";

    private static class LazyLook4AppHolder {
        public static Tum3AppUpdateHelper Instance = new Tum3AppUpdateHelper("Look4", TUM3_CFG_update_path_look4);
    }

    private static class LazyLook64AppHolder {
        public static Tum3AppUpdateHelper Instance = new Tum3AppUpdateHelper("Look64", TUM3_CFG_update_path_look64);
    }

    private static class LazyFullAppHolder {
        public static Tum3AppUpdateHelper Instance = new Tum3AppUpdateHelper("Aq2net", TUM3_CFG_update_path_full);
    }

    private static class LazyFull64AppHolder {
        public static Tum3AppUpdateHelper Instance = new Tum3AppUpdateHelper("Aq2net64", TUM3_CFG_update_path_full64);
    }

    public Tum3AppUpdateHelper(String base_fname, String cfg_var_name) {

        RealPath = Tum3cfg.getGlbParValue(cfg_var_name);
        RealBaseFName = base_fname;

    }

    public int GetVersion() {

        //System.out.println("[aq2j] Tum3AppUpdateHelper.GetVersion()");
        synchronized(CheckLock) { // XXX TODO!!! Make this lock shorter somehow.

            String tmp_ver_fname = RealPath + RealBaseFName + ".ver";
            if (RealPath.isEmpty()) {
                //System.out.println("[aq2j] WARNING: no path specified for " + RealBaseFName + " app update.");
                Tum3Logger.DoLogGlb(false, "WARNING: no path specified for " + RealBaseFName + " app update.");
                return 0;
            }
            File tmp_file = new File(tmp_ver_fname);
            long tmp_last_modified = tmp_file.lastModified();
            int  tmp_last_ver = 0;
            //System.out.println("[aq2j] GetVersion(): tmp_ver_fname=" + tmp_ver_fname + ", tmp_last_modified=" + tmp_last_modified);

            if ((tmp_last_modified == CurrStamp) && TriedToLoad) return CurrVersion;
            TriedToLoad = true;
            //System.out.println("[aq2j] GetVersion(): Really checking files...");

            FileInputStream tmp_stream = null;
            try {
                //System.out.println("[aq2j] GetVersion: <" + RealPath + RealBaseFName + ".ver" + ">");
                tmp_stream = new FileInputStream(tmp_file);
                byte[] buf_b = new byte[100];
                int numRead = tmp_stream.read(buf_b);
                if ((numRead > 0) && (numRead < 100)) {  
                    String readData = Tum3Util.BytesToStringRaw(buf_b, 0, numRead).trim();
                    tmp_last_ver = Integer.parseInt(readData);
                    //System.out.println("[aq2j] DEBUG: tmp_last_ver=<" + tmp_last_ver + ">");
                }
            } catch (Exception e) {
                Tum3Logger.DoLogGlb(false, "GetVersion(): " + RealBaseFName + ".ver file read exception: " + e + " (Hint: check permissions and/or file name case)");
            }
            if (tmp_stream != null) try {
                tmp_stream.close();
            } catch (Exception e) {
                Tum3Logger.DoLogGlb(true, "GetVersion(): " + RealBaseFName + ".ver file close exception: " + e);
            }

            if (tmp_last_ver > 0) {

                tmp_file = new File(RealPath + RealBaseFName + ".exe");
                long tmp_flen = tmp_file.length();
                if (tmp_file.isFile() && (tmp_flen > 0) && (tmp_flen < (const_prog_fsize_mb_limit * 1024*1024))) {
                    byte[] tmp_buff = new byte[(int)tmp_flen];

                    tmp_stream = null;
                    boolean tmp_success = false;
                    try {
                        tmp_stream = new FileInputStream(tmp_file);
                        int numRead = tmp_stream.read(tmp_buff);
                        if (numRead == tmp_flen) {  
                            //System.out.println("[aq2j] DEBUG: loaded app body =<" + numRead + "> bytes.");
                            CurrStamp = tmp_last_modified;
                            CurrVersion = tmp_last_ver;
                            CurrBody = tmp_buff;
                            tmp_success = true; // return CurrVersion;
                            Tum3Logger.DoLogGlb(false, "GetVersion(): loaded " + RealBaseFName + ".exe version " + CurrVersion);
                        }
                    } catch (Exception e) {
                        Tum3Logger.DoLogGlb(false, "GetVersion(): " + RealBaseFName + ".exe file read exception: " + e + " (Hint: check permissions and/or file name case)");
                    }
                    if (tmp_stream != null) try {
                        tmp_stream.close();
                    } catch (Exception e) {
                        Tum3Logger.DoLogGlb(true, "GetVersion(): " + RealBaseFName + ".exe file close exception: " + e);
                    }
                    if (tmp_success) return CurrVersion;
                }
            }

            return 0;
        }

    }

    public byte[] GetBody(int expected_version) {

        //System.out.println("[aq2j] Tum3AppUpdateHelper.GetBody()");
        synchronized(CheckLock) {
            if ((expected_version == CurrVersion) && (CurrVersion > 0)) return CurrBody;
            return null;
        }

    }
    public static Tum3AppUpdateHelper getLook4AppHolder() {

        return LazyLook4AppHolder.Instance;

    }

    public static Tum3AppUpdateHelper getLook64AppHolder() {

        return LazyLook64AppHolder.Instance;

    }

    public static Tum3AppUpdateHelper getFullAppHolder() {

        return LazyFullAppHolder.Instance;

    }

    public static Tum3AppUpdateHelper getFull64AppHolder() {

        return LazyFull64AppHolder.Instance;

    }

}
