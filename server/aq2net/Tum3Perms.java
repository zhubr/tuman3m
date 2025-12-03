/*
 * Copyright 2011-2024 Nikolai Zhubr <zhubr@rambler.ru>
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
package aq2net;


import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;


import aq2db.*;


public class Tum3Perms {

    private final static String TUM3_CFG_users_pwd_file = "users_pwd_file";
    private final static String TUM3_CFG_meta_pwd_file = "meta_pwd_file"; // YYY
    private final static String TUM3_CFG_users_perm_file = "users_perm_file";
    private final static String TUM3_CFG_guest_allowed = "guest_allowed";
    private final static String const_sect_Perms = "Perms";

    private final static int CONST_PERMN_PWD_OK   = 1;
    private final static int CONST_PERMN_ADMIN    = 2;
    private final static int CONST_PERMN_SIGNLIST = 4;
    private final static int CONST_PERMN_DENSITY  = 8;
    private final static int CONST_PERMN_ACQUIS   = 16;
    private final static int CONST_PERMN_UPLOAD   = 32;
    private final static int CONST_PERMN_HOTSTART = 64;
    private final static int CONST_PERMN_DO_UGC = 128;
    private final static int CONST_PERMN_ADD_TAGS = 256;
    private final static int CONST_PERMN_PUBLISH = 512;


    private int PermsHolder = 0;
    private boolean GuestAllowed = false;
    public final static String guest_username = "guest";
    private boolean upload_all_ids = false;
    private List<Integer> upload_ids = null;

    public static class Tum3UgcPerms {

        public final boolean isCommentingAllowed, isAddTagAllowed;

        public Tum3UgcPerms(boolean _CommentingAllowed, boolean _AddTagAllowed) {
            isCommentingAllowed = _CommentingAllowed;
            isAddTagAllowed = _AddTagAllowed;
        }

    }

    public static class Tum3UgcUserlist extends HashMap<String, Tum3UgcPerms> {
    }

    private static enum Tum3UserPerms {

        admin(CONST_PERMN_ADMIN),
        signlist(CONST_PERMN_SIGNLIST),
        density(CONST_PERMN_DENSITY),
        acquis(CONST_PERMN_ACQUIS),
        upload(CONST_PERMN_UPLOAD),
        hotstart(CONST_PERMN_HOTSTART),
        commenting(CONST_PERMN_DO_UGC),
        addtag(CONST_PERMN_ADD_TAGS),
        publishing(CONST_PERMN_PUBLISH);

        private int perm_as_number;

        private Tum3UserPerms(int _perm_as_number) {

            perm_as_number = _perm_as_number;

        }

    }

    private Tum3Perms(int thePerms, boolean _GuestAllowed, boolean _upload_all_ids, List<Integer> _upload_ids) {

        PermsHolder = thePerms;
        GuestAllowed = _GuestAllowed;
        upload_all_ids = _upload_all_ids;
        upload_ids = _upload_ids;
        //if (upload_ids != null) for (Integer id: upload_ids) { System.out.println("[DEBUG] id=" + id); }

    }


    public boolean isPwdOk() { return (PermsHolder & CONST_PERMN_PWD_OK) != 0; }
    public boolean isAdmin() { return (PermsHolder & CONST_PERMN_ADMIN) != 0; }
    public boolean isSignListEditingAllowed() { return (PermsHolder & (CONST_PERMN_SIGNLIST | CONST_PERMN_ADMIN)) != 0; }
    public boolean isDensityEditingAllowed() { return (PermsHolder & (CONST_PERMN_DENSITY| CONST_PERMN_ADMIN)) != 0; }
    public boolean isCommentingAllowed() { return (PermsHolder & (CONST_PERMN_DO_UGC| CONST_PERMN_ADMIN)) != 0; }
    public boolean isPublishingAllowed() { return (PermsHolder & (CONST_PERMN_PUBLISH| CONST_PERMN_ADMIN)) != 0; }
    public boolean isAddTagAllowed() { return (PermsHolder & (CONST_PERMN_ADD_TAGS| CONST_PERMN_ADMIN)) != 0; }
    public boolean isHotstartUploadAllowed() { return (PermsHolder & (CONST_PERMN_HOTSTART| CONST_PERMN_ADMIN)) != 0; }
    public boolean isAcquisControlAllowed() { return (PermsHolder & (CONST_PERMN_ACQUIS| CONST_PERMN_ADMIN)) != 0; }
    public boolean isSignalUploadAllowed(int _id) {
        if ((PermsHolder & (CONST_PERMN_UPLOAD| CONST_PERMN_ADMIN)) == 0) return false;
        if ((PermsHolder & CONST_PERMN_ADMIN) != 0) return true;
        if (upload_all_ids) return true;
        if ((upload_ids != null) && (_id > 0)) if (upload_ids.indexOf(_id) >= 0) return true;
        return false;
    }

    public boolean isGuestAllowed() { return GuestAllowed; }

    public static boolean CheckPwdIsCorrect(int _db_idx, String _DbName, String username, String userpwd) {

        return CheckPwdIsCorrectExt(_db_idx, _DbName, username, userpwd, Tum3cfg.getParValue(_db_idx, true, TUM3_CFG_users_pwd_file));

    }

    public static boolean CheckMetaPwdIsCorrect(int _db_idx, String _DbName, String username, String userpwd) {

        String tmp_pwd_filename = Tum3cfg.getParValue(_db_idx, true, TUM3_CFG_meta_pwd_file);
        if (tmp_pwd_filename.isEmpty()) tmp_pwd_filename = Tum3cfg.getParValue(_db_idx, true, TUM3_CFG_users_pwd_file); // YYY XXX Eventually remove this?
        return CheckPwdIsCorrectExt(_db_idx, _DbName, username, userpwd, tmp_pwd_filename);

    }

    private static boolean CheckPwdIsCorrectExt(int _db_idx, String _DbName, String username, String userpwd, String pwd_file_name) {

            boolean tmp_pwd_scan_ok = false;
            boolean tmp_pwd_correct = false;

            if (!pwd_file_name.isEmpty()) {
                File tmp_file = new File(pwd_file_name);
                FileInputStream tmp_stream = null;
                InputStreamReader tmp_reader = null;
                BufferedReader buff_in = null;
                try {
                    //System.out.println("[DEBUG] CheckPwdIsCorrect: opening <" + pwd_file_name + ">");
                    tmp_stream = new FileInputStream(tmp_file);
                    tmp_reader = new InputStreamReader(tmp_stream, "UTF-8");
                    buff_in = new BufferedReader(tmp_reader);
                    //System.out.println("[DEBUG] CheckPwdIsCorrect: ok. opened <" + pwd_file_name + ">");
                    String tmp_st;
                    while ((tmp_st = buff_in.readLine()) != null) {
                        //System.out.println("[DEBUG] CheckPwdIsCorrect: checking <" + tmp_st + ">");
                        String tmp_pwd_str = tmp_st.trim();
                        int tmp_colon = tmp_pwd_str.indexOf(":");
                        //System.out.println("[DEBUG] CheckPwdIsCorrect: <" + username + ":" + tmp_pwd_str.substring(0, tmp_colon) + ">");
                        if ((tmp_colon >= 1) && (tmp_colon < (tmp_pwd_str.length() - 2)))
                            if (tmp_pwd_str.substring(0, tmp_colon).equals(username)) {
                                String tmp_stored_pwd = tmp_pwd_str.substring(tmp_colon+1);
                                int tmp_salt_end = tmp_stored_pwd.length() - 1;
                                while ((tmp_salt_end > 0) && (tmp_stored_pwd.charAt(tmp_salt_end) != '$')) tmp_salt_end--;
                                String storedSalt = tmp_stored_pwd.substring(0, tmp_salt_end); 
                                //System.out.println("[DEBUG] CheckPwdIsCorrect: FOUND! <" + tmp_stored_pwd + ">, salt=" + storedSalt);
                                //String userpwd = "95627823", storedSalt = "$apr1$uYr70YmM$", tmp_stored_pwd = "ZPKbfFE9TYdF3N7/iPTys/";
                                String tmp_test = Md5Crypt.apr1Crypt(userpwd, storedSalt);
                                //System.out.println("stored=<" + tmp_stored_pwd + "> calc=<" + tmp_test + ">");
                                tmp_pwd_correct = tmp_stored_pwd.equals(tmp_test);
                                break;
                            }
                    }
                    tmp_pwd_scan_ok = true;
                } catch (Exception ignored) {
                }
                if (buff_in != null) try {
                    buff_in.close();
                } catch (Exception e) {
                    Tum3Logger.DoLog(_DbName, true, "CheckPwdIsCorrect: " + Tum3Util.getStackTrace(e));
                }
                if (tmp_reader != null) try {
                    tmp_reader.close();
                } catch (Exception e) {
                    Tum3Logger.DoLog(_DbName, true, "CheckPwdIsCorrect: " + Tum3Util.getStackTrace(e));
                }
                if (tmp_stream != null) try {
                    tmp_stream.close();
                } catch (Exception e) {
                    Tum3Logger.DoLog(_DbName, true, "CheckPwdIsCorrect: " + Tum3Util.getStackTrace(e));
                }
            }

            if (!tmp_pwd_scan_ok) {
                Tum3Logger.DoLog(_DbName, true, "WARNING: password file scan failed (Please check '" + TUM3_CFG_users_pwd_file + "' parameter)");
            }

            return tmp_pwd_correct; // YYY
    }

    public static Tum3UgcUserlist getUgcUserlist(int _db_idx, String _DbName) {

        Tum3UgcUserlist tmp_result = new Tum3UgcUserlist();
        String perm_file_name = Tum3cfg.getParValue(_db_idx, true, TUM3_CFG_users_perm_file);
        if (!perm_file_name.isEmpty()) {
            StringList tmp_perm_list = StringList.readFromFile(perm_file_name);
            NameValueList ini_sect_puff = new NameValueList();
            ini_sect_puff.FillFrom(tmp_perm_list, const_sect_Perms, true);
            for (int i = 0; i < ini_sect_puff.Count(); i++) {
                String username = ini_sect_puff.GetName(i);
                String tmp_perms_str = ini_sect_puff.GetBody(i).trim();
                //Tum3Logger.DoLogGlb(true, "[DEBUG] getUgcUserlist: username=<" + username + "> perms=<" + tmp_perms_str + ">");
                List<String> tmp_perms_list = Arrays.asList(tmp_perms_str.split(","));
                //for (Tum3UserPerms tmp_perm: Tum3UserPerms.values())
                boolean tmp_isCommentingAllowed = false, tmp_isAddTagAllowed = false;
                if (tmp_perms_list.indexOf(Tum3UserPerms.commenting.name()) >= 0)
                    tmp_isCommentingAllowed = true;
                if (tmp_perms_list.indexOf(Tum3UserPerms.addtag.name()) >= 0)
                    tmp_isAddTagAllowed = true;
                tmp_result.put(username, new Tum3UgcPerms(tmp_isCommentingAllowed, tmp_isAddTagAllowed));
            }
        }
        return tmp_result;

    }

    public static Tum3Perms CheckUserPwdAndPerms(int _db_idx, String _DbName, String username, String userpwd) {

        boolean tmp_pwd_correct = false;
        boolean tmp_perm_scan_ok = false;
        int tmp_perm_int = 0;
        boolean tmp_guest_allowed = false;
        boolean tmp_upload_all_ids = false;
        List<Integer> tmp_upload_ids = null;

        if (!guest_username.equals(username)) {

            tmp_pwd_correct = CheckPwdIsCorrect(_db_idx, _DbName, username, userpwd);

        }

        if (tmp_pwd_correct) tmp_perm_int |= CONST_PERMN_PWD_OK;

        if (tmp_pwd_correct) {
            String perm_file_name = Tum3cfg.getParValue(_db_idx, true, TUM3_CFG_users_perm_file);
            if (!perm_file_name.isEmpty()) {
                StringList tmp_perm_list = StringList.readFromFile(perm_file_name);
                NameValueList ini_sect_puff = new NameValueList(username);
                ini_sect_puff.FillFrom(tmp_perm_list, const_sect_Perms, false);
                String tmp_perms_str = ini_sect_puff.GetValueFor(username, "").trim();
                //System.out.println("[DEBUG] CheckUserPwdAndPerms: perms=<" + tmp_perms_str + ">");
                //Tum3Logger.DoLogGlb(true, "[DEBUG] getUgcUserlist: username=<" + username + "> perms=<" + tmp_perms_str + ">");
                String[] tmp_keywords = tmp_perms_str.split(",");
                String[] tmp_aux_values = new String[tmp_keywords.length];
                for (int tmp_i = 0; tmp_i < tmp_keywords.length; tmp_i++) {
                    if (tmp_keywords[tmp_i].contains(":")) {
                        String[] tmp_parts = tmp_keywords[tmp_i].split(":");
                        tmp_keywords[tmp_i] = tmp_parts[0];
                        tmp_aux_values[tmp_i] = tmp_parts[1];
                    } else
                        tmp_aux_values[tmp_i] = "";
                }
                List<String> tmp_perms_list = Arrays.asList(tmp_keywords);
                List<String> tmp_perms_opts_list = Arrays.asList(tmp_aux_values);
                for (Tum3UserPerms tmp_perm: Tum3UserPerms.values()) {
                    int tmp_index = tmp_perms_list.indexOf(tmp_perm.name());
                    if (tmp_index >= 0) {
                        tmp_perm_int |= tmp_perm.perm_as_number;
                        if (tmp_perm == Tum3UserPerms.upload) {
                            String tmp_upl_list_str = tmp_perms_opts_list.get(tmp_index);
                            //System.out.println("[DEBUG] upload=<" + tmp_upl_list_str + ">");
                            if (tmp_upl_list_str.trim().isEmpty()) tmp_upload_all_ids = true;
                            else {
                                String[] tmp_upl_sig_list = tmp_upl_list_str.split("/");
                                int[] tmp_arr_upload_ids = new int[tmp_upl_sig_list.length];
                                boolean tmp_warning = false;
                                for (int tmp_j = 0; tmp_j < tmp_upl_sig_list.length; tmp_j++) {
                                    try {
                                        tmp_arr_upload_ids[tmp_j] = 0;
                                        tmp_arr_upload_ids[tmp_j] = Integer.parseInt(tmp_upl_sig_list[tmp_j]);
                                    } catch (Exception e) {
                                        tmp_warning = true;
                                    }
                                }
                                tmp_upload_ids = new ArrayList<Integer>(tmp_arr_upload_ids.length);
                                for (int tmp_j: tmp_arr_upload_ids) tmp_upload_ids.add(tmp_j);
                                if (tmp_warning)
                                    Tum3Logger.DoLog(_DbName, false, "WARNING: allowed upload id list is invalid (" + tmp_upl_list_str + ")");
                            }
                        } else {
                            if (!tmp_perms_opts_list.get(tmp_index).isEmpty())
                                Tum3Logger.DoLog(_DbName, false, "WARNING: permission parameters for '" + tmp_perms_list.get(tmp_index) + "' unexpected, ignored");
                        }
                    }
                }
                //System.out.println("[DEBUG] tmp_perm_int=" + tmp_perm_int);
                if (tmp_perm_list.size() > 0) tmp_perm_scan_ok = true;
            }
            //if (!tmp_perm_scan_ok) {
            //    Tum3Logger.DoLog(_DbName, true, "WARNING: permissions file scan failed (Please check '" + TUM3_CFG_users_perm_file + "' parameter)");
            //}
        } else {
            tmp_guest_allowed = "1".equals(Tum3cfg.getParValue(_db_idx, true, TUM3_CFG_guest_allowed).trim());
        }

        return new Tum3Perms(tmp_perm_int, tmp_guest_allowed, tmp_upload_all_ids, tmp_upload_ids);

    }

}
