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

import java.util.*;
import java.io.*;
import java.nio.*;


public class Tum3Db implements Runnable, AppStopHook {

    public  final static String CONST_MSG_READONLY_NOW = "The database is read-only at this time";
    public  final static String CONST_MSG_ACCESS_DENIED = "Access denied";
    public  final static String CONST_MSG_INV_SHOT_NUMBER = "Raw data can be stored for current date only";
    private volatile boolean TerminateRequested = false;
    private Thread db_thread;

    //   TSkipDirection = (SkipBack, SkipForth, DontSkip);
    private final static int CONST_SkipBack = 0;
    private final static int CONST_SkipForth = 1;
    private final static int CONST_DontSkip = 2;

    private final static int CONST_SHOTS_WAIT_PERIOD = 1;      // Seconds
    private int CONST_SHOTS_DISPOSE_AFTER = 30;   // Seconds
    private int CONST_SHOTS_MAX_OPEN = 100;
    private final static String TUM3_CFG_db_root_volatile = "db_root_volatile";
    private final static String TUM3_CFG_max_shots_open = "max_shots_open";
    private final static String TUM3_CFG_unused_shot_close_delay = "unused_shot_close_delay";
    private final static String TUM3_CFG_master_db = "master_db";

    private static Tum3Db[] DbInstance = null;
    private static Object DbCreationLock = new Object();
    private String DB_ROOT_PATH, DB_ROOT_PATH_VOL;
    private StringList MasterList = null;
    private Object MasterListLock = new Object(), PostCreationLock = new Object();
    private boolean creation_complete = false;

    private HashMap<String, Tum3Shot> openShots, closingShots;

    private int db_index;
    private String db_name, masterdb_name;
    private Tum3Db master_db = null;


    protected Tum3Db(int _db_idx, String _masterdb_name) {

        // Note! Constructor should not do any slow or complicated actions,
        //  because it runs with a global lock hold.
        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        masterdb_name = _masterdb_name;

        openShots = new HashMap<String, Tum3Shot>();
        closingShots = new HashMap<String, Tum3Shot>();
        DB_ROOT_PATH = Tum3cfg.getParValue(db_index, false, Tum3cfg.TUM3_CFG_db_root);
        DB_ROOT_PATH_VOL = Tum3cfg.getParValue(db_index, false, TUM3_CFG_db_root_volatile);

        CONST_SHOTS_MAX_OPEN = Tum3cfg.getIntValue(db_index, true, TUM3_CFG_max_shots_open, CONST_SHOTS_MAX_OPEN);
        CONST_SHOTS_DISPOSE_AFTER = Tum3cfg.getIntValue(db_index, true, TUM3_CFG_unused_shot_close_delay, CONST_SHOTS_DISPOSE_AFTER);
    }

    public Tum3Db GetMasterDb() {

        return master_db;

    }

    private static String getCfgMasterDb(int _db_idx) {

        //System.out.println("[DEBUG] getCfgMasterDb(" + _db_idx + ")=" + Tum3cfg.getParValue(_db_idx, false, TUM3_CFG_master_db));
        return Tum3cfg.getParValue(_db_idx, false, TUM3_CFG_master_db);

    }

    private void FinishCreation() {

        synchronized(PostCreationLock) {
            if (!creation_complete) {
                creation_complete = true;
                Tum3Logger.DoLog(db_name, false, "DEBUG: Starting database '" + db_name + "' (data_path=" + DB_ROOT_PATH + ", data_path_volatile=" + DB_ROOT_PATH_VOL + ")");
                Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_SHOTS_MAX_OPEN=" + CONST_SHOTS_MAX_OPEN);
                Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_SHOTS_DISPOSE_AFTER=" + CONST_SHOTS_DISPOSE_AFTER);
            }
        }
    }

    public String DbName() {

        return db_name;

    }

    public void run() {

        AppStopHooker.AddHook(this);

        while (!TerminateRequested) {
            try {
                Thread.sleep(CONST_SHOTS_DISPOSE_AFTER * (long)1000);
            } catch (InterruptedException e) { }
            DisposeUnusedShots(false);
        }
        Tum3Logger.DoLog(db_name, false, "DEBUG: Tum3db ver " + a.CurrentVerNum + " exiting normally.");
        DisposeUnusedShots(true);

        AppStopHooker.RemoveHook(this);
    }

    public void AppStopped() {

        TerminateRequested = true;
        db_thread.interrupt();

    }

