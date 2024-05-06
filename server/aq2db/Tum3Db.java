/*
 * Copyright 2011-2024 Nikolai Zhubr <zhubr@mail.ru>
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

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.file.attribute.*;

import aq2net.Tum3Broadcaster;
import aq2net.GeneralDbDistribEvent;

public class Tum3Db implements Runnable, AppStopHook {

    public  final static String CONST_MSG_READONLY_NOW = "The database is read-only at this time";
    public  final static String CONST_MSG_ACCESS_DENIED = "Access denied";
    public  final static String CONST_MSG_INV_SHOT_NUMBER = "Raw data can be stored for current date only";
    public  final static String CONST_MSG_SRVLINK_ERR01 = "The specified shot name was not found";
    public  final static String CONST_MSG_SRVLINK_ERR02 = "Internal error locating shot name";
    private volatile boolean TerminateRequested = false;
    private Thread db_thread;

    //   TSkipDirection = (SkipBack, SkipForth, DontSkip);
    private final static int CONST_SkipBack = 0;
    private final static int CONST_SkipForth = 1;
    private final static int CONST_DontSkip = 2;

    private final static int CONST_SYNC_SHOTS_ONCE_LIMIT = 999; // YYY
    private static final int MIN_SYNDIR_CLEANUP_MINUTES = 60; // YYY
    private static final int DAY_SYNDIR_CLEANUP_MINUTES = 60 * 24; // YYY

    private static final boolean DEBUG_ON_WIN = false; // Debugging only. winxp does not implement symlinks well.
    private static final boolean DEBUG_MEM_USAGE = false; // YYY

    private static final String FSUFF_FLAGPREP = ".801"; // YYY
    public static final String FSUFF_FLAGDONE = ".800"; // YYY
    public static final String FSUFF_FLAGSYNCING = ".802"; // YYY
    private static final String FSUFF_FLAGSTALL = ".803"; // YYY
    private static final String FSUFF_FLAGPREP_ERA = ".805"; // YYY
    public static final String FSUFF_FLAGDONE_ERA = ".804"; // YYY
    public static final String FSUFF_FLAGSYNCING_ERA = ".806"; // YYY
    private static final String FSUFF_FLAGSTALL_ERA = ".807"; // YYY

    private static final String[][] SYNC_SUFF = {
        {FSUFF_FLAGPREP, FSUFF_FLAGDONE, FSUFF_FLAGSYNCING, FSUFF_FLAGSTALL},
        {FSUFF_FLAGPREP_ERA, FSUFF_FLAGDONE_ERA, FSUFF_FLAGSYNCING_ERA, FSUFF_FLAGSTALL_ERA }};
    public static final int SYNF_ADD = 0, SYNF_ERASE = 1;
    public static final int SYNF_PREP = 0, SYNF_DONE = 1, SYNF_SYNCING = 2, SYNF_STALL = 3;

    private final static int CONST_SRVINFO_UPD_MINS = 15; // YYY
    private final static int CONST_SYNCINFO_PUSH_SEC = 15; // YYY
    private final static int CONST_SHOTS_WAIT_PERIOD = 1;      // Seconds
    private int CONST_SHOTS_DISPOSE_AFTER = 30;   // Seconds
    private int CONST_SHOTS_MAX_OPEN = 100;
    private final static String TUM3_CFG_db_root_volatile = "db_root_volatile";
    private final static String TUM3_CFG_published_root = "published_root"; // YYY
    private final static String TUM3_CFG_published_root_volatile = "published_root_volatile"; // YYY
    private final static String TUM3_CFG_sync_state_root = "sync_state_root";
    private final static String TUM3_CFG_max_shots_open = "max_shots_open";
    private final static String TUM3_CFG_unused_shot_close_delay = "unused_shot_close_delay";
    private final static String TUM3_CFG_master_db = "master_db";
    private final static String TUM3_CFG_writeprotect_storage = "writeprotect_storage"; // YYY
    private final static String TUM3_CFG_enable_sync_raw = "enable_sync_raw"; // YYY

    private static Tum3Db[] DbInstance = null;
    private static Object DbCreationLock = new Object();
    private String DB_ROOT_PATH, DB_ROOT_PATH_VOL;
    private String PUBLISHED_ROOT_PATH, PUBLISHED_ROOT_PATH_VOL; // YYY
    private String DB_REL_ROOT_PATH, DB_REL_ROOT_PATH_VOL; // YYY
    public final String SYNC_STATE_PATH; // YYY
    private StringList MasterList[] = new StringList[2]; // YYY
    private Object MasterListLock = new Object(), PostCreationLock = new Object();
    private boolean creation_complete = false;
    private volatile boolean HaveFilterMode = false; // YYY
    private String FAutoCreatedMonthDir = ""; // YYY
    public final boolean downbulk_enabled, upbulk_enabled; // YYY

    private HashMap<String, Tum3Shot> openShots, closingShots;
    private ArrayList<String> FGlobalShotList = new ArrayList<String>(); // YYY

    private int db_index;
    private String db_name, masterdb_name;
    public final boolean isWriteable, withSyncState;
    private Tum3Db master_db = null;
    private Tum3UgcWorker ugc_worker = null;
    private volatile boolean IsWaitingTrig = false;

    private Object diag_lock = new Object(); // YYY
    private int diag_free_space_gb = 0; // YYY
    private boolean diag_free_space_found = false; // YYY
    private boolean diag_raid_status_ok = false; // YYY
    private boolean diag_raid_status_found = false; // YYY
    private String diag_raid_detail = ""; // YYY
    private Tum3Time diag_time; // YYY
    private final boolean downlink_enabled; // YYY
    private final boolean uplink_enabled; // YYY
    private String OtherServerInfo = ""; // YYY
    private volatile String OtherServer_sync_info = ""; // YYY
    private volatile boolean OtherServerIsConnected = false; // YYY
    private volatile Tum3Time OtherServer_last_time; // YYY
    private long diag_next_update; // YYY
    private int warn_free_space_gb = 0; // YYY
    public final boolean writeprotect_storage, enable_sync_raw; // YYY

    private volatile String bup_start_subdir = "", bup_start_day = ""; // YYY
    private volatile HashMap<String, StringList> bup_start_done_list = new HashMap<String, StringList>(); // YYY
    private volatile StringList bup_task_list = new StringList(); // YYY
    private volatile StringList bup_shot_items = new StringList(); // YYY
    private volatile StringList bup_flag_files = new StringList(), bup_data_files = new StringList(); // YYY
    private StringList bup_tmp_sign_flag_files = new StringList(), bup_tmp_sign_data_files = new StringList(); // YYY
    private volatile int bup_task_pos = -1, bup_shot_pos = -1; // YYY
    private volatile long bup_expected_ofs = 0; // YYY
    private volatile String bup_current_shot = "", bup_current_file = "", bup_current_file_real = "", bup_temp_fname = ""; // YYY
    private volatile RandomAccessFile bup_raf = null; // YYY
    private String bup_prev_seen_monthdir = ""; // YYY
    private volatile String bup_sync_status_str = "Not started"; // YYY
    private volatile String bup_sync_last_seen = ""; // YYY
    private volatile String bup_last_seen_syn_shot = ""; // YYY
    private volatile String bup_sync_status_time = (new Tum3Time()).AsString(); // YYY
    private volatile String bup_sync_error_str = "", bup_sync_error_long = ""; // YYY
    private volatile String bup_sync_error_time = ""; // YYY
    private volatile String bup_last_shot_in_continuator = ""; // YYY
    private volatile String bup_last_strange_shot = ""; // YYY
    private volatile String bup_last_reset_date = ""; // YYY
    private volatile boolean bup_in_volatile; // YYY
    private volatile boolean bup_syn_strange_count_finished = false; // YYY
    private volatile long bup_sync_status_push = 0; // YYY
    private volatile int bup_syn_strange_count = 0, bup_syn_strange_final = -1; // YYY
    private volatile double bup_visible_rate = -1; // YYY
    private volatile double[] bup_visible_rate_arr = {-1.0, -1.0, -1.0, -1.0};
    private final Object bup_syn_strange_lock = new Object(); // YYY
    private final Object bup_sync_status_lock = new Object(); // YYY

    private final boolean BUP_SYN_ERASE_WIPES = false; // Note: tested OK, but make little sense for real life.

    public static class BupTransferContinuator implements OutBuffContinuator {

        protected final long myLength;
        protected long writtenCount = 0;

        private volatile RandomAccessFile myFF;
        private String mySName, myFName;
        //private boolean was_error = false;
        //private String error_msg = "";
        //private byte WasEdited = 0;
        //private int user_count = 1;
        private boolean block_close = false; // YYY
        public final boolean is_volatile; // YYY


        public BupTransferContinuator(boolean _is_volatile, String _shot_name, String _file_name, RandomAccessFile thisFF, long thisLength) {
            is_volatile = _is_volatile;
            myFF = thisFF;
            myLength = thisLength;
            mySName = _shot_name;
            myFName = _file_name;
        }

        public String ShotName() {

            return mySName;

        }

        public String FileName() {

            return myFName;

        }

        public void EnsureOfs(long _seg_ofs) throws Exception {

            if (_seg_ofs != writtenCount) throw new Exception("Segment offset mismatch between OutBuffContinuator and OutgoingBuff");

        }

        public boolean WithWarning() {

            return false;

        }

        public long getPos() {

            return writtenCount;

        }

        public void ForceXByte() {
        }

        public boolean PleaseWait() {

            return false;

        }

        public boolean withTrailingStatus() {

            return false;

        }

        private byte getTrailingByte() {

            return 0;

        }

        public long getFullSizeX() {

            return myLength;

        }

        public byte getEditedByte() {

            return 0;

        }

        public int ReadTo(byte[] buff, int ofs, int count) throws Exception {

            long tmp_count_l = myLength - writtenCount;
            if (tmp_count_l > count) tmp_count_l = count;
            if (tmp_count_l < 0)     tmp_count_l = 0;
            int tmp_count = (int)tmp_count_l; // YYY
            if (tmp_count > 0)
                myFF.readFully(buff, ofs, tmp_count);
            writtenCount += tmp_count;
            //Tum3Logger.DoLog("BupTransferContinuator", true, " ReadTo(" + mySName + "," + myFName + "," + was_error + "," + ofs + "," + count + " = [tmp_count " + tmp_count + "] OK." );
            return tmp_count;
        }

        public void ForceLock() {

            block_close = true; // YYY

        }

        public void ForceRelease(boolean _and_close_now) throws Exception {

            block_close = false; //user_count--; // YYY 
            if (_and_close_now) close_Intnl(); // YYY

        }

        private void close_Intnl() throws Exception {

            //Tum3Logger.DoLog("BupTransferContinuator", true, "[debug] close() for " + mySName + "," + myFName + "," + was_error);
            //Tum3Logger.DoLog("BupTransferContinuator", true, "[debug] close() calltrace: " + Tum3Util.getStackTraceAuto());
            //if (user_count > 0) return;

            //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> TraceReaderContinuator.close(): myFF := null for '" + myFName + "'");
            if (null != myFF) {
                myFF.close();
                myFF = null;
            }
        }

        public void close() throws Exception {

            if (!block_close) close_Intnl(); // YYY

        }

        public void AddUser() { }

    }

    public static class SingleShotWriteHelper implements ShotChangeMonitor { // YYY

        private String curr_name;
        private ArrayList<Integer> curr_modified_ids = new ArrayList<Integer>(), curr_removed_ids = new ArrayList<Integer>();


        public SingleShotWriteHelper(String _curr_name) {

            curr_name = _curr_name;

        }

        public void AddUpdatedId(int _id, boolean _hurry, boolean _was_waiting, boolean _was_removed) throws Exception {

            if (_was_removed) {
                curr_removed_ids.add(_id);
            } else {
                if (_was_waiting) _id |= GeneralDbDistribEvent.ID_WAS_WAITING;
                curr_modified_ids.add(_id); // XXX TODO. Maybe check if already included?
            }
        }

        public void PushModifiedIds(Tum3Db _origin_db) {

            if (curr_modified_ids.size() > 0) {
                Tum3Broadcaster.DistributeGeneralEvent(_origin_db, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TRACEUPD_ARR, curr_name, curr_modified_ids), null); // Note: using "myself" prevents proper updates in aq2net client+server mode!
                curr_modified_ids.clear();
            }
            if (curr_removed_ids.size() > 0) {
                Tum3Broadcaster.DistributeGeneralEvent(_origin_db, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TRACEDEL_ARR, curr_name, curr_removed_ids), null);
                curr_removed_ids.clear();
            }
        }

    }

    protected Tum3Db(int _db_idx, String _masterdb_name) {

        // Note! Constructor should not do any slow or complicated actions,
        //  because it runs with a global lock hold.
        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        isWriteable = Tum3cfg.isWriteable(db_index); // YYY
        downlink_enabled = Tum3cfg.getGlbInstance().getDbDownlinkEnabled(db_index); // YYY
        downbulk_enabled = Tum3cfg.getGlbInstance().getDbDownBulkEnabled(db_index); // YYY
        upbulk_enabled = Tum3cfg.getGlbInstance().getDbUpBulkEnabled(db_index); // YYY
        uplink_enabled = Tum3cfg.getGlbInstance().getDbUplinkEnabled(db_index); // YYY
        masterdb_name = _masterdb_name;

        openShots = new HashMap<String, Tum3Shot>();
        closingShots = new HashMap<String, Tum3Shot>();
        DB_ROOT_PATH = Tum3cfg.getParValue(db_index, false, Tum3cfg.TUM3_CFG_db_root);
        DB_ROOT_PATH_VOL = Tum3cfg.getParValue(db_index, false, TUM3_CFG_db_root_volatile);

        SYNC_STATE_PATH = Tum3cfg.getParValue(db_index, false, TUM3_CFG_sync_state_root); // YYY
        writeprotect_storage = (0 != Tum3cfg.getIntValue(db_index, true, TUM3_CFG_writeprotect_storage, 0)); // YYY
        enable_sync_raw = (0 != Tum3cfg.getIntValue(db_index, false, TUM3_CFG_enable_sync_raw, 0)); // YYY

        CONST_SHOTS_MAX_OPEN = Tum3cfg.getIntValue(db_index, true, TUM3_CFG_max_shots_open, CONST_SHOTS_MAX_OPEN);
        CONST_SHOTS_DISPOSE_AFTER = Tum3cfg.getIntValue(db_index, true, TUM3_CFG_unused_shot_close_delay, CONST_SHOTS_DISPOSE_AFTER);

        withSyncState = isWriteable && (SYNC_STATE_PATH.length() > 0); // YYY
    }

    public Tum3Db GetMasterDb() {

        return master_db;

    }

    private static String getCfgMasterDb(int _db_idx) {

        //System.out.println("[DEBUG] getCfgMasterDb(" + _db_idx + ")=" + Tum3cfg.getParValue(_db_idx, false, TUM3_CFG_master_db));
        return Tum3cfg.getParValue(_db_idx, false, TUM3_CFG_master_db);

    }

    private static String CalcRelativePath(String _storage_root_path, String _symlink_root_path) { // YYY
    // Find representation of _storage_path relative to _symlink_path.
    // Example:
    // _storage_root_path /opt/aq2j/data/
    // _symlink_root_path /opt/aq2j/published/

        int tmp_cmn_len = _storage_root_path.length();
        if (tmp_cmn_len > _symlink_root_path.length())
            tmp_cmn_len = _symlink_root_path.length();

        int tmp_last_sep = -1;
        for (int tmp_i = 0; tmp_i < tmp_cmn_len;) {
            if (_storage_root_path.charAt(tmp_i) == _symlink_root_path.charAt(tmp_i)) {
                if (('\\' == _storage_root_path.charAt(tmp_i)) || ('/' == _storage_root_path.charAt(tmp_i))) tmp_last_sep = tmp_i;
                tmp_i++;
            } else break;
        }
        if (tmp_last_sep < 2) return _storage_root_path; // Common prefix too small, unlikely relative path would make sense.

        String tmp_storage_ending = _storage_root_path.substring(tmp_last_sep+1);
        String tmp_symlink_ending = _symlink_root_path.substring(tmp_last_sep+1);

        int tmp_dir_depth = 0;
        for (int tmp_i = 0; tmp_i < tmp_symlink_ending.length(); tmp_i++)
            if (('\\' == tmp_symlink_ending.charAt(tmp_i)) || ('/' == tmp_symlink_ending.charAt(tmp_i))) tmp_dir_depth++;

        StringBuilder tmp_rel_prefix = new StringBuilder();
        for (int tmp_i = 0; tmp_i <= tmp_dir_depth; tmp_i++)
            tmp_rel_prefix.append(".." + File.separator);

        //Tum3Logger.DoLog("[DEBUG]", false, "[DEBUG] <" + tmp_rel_prefix.toString() + tmp_storage_ending + ">");
        return tmp_rel_prefix.toString() + tmp_storage_ending;

    }

    private static boolean PathLooksGood(String _the_path) {

        if (null == _the_path) return true;
        if (_the_path.isEmpty()) return true;

        char tmp_last = _the_path.charAt(_the_path.length()-1);
        boolean first_ok;
        if (File.separatorChar == '\\') { // Windows-style
            if (_the_path.length() < 3) first_ok = false;
            else {
                char tmp_drive = _the_path.charAt(0);
                char tmp_colon = _the_path.charAt(1);
                char tmp_first = _the_path.charAt(2);
                first_ok = (tmp_drive == '\\') || ((tmp_colon == ':') && ((tmp_first == '/') || (tmp_first == '\\')));
            }
        } else { // Regular
            char tmp_first = _the_path.charAt(0);
            first_ok = (tmp_first == '/') || (tmp_first == '\\');
        }
        return first_ok && ((tmp_last == '/') || (tmp_last == '\\'));

    }    

    private void FinishCreation() {

        synchronized(PostCreationLock) {
            if (!creation_complete) {
                creation_complete = true;

                PUBLISHED_ROOT_PATH = Tum3cfg.getParValue(db_index, false, TUM3_CFG_published_root); // YYY
                PUBLISHED_ROOT_PATH_VOL = Tum3cfg.getParValue(db_index, false, TUM3_CFG_published_root_volatile); // YYY

                if (!PathLooksGood(DB_ROOT_PATH))
                    Tum3Logger.DoLog(db_name, true, "IMPORTANT! Data path should start and end with filesystem separator character.");
                if (!PathLooksGood(DB_ROOT_PATH_VOL))
                    Tum3Logger.DoLog(db_name, true, "IMPORTANT! Data volatile path should start and end with filesystem separator character.");
                if (!PathLooksGood(PUBLISHED_ROOT_PATH))
                    Tum3Logger.DoLog(db_name, true, "IMPORTANT! Published data path should start and end with filesystem separator character.");
                if (!PathLooksGood(PUBLISHED_ROOT_PATH_VOL))
                    Tum3Logger.DoLog(db_name, true, "IMPORTANT! Published volatile data volatile path should start and end with filesystem separator character.");

                DB_REL_ROOT_PATH = CalcRelativePath(DB_ROOT_PATH, PUBLISHED_ROOT_PATH); // YYY
                DB_REL_ROOT_PATH_VOL = CalcRelativePath(DB_ROOT_PATH_VOL, PUBLISHED_ROOT_PATH_VOL); // YYY

                HaveFilterMode = !PUBLISHED_ROOT_PATH.isEmpty();
                if (HaveFilterMode) if (PUBLISHED_ROOT_PATH_VOL.isEmpty() != DB_ROOT_PATH_VOL.isEmpty()) {
                    HaveFilterMode = false;
                    Tum3Logger.DoLog(db_name, true, "Volatile data path setting for main and published db looks inconsistent, disabling published mode.");
                }

                ugc_worker = Tum3UgcWorker.getUgcWorker(this); // YYY
                Tum3Logger.DoLog(db_name, false, "Starting the database (data_path=" + DB_ROOT_PATH + ", data_path_volatile=" + DB_ROOT_PATH_VOL + ")" + (isWriteable? " as writable" : " as read-only"));
                Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_SHOTS_MAX_OPEN=" + CONST_SHOTS_MAX_OPEN);
                Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_SHOTS_DISPOSE_AFTER=" + CONST_SHOTS_DISPOSE_AFTER);
                UpdateThisServerInfo(System.currentTimeMillis()); // YYY
                warn_free_space_gb = Tum3cfg.getWarnFreeSpaceGb(db_index); // YYY
                if (warn_free_space_gb > 0)
                    Tum3Logger.DoLog(db_name, false, "Enabled free space warning at " + warn_free_space_gb + " Gb.");
                else
                    Tum3Logger.DoLog(db_name, false, "Note: no free space warning threshold was specified.");
                //getPackedLastList();
                //try { BupResetFrom("230419/15:0000=0&1,0001=0,0002=0,0003=0,0004=0,0008=0,0011=0,0012=0;17:0000=0&1,0001=0,0002=0,0003=0,0004=0,0008=0,0011=0,0012=0,0013=0,0021=0,0022=0"); } catch(Exception ignored) {}
            }
        }
    }

    public String DbName() {

        return db_name;

    }

    private static final String getHostMemUsage() {
        try {
            File tmp_proc_self = new File("/proc/self/statm");
                                        // 13607 5827 1162 213 0 4830 0
                                        //       ^^^^
            if (tmp_proc_self.exists()) {
                FileInputStream tmp_stream = null;
                try {
                    tmp_stream = new FileInputStream(tmp_proc_self);
                    byte[] buf_b = new byte[256];
                    int numRead = tmp_stream.read(buf_b);
                    String readData = Tum3Util.BytesToStringRaw(buf_b, 0, numRead);
                    String[] tmp_values = readData.split(" ");
                    if (tmp_values.length > 4) {
                        try {
                            int tmp_kb = Integer.parseInt(tmp_values[1]);
                            return "" + (tmp_kb*4);
                        } catch (Exception ignored) {}
                    }
                    return "?";
                } finally {
                    if (null != tmp_stream) tmp_stream.close();
                }
            } else return "?";
        } catch (Exception ignored) {
            return "err";
        }
    }

    public void run() {

        AppStopHooker.AddHook(this);

        while (!TerminateRequested) {
            try {
                Thread.sleep(CONST_SHOTS_DISPOSE_AFTER * (long)1000);
            } catch (InterruptedException e) { }
            DisposeUnusedShots(false);
            if (!TerminateRequested) {
                if (DEBUG_MEM_USAGE) { // YYY
                    System.gc();
                    Tum3Logger.DoLog(db_name, true, "rtFree " + (Runtime.getRuntime().freeMemory() >>> 10) + " kB/" + "rtTotal " + (Runtime.getRuntime().totalMemory() >>> 10) + " kB; OS=" + getHostMemUsage() + " kB"); // YYY
                }
                long curr_millis = System.currentTimeMillis();
                if (curr_millis >= diag_next_update) {
                    UpdateThisServerInfo(curr_millis); // YYY
                    Tum3Broadcaster.DistributeFlag(this); // YYY
                }
            }
        }
        Tum3Logger.DoLog(db_name, false, "DEBUG: Tum3db ver " + a.CurrentVerNum + " exiting normally.");
        DisposeUnusedShots(true);

        AppStopHooker.RemoveHook(this);
    }

    public void AppStopped() {

        TerminateRequested = true;
        db_thread.interrupt();

    }

    private String YearExtend_Intl(String _short_year) throws Exception {

         //if (_short_year.length() == 2) throw new Exception("YearExtend_Intl wrong argument <" + _short_year + ">");

         if ('9' == _short_year.charAt(0)) return "19" + _short_year;
         else                              return "20" + _short_year;

    }

    private final static int Published2Filter(boolean published_only) {

        if (published_only) return 1;
        else return 0;

    }

    private final static boolean Filter2Published(int _filtermode) {

        if (0 == _filtermode) return false;
        else return true;

    }

    private void LoadMasterList() throws Exception {

        synchronized (MasterListLock) {

            for (int tmp_filtermode = 0; tmp_filtermode <= 1; tmp_filtermode++) if (MasterList[tmp_filtermode] == null) { // YYY

                //System.out.println("[DEBUG] Creating masterlist in " + db_name);
                StringList tmp_out_list = new StringList();
                StringList tmp_list = GetRawRootDir(Filter2Published(tmp_filtermode)); // new StringList(); // YYY

                if (null != master_db) {
                    //System.out.println("[DEBUG] Adding masterlist from " + master_db.db_name);
                    master_db.LoadMasterList();
                    for (int tmp_i=0; tmp_i < master_db.MasterList[0].size(); tmp_i++) {
                        String tmp_name = master_db.MasterList[0].get(tmp_i);
                        tmp_name = YearExtend_Intl(tmp_name);
                        //System.out.println("[DEBUG] masterlist: " + tmp_name + " ?");
                        if (tmp_list.indexOf(tmp_name) < 0)
                            tmp_list.add(tmp_name);
                    }
                }

                Collections.sort(tmp_list);
                for (int tmp_i=0; tmp_i < tmp_list.size(); tmp_i++)
                    tmp_out_list.add(tmp_list.get(tmp_i).substring(2));

                MasterList[tmp_filtermode] = tmp_out_list;
            }
        }
    } 

    public int getIndex() {

        return db_index;

    }

    public String NextShotTitle(String ManualTitle) throws Exception {

        if (!Tum3cfg.isWriteable(db_index)) throw new Exception(CONST_MSG_READONLY_NOW);

        //ArrayList<String> tmp_s = GetDateStrs();
        //String tmp_year, tmp_month, tmp_day;
        //tmp_year  = tmp_s.get(0);
        //tmp_month = tmp_s.get(1);
        //tmp_day   = tmp_s.get(2);

        String tmp_date_str = new Tum3Time().GetCurrYMD(); // tmp_year + tmp_month + tmp_day;
        String tmpSubdirName = tmp_date_str.substring(0, 4); // tmp_year + tmp_month;

        if (!ManualTitle.isEmpty())
            if (ManualTitle.length() < 8) ManualTitle = "";

        if (!ManualTitle.isEmpty())
            if (!ManualTitle.substring(0, 6).equals(tmp_date_str)) {
                //System.out.println("[aq3j] Requested bad shot number: " + ManualTitle.substring(0, 6) + " != " + tmp_date_str);
                ManualTitle = "";
            }

        File dir = new File(DB_ROOT_PATH + tmpSubdirName + File.separator);
        //System.out.println("[aq3j] Checking if exists: " + DB_ROOT_PATH + tmpSubdirName + File.separator);
        FAutoCreatedMonthDir = ""; // YYY
        if (dir.exists() && dir.isDirectory()) {
            //System.out.println("[aq3j] Yes, exists.");
        } else {
            //System.out.println("[aq3j] No, does not.");
            if (!dir.mkdir()) throw new Exception("Failed to create month directory.");
            FAutoCreatedMonthDir = tmpSubdirName; // YYY
            //System.out.println("[aq3j] Created.");
        }

        boolean tmp_use_local_suff = !masterdb_name.isEmpty();
        boolean tmp_match_only = !ManualTitle.isEmpty();
        String tmp_manual_upper = ManualTitle.toUpperCase();
        String tmp_max_suff = "00";
        if (tmp_use_local_suff) tmp_max_suff = "E00";
        for (File file: dir.listFiles()) if (file.isDirectory()) {
            String tmp_name = file.getName().toUpperCase();
            //System.out.println("[aq2j] DEBUG: considering subdir '" + tmp_name + "'");
            if ((8 <= tmp_name.length()) && (9 >= tmp_name.length())) {
                if (tmp_match_only) {
                    //System.out.println("[aq2j] DEBUG: comparing '" + tmp_name + "' and '"+ ManualTitle + "'");
                    if (tmp_manual_upper.equals(tmp_name)) throw new Exception("The requested number (" + ManualTitle + ") is already in use.");
                } else {
                    //System.out.println("[aq2j] DEBUG: parsing '" + tmp_name + "'");
                    if (tmp_name.substring(0, 6).equals(tmp_date_str)) {
                        String tmp_shot_only = tmp_name.substring(6).toUpperCase();
                        if (!ShotNumLocal(tmp_shot_only) || tmp_use_local_suff)
                            if (ShotSuffGreater(tmp_shot_only, tmp_max_suff, tmp_use_local_suff))
                                tmp_max_suff = tmp_shot_only;
                    }
                }
            }
        }

        if (tmp_match_only) return ManualTitle;
        else return tmp_date_str + IncShotStr(tmp_max_suff); // Tum3Util.IntToStr2(tmp_max_num + 1);

        //return "18010203";

    }

    public void CleanupMonthDir() {

        if (!Tum3cfg.isWriteable(db_index)) return;
        if (FAutoCreatedMonthDir.isEmpty()) return;
        String tmp_dest_dir = FAutoCreatedMonthDir;
        FAutoCreatedMonthDir = ""; // Immediately cleanup dir name to only allow one attempt of cleanup.
        try {
            File dir = new File(DB_ROOT_PATH + tmp_dest_dir + File.separator);
            //System.out.println("[aq3j] Checking if exists: " + DB_ROOT_PATH + tmp_dest_dir + File.separator);
            if (dir.exists() && dir.isDirectory()) {
                //System.out.println("[aq3j] Yes, exists.");
                if (dir.listFiles().length == 0) {
                    //System.out.println("[aq3j] Is empty, should delete.");
                    dir.delete(); // YYY
                }
            }
        } catch (Exception ignored) { }
    }

    public final static boolean ShotNumLocal(String the_num) {

        return (the_num.charAt(0) >= 'E') && (the_num.charAt(0) <= 'Z');

    }

    public final static boolean ShotNameLocal(String the_name) {

        return ShotNumLocal(the_name.substring(6));

    }

    private static String IncShotStr(String the_suff) throws Exception {

        the_suff = the_suff.toUpperCase();
        int tmp_i, tmp_j = the_suff.length();
        char[] tmp_st = new char[tmp_j];
        StringBuilder tmp_out = new StringBuilder();
        for (tmp_i = 0; tmp_i < tmp_j; tmp_i++) tmp_st[tmp_i] = the_suff.charAt(tmp_i);

        tmp_i = tmp_j - 1;
        while (tmp_i >= 0) {
            if (tmp_st[tmp_i] == 'Z') throw new Exception("Shot numbering overflow at " + the_suff);
            if (tmp_st[tmp_i] == '9') {
                tmp_st[tmp_i] = '0';
                if (tmp_i > 0) tmp_i--;
                else if (tmp_j < 3) { tmp_out.append("1"); break; } // YYY
                else throw new Exception("Shot numbering overflow at " + the_suff);
            } else {
                tmp_st[tmp_i]++;
                break;
            }
        }

        for (tmp_i = 0; tmp_i < tmp_j; tmp_i++) tmp_out.append(tmp_st[tmp_i]);

        //System.out.println("[DEBUG] increment " + the_suff + " is " + tmp_out.toString() + " .....");

        return tmp_out.toString(); // "01";

    }

    private static boolean ShotSuffGreater(String _new, String _old, boolean _use_local_suff) {

        //System.out.println("[DEBUG] Comparing " + _new + " to " +  _old + " .....");

        if (_new.length() > _old.length()) return true;
        if (_new.length() < _old.length()) return false;

        _new = _new.toUpperCase();
        _old = _old.toUpperCase();

        for (int tmp_i = 0; tmp_i < _new.length(); tmp_i++) {
            char ch_new = _new.charAt(tmp_i), ch_old = _old.charAt(tmp_i);
            //System.out.println("[DEBUG] Comparing chars " + ch_new + " to " + ch_old + " .....");
            if (ch_new > ch_old) return true;
            else if (ch_new < ch_old) return false;
        }

        return false;

    }

    private StringList GetRawSubdir(boolean published_only, String tmpSubdirName, boolean _days_only) {

        StringList tmp_list = new StringList();
        String tmp_actual_root = published_only ? PUBLISHED_ROOT_PATH : DB_ROOT_PATH; // YYY
        if (tmp_actual_root.length() > 0) {
            File dir = new File(tmp_actual_root + tmpSubdirName + File.separator);
            File tmp_files[] = dir.listFiles();
//Tum3Util.SleepExactly(3000);
            //System.out.println("[DEBUG] + tmpSubdirName=" + tmpSubdirName);
            if (null == tmp_files) {
                // Reminder: the directory is empty or non-existent at this time. Assume there are no files in it anyway.
                //String tmp_emsg = "WARNING: index " + MasterIndex + " somehow contains unusable directory <" + tmpSubdirName + "> in GetRawSubdir()";
                //Tum3Logger.DoLog(db_name, true, tmp_emsg);
                //throw new Exception(tmp_emsg);
            } else for (File file: tmp_files) {
                if (file.isDirectory()) {
                    String tmp_name = file.getName();
                    //System.out.println("[aq2j] DEBUG: considering subdir '" + tmp_name + "'");
                    if ((8 <= tmp_name.length()) && (9 >= tmp_name.length())) {
                        if (tmp_name.substring(0, 4).equals(tmpSubdirName) && Tum3Util.StrNumeric(tmp_name.substring(4, 6))) {
                            if (_days_only) { // YYY
                                String tmp_st = tmp_name.substring(4, 6);
                                if (!tmp_list.contains(tmp_st)) tmp_list.add(tmp_st);
                            } else tmp_list.add(tmp_name.substring(4));
                        }
                    }
                }
            }
        }

        return tmp_list;
    }

    private StringList GetThisDir(boolean published_only, int MasterIndex) throws Exception {
        
        int tmp_filtermode = Published2Filter(published_only); // YYY

        if ((MasterIndex < 1) || (MasterIndex > MasterList[tmp_filtermode].size())) {
            Tum3Logger.DoLog(db_name, true, "WARNING: index " + MasterIndex + " is out of range in GetThisDir()");
            return new StringList();
        }

        String tmpSubdirName = MasterList[tmp_filtermode].get(MasterIndex-1);
        StringList tmp_list = GetRawSubdir(published_only, tmpSubdirName, false);

        if (null != master_db) {
            StringList tmp_master_sublist = null;
            synchronized (master_db.MasterListLock) {
                int tmp_j = master_db.MasterList[0].indexOf(tmpSubdirName);
                if (tmp_j >= 0)
                    tmp_master_sublist = master_db.GetRawSubdir(false, tmpSubdirName, false);
            }
            if (null != tmp_master_sublist)
                for (int tmp_i = 0; tmp_i < tmp_master_sublist.size(); tmp_i++) {
                    String tmp_st = tmp_master_sublist.get(tmp_i);
                    if (tmp_list.indexOf(tmp_st) < 0)
                        tmp_list.add(tmp_st);
                }
        }
        tmp_list.SortAsShots(); // Collections.sort(tmp_list); // YYY
        //System.out.print("[DEBUG] "); for (int tmp_ii = 0; tmp_ii < tmp_list.size(); tmp_ii++) System.out.print(tmp_list.get(tmp_ii) + " "); System.out.println(" ");
        return tmp_list;
    }

    private String getFilesForDay(String tmpSubdirName, String tmpDay) {

        StringList tmp_list = new StringList();
        if (DB_ROOT_PATH.length() > 0) {
            File dir = new File(DB_ROOT_PATH + tmpSubdirName + File.separator);
            File tmp_files[] = dir.listFiles();
//Tum3Util.SleepExactly(3000);
            //System.out.println("[DEBUG] + tmpSubdirName=" + tmpSubdirName);
            if (null == tmp_files) {
                // Reminder: the directory is empty or non-existent at this time. Assume there are no files in it anyway.
            } else for (File file: tmp_files) {
                if (file.isDirectory()) {
                    String tmp_name = file.getName();
                    //System.out.println("[aq2j] DEBUG: considering subdir '" + tmp_name + "'");
                    if ((8 <= tmp_name.length()) && (9 >= tmp_name.length())) {
                        if (tmp_name.substring(0, 4).equals(tmpSubdirName) && tmp_name.substring(4, 6).equals(tmpDay)) {
                            String tmp_st = tmp_name.substring(6);
                            if (!tmp_list.contains(tmp_st)) tmp_list.add(tmp_st);
                        }
                    }
                }
            }
        }

        tmp_list.SortAsShots(); // Collections.sort(tmp_list); // YYY
        StringBuilder tmp_res = new StringBuilder();

        for (String tmp_shot: tmp_list) {
            String shotDir = DB_ROOT_PATH + tmpSubdirName + File.separator + tmpSubdirName + tmpDay + tmp_shot;
            //System.out.println("[DEBUG] shotDir=" + shotDir);
            File[] tmpFiles = listShotFiles_Intl(new File(shotDir)); // YYY
            if (tmpFiles == null) continue;
            StringList tmp_list2 = new StringList();
            for (File file: tmpFiles) tmp_list2.add(file.getName());
            Collections.sort(tmp_list2);

            StringBuilder tmp_res2 = new StringBuilder();
            String tmp_prev_idname = "";
            String tmp_prev_extlist = "";
            for (String tmp_sign_file: tmp_list2) {
                String tmp_basename = tmp_sign_file.substring(0, tmp_sign_file.length()-4);
                String tmp_ext = tmp_sign_file.charAt(tmp_sign_file.length()-2) + "";
                //System.out.println("[aq2j] DEBUG tmp_sign_file='" + tmp_sign_file + "'... ");
                if (tmp_basename.equals(tmp_prev_idname)) {
                    tmp_prev_extlist = tmp_prev_extlist + '&' + tmp_ext;
                } else {
                    if (!tmp_prev_idname.isEmpty()) {
                        if (tmp_res2.length() > 0) tmp_res2.append(",");
                        tmp_res2.append(tmp_prev_idname + "=" + tmp_prev_extlist);
                    }
                    tmp_prev_idname = tmp_basename;
                    tmp_prev_extlist = tmp_ext;
                }
            }
            if (!tmp_prev_idname.isEmpty()) {
                if (tmp_res2.length() > 0) tmp_res2.append(",");
                tmp_res2.append(tmp_prev_idname + "=" + tmp_prev_extlist);
            }
            String tmp_shot_flist = tmp_res2.toString();
            if (!tmp_shot_flist.isEmpty()) {
                if (tmp_res.length() > 0) tmp_res.append(";");
                tmp_res.append(tmp_shot + ":" + tmp_shot_flist);
            }
        }

        if (tmp_res.length() > 0) {
            String tmp_out = tmpSubdirName + tmpDay + "/" + tmp_res.toString();
            //System.out.println("[aq2j] DEBUG tmp_out='" + tmp_out + "'");
            return tmp_out;
        }

        return "";
    }

    private File[] listShotFiles_Intl(File dir) {

        if (null == dir) return null;

        return dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (8 <= name.length())
                        if ((name.charAt(name.length()-1) == '0') && (name.charAt(name.length()-3) == '0') && (name.charAt(name.length()-4) == '.'))
                            if (Tum3Util.StrNumeric(name.substring(0, name.length()-4)))
                                return true;
                    return false;
                }
        });
    }

    private File[] listFlagFilesSyn_Intl(String path_part) {

        File dir = new File(SYNC_STATE_PATH + path_part);
        if (null == dir) return null;

        return dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (8 <= name.length())
                        if ((name.charAt(name.length()-4) == '.') && (name.charAt(name.length()-3) == '8') && Tum3Util.StrNumeric(name.substring(name.length()-2)))
                            if (Tum3Util.StrNumeric(name.substring(0, name.length()-4)))
                                return true;
                    return false;
                }
        });
    }

    private File[] listDataFilesSyn_Intl(String path_part) {

        File dir = new File(DB_ROOT_PATH_VOL + path_part);
        if (null == dir) return null;

        return dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (8 <= name.length())
                        if (name.endsWith(Tum3Shot.FSUFF_NORMAL) || name.endsWith(Tum3Shot.FSUFF_BUP_GENERAL))
                            if (Tum3Util.StrNumeric(name.substring(0, name.length()-4)))
                                return true;
                    return false;
                }
        });
    }

    private void PrepBupShot_Intl() {

        bup_shot_items.clear();
        bup_flag_files.clear();
        bup_data_files.clear();
        String tmp_full_num = bup_task_list.get(bup_task_pos);
        String tmpSubdirName = tmp_full_num.substring(0, 4);
        String tmpDay = tmp_full_num.substring(4, 6);
        String tmp_shot = tmp_full_num.substring(6);
        File[] tmpFiles;
        if (bup_in_volatile) {
            tmpFiles = listFlagFilesSyn_Intl(tmpSubdirName + File.separator + tmpSubdirName + tmpDay + tmp_shot);
            if (null != tmpFiles) if (tmpFiles.length > 0) {
                File[] tmpFiles2 = listDataFilesSyn_Intl(tmpSubdirName + File.separator + tmpSubdirName + tmpDay + tmp_shot);
                if (null != tmpFiles2)
                    for (File file: tmpFiles2) bup_data_files.add(file.getName());
            }
        } else {
            String shotDir = DB_ROOT_PATH + tmpSubdirName + File.separator + tmpSubdirName + tmpDay + tmp_shot;
            //Tum3Logger.DoLog(db_name, true, "[DEBUG] Comparing shotDir=" + shotDir + " (tmpDay=" + tmpDay + " tmp_shot=" + tmp_shot + ")");
            tmpFiles = listShotFiles_Intl(new File(shotDir));
        }
        if (tmpFiles == null) return;
        StringList tmp_ready_files = null;
        if (!bup_in_volatile && !bup_start_subdir.isEmpty())
            if (tmpSubdirName.equals(bup_start_subdir.substring(2)) && tmpDay.equals(bup_start_day)) tmp_ready_files = bup_start_done_list.get(tmp_shot);
        //if (null == tmp_ready_files) Tum3Logger.DoLog(db_name, true, "[DEBUG] tmp_ready_files == null"); else Tum3Logger.DoLog(db_name, true, "[DEBUG] tmp_ready_files == " + tmp_ready_files);
        for (File file: tmpFiles) {
                String tmp_sign_file = file.getName();
                if (bup_in_volatile) {
                    bup_flag_files.add(tmp_sign_file);
                    String tmp_sign_base = tmp_sign_file.substring(0, tmp_sign_file.length() - 4);
                    if (bup_shot_items.indexOf(tmp_sign_base) < 0) bup_shot_items.add(tmp_sign_base);
                } else {
                    boolean tmp_is_ready = false;
                    if (null != tmp_ready_files) if (tmp_ready_files.contains(tmp_sign_file)) tmp_is_ready = true;
                    if (!tmp_is_ready) bup_shot_items.add(tmp_sign_file);
                }
        }
        Collections.sort(bup_shot_items);
    }

    private String getLastDay(String tmpSubdirName) {
    // Note. This always operate unfiltered!

        StringList tmp_list = GetRawSubdir(false, tmpSubdirName, true);

        if (tmp_list.size() <= 0) return "";
        Collections.sort(tmp_list);

        String tmp_last_day = tmp_list.get(tmp_list.size() - 1);
        String tmp_prev_day = "";
        if (tmp_list.size() > 1) tmp_prev_day = tmp_list.get(tmp_list.size() - 2);
        //System.out.println("[aq2j] DEBUG: tmp_last_day=<" + tmp_last_day + "> tmp_prev_day=<" + tmp_prev_day + ">");

        String tmp_res = getFilesForDay(tmpSubdirName, tmp_last_day); // Reminder: as soon as some raw data was already transfered to backup for a date, it means all revious days were 100% finished already.
        if (tmp_res.isEmpty() && !tmp_prev_day.isEmpty()) tmp_res = getFilesForDay(tmpSubdirName, tmp_prev_day);

        return tmp_res;
    }

    private StringList GetRawRootDir(boolean published_only) throws Exception {

        return GetAnyRootDir((published_only && HaveFilterMode) ? PUBLISHED_ROOT_PATH : DB_ROOT_PATH);

    }

    private StringList GetAnyRootDir(String root_path) throws Exception {

        StringList tmp_list = new StringList();

        if (root_path.length() > 0) {
            File dir = new File(root_path);
            for (File file: dir.listFiles()) {
                if (file.isDirectory()) {
                    String tmp_name = file.getName();
                    if (Tum3Util.StrNumeric(tmp_name) && (4 == tmp_name.length())) {
                        tmp_name = YearExtend_Intl(tmp_name);
                        tmp_list.add(tmp_name);
                    }
                }
            }
        }

        return tmp_list;
    }

    public void setBupVisibleStatus(String new_status) {

        setBupVisibleStatus(new_status, "", true);

    }

    public void setBupVisibleStatus(String new_status, String seen_shot_name, boolean is_summary_status) {

        String tmp_time = (new Tum3Time()).AsString();
        long tmp_millis = System.currentTimeMillis();
        boolean tmp_do_push = is_summary_status; // Pushing in case text is identical might seem useless, but it is actually usefull because it shows time of status then.
        synchronized(bup_sync_status_lock) {
            if (!bup_in_volatile && !seen_shot_name.isEmpty()) bup_sync_last_seen = seen_shot_name;
            if (!tmp_do_push) if (tmp_millis >= bup_sync_status_push) tmp_do_push = true;
            if (tmp_do_push) bup_sync_status_push = tmp_millis + 1000 * CONST_SYNCINFO_PUSH_SEC;
            bup_sync_status_time = tmp_time;
            bup_sync_status_str = new_status + (is_summary_status && !bup_sync_last_seen.isEmpty() ? " [-->" + bup_sync_last_seen + "]" : ".") + " Bogus " + GetSynBogusCount(); // YYY
        }
        //if (tmp_do_push) Tum3Logger.DoLog(db_name, false, "[aq2j] DEBUG: setBupVisibleStatus(" + new_status + ") " + seen_shot_name);
        if (tmp_do_push) Tum3Broadcaster.DistributeFlag(this);

    }

    public void setBupVisibleRate(double _rate_megabytes_per_sec) {

        //bup_visible_rate = _rate_megabytes_per_sec;

        int tmp_i, tmp_count = 0;
        double tmp_d = 0.0;
        for (tmp_i = 1; tmp_i < bup_visible_rate_arr.length; tmp_i++) bup_visible_rate_arr[tmp_i-1] = bup_visible_rate_arr[tmp_i];
        bup_visible_rate_arr[bup_visible_rate_arr.length-1] = _rate_megabytes_per_sec;
        for (tmp_i = 0; tmp_i < bup_visible_rate_arr.length; tmp_i++) if (bup_visible_rate_arr[tmp_i] >= 0) {
            tmp_d += bup_visible_rate_arr[tmp_i];
            tmp_count++;
        }
        if (tmp_count > 0) bup_visible_rate = tmp_d / tmp_count;
        else               bup_visible_rate = -1.0;
    }

    public void setBupVisibleError(String error_descr_short, String error_descr_long) {

        String tmp_time = (new Tum3Time()).AsString();
        boolean tmp_do_push = false;
        synchronized(bup_sync_status_lock) {
            if (bup_sync_error_str.isEmpty()) {
                bup_sync_error_time = tmp_time;
                bup_sync_error_str = error_descr_short;
                bup_sync_error_long = error_descr_long;
                tmp_do_push = true;
            }
        }
        if (tmp_do_push) Tum3Broadcaster.DistributeFlag(this);
    }

    public boolean BupErrorPresent() {

        synchronized(bup_sync_status_lock) {
            return !bup_sync_error_str.isEmpty();
        }

    }

    public String getThisServerInfoSync() {

        String tmp_str = "";
        synchronized(bup_sync_status_lock) {
            tmp_str = "sync_status_str=" + bup_sync_status_str + "\r\n" 
                + "sync_status_time=" + bup_sync_status_time + "\r\n"
                + "sync_last_seen=" + bup_sync_last_seen + "\r\n"
                + "sync_error_str=" + bup_sync_error_str + "\r\n"
                + "sync_error_time=" + bup_sync_error_time + "\r\n";
        }
        return tmp_str;
    }

    public String BupLastResetDate() {

        return bup_last_reset_date;

    }

    public void BupResetFrom(String _bup_string) throws Exception {

        // 230419/15:0000=0&1,0001=0,0002=0,0003=0,0004=0,0008=0,0011=0,0012=0;17:0000=0&1,0001=0,0002=0,0003=0,0004=0,0008=0,0011=0,0012=0,0013=0,0021=0,0022=0
        //System.out.println("[aq2j] DEBUG: tmp_body='" + tmp_body + "'");

        //if (true) throw new Exception("Test exceptn 2");
        //Tum3Logger.DoLog(db_name, false, "[DEBUG] _bup_string='" + _bup_string + "'");

        if (BupErrorPresent()) return;

        bup_visible_rate = -1.0;
        bup_in_volatile = false;
        bup_last_shot_in_continuator = "";
        bup_prev_seen_monthdir = "";
        bup_start_subdir = "";
        bup_start_day = "";
        bup_start_done_list.clear();
        bup_last_reset_date = ""; // YYY
        if (!_bup_string.isEmpty()) {
            String tmpSubdirName = "", tmpDay = "";
            if (_bup_string.length() < 7) {
                setBupVisibleError("Unexpected uplink reply (a)", "Uplink listing not recognized (a)");
                return;
            }
            if (_bup_string.charAt(6) != '/') {
                setBupVisibleError("Unexpected uplink reply (b)", "Uplink listing not recognized (b)");
                return;
            }
            tmpSubdirName = _bup_string.substring(0, 4);
            tmpDay = _bup_string.substring(4, 6);
            bup_last_reset_date = tmpSubdirName + tmpDay; // YYY
            if (!Tum3Util.StrNumeric(tmpSubdirName) || !Tum3Util.StrNumeric(tmpDay)) {
                setBupVisibleError("Unexpected uplink reply (c)", "Uplink listing not recognized (c)");
                return;
            }
            bup_start_subdir = YearExtend_Intl(tmpSubdirName);
            bup_start_day = tmpDay;
            _bup_string = _bup_string.substring(7);
            String[] tmp_shots = _bup_string.split(";");
            for (String tmp_shot: tmp_shots) {
                String[] tmp_num_and_flist = tmp_shot.split(":");
                if (2 != tmp_num_and_flist.length) {
                    setBupVisibleError("Unexpected uplink reply (d)", "Uplink listing not recognized (d)");
                    return;
                }
                String tmp_num = tmp_num_and_flist[0];
                String[] tmp_flist = tmp_num_and_flist[1].split(",");
                //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: tmp_num=" + tmp_num);
                StringList tmp_raw_files = new StringList();
                for (String tmp_sig_name: tmp_flist) {
                    String[] tmp_name_and_extlist = tmp_sig_name.split("=");
                    if (2 != tmp_name_and_extlist.length) {
                        setBupVisibleError("Unexpected uplink reply (e)", "Uplink listing not recognized (e)");
                        return;
                    }
                    String tmp_base_name = tmp_name_and_extlist[0];
                    if ((tmp_base_name.length() < 4) || !Tum3Util.StrNumeric(tmp_base_name)) {
                        setBupVisibleError("Unexpected uplink reply (f)", "Uplink listing not recognized (f)");
                        return;
                    }
                    String[] tmp_exts = tmp_name_and_extlist[1].split("&");
                    for (String tmp_one_ext: tmp_exts)
                        tmp_raw_files.add(tmp_base_name + ".0" + tmp_one_ext + "0");
                }
                if (tmp_raw_files.size() > 0) bup_start_done_list.put(tmp_num, tmp_raw_files);
            }
        }

        //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: bup_sync_error_long=<" + bup_sync_error_long + "> bup_start_subdir=<" + bup_start_subdir + "> bup_start_day=<" + bup_start_day + "> bup_start_done_list='" + bup_start_done_list + "'");

        StringList tmp_list = GetRawRootDir(false);
        StringList tmp_shot_list = new StringList();
        Collections.sort(tmp_list);

        //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: bup_start_subdir='" + bup_start_subdir + "' bup_start_day='" + bup_start_day + "'");
        StringList tmp_day_shot_list = new StringList(); // YYY
        String tmp_check_last_day = ""; // YYY
        boolean tmp_day_switched = false; // YYY
        for (int tmp_i = 0; tmp_i < tmp_list.size(); tmp_i++) if (bup_start_subdir.isEmpty() || (bup_start_subdir.compareTo(tmp_list.get(tmp_i)) <= 0)) {

            String tmpSubdirName = tmp_list.get(tmp_i).substring(2);
            //Tum3Logger.DoLog(db_name, false, "[DEBUG] entering tmpSubdirName='" + tmpSubdirName + "'");
            File dir = new File(DB_ROOT_PATH + tmpSubdirName + File.separator);
            File tmp_files[] = dir.listFiles();
            tmp_day_shot_list.clear(); // YYY

            // Consider month listing could be large. 
            // Before going into deeper checking, make this dir listing sorted, 
            // as otherwise it would be hard to decide when to stop.
            // Until sorted, we can NOT be satisfied by reaching CONST_SYNC_SHOTS_ONCE_LIMIT.
            if (null != tmp_files) for (File file: tmp_files) {
                if (file.isDirectory()) {
                    String tmp_name = file.getName();
                    if ((8 <= tmp_name.length()) && (9 >= tmp_name.length())) if (tmp_name.substring(0, 4).equals(tmpSubdirName))
                        tmp_day_shot_list.add(tmp_name.substring(4)); // YYY
                }
            }
            tmp_day_shot_list.SortAsShots(); // YYY

            // Now we do a second pass. This time the iteration is sorted,
            // so we know when day change happen and can avoid going too far.
            for (String tmp_day_shot: tmp_day_shot_list) { // YYY
                String tmp_name = tmpSubdirName + tmp_day_shot; // YYY
                boolean tmp_take_it = false;
                if (bup_start_day.isEmpty() || bup_start_subdir.isEmpty()) tmp_take_it = true;
                if (!tmp_take_it) if (!bup_start_subdir.equals(tmp_list.get(tmp_i))) tmp_take_it = true;
                if (!tmp_take_it) if (bup_start_subdir.equals(tmp_list.get(tmp_i)) && (bup_start_day.compareTo(tmp_name.substring(4, 6)) <= 0)) tmp_take_it = true;
                if (tmp_take_it) if (!(new File(DB_ROOT_PATH + tmpSubdirName + File.separator + tmp_name + File.separator + "0000" + Tum3Shot.FSUFF_NORMAL).exists())) tmp_take_it = false; // YYY
                if (tmp_take_it) { // YYY
                    tmp_shot_list.add(YearExtend_Intl(tmp_name));
                    if (!tmp_check_last_day.isEmpty() && !tmp_check_last_day.equals(tmp_name.substring(0, 6))) tmp_day_switched = true; // YYY
                    tmp_check_last_day = tmp_name.substring(0, 6);
                }
                //Tum3Logger.DoLog(db_name, false, "[DEBUG] tmp_name='" + tmp_name + "': tmp_take_it='" + tmp_take_it + "'");
                if (tmp_day_switched && (tmp_shot_list.size() > CONST_SYNC_SHOTS_ONCE_LIMIT)) break; // YYY
            }
            if (tmp_day_switched && (tmp_shot_list.size() > CONST_SYNC_SHOTS_ONCE_LIMIT)) break; // YYY
        }
        tmp_shot_list.SortAsShots(); // Collections.sort(tmp_shot_list); // YYY

        bup_task_list.clear();
        bup_task_pos = -1;
        tmp_check_last_day = ""; // YYY
        tmp_day_switched = false; // YYY
        for (int tmp_i = 0; tmp_i < tmp_shot_list.size(); tmp_i++) { // YYY
            String tmp_task_next_shot = tmp_shot_list.get(tmp_i).substring(2);
            if (tmp_day_switched && (bup_task_list.size() >= CONST_SYNC_SHOTS_ONCE_LIMIT)) // YYY
                break; // YYY
            else {
                bup_task_list.add(tmp_task_next_shot);
                if (!tmp_check_last_day.isEmpty() && !tmp_check_last_day.equals(tmp_task_next_shot.substring(0, 6))) tmp_day_switched = true; // YYY
                tmp_check_last_day = tmp_task_next_shot.substring(0, 6);
            }
        }

        //if (bup_task_list.size() > 0) Tum3Logger.DoLog(db_name, false, "Backup sync: found " + bup_task_list.size() + " raw shots for consideration.");

        //Tum3Logger.DoLog(db_name, false, "[DEBUG] non-volatile bup_task_list='" + bup_task_list + "'");

        BupTryNextTask_Intl();
        //if ((bup_task_pos >= 0) && (bup_task_pos < bup_task_list.size())) if (bup_shot_items.size() > 0) Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: bup_task_pos=" + bup_task_pos + " shot=" + bup_task_list.get(bup_task_pos) + ", bup_shot_items='" + bup_shot_items + "'");
        BupCloseCurrent(false);

    }

    public void BupResetSyn() {

        synchronized(bup_syn_strange_lock) {
            if (bup_syn_strange_count_finished) bup_syn_strange_final = bup_syn_strange_count;
            bup_syn_strange_count = 0;
            bup_syn_strange_count_finished = false;
        }
        bup_last_seen_syn_shot = "";
        bup_last_strange_shot = ""; // YYY

        bup_visible_rate = -1.0;
    }

    public boolean BupContinueFromSyn() throws Exception {

        if ((SYNC_STATE_PATH.length() <= 0) || !VolatilePathPresent()) return false;

        if (BupErrorPresent()) return false;

        bup_in_volatile = true;
        bup_last_shot_in_continuator = "";
        bup_start_subdir = "";
        bup_start_day = "";
        String bup_start_nn = "";
        bup_start_done_list.clear();
        if (!bup_last_seen_syn_shot.isEmpty()) {
            bup_start_subdir = YearExtend_Intl(bup_last_seen_syn_shot.substring(0, 4));
            bup_start_day = bup_last_seen_syn_shot.substring(4, 6);
            bup_start_nn = bup_last_seen_syn_shot.substring(6);
        }
        //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: bup_last_seen_syn_shot='" + bup_last_seen_syn_shot + "'");

        StringList tmp_list = GetAnyRootDir(SYNC_STATE_PATH);
        StringList tmp_shot_list = new StringList();
        Collections.sort(tmp_list);

        for (int tmp_i = 0; tmp_i < tmp_list.size(); tmp_i++) if (bup_start_subdir.isEmpty() || (bup_start_subdir.compareTo(tmp_list.get(tmp_i)) <= 0)) {

            String tmpSubdirName = tmp_list.get(tmp_i).substring(2);
            File dir = new File(SYNC_STATE_PATH + tmpSubdirName + File.separator);
            File tmp_files[] = dir.listFiles();
            if (null != tmp_files) for (File file: tmp_files) {
                if (file.isDirectory()) {
                    String tmp_name = file.getName();
                    if ((8 <= tmp_name.length()) && (9 >= tmp_name.length())) if (tmp_name.substring(0, 4).equals(tmpSubdirName)) {
                        boolean tmp_take_it = bup_last_seen_syn_shot.isEmpty() || (StringList.compareAsShots(bup_last_seen_syn_shot, tmp_name) /* bup_last_seen_syn_shot.compareTo(tmp_name) */ < 0); // YYY
                        if (tmp_take_it) tmp_shot_list.add(YearExtend_Intl(tmp_name));
                    }
                }
            }
            if (tmp_shot_list.size() >= CONST_SYNC_SHOTS_ONCE_LIMIT) break;
        }
        tmp_shot_list.SortAsShots(); // Collections.sort(tmp_shot_list); // YYY

        bup_task_list.clear();
        bup_task_pos = -1;
        for (int tmp_i = 0; (tmp_i < tmp_shot_list.size()) && (bup_task_list.size() < CONST_SYNC_SHOTS_ONCE_LIMIT); tmp_i++)
            bup_task_list.add(tmp_shot_list.get(tmp_i).substring(2));

        if (bup_task_list.size() > 0) Tum3Logger.DoLog(db_name, false, "Backup sync: found " + bup_task_list.size() + " updated shots in volatile."); // YYY
        //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: volatile bup_task_list='" + bup_task_list + "'");

        BupTryNextTask_Intl();
        //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: bup_task_list.size()=" + bup_task_list.size());
        //if (bup_task_list.size() > 0) Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: bup_task_list = (" + bup_task_list.get(0) + " - " + bup_task_list.get(bup_task_list.size()-1) + ")");
        BupCloseCurrent(false);

        if (bup_task_list.size() <= 0) bup_syn_strange_count_finished = true;

        return bup_task_list.size() > 0;
    }

    private void BupTryNextTask_Intl() {

        bup_shot_pos = 0;
        while (bup_task_pos < bup_task_list.size()) {
            bup_task_pos++;
            if (bup_task_pos < bup_task_list.size()) {
                PrepBupShot_Intl();
                if ((bup_shot_items.size() > 0) || bup_in_volatile) break; // YYY
            }
        }
    }

    public long AcceptBupPortion(boolean _is_volatile, String _shot_name, String _file_name, long _full_size, long _seg_offset, byte[] _body, int _ofs, int _len) throws Exception {

        long tmp_result = -1;

        //if (true) throw new Exception("Test exceptn 5");
        bup_in_volatile = _is_volatile; // YYY
        if (!_shot_name.equals(bup_current_shot) || !_file_name.equals(bup_current_file)) {
            //Tum3Logger.DoLog(db_name, true, "[aq2j] AcceptBupPortion: new _shot_name=" + _shot_name + " _file_name=" + _file_name);
            if (null != bup_raf) throw new Exception("AcceptBupPortion: unexpected file change");
            BupCloseCurrent(false);
            bup_current_shot = _shot_name;
            bup_current_file = _file_name;
        }
        if (bup_expected_ofs != _seg_offset) throw new Exception("AcceptBupPortion: unexpected offset " + _seg_offset);

        if (null == bup_raf) {
            boolean tmp_fname_ok = false;
            if (_is_volatile) tmp_fname_ok = Tum3Util.StrNumeric(bup_current_file) && (bup_current_file.length() >= 3) && (bup_current_file.length() <= 5);
            else {
            if (bup_current_file.length() >= 5)
                if  ((bup_current_file.charAt(bup_current_file.length()-4) == '.')
                  && (bup_current_file.charAt(bup_current_file.length()-3) == '0')
                  && (bup_current_file.charAt(bup_current_file.length()-1) == '0'))
                    tmp_fname_ok = true;
            }
            if (!tmp_fname_ok) throw new Exception("AcceptBupPortion: unexpected filename <" + bup_current_file + ">");
            bup_current_file_real = _is_volatile? bup_current_file + Tum3Shot.FSUFF_NORMAL : bup_current_file; // YYY
            String tmpActualPath = _is_volatile? DB_ROOT_PATH_VOL : DB_ROOT_PATH; // YYY
            String shotSubdir = bup_current_shot.substring(0, 4);
            if (0 == _full_size) {
                if (!_is_volatile) throw new Exception("AcceptBupPortion: unexpected 0 size for filename <" + bup_current_file_real + ">");
                File tmp_file_prev = new File(DB_ROOT_PATH_VOL + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file + Tum3Shot.FSUFF_BUP_GENERAL); // YYY
                File tmp_file_erased = new File(DB_ROOT_PATH_VOL + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file + Tum3Shot.FSUFF_BUP_ERASED); // YYY
                File tmp_dest_file = new File(DB_ROOT_PATH_VOL + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file_real); // YYY
                tmp_file_prev.delete();
                if (tmp_file_prev.exists()) throw new Exception("Volatile sync error: <" + DB_ROOT_PATH_VOL + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file + Tum3Shot.FSUFF_BUP_GENERAL + "> could not be deleted.");
                if (tmp_file_erased.exists() && tmp_dest_file.exists()) tmp_file_erased.delete();
                tmp_dest_file.renameTo(tmp_file_erased);
                tmp_dest_file = new File(DB_ROOT_PATH_VOL + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file_real); // YYY
                if (tmp_dest_file.exists()) throw new Exception("Volatile sync error: <" + DB_ROOT_PATH_VOL + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file_real + "> could not be deleted.");
                if (BUP_SYN_ERASE_WIPES) { // YYY
                    tmp_file_erased.delete(); // YYY
                    // Try to also delete dir path if possible.
                    (new File(DB_ROOT_PATH_VOL + shotSubdir + File.separator + bup_current_shot)).delete();
                    (new File(DB_ROOT_PATH_VOL + shotSubdir)).delete();
                }
            } else {
                StringBuilder tmp_temp_fname = new StringBuilder(bup_current_file_real);
                tmp_temp_fname.setCharAt(bup_current_file_real.length()-3, '3'); // YYY
                bup_temp_fname = tmp_temp_fname.toString();
                String tmp_full_path = tmpActualPath + shotSubdir + File.separator + bup_current_shot + File.separator + bup_temp_fname;
                //Tum3Logger.DoLog(db_name, true, "[aq2j] AcceptBupPortion: opening tmp_full_path=" + tmp_full_path);

                File tmp_monthdir = new File(tmpActualPath + shotSubdir);
                if (!tmp_monthdir.exists()) tmp_monthdir.mkdir();
                if (!bup_prev_seen_monthdir.equals(shotSubdir)) {
                    bup_prev_seen_monthdir = shotSubdir;
                    AppendMonthToMasterList(0, shotSubdir, false);
                }
                File tmp_shotdir = new File(tmpActualPath + shotSubdir + File.separator + bup_current_shot);
                if (!tmp_shotdir.exists()) tmp_shotdir.mkdir();
                if (!tmp_shotdir.exists())
                    throw new Exception("Failed to create <" + tmpActualPath + shotSubdir + File.separator + bup_current_shot + "> dir. Maybe access rights or paths are messed up?"); // YYY

                if (!_is_volatile) if (new File(tmpActualPath + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file_real).exists())
                    throw new Exception("Internal sync error: <" + bup_current_shot + File.separator + bup_current_file_real + "> already exists.");
                bup_raf = new RandomAccessFile(tmp_full_path, "rw");
            }
        }

        //Tum3Logger.DoLog(db_name, true, "[aq2j] AcceptBupPortion: writing _shot_name=" + _shot_name + " _file_name=" + _file_name + " _seg_offset=" + _seg_offset + " _ofs=" + _ofs + " _len=" + _len);
        if (_len > 0) bup_raf.write(_body, _ofs, _len);
        //Tum3Logger.DoLog(db_name, true, "[aq2j] AcceptBupPortion: wrote _shot_name=" + _shot_name + " _file_name=" + _file_name + " _seg_offset=" + _seg_offset + " _ofs=" + _ofs + " _len=" + _len);
        bup_expected_ofs += _len;
        tmp_result = bup_expected_ofs;
        if (bup_expected_ofs >= _full_size) {
            //Tum3Logger.DoLog(db_name, true, "[aq2j] AcceptBupPortion: file done. _shot_name=" + _shot_name + " _file_name=" + _file_name + " total_size=" + bup_expected_ofs);
            BupCloseCurrent(true);
        }
        return tmp_result;
    }

    private void BupCloseCurrent(boolean _with_success) throws Exception {

        if (null != bup_raf) {
            if (_with_success) bup_raf.setLength(bup_expected_ofs);
            bup_raf.close();
            bup_raf = null;
            if (_with_success) {
                String shotSubdir = bup_current_shot.substring(0, 4);
                String tmpActualPath = bup_in_volatile ? DB_ROOT_PATH_VOL : DB_ROOT_PATH; // YYY
                File tmp_dest_file = new File(tmpActualPath + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file_real); // YYY
                if (bup_in_volatile) {
                    File tmp_file_prev = new File(tmpActualPath + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file + Tum3Shot.FSUFF_BUP_GENERAL); // YYY
                    if (tmp_file_prev.exists() && tmp_dest_file.exists()) tmp_file_prev.delete();
                    tmp_dest_file.renameTo(tmp_file_prev);
                    tmp_dest_file = new File(tmpActualPath + shotSubdir + File.separator + bup_current_shot + File.separator + bup_current_file_real); // YYY
                    if (tmp_dest_file.exists()) throw new Exception("BupCloseCurrent: deleting previous " + bup_current_file_real + " failed.");
                }
                boolean tmp_ok = (new File(tmpActualPath + shotSubdir + File.separator + bup_current_shot + File.separator + bup_temp_fname))
                        .renameTo(tmp_dest_file);
                if (!tmp_ok) throw new Exception("BupCloseCurrent: renaming " + bup_temp_fname + " to " + bup_current_file_real + " failed.");
                if (writeprotect_storage && !bup_in_volatile) if (!tmp_dest_file.setWritable(false)) throw new Exception("BupCloseCurrent: setting R/O " + bup_current_file_real + " failed.");
            }
        }
        bup_current_shot = "";
        bup_current_file = "";
        bup_temp_fname = "";
        bup_expected_ofs = 0;
    }

    public void BupStop() {

        if (null != bup_raf)
            try { BupCloseCurrent(false); }
            catch (Exception e) {
                Tum3Logger.DoLog(db_name, true, "Exception in BupCloseCurrent(): " + Tum3Util.getStackTrace(e));
                setBupVisibleError("Failure in BupCloseCurrent", "Failure in BupCloseCurrent: " + e.toString());
            }
    }

    public void BupVolFileSuccess(String _shot_name, String _file_name, boolean _erasure) throws Exception {

        SyncStatusVolSendEnd(_shot_name.substring(0, 4), _shot_name, _file_name, _erasure ? SYNF_ERASE : SYNF_ADD); // YYY

    }


    private void FindInFiles(StringList full_list, String base_name, StringList found_list) {

        found_list.clear();
        for (String tmp_st: full_list) if (tmp_st.startsWith(base_name)) found_list.add(tmp_st.substring(base_name.length()-1));

    }

    public final static String SignalFName(int thisSignalId) {
        StringBuffer tmp_st = new StringBuffer();
        tmp_st.append("" + thisSignalId);
        while (tmp_st.length() < 4) tmp_st.insert(0, '0');
        return tmp_st.toString();
    }

    private void SetFileMTime(File tmp_new_file2) throws Exception {

        // Ensure flag-file's mtime is approximately current, so that later cleanup process could make a guess about how old is any remaining garbage, if any.
        long tmp_current_millis = System.currentTimeMillis();
        long tmp_last_mod_prev = tmp_new_file2.lastModified();
        if (tmp_last_mod_prev <= 0) throw new Exception("lastModified() failed");
        if ((tmp_current_millis - tmp_last_mod_prev) >= ((long)60000 * MIN_SYNDIR_CLEANUP_MINUTES)) {
            try {
                Files.setLastModifiedTime(tmp_new_file2.toPath(), FileTime.fromMillis(tmp_current_millis)); // YYY
            } catch (Exception ignored) {
                RandomAccessFile tmp_raf = null;
                try {
                    tmp_raf = new RandomAccessFile(tmp_new_file2, "rw");
                    tmp_raf.write(0);
                    tmp_raf.setLength(0);
                } finally {
                    if (null != tmp_raf) {
                        tmp_raf.close();
                        tmp_raf = null;
                    }
                }
            }
        }

    }

    public int GetSynBogusCount() {

        int tmp_curr, tmp_final;
        synchronized(bup_syn_strange_lock) {
            tmp_curr = bup_syn_strange_count;
            tmp_final = bup_syn_strange_final;
        }
        if (tmp_final < 0) return tmp_curr;
        else {
            if (tmp_curr > tmp_final) return tmp_curr;
            else return tmp_final;
        }

    }

    public BupTransferContinuator BupNextToSend() throws Exception {

        //if (true) throw new Exception("Test exceptn 3");
        BupTransferContinuator tmp_result = null;
        boolean tmp_found_some = false;

        while (!tmp_found_some && (bup_task_pos < bup_task_list.size()) && (bup_task_pos >= 0)) {
            String shotName = bup_task_list.get(bup_task_pos);
            String shotSubdir = shotName.substring(0, 4);
            if (bup_shot_items.size() > 0) {
                String fileName = bup_shot_items.get(bup_shot_pos);
                if (!bup_last_shot_in_continuator.equals(shotName)) {
                    bup_last_shot_in_continuator = shotName;
                    String tmp_job_size = "" + bup_task_list.size();
                    String tmp_last_in_job = bup_task_list.get(bup_task_list.size()-1);
                    if (bup_task_list.size() >= CONST_SYNC_SHOTS_ONCE_LIMIT) tmp_job_size = tmp_job_size + "+";
                    double tmp_rate = bup_visible_rate;
                    String tmp_rate_str = tmp_rate < 0 ? "? MB/s" : String.format("%.1f MB/s", tmp_rate); // YYY
                    setBupVisibleStatus((bup_in_volatile ? "Vol " : "Raw ") + (bup_task_pos + 1) + "/" + tmp_job_size + " [" + shotName + " --> " + tmp_last_in_job + "] " + tmp_rate_str, shotName, false);
                }
                if (bup_in_volatile) {
                    bup_last_seen_syn_shot = shotName;
                    FindInFiles(bup_flag_files, fileName + ".", bup_tmp_sign_flag_files);
                    FindInFiles(bup_data_files, fileName + ".", bup_tmp_sign_data_files);
                    boolean tmp_flag_to_upd = bup_tmp_sign_flag_files.contains(FSUFF_FLAGDONE) || bup_tmp_sign_flag_files.contains(FSUFF_FLAGSYNCING);
                    boolean tmp_flag_to_era = bup_tmp_sign_flag_files.contains(FSUFF_FLAGDONE_ERA) || bup_tmp_sign_flag_files.contains(FSUFF_FLAGSYNCING_ERA);
                    boolean tmp_prep_upd = bup_tmp_sign_flag_files.contains(FSUFF_FLAGPREP);
                    boolean tmp_prep_era = bup_tmp_sign_flag_files.contains(FSUFF_FLAGPREP_ERA);
                    boolean tmp_stall_upd = bup_tmp_sign_flag_files.contains(FSUFF_FLAGSTALL);
                    boolean tmp_stall_era = bup_tmp_sign_flag_files.contains(FSUFF_FLAGSTALL_ERA);
                    boolean tmp_data_exists = bup_tmp_sign_data_files.contains(Tum3Shot.FSUFF_NORMAL) || bup_tmp_sign_data_files.contains(Tum3Shot.FSUFF_BUP_GENERAL);
                    //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: shot=" + shotName + ", sign=" + fileName + ": bup_tmp_sign_flag_files='" + bup_tmp_sign_flag_files + "', bup_tmp_sign_data_files='" + bup_tmp_sign_data_files + "', tmp_flag_to_upd=" + tmp_flag_to_upd + " tmp_flag_to_era=" + tmp_flag_to_era + " tmp_data_exists=" + tmp_data_exists);
                    boolean tmp_can_erase = tmp_flag_to_era && !tmp_flag_to_upd && !tmp_data_exists;
                    boolean tmp_can_update = tmp_flag_to_upd && tmp_data_exists;
                    if (tmp_stall_upd || tmp_prep_upd) SyncStatusVolCleanup(shotSubdir, shotName, fileName, SYNF_ADD,   tmp_stall_upd, tmp_prep_upd); // YYY
                    if (tmp_stall_era || tmp_prep_era) SyncStatusVolCleanup(shotSubdir, shotName, fileName, SYNF_ERASE, tmp_stall_era, tmp_prep_era); // YYY
                    if (tmp_can_update) {
                        SyncStatusVolSendBegin(shotSubdir, shotName, fileName, SYNF_ADD);

                        String tmpActualPath = DB_ROOT_PATH_VOL;
                        String tmp_full_path = tmpActualPath + shotSubdir + File.separator + shotName + File.separator + fileName;
                        boolean tmp_open_ok = false;
                        RandomAccessFile raf = null;

                        for (int tmp_attempt = 1; tmp_attempt <= 4; tmp_attempt++) {
                            String Fext;
                            if ((tmp_attempt & 1) == 1) Fext = Tum3Shot.FSUFF_NORMAL;
                            else                        Fext = Tum3Shot.FSUFF_BUP_GENERAL;
                            try {
                                raf = new RandomAccessFile(tmp_full_path + Fext, "r");
                                //System.out.println("[aq2j] DEBUG: File '" + Fname + "' opened(r).");
                                tmp_open_ok = true;
                                break;
                            } catch (Exception e) {
                                if (((tmp_attempt & 1) == 1) && !(e instanceof FileNotFoundException)) // YYY
                                    throw e; // Note. The loop is supposed to only catch a renamed file, not any sorts of errors with it.
                            }
                        }
                        if (tmp_open_ok) {
                            tmp_result = new BupTransferContinuator(true, shotName, fileName, raf, raf.length());
                            tmp_found_some = true;
                        } else IncrementStrange(shotName);
                    } else if (tmp_can_erase) {
                        SyncStatusVolSendBegin(shotSubdir, shotName, fileName, SYNF_ERASE);
                        tmp_result = new BupTransferContinuator(true, shotName, fileName, null, 0);
                        tmp_found_some = true;
                    } else {
                        //if (tmp_flag_to_upd || tmp_flag_to_era) // Is it actually needed?
                            IncrementStrange(shotName);
                    }
                } else {
                    String tmpActualPath = DB_ROOT_PATH;
                    String tmp_full_path = tmpActualPath + shotSubdir + File.separator + shotName + File.separator + fileName;
                    RandomAccessFile raf = new RandomAccessFile(tmp_full_path, "r");
                    tmp_result = new BupTransferContinuator(false, shotName, fileName, raf, raf.length());
                    tmp_found_some = true;
                }
            }
            bup_shot_pos++;
            if (bup_shot_pos >= bup_shot_items.size()) BupTryNextTask_Intl();
            if (!tmp_found_some) TryToCleanupDirs(shotSubdir, shotName); // YYY
        }

        return tmp_result;
    }


    private void IncrementStrange(String shotName) {

        if (!bup_last_strange_shot.equals(shotName)) {
            bup_last_strange_shot = shotName;
            if (bup_syn_strange_count < Integer.MAX_VALUE) bup_syn_strange_count++;
        }
    }

    private void TryToCleanupDirs(String shotSubdir, String shotName) {

        if (0 == bup_shot_pos) {
            (new File(SYNC_STATE_PATH + shotSubdir + File.separator + shotName)).delete();
            //Tum3Logger.DoLog(db_name, false, "[DEBUG] Tried to delete dir: <" + SYNC_STATE_PATH + shotSubdir + File.separator + shotName + ">");
            boolean tmp_also_monthdir = (bup_task_pos >= bup_task_list.size()) || (bup_task_pos < 0);
            if (!tmp_also_monthdir) {
                String tmp_next_subdirName = bup_task_list.get(bup_task_pos).substring(0, 4);
                tmp_also_monthdir = !shotSubdir.equals(tmp_next_subdirName);
            }
            // If monthdir will change, also try to remove monthdir.
            if (tmp_also_monthdir) {
                (new File(SYNC_STATE_PATH + shotSubdir)).delete();
                //Tum3Logger.DoLog(db_name, false, "[DEBUG] Tried to delete dir: <" + SYNC_STATE_PATH + shotSubdir + ">");
            }
        }
    }

    public void SyncStatusVolSendBegin(String shotSubdir, String shotName, String _file_base, int _op_type) throws Exception {

        int tmp_other_type = 1 - _op_type;
        String tmp_fsuff_done    = SYNC_SUFF[_op_type][SYNF_DONE];
        String tmp_fsuff_syncing = SYNC_SUFF[_op_type][SYNF_SYNCING];
        String tmp_fsuff_done_other = SYNC_SUFF[tmp_other_type][SYNF_DONE];
        String tmp_fsuff_syncing_other = SYNC_SUFF[tmp_other_type][SYNF_SYNCING];

        String tmp_cmn_part = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + _file_base;
        String tmp_new_fname1 = tmp_cmn_part + tmp_fsuff_done;
        String tmp_new_fname2 = tmp_cmn_part + tmp_fsuff_syncing;
        String tmp_new_fname3 = tmp_cmn_part + tmp_fsuff_done_other;
        String tmp_new_fname4 = tmp_cmn_part + tmp_fsuff_syncing_other;
        File tmp_new_file1 = new File(tmp_new_fname1);
        File tmp_new_file2 = new File(tmp_new_fname2);
        File tmp_new_file3 = new File(tmp_new_fname3);
        File tmp_new_file4 = new File(tmp_new_fname4);

        if (tmp_new_file1.exists()) tmp_new_file2.delete();
        tmp_new_file1.renameTo(tmp_new_file2);
        if (tmp_new_file1.exists() || !tmp_new_file2.exists()) throw new Exception("Failed to rename sync flag file <" + tmp_new_fname1 + "> to <" + tmp_new_fname2 + ">");

        if (tmp_new_file3.exists()) tmp_new_file4.delete();
        tmp_new_file3.renameTo(tmp_new_file4);
        tmp_new_file4.delete();
        if (tmp_new_file3.exists() || tmp_new_file4.exists()) throw new Exception("Failed to remove sync flag files <" + tmp_new_fname3 + "> or <" + tmp_new_fname4 + ">");

    }

    public void SyncStatusVolSendEnd(String shotSubdir, String shotName, String _file_base, int _op_type) throws Exception {

        String tmp_fsuff_syncing = SYNC_SUFF[_op_type][SYNF_SYNCING];
        String tmp_cmn_part = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + _file_base;
        String tmp_new_fname2 = tmp_cmn_part + tmp_fsuff_syncing;
        File tmp_new_file2 = new File(tmp_new_fname2);

        tmp_new_file2.delete();
        tmp_new_file2 = new File(tmp_new_fname2);
        if (tmp_new_file2.exists()) throw new Exception("Failed to remove sync flag file <" + tmp_new_fname2 + ">");

        // Try to also remove empty directories, but only at the end of files for specific shot.
        TryToCleanupDirs(shotSubdir, shotName);

    }

    private void SyncStatusVolCleanup(String shotSubdir, String shotName, String _file_base, int _op_type, boolean _seen_stall, boolean _seen_prep) throws Exception {

        if (!withSyncState) return;

        String tmp_fsuff_prep = SYNC_SUFF[_op_type][SYNF_PREP];
        String tmp_fsuff_done = SYNC_SUFF[_op_type][SYNF_DONE];
        String tmp_fsuff_stall = SYNC_SUFF[_op_type][SYNF_STALL];
        String tmp_prep_fname = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + _file_base + tmp_fsuff_prep;
        String tmp_stall_fname = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + _file_base + tmp_fsuff_stall;
        String tmp_ready_fname = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + _file_base + tmp_fsuff_done; // YYY
        File tmp_prep_file = new File(tmp_prep_fname);
        File tmp_stall_file = new File(tmp_stall_fname);
        File tmp_ready_file = new File(tmp_ready_fname);

        if (_seen_stall) {
                // Note: at this point stall file might or might not exist.
                tmp_stall_file.renameTo(tmp_ready_file);
                // Note: at this point stall file might or might not exist.
                tmp_stall_file.delete();
        }

        if (_seen_prep) {
            long tmp_last_mod_prev = tmp_prep_file.lastModified();
            if (tmp_last_mod_prev >= 0)
                if ((System.currentTimeMillis() - tmp_last_mod_prev) >= ((long)60000 * DAY_SYNDIR_CLEANUP_MINUTES)) {
                    tmp_prep_file.renameTo(tmp_stall_file);
                }
        }
    }

    public void SyncStatusVolOpBegin(String shotSubdir, String shotName, int _ID, int _op_type) throws Exception {
    // Reminder! If it cannot do the thing then it MUST throw!
    // Reminder2! Concurrent calls for the same (shot+id) are strictly 
    //   prevented with external locking, however need to consider that some 
    //   previous operation could be unexpectedly interrupted at any stage.

        if (!withSyncState) return;

        String tmp_fsuff_prep = SYNC_SUFF[_op_type][SYNF_PREP]; // FSUFF_FLAGPREP; // YYY
        String tmp_fsuff_done = SYNC_SUFF[_op_type][SYNF_DONE]; // YYY
        String tmp_fsuff_stall = SYNC_SUFF[_op_type][SYNF_STALL];
        File tmp_monthdir    = new File(SYNC_STATE_PATH + shotSubdir);
        File tmp_shotdir     = new File(SYNC_STATE_PATH + shotSubdir + File.separator + shotName);
        String tmp_prep_fname = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ID) + tmp_fsuff_prep;
        String tmp_ready_fname = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ID) + tmp_fsuff_done; // YYY
        String tmp_stall_fname = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ID) + tmp_fsuff_stall; // YYY
        File tmp_prep_file = new File(tmp_prep_fname);
        File tmp_ready_file = new File(tmp_ready_fname); // YYY
        File tmp_stall_file = new File(tmp_stall_fname); // YYY

        boolean tmp_ok = false;
        for (int attempt = 0; (attempt < 3) && !tmp_ok; attempt++) {
            try {
                tmp_monthdir.mkdir();
                tmp_shotdir.mkdir();

                tmp_prep_file.renameTo(tmp_ready_file); // YYY
                tmp_prep_file.delete(); // YYY
                tmp_stall_file.renameTo(tmp_ready_file); // YYY
                tmp_stall_file.delete(); // YYY

                tmp_prep_file.createNewFile(); 
                // There is a race with SyncStatusVolCleanup(), but it only happens 
                // once in at least BUP_IDLE_CHECK_SECONDS, so multiple checks 
                // using tmp_stall_file.exists() should do the trick (hopefully).

                if (tmp_prep_file.exists() && !tmp_stall_file.exists()) tmp_ok = true; // YYY

            } catch (Exception ignored) {}
        }
        if (!tmp_ok) throw new Exception("Failed to create sync flag file <" + tmp_prep_fname + "> or cleanup respective stall flag file");

        //SetFileMTime(tmp_prep_file);
    }

    public void SyncStatusVolOpEnd(String shotSubdir, String shotName, int _ID, int _op_type, boolean _BeginOk, boolean _DataLikelyModified) {
    // Reminder! If it cannot do the thing then it MUST log, but never throw.
    // Reminder2! Concurrent calls for the same (shot+id) are strictly 
    //   prevented with external locking, however need to consider that some 
    //   previous operation could be unexpectedly interrupted at any stage.

        if (!withSyncState) return;

        String tmp_fsuff_prep = SYNC_SUFF[_op_type][SYNF_PREP]; // FSUFF_FLAGPREP; // YYY
        String tmp_fsuff_done = SYNC_SUFF[_op_type][SYNF_DONE]; // FSUFF_FLAGDONE; // YYY
        int tmp_other_type = 1 - _op_type; // YYY
        String tmp_fsuff_prep_other = SYNC_SUFF[tmp_other_type][SYNF_PREP]; // YYY
        String tmp_fsuff_done_other = SYNC_SUFF[tmp_other_type][SYNF_DONE]; // YYY

        String tmp_cmn_part = SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ID); // YYY
        String tmp_new_fname1 = tmp_cmn_part + tmp_fsuff_prep;
        String tmp_new_fname2 = tmp_cmn_part + tmp_fsuff_done;
        String tmp_new_fname3 = tmp_cmn_part + tmp_fsuff_prep_other; // YYY
        String tmp_new_fname4 = tmp_cmn_part + tmp_fsuff_done_other; // YYY
        File tmp_new_file1 = new File(tmp_new_fname1);
        File tmp_new_file2 = new File(tmp_new_fname2);
        File tmp_new_file3 = new File(tmp_new_fname3); // YYY
        File tmp_new_file4 = new File(tmp_new_fname4); // YYY

        if (_DataLikelyModified) {
            boolean tmp_ok = false;
            try {
                if (tmp_new_file1.renameTo(tmp_new_file2)) tmp_ok = true; // YYY
                if (!tmp_ok) if (tmp_new_file2.isFile()) tmp_ok = true; // YYY
                if (tmp_ok) tmp_new_file1.delete();
            } catch (Exception ignored) {}
            if (!tmp_ok) {
                Tum3Logger.DoLog(db_name, true, "Sync error: <" + tmp_new_fname2 + "> could not be renamed from <" + tmp_new_fname1 + "> and apparently does not exist");
            }
            try {
                SetFileMTime(tmp_new_file2);
            } catch (Exception e) {
                if (tmp_ok) Tum3Logger.DoLog(db_name, true, "Sync error: failed to update mtime on <" + tmp_new_fname2 + ">: " + e.toString());
            }
            tmp_ok = false;
            try {
                tmp_new_file3.delete(); // YYY
                tmp_new_file4.delete(); // YYY
                if (!tmp_new_file3.exists() && !tmp_new_file4.exists()) tmp_ok = true;
            } catch (Exception ignored) {}
            if (!tmp_ok) {
                Tum3Logger.DoLog(db_name, true, "Sync error: <" + tmp_new_fname3 + "> or <" + tmp_new_fname4 + "> could not be deleted");
            }
        } else {
            try {
                tmp_new_file1.delete();
                tmp_new_file2.delete();
            } catch (Exception ignored) {}
            if (_BeginOk) {
                if (tmp_new_file1.exists())
                    Tum3Logger.DoLog(db_name, true, "Sync error: <" + tmp_new_fname1 + "> could not be deleted");
                if (tmp_new_file2.exists())
                    Tum3Logger.DoLog(db_name, true, "Sync error: <" + tmp_new_fname2 + "> could not be deleted");
            }
        }
    }

    public String getPackedLastList() throws Exception {
    // Note. This always operate unfiltered!

        //if (true) throw new Exception("Test exceptn 1");
        StringList tmp_list = GetRawRootDir(false);

        if (tmp_list.size() <= 0) return "";
        Collections.sort(tmp_list);
        String tmp_last_month = tmp_list.get(tmp_list.size() - 1).substring(2);
        String tmp_prev_month = "";
        if (tmp_list.size() > 1) tmp_prev_month = tmp_list.get(tmp_list.size() - 2).substring(2);
        //System.out.println("[aq2j] DEBUG: tmp_last_month=<" + tmp_last_month + "> tmp_prev_month=<" + tmp_prev_month + ">");

        String tmp_res = getLastDay(tmp_last_month);
        if (tmp_res.isEmpty() && !tmp_prev_month.isEmpty()) tmp_res = getLastDay(tmp_prev_month);

        return tmp_res;
    }

    public boolean VolatilePathPresent() {

        return DB_ROOT_PATH_VOL.length() > 0;

    }

    private void AppendMonthToMasterList(int _filtermode, String _master_name, boolean _auto_load) throws Exception { // YYY

        synchronized (MasterListLock) {
            if (MasterList[_filtermode] == null) {
                if (!_auto_load) return; // YYY
                LoadMasterList();
            }
            if (MasterList[_filtermode].indexOf(_master_name) < 0)
                MasterList[_filtermode].add(_master_name);
        }
    }

    public String UpdateUgcData(byte thrd_ctx, String UserName, boolean UserCanAddTags, UgcReplyHandler the_link, int _req_id, String _shot_name, byte[] _upd_arr) throws Exception {

        return ugc_worker.UpdateUgcData(thrd_ctx, the_link, UserName, UserCanAddTags, _req_id, _shot_name, _upd_arr);

    }

    public String GetUgcData(byte thrd_ctx, UgcReplyHandler the_link, int _req_id, String _shot_name) throws Exception {

        return ugc_worker.GetUgcData(thrd_ctx, the_link, _req_id, _shot_name);

    }

    public Tum3Shot newShot(String _shot_name, String _the_program, int[] _expected_ids, ByteArrayOutputStream _aq_profile_body) throws Exception {

        if (!Tum3cfg.isWriteable(db_index)) throw new Exception(CONST_MSG_READONLY_NOW);
        if (DB_ROOT_PATH.length() <= 0) throw new Exception("The database path is not defined.");

        String tmp_name = _shot_name.toUpperCase();
        Tum3Shot tmpShot = null;
        synchronized (openShots) {
            if (openShots.containsKey(tmp_name)) throw new Exception("Internal error: shot name is already in use.");
            tmpShot = new Tum3Shot(this, DB_ROOT_PATH, DB_ROOT_PATH_VOL, tmp_name.substring(0, 4), tmp_name, true, _the_program, _expected_ids, _aq_profile_body);
            tmpShot.ShotAddUser();
            openShots.put(tmp_name, tmpShot);
        }
        String tmp_master_name = tmp_name.substring(0, 4);
        if (Tum3Util.StrNumeric(tmp_master_name) && (4 == tmp_master_name.length()))
            AppendMonthToMasterList(0, tmp_master_name, true); // YYY
        tmpShot.CompleteCreation(); // Note. If already exists, it opens normally as old 
        // and then raises an exception that propagates out.

        return tmpShot; // By this time, normally, 0000.000 has already been created.

    }

    public Tum3Shot getShot(String this_shot_name, boolean _allow_master) throws Exception {
    // Note. This function is supposed to open existing shots only.

        Tum3Shot tmpShot = null;
        String tmp_name = this_shot_name.toUpperCase();

        //System.out.println("[DEBUG] getShot(): opening '" + this_shot_name + "' in " + db_name);

        //boolean tmp_need_to_complete = false;

        if (DB_ROOT_PATH.length() > 0) {
            boolean tmp_force_dispose_unused = false;
            boolean tmp_need_add = true;
            boolean tmp_try_again = false;
            synchronized (openShots) {
                do {
                    tmp_try_again = false; // YYY
                    if (openShots.containsKey(tmp_name)) {
                        tmpShot = (Tum3Shot) openShots.get(tmp_name);
                        if (null == tmpShot) {
                            tmp_try_again = true;
                            try {
                                openShots.wait(CONST_SHOTS_WAIT_PERIOD * (long)1000);
                            } catch (Exception e) { }
                        } else {
                            tmp_need_add = false;
                            tmpShot.ShotAddUser();
                        }
                    }
                } while (tmp_try_again && !TerminateRequested);
            } // YYY
             
            if (tmp_need_add) {
                String shotSubdir = tmp_name.substring(0, 4); // YYY
                if ((new File(DB_ROOT_PATH + shotSubdir + File.separator + tmp_name + File.separator + "0000" + Tum3Shot.FSUFF_NORMAL)).isFile()) { // YYY
                    synchronized (openShots) { // YYY
                        do { // YYY
                            tmp_try_again = false; // YYY
                            if (openShots.containsKey(tmp_name)) {
                                tmpShot = (Tum3Shot) openShots.get(tmp_name);
                                if (null == tmpShot) {
                                    tmp_try_again = true;
                                    try {
                                        openShots.wait(CONST_SHOTS_WAIT_PERIOD * (long)1000);
                                    } catch (Exception e) { }
                                } else {
                                    tmp_need_add = false;
                                    tmpShot.ShotAddUser();
                                }
                            }
                        } while (tmp_try_again && !TerminateRequested);
                        if (tmp_need_add) {
                            tmpShot = new Tum3Shot(this, DB_ROOT_PATH, DB_ROOT_PATH_VOL, shotSubdir, tmp_name, false, "", null, null);
                            //tmp_need_to_complete = true;
                            tmpShot.ShotAddUser();
                            openShots.put(tmp_name, tmpShot);
                            tmp_force_dispose_unused = (openShots.size() > CONST_SHOTS_MAX_OPEN);
                        }
                    }
                }
            }
            if (tmp_force_dispose_unused) DisposeUnusedShots(false);
        }

        if (null != tmpShot)
            tmpShot.CompleteCreation();

        if (_allow_master && (null != master_db)) {
            boolean tmp_local_found = false;
            if (null != tmpShot) {
                //tmpShot.CompleteCreation(); // YYY This one looks excessive?
                if (!tmpShot.NotStored()) tmp_local_found = true;
            }
            if (!tmp_local_found && !ShotNumLocal(this_shot_name)) {
                if (null != tmpShot) {
                    tmpShot.ShotRelease();
                    tmpShot = null;
                }
                tmpShot = master_db.getShot(this_shot_name, false);
            }
        }

        return tmpShot;
    }

    private void DisposeUnusedShots(boolean for_shutdown) {

        //System.out.println("[aq2j] DEBUG: DisposeUnusedShots()");

        boolean tmp_need_to_notify = false;
        int tmp_curr_shots = 0;

        synchronized (openShots) {
            int tmp_count_to_dispose = openShots.size() - CONST_SHOTS_MAX_OPEN;
            if (tmp_count_to_dispose < 0) tmp_count_to_dispose = 0;
            long tmp_max_millis = System.currentTimeMillis() - CONST_SHOTS_DISPOSE_AFTER*(long)1000;
            for (Map.Entry<String, Tum3Shot> entry : openShots.entrySet()) {
                Tum3Shot tmpShot = entry.getValue();
                if (for_shutdown || (tmpShot.notUsed(tmp_max_millis, (tmp_count_to_dispose > 0)))) { // Reminder note: if UserCount == 0 then there is no way to obtain any new reference other than inside of openShots lock. Therefore, no additional atomicity is necessary here.
                    String tmp_name = entry.getKey();
                    //System.out.println("[aq2j] DEBUG: DisposeUnusedShots(): '" + tmp_name + "' will now be disposed.");
                    closingShots.put(tmp_name, tmpShot);
                    tmp_need_to_notify = true;
                    entry.setValue(null);
                    if (tmp_count_to_dispose > 0) tmp_count_to_dispose--;
                }
            }
            tmp_curr_shots = openShots.size();
        }

        for (Tum3Shot tmpShot : closingShots.values()) {
            tmpShot.Detach();
        }

        if (tmp_need_to_notify) {
            synchronized (openShots) {
                for (String tmp_name : closingShots.keySet()) {
                    openShots.remove(tmp_name);
                    //System.out.println("[aq2j] DEBUG: closed shot '" + tmp_name + "'");
                }
                openShots.notifyAll();
                tmp_curr_shots = openShots.size();
            }
        }

        //int tmp_client_count = GetClientCount();
        if ((closingShots.size() != 0) || (tmp_curr_shots != 0) /* || (tmp_client_count != 0) */) {
            //System.out.println("[aq2j] DEBUG: DisposeUnusedShots() closed " + closingShots.size() + " shots, remaining " + tmp_curr_shots + " shots" /* + "; clients=" + tmp_client_count */);
        }
        closingShots.clear();
    }

    public int GetOpenShotsCount() {
        synchronized (openShots) {
            return openShots.size();
        }
    }

    public String GetStatusInfo() {
        return /* "ClientCount=" + GetClientCount() + ", " + */ "OpenShotsCount=" + GetOpenShotsCount();
    }

    public String LatestShotDate() throws Exception {

        LoadMasterList();

        // Note: synchronization is not needed here bacause MasterList can not be modified by any different thread (except in LoadMasterList()).
        int tmp_j = MasterList[0].size() - 1;
        while (tmp_j >= 0) {
            //System.out.println("[DEBUG] LatestShotDate: " + MasterList[0].get(tmp_j) + " ...");
            StringList tmpList = GetThisDir(false, tmp_j+1);
            if (tmpList.size() == 0) tmp_j--;
            else
                return MasterList[0].get(tmp_j) + tmpList.get(tmpList.size() - 1).substring(0, 2);
        }

        return "";

    }

    public void PackDirectory(boolean published_only, String thisName, int thisSkipDirection, ByteArrayOutputStream thisBuff) throws Exception {

        StringList theList;
        String RefName = "", st;
        int tmp_i, tmp_j;

        published_only &= HaveFilterMode;
        int tmp_filtermode = Published2Filter(published_only); // YYY

        //System.out.println("[aq2j] DEBUG: in PackDirectory('" + thisName + "', " + thisSkipDirection + ")");

        if ((thisName.length() == 0) && (thisSkipDirection == 0)) {

            LoadMasterList();
            theList = MasterList[tmp_filtermode];
            thisName = "";
            RefName = "";

        } else if (thisName.length() == 9) {

            LoadMasterList();
            String Name2 = thisName.substring(5, 9);
            thisName = thisName.substring(0, 4);
            RefName = thisName;

            StringList tmpList;
            tmp_j = MasterList[tmp_filtermode].indexOf(thisName);
            if (tmp_j >= 0) {
                //System.out.println("[aq2j] DEBUG: PackDirectory: Good!");
                tmpList = GetThisDir(published_only, tmp_j+1);
                if ((tmpList.size() == 0) && ((thisSkipDirection == CONST_SkipBack) || (thisSkipDirection == CONST_SkipForth))) {
                    while (((tmp_j > 0) || (thisSkipDirection == CONST_SkipForth)) && ((tmp_j < (MasterList[tmp_filtermode].size()-1)) || (thisSkipDirection == CONST_SkipBack))
                            && (tmpList.size() == 0) && (!MasterList[tmp_filtermode].get(tmp_j).equals(Name2))) {
                        if (thisSkipDirection == CONST_SkipBack) tmp_j--;
                        else tmp_j++;
                        if (!MasterList[tmp_filtermode].get(tmp_j).equals(Name2)) {
                            tmpList = GetThisDir(published_only, tmp_j+1);
                            if (tmpList.size() > 0)
                                thisName = MasterList[tmp_filtermode].get(tmp_j);
                        }
                    }
                }
            } else {
                tmpList = new StringList();
            }
            theList = tmpList;
        } else {
            Tum3Logger.DoLog(db_name, true, "FATAL: Unknown request format in PackDirectory");
            throw new Exception("FATAL: Unknown request format in PackDirectory");
        }

        {
            //System.out.println("[aq2j] DEBUG: dump in PackDirectory follows:");
            //for (int tmp_k=0; tmp_k<theList.size(); tmp_k++)
            //  System.out.print(" '" + theList.get(tmp_k) + "' ");
            //System.out.println("");
        }

        thisName = (char)0 // = Full refresh.
                +thisName+(char)0+RefName;
        thisBuff.write(Tum3Util.StringToByteZ(thisName));

        for (tmp_i = 0; tmp_i < theList.size(); tmp_i++) {
            st = theList.get(tmp_i);
            if (((st.length() == 4) || (st.length() == 5))) {
                // System.out.println("[aq2j] DEBUG: adding '" + st + "'");
                thisBuff.write(Tum3Util.StringToByteZ(st));
            }
        }
    }

    private static Tum3Db createInstance(int _db_idx, String _masterdb_name) {

        Tum3Db tmp_inst = new Tum3Db(_db_idx, _masterdb_name);
        tmp_inst.db_thread = new Thread(tmp_inst);
        if (!Tum3cfg.isWriteable(_db_idx)) tmp_inst.db_thread.setDaemon(true);
        tmp_inst.db_thread.start();
        return tmp_inst;

    }

    public void RegisterNewShot(String _new_shot_num) { // YYY

        FAutoCreatedMonthDir = ""; // YYY  As soon as some shot triggered, we likely do not want to cleanup month directory anymore.
        synchronized(FGlobalShotList) { FGlobalShotList.add(_new_shot_num); }

    }

    public void setUplink(InterconInitiator _initiator) {

        new UplinkScheduler(_initiator, this, "meta").WorkStart();

    }

    public void setUpBulk(InterconInitiator _initiator) {

        new UplinkScheduler(_initiator, this, "bulk").WorkStart();

    }

    private void UpdateThisServerInfo(long _curr_millis) {

        //Tum3Logger.DoLog(db_name, true, "[DEBUG] UpdateThisServerInfo()");

        int tmp_free_space_gb = 0;
        boolean tmp_free_space_found = false;
        try {
            if (DB_ROOT_PATH.length() > 0) {
                String tmp_last_dir = "198001";
                File dir = new File(DB_ROOT_PATH);
                for (File file: dir.listFiles()) {
                    if (file.isDirectory()) {
                        String tmp_name = file.getName();
                        if (Tum3Util.StrNumeric(tmp_name) && (4 == tmp_name.length())) {
                            tmp_name = YearExtend_Intl(tmp_name);
                            if (tmp_last_dir.compareTo(tmp_name) < 0) tmp_last_dir = tmp_name;
                            //System.out.println("[aq2j] DEBUG: subdir '" + tmp_name + "' tmp_last_dir=" + tmp_last_dir);
                        }
                    }
                }
                tmp_last_dir = tmp_last_dir.substring(2);
                File tmp_last_dir_obj = new File(DB_ROOT_PATH + tmp_last_dir);
                long tmp_space_bytes = tmp_last_dir_obj.getUsableSpace();
                tmp_free_space_found = (tmp_space_bytes > 0L);
                tmp_free_space_gb = (int)(tmp_space_bytes >> 30);
            }
        } catch (Exception ignored) {}

        //System.out.println("[aq2j] DEBUG: tmp_free_space_gb=" + tmp_free_space_gb);

        boolean tmp_raid_status_ok = true;
        boolean tmp_raid_status_found = false;
        StringBuilder tmp_raid_detail = new StringBuilder();
        try {
            String tmp_os_name = System.getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH);
            if (tmp_os_name.startsWith("linux")) {
                FileReader tmp_fr = null;
                BufferedReader tmp_br = null;
                try {
                    tmp_fr = new FileReader("/proc/mdstat");
                    tmp_br = new BufferedReader(tmp_fr);
                    String tmp_line = null;
                    boolean tmp_get_raid1_ups = false;
                    while ((tmp_line = tmp_br.readLine()) != null) {
                        //System.out.println(tmp_line);
                        if (tmp_line.contains(" active raid1 ")) tmp_get_raid1_ups = true;
                        else if (tmp_get_raid1_ups) {
                            tmp_get_raid1_ups = false;
                            tmp_raid_status_found = true;
                            String[] tmp_strs = tmp_line.split("\\[");
                            String tmp_raid1_str = tmp_strs[tmp_strs.length-1].trim();
                            if (tmp_raid1_str.length() >= 3) {
                                if (']' == tmp_raid1_str.charAt(tmp_raid1_str.length()-1)) tmp_raid1_str = tmp_raid1_str.substring(0, tmp_raid1_str.length()-1);
                                if (tmp_raid_detail.length() > 0) tmp_raid_detail.append(", ");
                                tmp_raid_detail.append(tmp_raid1_str);
                                for (int i=0; i < tmp_raid1_str.length(); i++)
                                    if (tmp_raid1_str.charAt(i) != 'U') tmp_raid_status_ok = false;
                                //System.out.println("[DEBUG] " + tmp_raid1_str + " status=" + tmp_raid_status_ok);
                            }
                        }
                    }
                } finally {
                    if (null != tmp_br) {
                        try { tmp_br.close(); } catch (Exception ignored2) {}
                        tmp_br = null;
                    }
                    if (null != tmp_fr) {
                        try { tmp_fr.close(); } catch (Exception ignored2) {}
                        tmp_fr = null;
                    }
                }
            }
        } catch (Exception e) {
            tmp_raid_status_found = false;
        }
        String tmp_raid_detail_s = tmp_raid_detail.toString().trim();
        Tum3Time tmp_time = new Tum3Time();

        //System.out.println("[aq2j] DEBUG: tmp_raid_status_found=" + tmp_raid_status_found + " tmp_raid_status_ok=" + tmp_raid_status_ok + " tmp_raid_detail=" + tmp_raid_detail_s);

        synchronized(diag_lock) {
            diag_free_space_gb = tmp_free_space_gb;
            diag_free_space_found = tmp_free_space_found;
            diag_raid_status_ok = tmp_raid_status_ok;
            diag_raid_status_found = tmp_raid_status_found;
            diag_raid_detail = tmp_raid_detail_s;
            diag_time = tmp_time;
        }

        diag_next_update = _curr_millis + 1000*60*(long)CONST_SRVINFO_UPD_MINS; // YYY
    }

    public String getThisServerInfo() {

        int tmp_free_space_gb;
        boolean tmp_free_space_found;
        boolean tmp_raid_status_ok;
        boolean tmp_raid_status_found;
        String tmp_raid_detail;
        Tum3Time tmp_time = new Tum3Time();

        synchronized(diag_lock) {
            tmp_free_space_gb = diag_free_space_gb;
            tmp_free_space_found = diag_free_space_found;
            tmp_raid_status_ok = diag_raid_status_ok;
            tmp_raid_status_found = diag_raid_status_found;
            tmp_raid_detail = diag_raid_detail;
            tmp_time = diag_time;
        }

        StringBuilder tmp_info = new StringBuilder();
        if (tmp_free_space_found) {
            tmp_info.append("free_space=" + tmp_free_space_gb + "\r\n");
            if (warn_free_space_gb > 0)
                tmp_info.append("low_space_warn=" + warn_free_space_gb + "\r\n");
        }
        if (tmp_raid_status_found) {
            tmp_info.append("raid_status=" + (tmp_raid_status_ok ? "ok" : "FAILED!") + "\r\n");
            if (tmp_raid_detail.length() > 0) tmp_info.append("raid_details=" + tmp_raid_detail + "\r\n");
        }
        if (tmp_info.length() > 0) tmp_info.append("info_time=" + tmp_time.AsString() + "\r\n");
        return tmp_info.toString();
    }

    public String getDiskWarningMsg() {

        String tmp_msg = "";

        int tmp_free_space_gb;
        boolean tmp_free_space_found;
        boolean tmp_raid_status_ok;
        boolean tmp_raid_status_found;
        String tmp_raid_detail;

        synchronized(diag_lock) {
            tmp_free_space_gb = diag_free_space_gb;
            tmp_free_space_found = diag_free_space_found;
            tmp_raid_status_ok = diag_raid_status_ok;
            tmp_raid_status_found = diag_raid_status_found;
            tmp_raid_detail = diag_raid_detail;
        }
        if (tmp_free_space_found && (warn_free_space_gb > 0) && (tmp_free_space_gb < warn_free_space_gb))
            tmp_msg = "Storage space left is " + tmp_free_space_gb + " Gb only. ";
        if (tmp_raid_status_found && !tmp_raid_status_ok)
            tmp_msg = tmp_msg + "Storage redundancy has failed (" + tmp_raid_detail + "). ";
        if (!tmp_msg.isEmpty()) tmp_msg = "IMPORTANT! STORAGE WARNING! " + tmp_msg;

        return tmp_msg;
    }

    public String getThisServerInfoExt() {

        return "conn_status=online\r\n" + "last_time=" + (new Tum3Time()).AsString() + "\r\n" + getThisServerInfo();

    }

    public void setOtherServerAllInfo(String _Info, String _Sync, Object excluded_sender) {

        OtherServerInfo = _Info;
        OtherServer_last_time = new Tum3Time();
        OtherServer_sync_info = _Sync;
        Tum3Broadcaster.DistributeFlag(this, excluded_sender); // YYY

    }

    public void setOtherServerInfo(String _Info) {

        OtherServerInfo = _Info;
        OtherServer_last_time = new Tum3Time();
        Tum3Broadcaster.DistributeFlag(this);

    }

    public void setOtherServerInfoSync(String _Sync) {

        OtherServer_sync_info = _Sync;
        Tum3Broadcaster.DistributeFlag(this);

    }

    private String getOtherServerInfoSync() { // YYY

        if (null != OtherServer_sync_info) return OtherServer_sync_info;
        else return "";

    }

    public void setOtherServerConnected(boolean _is_connected) {

        OtherServerIsConnected = _is_connected;
        //Tum3Logger.DoLog(db_name, true, "[DEBUG] OtherServerIsConnected := " + OtherServerIsConnected);
        OtherServer_last_time = new Tum3Time();
        Tum3Broadcaster.DistributeFlag(this);

    }

    private String getOtherServerInfoExt() {

        String tmp_str = "conn_status=" + (OtherServerIsConnected ? "online" : "offline") + "\r\n";
        if (null != OtherServer_last_time) tmp_str = tmp_str + "last_time=" + OtherServer_last_time.AsString() + "\r\n";
        if (null != OtherServerInfo) tmp_str = tmp_str + OtherServerInfo;

        return tmp_str;
    }

    public String getServerInfo() {

        String tmp_result_str = "";

        if (uplink_enabled) tmp_result_str = // YYY
            "[master]\r\n" + getThisServerInfoExt() +
            "[backup]\r\n" + getOtherServerInfoExt();
        else if (downlink_enabled) tmp_result_str = // YYY
            "[master]\r\n" + getOtherServerInfoExt() +
            "[backup]\r\n" + getThisServerInfoExt();
        else tmp_result_str = // YYY
            "[server]\r\n" + getThisServerInfoExt();

        if (uplink_enabled) tmp_result_str = tmp_result_str +
            "[sync]\r\n" + getThisServerInfoSync(); // YYY
        else if (downlink_enabled) tmp_result_str = tmp_result_str +
            "[sync]\r\n" + getOtherServerInfoSync(); // YYY

        return tmp_result_str;
    }

    private int LastShotId_int() {

        synchronized(FGlobalShotList) { return FGlobalShotList.size() - 1; }

    }

    public static int LastShotId(int _db_idx) {

        Tum3Db tmp_inst = getDbInstance(_db_idx, true);
        if (null == tmp_inst) return -1; // YYY
        else return tmp_inst.LastShotId_int();

    }

    private String GetShotNumber_int(int _shot_id) {

        synchronized(FGlobalShotList) { 
          if ((_shot_id >= 0) && (_shot_id < FGlobalShotList.size()))
            return FGlobalShotList.get(_shot_id);
          else
            return "";
        }

    }

    public static String GetShotNumber(int _db_idx, int _shot_id) {

        Tum3Db tmp_inst = getDbInstance(_db_idx, true);
        return tmp_inst.GetShotNumber_int(_shot_id);

    }

    public String ExternalPutTrace_int(int _db_idx, String _shot_name, int _signal_id, ByteBuffer _header, ByteBuffer _body, boolean DataIsVolatile, String _caller_ip_addr, ShotChangeMonitor _helper) {
    // See also: SrvLink.UploadOne()

        String tmp_name = "signal id <" + _signal_id + ">";
        String tmp_err_prefix = "Could not store " + tmp_name + " of " + _shot_name + ": ";
        Tum3SignalList tmpSignalList = Tum3SignalList.GetSignalList();
        int tmp_index = tmpSignalList.FindIndex(_signal_id);
        if ((tmp_index < 1) || (tmp_index > tmpSignalList.SignalCount())) return tmp_err_prefix + "signal id is not valid.";
        else {
            NameValueList tmp_entry = tmpSignalList.GetSignalEntry(tmp_index);
            tmp_name = tmp_entry.GetValueFor(Tum3SignalList.const_signal_title, tmp_name);
            tmp_err_prefix = "Could not store " + tmp_name + " of " + _shot_name + ": ";
            if (!Tum3SignalList.AllowExtUpload(tmp_entry)) return tmp_err_prefix + "not allowed";
        }

        Tum3Shot tmp_shot = null;
        try {
            tmp_shot = getShot(_shot_name, true);
        } catch (Exception e) {
            return tmp_err_prefix + e;
        }
        if (tmp_shot == null) return tmp_err_prefix + CONST_MSG_SRVLINK_ERR01;
        if ((null == tmp_shot.GetDb()) || ((tmp_shot.GetDb() != this) && (tmp_shot.GetDb() != GetMasterDb()))) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + CONST_MSG_SRVLINK_ERR02;
        }

        if (!tmp_shot.isWriteable || !Tum3cfg.isWriteable(_db_idx)) { // YYY
            tmp_shot.ShotRelease();
            return tmp_err_prefix + CONST_MSG_READONLY_NOW;
        }

        if (!(DataIsVolatile && VolatilePathPresent())) if (!(new Tum3Time().GetCurrYMD()).equals(_shot_name.substring(0,6))) { // Restrict shot date to current unless volatile.
            tmp_shot.ShotRelease();
            return tmp_err_prefix + CONST_MSG_INV_SHOT_NUMBER;
        }

        boolean tmp_ref_ok = false;
        String tmp_result = "Unknown error";
        try {
            Tum3Shot.insertHostName(_header, "[" + _caller_ip_addr + "]");
            tmp_ref_ok = true;
            tmp_shot.putTrace(_signal_id, _header, _body, _helper, DataIsVolatile);
            tmp_result = "";
        } catch (Exception e) {
            tmp_result = "Exception " + Tum3Util.getStackTrace(e);
        }
        if (!tmp_ref_ok) tmp_shot.ShotRelease();
        //if (tmp_shot != null) tmp_shot.Release();

        if (tmp_result.length() > 0)
            return tmp_err_prefix + tmp_result;
        else
            return "";
    }

    public static String ExternalPutTrace(int _db_idx, String _shot_num, int _signal_id, ByteBuffer _hdr_buf, ByteBuffer _data_buf, boolean DataIsVolatile, String _caller_ip_addr, ShotChangeMonitor _helper) {

        Tum3Db tmp_inst = getDbInstance(_db_idx, false);
        String tmp_err_msg = tmp_inst.ExternalPutTrace_int(_db_idx, _shot_num, _signal_id, _hdr_buf, _data_buf, DataIsVolatile, _caller_ip_addr, _helper);

        if (tmp_err_msg.isEmpty())
            Tum3Logger.DoLog(tmp_inst.db_name, false, "Successfully stored " + _shot_num + "." + _signal_id + " from " + _caller_ip_addr);
        else
            Tum3Logger.DoLog(tmp_inst.db_name, false, "Failed storing " + _shot_num + "." + _signal_id + " from " + _caller_ip_addr + ": " + tmp_err_msg);

        return tmp_err_msg;
    }

    private void FlushHelper_int(SingleShotWriteHelper _helper) {

        _helper.PushModifiedIds(this);

    }

    public static void FlushHelper(int _db_idx, SingleShotWriteHelper _helper) {

        Tum3Db tmp_inst = getDbInstance(_db_idx, true);
        if (null == tmp_inst) return;
        tmp_inst.FlushHelper_int(_helper);
    }

    private int GetWaitingTrig_int() {

        if (IsWaitingTrig) return 1; else return 0;

    }

    public static int GetWaitingTrig(int _db_idx) {

        Tum3Db tmp_inst = getDbInstance(_db_idx, true);
        if (null == tmp_inst) return -1; // YYY
        else return tmp_inst.GetWaitingTrig_int();

    }

    public void setWaitingTrig(boolean _value) {

        IsWaitingTrig = _value;

    }

    public static Tum3Db getDbInstance(int _db_idx) {

        return getDbInstance(_db_idx, false);

    }

    public static Tum3Db getDbInstance(int _db_idx, boolean _no_create) {

        Tum3Db tmp_inst = null, tmp_master_inst = null;
        String tmp_warning_msg1 = "", tmp_warning_msg2 = "";

        synchronized(DbCreationLock) {

            if (DbInstance == null)
                DbInstance = new Tum3Db[Tum3cfg.getGlbInstance().getDbCount()];

            tmp_inst = DbInstance[_db_idx];
            if (_no_create) return tmp_inst; // YYY
            if (tmp_inst == null) {
                tmp_inst = createInstance(_db_idx, getCfgMasterDb(_db_idx));
                DbInstance[_db_idx] = tmp_inst;

                String tmp_masterdb_name = tmp_inst.masterdb_name;
                //System.out.println("[DEBUG] tmp_masterdb_name=" + tmp_masterdb_name);
                if (!tmp_masterdb_name.isEmpty()) {
                    for (int i = 0; i < DbInstance.length; i++)
                        if (DbInstance[i] != null)
                            if (DbInstance[i].DbName().equals(tmp_masterdb_name))
                                tmp_master_inst = DbInstance[i];

                    if (tmp_master_inst == null) {
                        int tmp_master_index = Tum3cfg.getGlbInstance().getDbIndex(tmp_masterdb_name);
                        //System.out.println("[DEBUG] tmp_master_index=" + tmp_master_index);
                        if ((tmp_master_index >= 0) && (tmp_master_index < Tum3cfg.getGlbInstance().getDbCount())) {
                            String tmp_master_master_name = getCfgMasterDb(tmp_master_index);
                            if ((tmp_master_master_name.isEmpty()) && (DbInstance[tmp_master_index] == null)) {
                                tmp_master_inst = createInstance(tmp_master_index, tmp_master_master_name);
                                DbInstance[tmp_master_index] = tmp_master_inst;
                            } else {
                                if (!tmp_master_master_name.isEmpty())
                                    tmp_warning_msg1 = "Attempt to link to a master db '" + tmp_masterdb_name + "' which in turn liks to '" + tmp_master_master_name + "'";
                                if (DbInstance[tmp_master_index] != null)
                                    tmp_warning_msg2 = "Internal error: attempt to link to a master db '" + tmp_masterdb_name + "' which is already registered as '" + DbInstance[tmp_master_index].DbName() + "'";
                            }
                        } else {
                            tmp_warning_msg1 = "Master db '" + tmp_masterdb_name + "' does not exist.";
                        }
                    }
                    tmp_inst.master_db = tmp_master_inst;
                    //System.out.println("[DEBUG] assigned " + tmp_inst.db_name + " .master_db.db_name=" + tmp_inst.master_db.db_name);
                }
            }
        }

        if (tmp_master_inst != null) {
            tmp_master_inst.FinishCreation();
            tmp_master_inst.addDbClient();
        }

        if (tmp_inst != null) {
            tmp_inst.FinishCreation();
            tmp_inst.addDbClient();
        }

        if (!tmp_warning_msg1.isEmpty()) {
            if (tmp_inst != null)
                Tum3Logger.DoLog(tmp_inst.DbName(), false, tmp_warning_msg1);
            else
                Tum3Logger.DoLogGlb(false, tmp_warning_msg1);
        }

        if (!tmp_warning_msg2.isEmpty()) {
            Tum3Logger.DoLogGlb(true, tmp_warning_msg2);
        }

        return tmp_inst;
    }

    private void addDbClient() {
        // Maybe for future use.
    }

    public void releaseDbClient() {
        // Maybe for future use.
    }

    private String SymlinkShotList(StringList _shot_names, StringList _notify_list) throws Exception { // YYY

        String tmp_prev_shotSubdir = "";

        if (!HaveFilterMode) return "No path(s) configured for publishing currently.";

        for (int i = 0; i < _shot_names.size();) {

            String tmp_shot_name = _shot_names.get(i);
            String shotSubdir = tmp_shot_name.substring(0, 4);
            boolean tmp_done_shot = false;
            boolean tmp_failed_shot = false;
            boolean tmp_add_notify = false;

            for (boolean tmp_is_vol: Arrays.asList(false, true)) {

                String tmpActualPath = tmp_is_vol ? PUBLISHED_ROOT_PATH_VOL : PUBLISHED_ROOT_PATH;
                String tmpTargetPath = tmp_is_vol ? DB_REL_ROOT_PATH_VOL : DB_REL_ROOT_PATH;
                if (!tmpActualPath.isEmpty() && !tmpTargetPath.isEmpty()) {
                    File tmp_monthdir = new File(tmpActualPath + shotSubdir);
                    tmp_monthdir.mkdir();
                    if (tmp_monthdir.exists()) {
                        File tmp_shotdir = new File(tmpActualPath + shotSubdir + File.separator + tmp_shot_name);
                        File tmp_target_shotdir = new File(tmpTargetPath + shotSubdir + File.separator + tmp_shot_name);
                        try { Files.createSymbolicLink(tmp_shotdir.toPath(), tmp_target_shotdir.toPath()); tmp_add_notify = true; } catch (Exception ignored) {}
                        if (DEBUG_ON_WIN) { if (tmp_shotdir.mkdir()) tmp_add_notify = true; }
                        if (tmp_shotdir.exists()) tmp_done_shot = true;
                    } else tmp_failed_shot = true;
                }
            }
            if (!tmp_prev_shotSubdir.equals(shotSubdir)) {
                tmp_prev_shotSubdir = shotSubdir;
                AppendMonthToMasterList(1, shotSubdir, false);
            }
            if (tmp_add_notify) _notify_list.add(tmp_shot_name);
            if (tmp_done_shot && !tmp_failed_shot) i++;
            else _shot_names.remove(i);
        }

        return "";

    }

    public String PublishShots(StringList _shot_names) {

        String tmp_result = "Unknown error";
        StringList tmp_notify_list = new StringList();

        try {

            tmp_result = SymlinkShotList(_shot_names, tmp_notify_list);

        } catch(Exception e) {
            Tum3Logger.DoLog(db_name, true, e.toString() + Tum3Util.getStackTrace(e));
            return "Unexpected exception " + e.toString();
        }

        if (tmp_notify_list.size() > 0) Tum3Broadcaster.DistributeGeneralEvent(this, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_NEWSHOT, tmp_notify_list, GeneralDbDistribEvent.IS_PUBLISHING), null); // YYY
        return tmp_result;

    }
}
