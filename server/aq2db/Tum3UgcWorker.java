/*
 * Copyright 2022-2023 Nikolai Zhubr <zhubr@mail.ru>
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

import aq2net.Tum3Broadcaster;
import aq2net.GeneralDbDistribEvent;


public class Tum3UgcWorker {

    private Tum3Db owner_db;
    private String DB_ROOT_PATH_UGC;
    private int CONST_MAX_UGC_UPD = 50;
    private final int CONST_UGC_WAKEUP_MILLIS = 500;
    private final int CONST_UGC_ATTEMPT_LIMIT = 8;

    private final static String TUM3_CFG_db_root_ugc = "db_root_ugc";
    private final static String TUM3_CFG_max_ugc_upd = "max_ugc_upd";

    private boolean ugc_enabled = false;
    private ShotNameHolder lock_list[];
    private int lock_count = 0;
    private Object ugc_lock = new Object();
    private StringList known_tags;
    private Object known_tags_lock = new Object();
    private boolean now_saving_tags_list = false;


    private static class ShotNameHolder {

        private final static int CONST_SHOT_NAME_LIMIT = 12;
        private char[] name_arr = new char[CONST_SHOT_NAME_LIMIT];
        private int name_len = 0;
        public boolean in_writer = false;
        public int curr_readers = 0;

        public void FillWith(String new_name, boolean _reader_only) {

            name_len = new_name.length();
            for (int i = 0; i < name_len; i++) name_arr[i] = new_name.charAt(i);
            if (_reader_only) curr_readers++;
            else in_writer = true;

        }

        public void Reset(boolean _reader_only) {

            if (_reader_only) curr_readers--;
            else in_writer = false;
            if ((0 == curr_readers) && !in_writer) name_len = 0;

        }

        public boolean isEmpty() {

            return (name_len == 0);

        }

        public boolean NameMatches(String new_name) {

            if (0 == name_len) return false;
            if (name_len != new_name.length()) return false;
            for (int i = 0; i < name_len; i++) if (name_arr[i] != new_name.charAt(i)) return false;
            return true;

        }

    }

    protected Tum3UgcWorker(Tum3Db _owner_db) {

        owner_db = _owner_db;

        DB_ROOT_PATH_UGC = Tum3cfg.getParValue(owner_db.getIndex(), false, TUM3_CFG_db_root_ugc);
        if (!DB_ROOT_PATH_UGC.isEmpty()) if (new File(DB_ROOT_PATH_UGC).isDirectory()) {

            CONST_MAX_UGC_UPD = Tum3cfg.getIntValue(owner_db.getIndex(), true, TUM3_CFG_max_ugc_upd, CONST_MAX_UGC_UPD);

            lock_list = new ShotNameHolder[CONST_MAX_UGC_UPD];
            for (int i = 0; i < CONST_MAX_UGC_UPD; i++) lock_list[i] = new ShotNameHolder();

            String tmp_load_from;
            if (new File(DB_ROOT_PATH_UGC + "0001.104").isFile())
                tmp_load_from = DB_ROOT_PATH_UGC + "0001.104";
            else
                tmp_load_from = DB_ROOT_PATH_UGC + "0001.103";
            known_tags = StringList.readFromFile(tmp_load_from);

            ugc_enabled = true;
//System.out.println("[DEBUG] Ugc enabled with tag count=" + known_tags.size());
        }

    }

    private byte[] ReadFileIntl(File inp_file) throws Exception {

        long tmp_length_l = inp_file.length();
        if ((tmp_length_l > 0) && (tmp_length_l < 2000000)) {
            int tmp_length = (int)tmp_length_l;
            FileInputStream tmp_stream = null;
            try {
                tmp_stream = new FileInputStream(inp_file);
                byte[] buf_b = new byte[tmp_length];
                int numRead = 0;
                while (numRead < tmp_length) {
                    int tmp_inc = tmp_stream.read(buf_b, numRead, (tmp_length - numRead));
                    if (tmp_inc > 0) numRead += tmp_inc;
                    else break;
                }
                if (numRead == tmp_length) return buf_b;
                else throw new Exception("Read error");
            } finally {
                if (null != tmp_stream) try {
                    tmp_stream.close();
                } catch (Exception ignored) { }
            }
        }
        return null;

    }

    public String GetUgcData(byte thrd_ctx, UgcReplyHandler the_link, int _req_id, String _shot_name) throws Exception {

        if (!ugc_enabled) return "UGC subsystem not enabled or failed to start";
        if (-1 == _req_id) return GetShotUgc(thrd_ctx, the_link, _shot_name);
        else if (-3 == _req_id) return GetUgcTagList(thrd_ctx, the_link); // YYY
        return "Unknown request";
    }

    private String GetUgcTagList(byte thrd_ctx, UgcReplyHandler the_link) throws Exception {

        StringList tmp_known_tags_copy;
        synchronized(known_tags_lock) {
            tmp_known_tags_copy = known_tags;
        }
        byte[] tmp_b = Tum3Util.StringToBytesRaw(tmp_known_tags_copy.BuildString());
        the_link.GenerateUgcReply(thrd_ctx, -3, "", "", tmp_b);

        return "";

    }

    private String GetShotUgc(byte thrd_ctx, UgcReplyHandler the_link, String _shot_name) throws Exception {

//System.out.println("[DEBUG] Ugc get in shot " + _shot_name);

        byte[] tmp_buff = null;
        int tmp_lock_pos = -1;
        if ((_shot_name.length() < 8) || (_shot_name.length() > 9)) return "Unexpected shot name length " + _shot_name.length();
        String tmpSubdirName = _shot_name.substring(0, 4);

        tmp_lock_pos = TryToLockShot(_shot_name, true);
        if (tmp_lock_pos >= 0) {
            try {
                File tmp_file_newer = new File(DB_ROOT_PATH_UGC + tmpSubdirName + File.separator + _shot_name + ".102");
                File tmp_file_older = new File(DB_ROOT_PATH_UGC + tmpSubdirName + File.separator + _shot_name + ".101");
                if (tmp_file_newer.exists()) tmp_buff = ReadFileIntl(tmp_file_newer);
                else if (tmp_file_older.exists()) tmp_buff = ReadFileIntl(tmp_file_older);
            } finally {
                TryToReleaseShot(tmp_lock_pos, true);
            }
        } else {
            return "Too busy to read the file now, sorry";
        }

        the_link.GenerateUgcReply(thrd_ctx, -1, _shot_name, "", tmp_buff);

        return "";

    }

    public String UpdateUgcData(byte thrd_ctx, UgcReplyHandler the_link, String UserName, boolean UserCanAddTags, int _req_id, String _shot_name, byte[] _upd_arr) throws Exception {

        String tmp_result_msg = "Unknown error";
        boolean tmp_update_tag_list = false;
        boolean tmp_we_locked_tag_list = false;
        int tmp_lock_pos = -1;
        StringList tmp_updated_tag_list;

//System.out.println("[DEBUG] Ugc update in shot " + _shot_name + " id " + _req_id);

        if (!ugc_enabled) return "UGC subsystem not enabled or failed to start";
        if (_shot_name.length() > ShotNameHolder.CONST_SHOT_NAME_LIMIT) return "Shot name exceeds CONST_SHOT_NAME_LIMIT";
        if (_shot_name.length() < 8) return "Shot name is too small"; // YYY
        if (!Tum3Util.StrNumeric(_shot_name.substring(6))) return "Shot name is not valid for common storage"; // YYY

        //StringList tmp_upd_strs = new StringList(Tum3Util.BytesToStringRaw(_upd_arr, 0, _upd_arr.length).split("\r\n"));
        Tum3UgcUpdParser tmp_upd_parser = new Tum3UgcUpdParser(UserName, new StringList(Tum3Util.BytesToStringRaw(_upd_arr, 0, _upd_arr.length).split("\r\n")));

        synchronized(known_tags_lock) {
            for (String requested_tag: tmp_upd_parser.getTags()) if (known_tags.indexOf(requested_tag) < 0) {
                if (!UserCanAddTags) return "Unknown tag '" + requested_tag + "'";
                tmp_update_tag_list = true;
                break;
            }
            if (tmp_update_tag_list) {

                if (now_saving_tags_list) {

                    int tmp_try_count = 0;
                    do {
                        tmp_try_count++;
                        try {
                            known_tags_lock.wait(CONST_UGC_WAKEUP_MILLIS);
                        } catch(Exception e) { }
                        Tum3Util.SleepExactly(200); // Just in case.
                    } while (now_saving_tags_list && (tmp_try_count < CONST_UGC_ATTEMPT_LIMIT));

                }
                if (!now_saving_tags_list) {

                    now_saving_tags_list = true;
                    tmp_we_locked_tag_list = true;

                }

            }
        }

        if (tmp_update_tag_list && !tmp_we_locked_tag_list) return "Timed out waiting for tag list update, sorry";

        if (tmp_update_tag_list && tmp_we_locked_tag_list) {

            String tmp_save_error = "";
            try {
                tmp_updated_tag_list = known_tags.dup();
                for (String requested_tag: tmp_upd_parser.getTags()) if (tmp_updated_tag_list.indexOf(requested_tag) < 0)
                    tmp_updated_tag_list.add(requested_tag);

                File tmp_file = new File(DB_ROOT_PATH_UGC + "0001.tmp");
                File tmp_file_newer = new File(DB_ROOT_PATH_UGC + "0001.104");
                File tmp_file_older = new File(DB_ROOT_PATH_UGC + "0001.103");

//System.out.println("[DEBUG] Saving new tag list.");
                tmp_file.delete();
                if (tmp_file.exists()) tmp_save_error = "Failed to cleanup " + "0001.tmp";
                if (tmp_save_error.isEmpty())
                    if (tmp_file_newer.isFile()) {
                        tmp_file_older.delete();
                    if (tmp_file_older.exists()) tmp_save_error = "Failed to cleanup " + "0001.103";
                    else if (!tmp_file_newer.renameTo(tmp_file_older)) tmp_save_error = "Failed to rename " + "0001.104";
                }
                if (tmp_save_error.isEmpty())
                    if (!tmp_updated_tag_list.writeToFile(tmp_file)) tmp_save_error = "Failed to write " + "0001.tmp";
                if (tmp_save_error.isEmpty())
                    if (!tmp_file.renameTo(tmp_file_newer)) tmp_save_error = "Failed to rename " + "0001.tmp";
                if (tmp_save_error.isEmpty()) {
                    tmp_file_older.delete();
                    if (tmp_file_older.exists()) tmp_save_error = "Failed to delete " + "0001.103";
                }
                if (tmp_save_error.isEmpty())
                    if (!tmp_file_newer.renameTo(tmp_file_older)) tmp_save_error = "Failed to rename " + "0001.104";
                if (!tmp_save_error.isEmpty())
                    tmp_updated_tag_list = known_tags; // Just step back.

                synchronized(known_tags_lock) {

                    known_tags = tmp_updated_tag_list;
                    now_saving_tags_list = false;
                    tmp_we_locked_tag_list = false;
                    known_tags_lock.notifyAll();

                }

                Tum3Broadcaster.DistributeGeneralEvent(owner_db, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_UGC_LIST_UPD, -4), null); // YYY

            } catch (Exception e) {

                synchronized(known_tags_lock) {
                    if (tmp_we_locked_tag_list) now_saving_tags_list = false;
                    known_tags_lock.notifyAll();
                }
                if (tmp_save_error.isEmpty()) tmp_save_error = e.getMessage();

            }

            if (!tmp_save_error.isEmpty()) return tmp_save_error;
        }

        tmp_lock_pos = TryToLockShot(_shot_name, false);
        if (tmp_lock_pos >= 0) {
            try {
                tmp_result_msg = UpdateUgcDataIntl(_shot_name, tmp_upd_parser);
            } finally {
                TryToReleaseShot(tmp_lock_pos, false);
            }
        } else {
            tmp_result_msg = "Too busy to save now, sorry";
        }

        the_link.GenerateUgcReply(thrd_ctx, _req_id, _shot_name, tmp_result_msg, null);

        return "";

    }


    private void TryToReleaseShot(int tmp_lock_pos, boolean _reading_only) {

        synchronized(ugc_lock) {
            lock_list[tmp_lock_pos].Reset(_reading_only);
            while (lock_count > 0) {
                if (lock_list[lock_count-1].isEmpty()) lock_count--;
                else break;
            }
            ugc_lock.notifyAll();
        }
    }

    private int TryToLockShot(String _shot_name, boolean _reading_only) {

        int tmp_lock_pos = -1;
        boolean tmp_yes_can_work = false;

        synchronized(ugc_lock) {

            boolean tmp_is_busy;
            int tmp_try_count = 0;
            do {
                tmp_try_count++;
                tmp_is_busy = false;
                for (int j = 0; j < lock_count; j++) if (lock_list[j].NameMatches(_shot_name)) {
                    if (_reading_only) {
                        if (lock_list[j].in_writer) tmp_is_busy = true;
                    } else {
                        if (lock_list[j].curr_readers > 0) tmp_is_busy = true;
                    }
                    break;
                }
                if (tmp_is_busy) {
                    try {
                        ugc_lock.wait(CONST_UGC_WAKEUP_MILLIS);
                    } catch(Exception e) { }
                    Tum3Util.SleepExactly(200); // Just in case.
                }
            } while (tmp_is_busy && (tmp_try_count < CONST_UGC_ATTEMPT_LIMIT));
            if (!tmp_is_busy) {
                for (int j = 0; j < lock_count; j++) if (lock_list[j].isEmpty()) {
                    tmp_lock_pos = j;
                    break;
                }
                if (tmp_lock_pos >= 0) tmp_yes_can_work = true;
                else if (tmp_lock_pos < (CONST_MAX_UGC_UPD - 1)) {
                    tmp_yes_can_work = true;
                    tmp_lock_pos = lock_count;
                    lock_count++;
                }
                if (tmp_yes_can_work) lock_list[tmp_lock_pos].FillWith(_shot_name, _reading_only);
            }
        }

        return tmp_yes_can_work ? tmp_lock_pos : -1;
    }

    private String UpdateUgcDataIntl(String _shot_name, Tum3UgcUpdParser upd_parser) throws Exception {

        if ((_shot_name.length() < 8) || (_shot_name.length() > 9)) throw new Exception("Unexpected shot name length " + _shot_name.length());
        String tmpSubdirName = _shot_name.substring(0, 4); // tmp_year + tmp_month;

        File dir = new File(DB_ROOT_PATH_UGC + tmpSubdirName + File.separator);
        //System.out.println("[aq3j] Checking if exists: " + DB_ROOT_PATH_UGC + tmpSubdirName + File.separator);
        if (!dir.exists()) dir.mkdir();
        if (dir.exists() && dir.isDirectory()) {
            //System.out.println("[aq3j] Yes, month dir exists.");

            // Note. We expect to be the only thread modifying UGC for this database.
            // However, there might be some interrupoted previous writes.
            // Therefore, we will try to automatically recover.
            File tmp_file = new File(DB_ROOT_PATH_UGC + tmpSubdirName + File.separator + _shot_name + ".tmp");
            File tmp_file_newer = new File(DB_ROOT_PATH_UGC + tmpSubdirName + File.separator + _shot_name + ".102");
            File tmp_file_older = new File(DB_ROOT_PATH_UGC + tmpSubdirName + File.separator + _shot_name + ".101");
            tmp_file.delete();
            if (tmp_file.exists()) return "Failed to cleanup " + _shot_name + ".tmp";
            if (tmp_file_newer.isFile()) {
                tmp_file_older.delete();
                if (tmp_file_older.exists()) return "Failed to cleanup " + _shot_name + ".101";
                if (!tmp_file_newer.renameTo(tmp_file_older)) return "Failed to rename " + _shot_name + ".102";
            }

            StringList tmp_new_ugc = StringList.readFromFile(tmp_file_older);
            boolean tmp_shot_modified = upd_parser.mergeWith(tmp_new_ugc);
            if (tmp_shot_modified) { // YYY
                if (tmp_new_ugc.size() == 0) {

                    tmp_file_older.delete();
                    if (tmp_file_older.exists()) return "Failed to delete " + _shot_name + ".101";

                } else {
                    tmp_new_ugc.writeToFile(tmp_file);

                    if (!tmp_file.renameTo(tmp_file_newer)) return "Failed to rename " + _shot_name + ".tmp";

                    tmp_file_older.delete();
                    if (tmp_file_older.exists()) return "Failed to delete " + _shot_name + ".101";
                    if (!tmp_file_newer.renameTo(tmp_file_older)) return "Failed to rename " + _shot_name + ".102";
                }

                Tum3Broadcaster.DistributeGeneralEvent(owner_db, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_UGC_SHOT_UPD, _shot_name), null);
            }

            return "";

        } else {
            return "Failed to create month dir";
        }

        //return "not implemented";

    }

    public static Tum3UgcWorker getUgcWorker(Tum3Db _owner_db) {

        return new Tum3UgcWorker(_owner_db);

    }

}