    private void LoadMasterList() {

        synchronized (MasterListLock) {

            if (MasterList != null) return;

            //System.out.println("[DEBUG] Creating masterlist in " + db_name);
            MasterList = new StringList();
            StringList tmp_list = new StringList();

            if (DB_ROOT_PATH.length() > 0) {
                File dir = new File(DB_ROOT_PATH);
                for (File file: dir.listFiles()) {
                    if (file.isDirectory()) {
                        String tmp_name = file.getName();
                        if (Tum3Util.StrNumeric(tmp_name) && (4 == tmp_name.length())) {
                            if ('9' == tmp_name.charAt(0)) tmp_name = "19" + tmp_name;
                            else tmp_name = "20" + tmp_name;
                            //System.out.println("[aq2j] DEBUG: subdir '" + tmp_name + "'");
                            tmp_list.add(tmp_name);
                        }
                    }
                }
            }

            if (null != master_db) {
                //System.out.println("[DEBUG] Adding masterlist from " + master_db.db_name);
                master_db.LoadMasterList();
                for (int tmp_i=0; tmp_i < master_db.MasterList.size(); tmp_i++) {
                    String tmp_name = master_db.MasterList.get(tmp_i);
                    if ('9' == tmp_name.charAt(0)) tmp_name = "19" + tmp_name;
                    else tmp_name = "20" + tmp_name;
                    //System.out.println("[DEBUG] masterlist: " + tmp_name + " ?");
                    if (tmp_list.indexOf(tmp_name) < 0)
                        tmp_list.add(tmp_name);
                }
            }

            Collections.sort(tmp_list);
            for (int tmp_i=0; tmp_i < tmp_list.size(); tmp_i++)
                MasterList.add(tmp_list.get(tmp_i).substring(2));
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
        if (dir.exists() && dir.isDirectory()) {
            //System.out.println("[aq3j] Yes, exists.");
        } else {
            //System.out.println("[aq3j] No, does not.");
            if (!dir.mkdir()) throw new Exception("Failed to create month directory.");
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
                else if (tmp_j < 3) tmp_out.append("1");
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

    private StringList GetRawSubdir(String tmpSubdirName) {

        StringList tmp_list = new StringList();

        if (DB_ROOT_PATH.length() > 0) {
            File dir = new File(DB_ROOT_PATH + tmpSubdirName + File.separator);
            File tmp_files[] = dir.listFiles();
            //System.out.println("[DEBUG] + tmpSubdirName=" + tmpSubdirName);
            if (null == tmp_files) {
                // Reminder: the directory is empty or non-existent at this time. Assume there are no files in it anyway.
                //String tmp_emsg = "WARNING: index " + MasterIndex + " somehow contains unusable directory <" + tmpSubdirName + "> in GetThisDir()";
                //Tum3Logger.DoLog(db_name, true, tmp_emsg);
                //throw new Exception(tmp_emsg);
            } else for (File file: tmp_files) {
                if (file.isDirectory()) {
                    String tmp_name = file.getName();
                    //System.out.println("[aq2j] DEBUG: considering subdir '" + tmp_name + "'");
                    if ((8 <= tmp_name.length()) && (9 >= tmp_name.length())) {
                        if (tmp_name.substring(0, 4).equals(tmpSubdirName) && Tum3Util.StrNumeric(tmp_name.substring(4, 6)))
                            tmp_list.add(tmp_name.substring(4));
                    }
                }
            }
        }

        return tmp_list;
    }

    private StringList GetThisDir(int MasterIndex) throws Exception {

        if ((MasterIndex < 1) || (MasterIndex > MasterList.size())) {
            Tum3Logger.DoLog(db_name, true, "WARNING: index " + MasterIndex + " is out of range in GetThisDir()");
            return new StringList();
        }

        String tmpSubdirName = MasterList.get(MasterIndex-1);
        StringList tmp_list = GetRawSubdir(tmpSubdirName);

        if (null != master_db) {
            StringList tmp_master_sublist = null;
            synchronized (master_db.MasterListLock) {
                int tmp_j = master_db.MasterList.indexOf(tmpSubdirName);
                if (tmp_j >= 0)
                    tmp_master_sublist = master_db.GetRawSubdir(tmpSubdirName);
            }
            if (null != tmp_master_sublist)
                for (int tmp_i = 0; tmp_i < tmp_master_sublist.size(); tmp_i++) {
                    String tmp_st = tmp_master_sublist.get(tmp_i);
                    if (tmp_list.indexOf(tmp_st) < 0)
                        tmp_list.add(tmp_st);
                }
        }
        Collections.sort(tmp_list);
        return tmp_list;
    }

    public boolean VolatilePathPresent() {

        return DB_ROOT_PATH_VOL.length() > 0;

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
        if (Tum3Util.StrNumeric(tmp_master_name) && (4 == tmp_master_name.length())) {
            synchronized (MasterListLock) {
                if (MasterList == null) LoadMasterList();
                if (MasterList.indexOf(tmp_master_name) < 0)
                    MasterList.add(tmp_master_name);
            }
        }
        tmpShot.CompleteCreation(); // Note. If already exists, it opens normally as old 
        // and then raises an exception that propagates out.

        return tmpShot;

    }

    public Tum3Shot getShot(String this_shot_name, boolean _allow_master) throws Exception {

        Tum3Shot tmpShot = null;
        String tmp_name = this_shot_name.toUpperCase();

        //System.out.println("[DEBUG] getShot(): opening '" + this_shot_name + "' in " + db_name);

        //boolean tmp_need_to_complete = false;

        if (DB_ROOT_PATH.length() > 0) {
            boolean tmp_force_dispose_unused = false;
            synchronized (openShots) {
                boolean tmp_need_add = true;
                boolean tmp_try_again = false;
                do {
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
                    tmpShot = new Tum3Shot(this, DB_ROOT_PATH, DB_ROOT_PATH_VOL, tmp_name.substring(0, 4), tmp_name, false, "", null, null);
                    //tmp_need_to_complete = true;
                    tmpShot.ShotAddUser();
                    openShots.put(tmp_name, tmpShot);
                }
                tmp_force_dispose_unused = (openShots.size() > CONST_SHOTS_MAX_OPEN);
            }
            if (tmp_force_dispose_unused) DisposeUnusedShots(false);
        }

        if (null != tmpShot)
            tmpShot.CompleteCreation();

        if (_allow_master && (null != master_db)) {
            boolean tmp_local_found = false;
            if (null != tmpShot) {
                tmpShot.CompleteCreation();
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
                    //      System.out.println("[aq2j] DEBUG: DisposeUnusedShots(): '" + tmp_name + "' will now be disposed.");
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
        int tmp_j = MasterList.size() - 1;
        while (tmp_j >= 0) {
            //System.out.println("[DEBUG] LatestShotDate: " + MasterList.get(tmp_j) + " ...");
            StringList tmpList = GetThisDir(tmp_j+1);
            if (tmpList.size() == 0) tmp_j--;
            else
                return MasterList.get(tmp_j) + tmpList.get(tmpList.size() - 1).substring(0, 2);
        }

        return "";

    }

    public void PackDirectory(String thisName, int thisSkipDirection, ByteArrayOutputStream thisBuff) throws Exception {

        StringList theList;
        String RefName = "", st;
        int tmp_i, tmp_j;

        //System.out.println("[aq2j] DEBUG: in PackDirectory('" + thisName + "', " + thisSkipDirection + ")");

        if ((thisName.length() == 0) && (thisSkipDirection == 0)) {

            LoadMasterList();
            theList = MasterList;  
            thisName = "";
            RefName = "";

        } else if (thisName.length() == 9) {

            LoadMasterList();
            String Name2 = thisName.substring(5, 9);
            thisName = thisName.substring(0, 4);
            RefName = thisName;

            StringList tmpList;
            tmp_j = MasterList.indexOf(thisName);
            if (tmp_j >= 0) {
                //System.out.println("[aq2j] DEBUG: PackDirectory: Good!");
                tmpList = GetThisDir(tmp_j+1);
                if ((tmpList.size() == 0) && ((thisSkipDirection == CONST_SkipBack) || (thisSkipDirection == CONST_SkipForth))) {
                    while (((tmp_j > 0) || (thisSkipDirection == CONST_SkipForth)) && ((tmp_j < (MasterList.size()-1)) || (thisSkipDirection == CONST_SkipBack))
                            && (tmpList.size() == 0) && (!MasterList.get(tmp_j).equals(Name2))) {
                        if (thisSkipDirection == CONST_SkipBack) tmp_j--;
                        else tmp_j++;
                        if (!MasterList.get(tmp_j).equals(Name2)) {
                            tmpList = GetThisDir(tmp_j+1);
                            if (tmpList.size() > 0)
                                thisName = MasterList.get(tmp_j);
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

    public static Tum3Db getDbInstance(int _db_idx) {

        Tum3Db tmp_inst = null, tmp_master_inst = null;
        String tmp_warning_msg1 = "", tmp_warning_msg2 = "";

        synchronized(DbCreationLock) {

            if (DbInstance == null)
                DbInstance = new Tum3Db[Tum3cfg.getGlbInstance().getDbCount()];

            tmp_inst = DbInstance[_db_idx];
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

}
