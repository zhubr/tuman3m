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
package aq2j;


import java.io.*;
import java.nio.*;
import java.util.*;

import aq2db.*;
import aq2net.*;
import aq3host.*;


class TraceRequest {
    public String ShotName;
    public int SignalId;

    public TraceRequest(String thisName, int thisId) {
        ShotName = thisName;
        SignalId = thisId;
        //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> TraceRequest(" + thisName + ", " + thisId + ")");
    }

    public boolean equals(String thisName, int thisId) {

        return thisName.equals(ShotName) && (thisId == SignalId);

    }

    public boolean equals(TraceRequest other) {

        return equals(other.ShotName, other.SignalId);

    }
}

class ReqTraceListClass extends ArrayList<TraceRequest> {

    public int indexOf(String thisName, int thisId) {

        for (int i = 0; i < size(); i++) {
            TraceRequest tmp_item = get(i);
            if (tmp_item.equals(thisName, thisId))
                return i;
        }
        return -1;

    }

}

class PendingTraceListStrings extends ArrayList<String> {
}

public class SrvLink implements TumProtoConsts, SrvLinkIntf, aq3sender {

    private final static String CONST_MSG_SRVLINK_ERR01 = "Internal error opening shot name";
    private final static String CONST_MSG_SRVLINK_ERR02 = "Internal error locating shot name";
    public final static short[] decrypt_arr = {
        50, 20, 66, 255, 49, 122, 195, 254, 189, 178, 253, 26, 250, 251, 222, 
        142, 56, 95, 32, 246, 247, 243, 59, 241, 101, 229, 109, 76, 242, 239, 
        238, 237, 23, 234, 209, 53, 226, 105, 5, 221, 219, 200, 236, 34, 252, 
        153, 235, 159, 212, 233, 42, 208, 119, 231, 13, 135, 205, 132, 220, 
        227, 197, 198, 123, 215, 15, 199, 186, 190, 204, 180, 207, 36, 174, 
        169, 40, 168, 16, 177, 170, 63, 73, 24, 84, 167, 54, 94, 173, 203, 151,
        166, 154, 2, 4, 113, 145, 7, 81, 91, 140, 218, 133, 11, 48, 22, 149, 
        193, 14, 171, 125, 139, 100, 33, 121, 138, 71, 162, 130, 137, 157, 97, 
        79, 143, 155, 43, 183, 108, 115, 134, 47, 19, 82, 127, 51, 17, 44, 244, 
        141, 188, 83, 57, 31, 46, 110, 29, 52, 158, 210, 74, 38, 102, 9, 89, 
        245, 69, 88, 217, 45, 68, 128, 129, 160, 72, 112, 118, 64, 6, 249, 28, 
        35, 103, 30, 179, 18, 182, 106, 225, 96, 202, 192, 8, 185, 120, 61, 
        240, 12, 172, 181, 230, 131, 37, 114, 146, 90, 78, 116, 75, 184, 191, 
        67, 104, 223, 80, 152, 85, 187, 163, 176, 147, 248, 98, 136, 107, 25, 
        165, 62, 211, 21, 206, 1, 86, 41, 39, 196, 3, 201, 65, 111, 117, 10, 
        124, 161, 0, 92, 150, 77, 55, 58, 175, 144, 27, 148, 156, 232, 164, 
        213, 126, 228, 60, 99, 87, 70, 194, 216, 93, 224, 214
    };

    public final static byte THRD_EXTERNAL = 4;
    public final static byte THRD_INTERNAL = 5;
    private final static byte THRD_UNKNOWN = 0;

    private static volatile int dbg_serial = 0;
    public int my_dbg_serial = 0;

    private SrvLinkOwner Owner = null;
    private aq3h aq3hInstance = null;

    private final static int CONST_TRAILING_BYTES_LIMIT = 1024*1024*256; // XXX TODO!!! Make this configurable?

    private final static int STAGE_SIGN_4BYTES = 0;
    private final static int STAGE_TRAILING_LEN = 1;
    private final static int STAGE_TRAILING_BODY = 2;

    private final static String TUM3_CFG_max_out_queue_kbytes = "max_out_queue_kbytes";
    private final static String TUM3_CFG_max_out_queue_len = "max_out_queue_len";
    private final static String TUM3_CFG_max_out_buff_count = "max_out_buff_count";
    private final static String TUM3_CFG_idle_check_alive_delay = "idle_check_alive_delay";

    private final static String const_signal_title = "Title";
    private final static String const_signal_is_density = "IsDensity";

    private final static int CONST_OUT_BUFF_COUNT_MAX_default = 10;
    private final static int CONST_MAX_TRACE_OUT_QUEUE_KBYTES_default = 1;
    private final static int CONST_MAX_TRACE_OUT_QUEUE_LEN_default = 10;
    private final static int CONST_KEEPALIVE_INTERVAL_SEC_default = 20;

    private final static int CONST_MAX_TALK_OUT_QUEUE = 20;
    private final static int CONST_OUT_BUFF_WAIT_WAKEUP_SEC = 1000;
    private final static int CONST_MAX_REQ_STRING_COUNT = 1000;

    private static final int CONST_KEEPALIVE_INTERVAL_SEC[];
    private static final int CONST_MAX_TRACE_OUT_QUEUE_BYTES[];
    private static final int CONST_MAX_TRACE_OUT_QUEUE_LEN[];
    private static final int CONST_OUT_BUFF_COUNT_MAX[];

    private final static int CONST_LOGIN_FAIL_PENDING_SEC = 15;
    private final static int CONST_LOGIN_TIMEOUT_SEC = 15;

    private volatile boolean CancellingLink = false;
    private Object OutBuffFullLock = new Object();
    private Object OutBuffEmptyLock = new Object();
    private int curr_stage = STAGE_SIGN_4BYTES;
    private int curr_remaining_bytes = 4;
    private byte curr_req_code = 0;
    private int curr_req_trailing_len = 0;
    private byte[] req_header, req_size_holder, req_body;
    private volatile OutgoingBuff[] out_buffs_empty, out_buffs_full;
    private volatile int out_buffs_empty_fill, out_buffs_full_fill;
    private int out_buffs_count = 0;
    private OutgoingBuff out_buff_now_sending = null;
    private boolean WasAuthorized = false;
    private long ConnectionStartedAt;
    private volatile int FFeatureSelectWord = 0; // Moved from local.
    private boolean LoginFailedState = false;
    private long LoginFailedAt;
    private Tum3Perms UserPermissions, MasterdbUserPermissions;
    private volatile String AuthorizedLogin = "";
    private volatile byte[] bin_username = null;
    private Tum3Db dbLink = null;
    private ShotWriteHelper currWritingShotHelper = null;
    private ReqTraceListClass PausedTraceList = new ReqTraceListClass();
    private ReqTraceListClass PendingTraceList = new ReqTraceListClass();

    private boolean segmented_data_allowed = false;
    private TraceRequest segmented_TraceReq = null;
    private OutBuffContinuator segmented_data = null;
    private long segmented_Full = 0, segmented_Ofs = 0;
    private int segmented_chunk_size = 0;

    private volatile int hanging_out_trace_bytes=0, hanging_out_trace_number=0;
    private RecycledBuffContext ctxRecycledReader;
    private volatile long last_client_activity_time;
    private volatile boolean SupportOOB;
    private int keepalive_code_next;
    private volatile int keepalive_code_sent;
    private volatile boolean keepalive_sent_oob = false, keepalive_sent_inline = false;
    private volatile String oob_msg_remainder = "";
    private volatile int TalkMsgQueueFill = 0;
    private volatile GeneralDbDistribEvent[] TalkMsgQueue;
    private volatile boolean TalkMsgQueueOverflow = false;

    private volatile int RCompatVersion;
    private int RLinkKilobits, FModeratedDownloadBytes;
    private int RCompatFlags = 0;
    private volatile int FReplyQueue_size;
    private boolean FDoModerateDownloadRate;
    private volatile boolean FModerateNeedSendRequest, FModerateRequestWasSent;
    private boolean use_tracecome_x = false;

    private int found_look4ver = 0;
    private Tum3AppUpdateHelper app_helper = null;

    private int db_index;
    private String db_name;

    static {

        int tmp_db_count = Tum3cfg.getGlbInstance().getDbCount();
        Tum3cfg cfg = Tum3cfg.getGlbInstance();

        CONST_MAX_TRACE_OUT_QUEUE_BYTES = new int[tmp_db_count];
        CONST_MAX_TRACE_OUT_QUEUE_LEN = new int[tmp_db_count];
        CONST_OUT_BUFF_COUNT_MAX = new int[tmp_db_count];
        CONST_KEEPALIVE_INTERVAL_SEC = new int[tmp_db_count];

        for (int tmp_i = 0; tmp_i < tmp_db_count; tmp_i++) {
            String db_name = cfg.getDbName(tmp_i);
            CONST_MAX_TRACE_OUT_QUEUE_BYTES[tmp_i] = 1024 * Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_out_queue_kbytes, CONST_MAX_TRACE_OUT_QUEUE_KBYTES_default);
            CONST_MAX_TRACE_OUT_QUEUE_LEN[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_out_queue_len, CONST_MAX_TRACE_OUT_QUEUE_LEN_default);
            CONST_OUT_BUFF_COUNT_MAX[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_out_buff_count, CONST_OUT_BUFF_COUNT_MAX_default);
            CONST_KEEPALIVE_INTERVAL_SEC[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_idle_check_alive_delay, CONST_KEEPALIVE_INTERVAL_SEC_default);

            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_MAX_TRACE_OUT_QUEUE_BYTES=" + CONST_MAX_TRACE_OUT_QUEUE_BYTES[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_MAX_TRACE_OUT_QUEUE_LEN=" + CONST_MAX_TRACE_OUT_QUEUE_LEN[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_OUT_BUFF_COUNT_MAX=" + CONST_OUT_BUFF_COUNT_MAX[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_KEEPALIVE_INTERVAL_SEC=" + CONST_KEEPALIVE_INTERVAL_SEC[tmp_i]);
        }
    }

    class RecycledBuffContext {

        public OutgoingBuff out_buff_for_send = null;

    }

    public SrvLink(int _db_idx, SrvLinkOwner thisOwner) {
        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        dbg_serial++;
        my_dbg_serial = dbg_serial;
        SupportOOB = thisOwner.SupportOOB();
        req_header = new byte[4];
        req_size_holder = new byte[4];
        TalkMsgQueue = new GeneralDbDistribEvent[CONST_MAX_TALK_OUT_QUEUE];
        synchronized(OutBuffEmptyLock) {
            out_buffs_empty = new OutgoingBuff[CONST_OUT_BUFF_COUNT_MAX[db_index]+2];
            out_buffs_empty_fill = 0;
        }
        synchronized(OutBuffFullLock) {
            out_buffs_full = new OutgoingBuff[CONST_OUT_BUFF_COUNT_MAX[db_index]+2];
            out_buffs_full_fill = 0;
        }
        ctxRecycledReader = new RecycledBuffContext();
        req_body = null;
        Owner = thisOwner;
        ConnectionStartedAt = System.currentTimeMillis();
    }

    public Tum3Db GetDb() {

        return dbLink;

    }

    public Tum3Perms getUserPerms() {

        return UserPermissions;

    }

    public Tum3Perms getMasterdbPerms() {

        return MasterdbUserPermissions;

    }

    public String CallerNetAddr() {

        return Owner.get_transp_title() + "/" + Owner.get_transp_caller();

    }

    public String DebugTitle() {

        String tmp_st = Owner.get_transp_user();
        if (tmp_st.isEmpty() && !AuthorizedLogin.isEmpty()) tmp_st = AuthorizedLogin;
        if (!tmp_st.isEmpty()) tmp_st = tmp_st + "@";
        tmp_st = tmp_st + CallerNetAddr();
        return tmp_st;

    }

    public byte[] GetBinaryUsername() {

        return bin_username;

    }

    public String get_authorized_username() {

        return AuthorizedLogin;

    }

    public void DoLink() throws Exception {

        UpdateLastClientActivityTime();
        ConnectionStartedAt = System.currentTimeMillis();

    }

    public void CancelLink() {

        if (aq3hInstance != null) aq3hInstance.DeregisterLink(this);
        flushWritingShot();
        OutgoingBuff[] tmp_buffs = null;
        synchronized(OutBuffFullLock) { 
            if (CancellingLink) return;
            //System.out.println("[aq2j] DEBUG: CancelLink().");
            CancellingLink = true;
            if (out_buffs_full_fill > 0) {
                tmp_buffs = new OutgoingBuff[out_buffs_full_fill];
                for (int tmp_i=0; tmp_i < out_buffs_full_fill; tmp_i++)
                    tmp_buffs[tmp_i] = out_buffs_full[tmp_i];
                out_buffs_full_fill = 0;
            }
        }
        if (null != tmp_buffs)
            for (int tmp_i=0; tmp_i < tmp_buffs.length; tmp_i++)
                tmp_buffs[tmp_i].CancelData();
        if (null != segmented_data) Segmented_data_cancel();
        if (dbLink != null) {
            Tum3Broadcaster.release(dbLink, this);
            dbLink.releaseDbClient();
            dbLink = null;
        }
    }

    private boolean PreverifyReq(byte req_code, int req_trailing_len) {
        // XXX TODO!!! Actually implement some checks.
        return true;
    }

    /*
      private void WakeupServerReader() {

        // Websocket blocks inside run() on input_blocker, for CONST_WS_DST_WAKEUP_MILLIS max.
        // NIO TCP blocks inside run() on sc_selector, for CONST_TCP_CLIENT_WAKEINTERVAL max.

        if (!is_single_thread)
          synchronized(OutBuffFullLock) { OutBuffFullLock.notify(); }

      }
     */
    private OutgoingBuff GetBuff(byte thrd_ctx, RecycledBuffContext ctx, boolean may_block) throws Exception {
        return GetBuff(thrd_ctx, ctx, may_block, false);
    }

    private OutgoingBuff GetBuff(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
        return GetBuff(thrd_ctx, ctx, true, false);
    }

    private OutgoingBuff GetBuff(byte thrd_ctx, RecycledBuffContext ctx, boolean may_block, boolean try_harder) throws Exception {
        // Note: might be called from 2 threads, so need to respect thread context
        //   and always protect out_buffs by synchronized(OutBuffEmptyLock){}.
        // Note2. This function might block till it is actually possible 
        //  to provide some free buffer object. This would ultimately cause 
        //  blocking inside of SendToServer(), which is perfectly legal.
        //System.out.print("+");
        if (may_block && ((thrd_ctx == THRD_UNKNOWN) || (thrd_ctx == THRD_EXTERNAL))) throw new Exception("Internal error, may_block and thrd_ctx=" + thrd_ctx);

        OutgoingBuff tmp_buff = null;

        //if (ctx.out_buff_now_filling != null) {
        //  System.out.println("[aq2j] FATAL: <" + Thread.currentThread().getId() + "> GetBuff(" + dbg_text + "): out_buff_now_filling != null");
        //  throw new Exception("FATAL: unexpected out_buff_now_filling in GetBuff()");
        //}

        if (null != ctx) {
            tmp_buff = ctx.out_buff_for_send;
            ctx.out_buff_for_send = null;
            //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> GetBuff(" + dbg_text + "): reusing out buff, is_null=" + (tmp_buff == null));
        } else {
            //System.out.println("[aq2j] SrvLink.GetBuff(" + dbg_text + "): obtaining buff...");

            boolean tmp_wait_started = false;
            long tmp_wait_started_at = System.currentTimeMillis();

            if (THRD_INTERNAL == thrd_ctx) {
                boolean tmp_ready = false;
                while ((tmp_buff == null) && !CancellingLink && ((System.currentTimeMillis() - tmp_wait_started_at) < CONST_KEEPALIVE_INTERVAL_SEC[db_index]*1000)) {
                    synchronized(OutBuffEmptyLock) {
                        if (out_buffs_empty_fill > 0) {
                            tmp_buff = out_buffs_empty[out_buffs_empty_fill - 1];
                            out_buffs_empty_fill--;
                        } else if ((out_buffs_count < CONST_OUT_BUFF_COUNT_MAX[db_index]) || (try_harder && ((out_buffs_count < (CONST_OUT_BUFF_COUNT_MAX[db_index]+2))))) {
                            tmp_buff = new OutgoingBuff(db_index); // XXX TODO!!! Not very good - allocating memory while holding a lock.
                            out_buffs_count++;
                        } 
                    }
                    if (tmp_buff == null) {
                        if (may_block) {
                            tmp_wait_started = true;
                            //  try {
                            //    OutBuffEmptyLock.wait(CONST_OUT_BUFF_WAIT_WAKEUP_SEC); // Reminder! In single-thread arrangement simply waiting on OutBuffEmptyLock is not very usefull because there might be no other thread to wait for!
                            //  } catch(Exception e) { }
                            //if (tmp_ready) Tum3Logger.DoLog(db_name, true, "Unexpected in GetBuff: tmp_ready but no free buffers." + " Session: " + DebugTitle());
                            tmp_ready = Owner.WaitForOutputDone(CONST_OUT_BUFF_WAIT_WAKEUP_SEC);
                        } else {
                            break;
                        }
                    }
                }
            }

            if ((thrd_ctx == THRD_UNKNOWN) || (thrd_ctx == THRD_EXTERNAL))
                synchronized(OutBuffEmptyLock) {
                    if (out_buffs_empty_fill > 0) {
                        tmp_buff = out_buffs_empty[out_buffs_empty_fill - 1];
                        out_buffs_empty_fill--;
                    } else {
                        if ((out_buffs_count < CONST_OUT_BUFF_COUNT_MAX[db_index]) || (try_harder && ((out_buffs_count < (CONST_OUT_BUFF_COUNT_MAX[db_index]+2))))) {
                            tmp_buff = new OutgoingBuff(db_index); // XXX TODO. Better avoid new in synchronized.
                            out_buffs_count++;
                        }
                    }
                }

            if ((tmp_buff == null) && may_block) Tum3Logger.DoLog(db_name, true, "DEBUG: " + DebugTitle() + " GetBuff() timeout. Consider increasing " + TUM3_CFG_idle_check_alive_delay + " and/or " + TUM3_CFG_max_out_buff_count + ". Session: " + DebugTitle() + "; " + Tum3Util.getStackTraceAuto());
            //if (tmp_wait_started) System.out.print("[" + my_dbg_serial + ": GetBuff done " + (System.currentTimeMillis() - tmp_wait_started_at) + "]");
            //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> GetBuff(" + dbg_text + "): found out buff, is_null=" + (tmp_buff == null));
        }
        //ctx.out_buff_now_filling = tmp_buff;
        //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> GetBuff(" + dbg_text + "): out_buff_now_filling := tmp_buff");
        //System.out.print("-");
        return tmp_buff;
    }

    private void RefuseBuff(byte thrd_ctx, RecycledBuffContext ctx, OutgoingBuff theBuff) {
        // Note: might eventually be called from 2 threads, so need to respect thread context.

        //ctx.out_buff_now_filling = null;

        if (null != ctx) {
            ctx.out_buff_for_send = theBuff;
        } else {
            synchronized(OutBuffEmptyLock) {
                if (out_buffs_count <= CONST_OUT_BUFF_COUNT_MAX[db_index]) {
                    out_buffs_empty[out_buffs_empty_fill] = theBuff;
                    out_buffs_empty_fill++;
                    //if (!is_single_thread) OutBuffEmptyLock.notify(); // Removed because there is no longer any OutBuffEmptyLock.wait().
                } else {
                    out_buffs_count--;
                }
            }
            if (thrd_ctx != THRD_INTERNAL) {
                //System.out.println("[DEBUG] RefuseBuff: WakeupMain");
                WakeupMain();
            }
        }
    }

    private void PutBuff(byte thrd_ctx, OutgoingBuff buff, RecycledBuffContext ctx) throws Exception {
        // Note: might be called from 2 threads, so need to respect thread context.

        //if (buff != ctx.out_buff_now_filling) {
        //  System.out.println("[aq2j] FATAL: wrong buff provided to PutBuff()");
        //  throw new Exception("FATAL: wrong buff provided to PutBuff()");
        //}

        buff.CheckBuffFill();

        if (null == ctx)
            synchronized(OutBuffFullLock) {
                if (CancellingLink) throw new Exception("WARNING: PutBuff() while CancellingLink.");
                out_buffs_full[out_buffs_full_fill] = buff; // ctx.out_buff_now_filling;
                out_buffs_full_fill++;
                //System.out.println("[aq2j] out_buffs_full_fill=" + out_buffs_full_fill);
            }
        if (thrd_ctx != THRD_INTERNAL) {
            //System.out.println("[DEBUG] PutBuff: WakeupMain");
            WakeupMain();
        }

        //ctx.out_buff_now_filling = null;
        //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> PutBuff(" + dbg_text + "): out_buff_now_filling := null");
    }

    private void Process_ReportAvailVer(byte thrd_ctx, RecycledBuffContext ctx, byte req_code) throws Exception {

        //System.out.println("[aq2j] DEBUG: Process_ReportAvailVer()");
        if (REQUEST_TYPE_REPORTAVAILVERSION_FULL == req_code) app_helper = Tum3AppUpdateHelper.getFullAppHolder();
        if (REQUEST_TYPE_REPORTAVAILVERSION      == req_code) app_helper = Tum3AppUpdateHelper.getLook4AppHolder();
        if (REQUEST_TYPE_REPORTAVAILVERSION_64   == req_code) app_helper = Tum3AppUpdateHelper.getLook64AppHolder();
        if (REQUEST_TYPE_REPORTAVAILVERSION_FULL_64 == req_code) app_helper = Tum3AppUpdateHelper.getFull64AppHolder();
        if (app_helper != null) found_look4ver = app_helper.GetVersion();
        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx);
        tmpBuff.InitSrvReply(REQUEST_TYPE_AVAILVERSION, 4, 4);
        tmpBuff.putInt(found_look4ver);
        PutBuff(thrd_ctx, tmpBuff, ctx);
    }

    private boolean CheckValidLogin(String thisUser, String thisPassword) {

        boolean tmp_success = false;

        UserPermissions = Tum3Perms.CheckUserPwdAndPerms(db_index, db_name, thisUser, thisPassword);
        if (UserPermissions.isPwdOk()) {
            tmp_success = true;
            AuthorizedLogin = thisUser;
        } else {
            if (UserPermissions.isGuestAllowed()) {
                tmp_success = true;
                AuthorizedLogin = UserPermissions.guest_username;
            }
        }
        if (tmp_success && !AuthorizedLogin.isEmpty()) {
            bin_username = Tum3Util.StringToBytesRaw(AuthorizedLoginPlus());
            for (int tmp_i = 0; tmp_i < bin_username.length; tmp_i++)
                if ((bin_username[tmp_i] == 0) || (bin_username[tmp_i] == (byte)',')) bin_username[tmp_i] = (byte)'?';
        }
        String tmp_pwd_subst = "*";
        if (thisPassword.isEmpty()) tmp_pwd_subst = "";
        Tum3Logger.DoLog(db_name, false, "Login: " + DebugTitle() + " client_ver=" + RCompatVersion + " CompatFlags=" + RCompatFlags + " username='" + thisUser + "' password='" + tmp_pwd_subst /* thisPassword */ + "' auth_ok=" + tmp_success + " auth_as='" + AuthorizedLogin + "'");

        return tmp_success;
    }

    private void PrepareSecondaryLogin(int _for_db_idx, String _for_db_name, String thisUser, String thisPassword) {

        MasterdbUserPermissions = Tum3Perms.CheckUserPwdAndPerms(_for_db_idx, _for_db_name, thisUser, thisPassword);

    }

    private String AuthorizedLoginPlus() {

        return AuthorizedLogin + "." + db_name;

    }

    private void InitDbAccess() {

        if (null == dbLink) {
            dbLink = Tum3Db.getDbInstance(db_index);
            Tum3Broadcaster.addclient(dbLink, this);
        }

    }

    private int ResumeTraceRequests(String thisName, int[] thisIds) {

        int tmp_hit = 0;
        //        for (int j = 0; j < thisIds.length; j++) if (thisIds[j] > 0)
        //          if (thisIds[j] > 0) {
        //System.out.print(" {" + (thisIds[j] & GeneralDbDistribEvent.ID_MASK) + "} ");
        //          }
        { // synchronized (PendingTraceList) // Removed synchronized
            for (int i = PausedTraceList.size() - 1; i >= 0; i--) {
                TraceRequest tmp_item = PausedTraceList.get(i);
                boolean tmp_resume_it = false;
                if (thisName.equals(tmp_item.ShotName))
                    for (int j = 0; j < thisIds.length; j++) if (thisIds[j] > 0)
                        if ((thisIds[j] & GeneralDbDistribEvent.ID_MASK) == tmp_item.SignalId) {
                            //System.out.println("[DEBUG] resuming: " + tmp_item.ShotName + "." + tmp_item.SignalId);
                            tmp_resume_it = true;
                            thisIds[j] = 0; //Integer.MAX_VALUE;
                            //System.out.print(" {thisIds[" + j + "] := " + thisIds[j] + "} ");
                            break;
                        }
                if (tmp_resume_it) {
                    tmp_hit++;
                    PausedTraceList.remove(i);
                    PendingTraceList.add(tmp_item);
                }
            }
            for (int j = 0; j < thisIds.length; j++) if (thisIds[j] > 0)
                if ((thisIds[j] & GeneralDbDistribEvent.ID_WAS_WAITING) != 0) {
                    //System.out.print(" {-" + (thisIds[j] & GeneralDbDistribEvent.ID_MASK) + "} ");
                    thisIds[j] = 0; //Integer.MAX_VALUE;
                    tmp_hit++;
                }
            //System.out.println("[DEBUG] PausedTraceList.size()=" + PausedTraceList.size());
        }
        //System.out.println("[DEBUG] ResumeTraceRequests: tmp_hit=" + tmp_hit);
        return tmp_hit;
    }

    private boolean NoPauseOut() {

        return 
                (!(FModerateNeedSendRequest || FModerateRequestWasSent) || !FDoModerateDownloadRate)
                &&
                ((hanging_out_trace_bytes < CONST_MAX_TRACE_OUT_QUEUE_BYTES[db_index]) && (hanging_out_trace_number < CONST_MAX_TRACE_OUT_QUEUE_LEN[db_index]));

    }

    private boolean GetTracesContinue(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        boolean tmp_buff_used = false;
        boolean tmpModerateNeedSendRequest = false;
        String tmp_warn_txt = "";

        if (dbLink == null) {
            return false;
        }

        //System.out.println("[DEBUG] GetTracesContinue()...");
        TraceRequest tmp_trace_request = null;
        //String       tmp_trace_idx = "";
        boolean tmp_sent_some = false;
        do {
            tmp_trace_request = null;
            OutBuffContinuator tmp_trace_data = null;
            { // synchronized (PendingTraceList)
                //System.out.print("P="+PendingTraceList.size() + "/hb=" + hanging_out_trace_bytes + "/hc=" + hanging_out_trace_number + " ");
                if ((segmented_TraceReq == null) && (0 == PendingTraceList.size())) break;
                if (NoPauseOut()) {
                    //System.out.print("(Go)");
                    if (segmented_TraceReq != null) {
                        tmp_trace_request = segmented_TraceReq;
                        tmp_trace_data = segmented_data;
                        tmp_trace_data.AddUser();
                    } else {
                        tmp_trace_request = PendingTraceList.get(0);
                        //tmp_trace_idx = PendingTraceNames.get(0);
                        PendingTraceList.remove(0);
                        //PendingTraceNames.remove(0);
                    }
                } else {
                    //System.out.print("(Wait)");
                    //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> in GetTracesContinue(): pausing. FModerateNeedSendRequest=" + FModerateNeedSendRequest + " FModerateRequestWasSent=" + FModerateRequestWasSent);
                }
            }
            if (tmp_trace_request != null) {
                //System.out.print("(A)");
                //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> in GetTracesContinue(): getting buf for "+tmp_trace_request.ShotName+"."+tmp_trace_request.SignalId+" ...");
                OutgoingBuff tmp_for_recycle = null;
                if (ctx != null ) tmp_for_recycle = ctx.out_buff_for_send;
                OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx, false);
                // Note: THRD_SERVER_READER prohibits waiting, but always provides 
                //  a usable recycled buff ctx instead. Therefore, GetBuff() can not fail here!
                //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> in GetTracesContinue(): got buf for "+tmp_trace_request.ShotName+"."+tmp_trace_request.SignalId+" OK.");
                if (null == tmpBuff) {
                    { // synchronized (PendingTraceList)
                        if (tmp_trace_request == segmented_TraceReq)
                            tmp_trace_data.close();
                        else
                            PendingTraceList.add(0, tmp_trace_request);
                        //PendingTraceNames.add(0, tmp_trace_idx);
                    }
                    break;
                } else
                    tmp_buff_used = (tmp_for_recycle == tmpBuff);

                //System.out.print("(B)");
                if (tmp_trace_request != segmented_TraceReq) {
                    Tum3Shot tmp_shot = dbLink.getShot(tmp_trace_request.ShotName, true);
                    if (tmp_shot != null) {
                        tmp_trace_data = tmp_shot.getTraceReader(tmp_trace_request.SignalId, use_tracecome_x);
                        tmp_shot.ShotRelease(); // Moved here from below
                        tmp_shot = null;
                        if (tmp_trace_data != null) {
                            if (tmp_trace_data.WithWarning()) tmp_warn_txt += tmp_trace_request.ShotName + ":" + tmp_trace_request.SignalId + ", ";
                            if (tmp_trace_data.PleaseWait()) {
                                { // synchronized (PendingTraceList)
                                    PausedTraceList.add(tmp_trace_request);
                                }
                                RefuseBuff(thrd_ctx, ctx, tmpBuff);
                                tmp_buff_used = false;
                                continue;
                            }
                        }
                    }
                }

                tmp_sent_some = true;
                int tmp_this_trace_len = 0;
                int tmp_this_trace_num = 0;
                boolean tmp_is_segment = false;

                //System.out.print("(C)");
                if (tmp_trace_data == null)
                    tmpBuff.InitSrvReply(REQUEST_TYPE_TRACENOTAVAIL, 24, 24);
                else {
                    int seg_limit = FModeratedDownloadBytes;
                    //seg_limit = 50000; // debug only!
                    tmp_this_trace_num = 1;
                    if (segmented_data_allowed && (segmented_data == null) && (tmp_trace_data.getFullSizeX() > seg_limit)) {
                        tmp_trace_data.ForceXByte(); // For easier parsing, always provide a trailing status byte in segmented mode, even if not used for anything.
                        segmented_TraceReq = tmp_trace_request;
                        segmented_data = tmp_trace_data;
                        //System.out.println("Segmented start: segmented_data.AddUser()");
                        segmented_data.AddUser();
                        segmented_Full = segmented_data.getFullSizeX();
                        segmented_Ofs = 0;
                        long tmp_seg_count = (segmented_Full + seg_limit - 1) / seg_limit;
                        segmented_chunk_size = (int)((segmented_Full + tmp_seg_count - 1) / tmp_seg_count);
                        if (segmented_chunk_size > seg_limit) segmented_chunk_size = seg_limit;
                        if (segmented_chunk_size <= 0) segmented_chunk_size = seg_limit;
                    }
                    if (segmented_TraceReq != null) {
                        //System.out.print("@");
                        long tmp_seg_size = segmented_Full - segmented_Ofs;
                        if (tmp_seg_size > segmented_chunk_size) tmp_seg_size = segmented_chunk_size;
                        if (tmp_seg_size <= 0) throw new Exception("Internal error: tmp_seg_size=" + tmp_seg_size);
                        tmp_this_trace_len = (int)tmp_seg_size;
                        //System.out.println("[DEBUG] tmp_seg_size=" + tmp_seg_size + ", seg_limit=" + seg_limit);
                        boolean tmp_is_last_seg = (segmented_Ofs + tmp_seg_size) >= segmented_Full; // segmented_data.IsLastSegment()
                        tmpBuff.SetSegment(segmented_Ofs, tmp_this_trace_len, tmp_is_last_seg);
                        segmented_Ofs += tmp_seg_size;
                        if (tmp_is_last_seg) {
                            //System.out.println("Segmented end: Segmented_data_cancel()");
                            Segmented_data_cancel();
                        }
                        tmp_is_segment = true;
                    } else {
                        tmp_this_trace_len = tmp_trace_data.getFullSizeX();
                        tmpBuff.SetSegment(0, tmp_this_trace_len, true);
                    }

                    /*

  Simplified layout for segmented dataflow:
  =========================================
{
    OutBuff.SetSegment(...varies...);
    OutBuff.InitSrvReply();
} might repeat;

{
    outbound.AcceptFrom(); -->> OutBuff.SendToByteArray();
    OutBuff.SentAll();
    OutBuff.CancelData();
} might repeat;

                     */

                    int tmp_dbg_hanging_out_trace_bytes = -1;
                    int tmp_dbg_hanging_out_trace_number = -1;
                    { // synchronized (PendingTraceList) // Reminder: locking on PendingTraceList is no longer necessary because of elimination of multithreading.
                        hanging_out_trace_bytes += tmp_this_trace_len;
                        hanging_out_trace_number += tmp_this_trace_num;
                        tmp_dbg_hanging_out_trace_bytes = hanging_out_trace_bytes;
                        tmp_dbg_hanging_out_trace_number = hanging_out_trace_number;

                        if (FDoModerateDownloadRate) {
                            FReplyQueue_size += tmp_this_trace_len;
                            if ((FReplyQueue_size >= FModeratedDownloadBytes) && !FModerateRequestWasSent && !FModerateNeedSendRequest) {
                                //System.out.println("[aq2j] DEBUG: FModerateNeedSendRequest := true");
                                FModerateNeedSendRequest = true;
                                tmpModerateNeedSendRequest = true;
                            }
                        }
                    }
                    //System.out.println("[aq2j] DEBUG: in GetTracesContinue(): hng=" + tmp_dbg_hanging_out_trace_bytes + "," + tmp_dbg_hanging_out_trace_number);

                    if (tmp_is_segment) {
                        tmpBuff.InitSrvReply(REQUEST_TYPE_TRACECOME_S, 24+8+8+8, 24+8+8+8, tmp_trace_data);
                    } else {
                        byte tmp_opcode = REQUEST_TYPE_TRACECOME;
                        if (tmp_trace_data.withTrailingStatus()) tmp_opcode = REQUEST_TYPE_TRACECOME_X;
                        tmpBuff.InitSrvReply(tmp_opcode, 24, 24, tmp_trace_data);
                    }
                }
                tmpBuff.putString(Tum3Util.StringToPasString(tmp_trace_request.ShotName, 15));
                tmpBuff.putInt(tmp_trace_request.SignalId);
                int HAccessOptions = 0;
                if (tmp_trace_data != null) HAccessOptions = 0x03 & tmp_trace_data.getEditedByte();
                tmpBuff.putInt(HAccessOptions);
                if (tmp_is_segment) {
                    tmpBuff.putLong(tmp_trace_data.getFullSizeX());
                    tmpBuff.putLong(tmpBuff.getSegOfs());
                    //System.out.print("(" + tmpBuff.getSegOfs() + "/" + tmp_trace_data.getFullSizeX() + ")");
                    tmpBuff.putLong(0); // Reserved.
                }
                try {
                    PutBuff(thrd_ctx, tmpBuff, ctx);
                    //System.out.println("["+tmp_trace_request.ShotName+"."+tmp_trace_request.SignalId+";" + (tmp_trace_data != null) + "]");
                    //if (tmp_trace_request.SignalId == -1) System.out.println("["+ System.currentTimeMillis() + "] " + tmp_trace_request.ShotName+"."+tmp_trace_request.SignalId+";" + (tmp_trace_data != null));
                    //System.out.print("*");
                } catch (Exception e) {
                    Tum3Logger.DoLog(db_name, true, "WARNING: exception in GetTracesContinue()." + " Session: " + DebugTitle());
                    tmpBuff.CancelData();
                    throw e;
                }
            }
            //String tmp_1 = "0", tmp_2 = "0", tmp_3 = "0";
            //if (tmp_trace_request != null) tmp_1 = "1";
            //if (ctx != null /* ctx.IsRecycledReader */) tmp_2 = "1";
            //if (tmp_sent_some) tmp_3 = "1";
            //System.out.print("<" + tmp_1 + tmp_2 + tmp_3 + ">");
        } while ((tmp_trace_request != null) && !((ctx != null) && tmp_sent_some));

        if (tmpModerateNeedSendRequest) {
            { // synchronized (PendingTraceList)
                FModerateRequestWasSent = true;
                FModerateNeedSendRequest = false;
            }
            //System.out.println("[aq2j] DEBUG: DoSendDownloadPaused()");
            if (!DoSendDownloadPaused(thrd_ctx, null)) {
                //System.out.println("[aq2j] DEBUG: DoSendDownloadPaused() failed !!!!!!!!!");
                { // synchronized (PendingTraceList)
                    FModerateRequestWasSent = false;
                    FModerateNeedSendRequest = true;
                }
            }
            //System.out.println("[aq2j] DEBUG: DoSendDownloadPaused() done.");
        }

        if (!tmp_warn_txt.isEmpty()) _NewMessageBoxCompat(thrd_ctx, "WARNING: both raw and variable data present for " + tmp_warn_txt + "likely erroneously.", false);

        return tmp_buff_used;
    }


    private void Segmented_data_cancel() {

        segmented_TraceReq = null;
        segmented_data.close();
        segmented_data = null;
        segmented_Full = 0;
        segmented_Ofs = 0;

    }

    private void TrySendContinuationReq(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        boolean tmpModerateNeedSendRequest = false;

        { // synchronized (PendingTraceList)
            tmpModerateNeedSendRequest = FModerateNeedSendRequest;
            if (tmpModerateNeedSendRequest) {
                FModerateRequestWasSent = true;
                FModerateNeedSendRequest = false;
            }
        }
        //System.out.println("[aq2j] DEBUG: DoSendDownloadPaused()");
        if (tmpModerateNeedSendRequest)
            if (!DoSendDownloadPaused(thrd_ctx, ctx)) {
                //System.out.println("[aq2j] DEBUG: DoSendDownloadPaused() 2 failed !!!!!!!!!");
                { // synchronized (PendingTraceList)
                    FModerateRequestWasSent = false;
                    FModerateNeedSendRequest = true;
                }
            }
    }

    private void Process_GetTrace(byte thrd_ctx, byte[] req_body, int req_trailing_len, boolean refuse) throws Exception {

        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, false, "Internal error: no dbLink in Process_GetTrace");
            throw new Exception("no dbLink in Process_GetTrace");
        }

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
        tmpBB.limit(req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);

        try {
            while ((tmpBB.position()+12) < req_trailing_len) {
                int tmp_str_len = tmpBB.get();
                if ((tmp_str_len < 8) || (tmp_str_len > 10) || ((tmpBB.position()+tmp_str_len+4) > req_trailing_len)) throw new Exception("[aq2j] WARNING: invalid name length");
                StringBuffer tmp_shot_name = new StringBuffer();
                for (int tmp_i=0; tmp_i < tmp_str_len; tmp_i++) tmp_shot_name.append((char)tmpBB.get());
                if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("[aq2j] WARNING: invalid request");
                int tmp_id_count = tmpBB.getInt();
                int tmp_id_passed = 0;
                //IntList tmp_id_list = new IntList();
                String tmp_shot = tmp_shot_name.toString();
                //System.out.println("[aq2j] DEBUG: tmp_shot=" + tmp_shot + " tmp_id_count=" + tmp_id_count);
                { // synchronized (PendingTraceList)
                    while (((tmpBB.position()+3) < req_trailing_len) && (tmp_id_passed < tmp_id_count)) {
                        tmp_id_passed++;
                        int tmp_sign = tmpBB.getInt();
                        String tmp_key = tmp_shot + "." + tmp_sign;
                        //System.out.print(tmp_sign + ",");
                        //if (tmp_sign == -1) System.out.println("[" + System.currentTimeMillis() + "] " + tmp_shot + "." + tmp_sign + " fetch!");
                        if (refuse) {
                            if (segmented_TraceReq != null) if (segmented_TraceReq.equals(tmp_shot, tmp_sign))
                                Segmented_data_cancel();
                            int tmp_idx = PendingTraceList.indexOf(tmp_shot, tmp_sign); // PendingTraceNames.indexOf(tmp_key);
                            if (tmp_idx >= 0) {
                                //System.out.println("[aq2j] DEBUG: refused " + tmp_shot + "." + tmp_sign);
                                PendingTraceList.remove(tmp_idx);
                            }
                            tmp_idx = PausedTraceList.indexOf(tmp_shot, tmp_sign);
                            if (tmp_idx >= 0) PausedTraceList.remove(tmp_idx);
                        } else {
                            boolean tmp_found = (PendingTraceList.indexOf(tmp_shot, tmp_sign) >= 0) 
                                    || (PausedTraceList.indexOf(tmp_shot, tmp_sign) >= 0);
                            if (!tmp_found && (segmented_TraceReq != null)) if (segmented_TraceReq.equals(tmp_shot, tmp_sign)) tmp_found = true;
                            if (!tmp_found)
                                PendingTraceList.add(new TraceRequest(tmp_shot, tmp_sign));
                            //System.out.println("[aq2j] DEBUG: Get trace: " + tmp_shot + "." + tmp_sign);
                        }
                    }
                }
                //System.out.println("");
                //System.out.println("[aq2j] DEBUG: tmp_shot=" + tmp_shot + " end.");
                //GetTracesForShot(, tmp_id_list);
            }
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "WARNING: unexpected format request in Process_GetTrace() ignored." + " Session: " + DebugTitle());
        }
        if (!refuse)
            GetTracesContinue(thrd_ctx, null);
    }


    private void Process_GetConfigs(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {

        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_GetConfigs." + " Session: " + DebugTitle());
            throw new Exception("no dbLink in Process_GetConfigs");
        }

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
        tmpBB.limit(req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);

        String tmp_conf_names = "";
        try {
            StringBuffer tmp_buff = new StringBuffer();
            while (tmpBB.position() < req_trailing_len) tmp_buff.append((char)tmpBB.get());
            tmp_conf_names = tmp_buff.toString();
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "WARNING: unexpected format request in Process_GetConfigs() ignored." + " Session: " + DebugTitle());
        }

        //System.out.println("[aq2j] DEBUG: Process_GetConfigs(): <" + tmp_conf_names +">");
        if (tmp_conf_names.length() > 0) {
            NameValueList tmp_conf = new NameValueList();
            Tum3SignalList.GetSignalList().GetConfs(tmp_conf_names, tmp_conf);
            int tmp_size = 0;
            for (int tmp_k = 0; tmp_k < tmp_conf.Count(); tmp_k++) 
                tmp_size += 4 + tmp_conf.GetName(tmp_k).length() + 4 + tmp_conf.GetBody(tmp_k).length();
            OutgoingBuff tmpBuff = GetBuff(thrd_ctx, null);
            tmpBuff.InitSrvReply(REQUEST_TYPE_CONFIGSFETCH, tmp_size, tmp_size);
            for (int tmp_k = 0; tmp_k < tmp_conf.Count(); tmp_k++) {
                String tmp_name = tmp_conf.GetName(tmp_k);
                String tmp_body = tmp_conf.GetBody(tmp_k);

                //if (tmp_k == 0) {
                //String line = tmp_body.substring(0, 35);
                //System.out.println("[DEBUG]: Process_GetConfigs1: <" + line + "><" + Tum3Util.StrHexDump(line) + ">");
                //}
                tmpBuff.putInt(tmp_name.length());
                tmpBuff.putString(tmp_name);
                tmpBuff.putInt(tmp_body.length());
                //ByteArrayOutputStream tmp_dir_buf = new ByteArrayOutputStream();
                //byte[] tmp_b = Tum3Util.StringToBytesRaw(tmp_body);
                /*
if (tmp_k == 0) {
StringBuffer line = new StringBuffer();
for (int tmp_o = 0; tmp_o < 35; tmp_o++) line.append(" " + Integer.toHexString(0xFF & tmp_b[tmp_o]));
System.out.println("[DEBUG]: Process_GetConfigs2: <" + line.toString() + ">");
}
                 */

                //tmp_dir_buf.write(tmp_b);
                //tmpBuff.putStream(tmp_dir_buf);
                tmpBuff.putBytes(Tum3Util.StringToBytesRaw(tmp_body));
            }
            try {
                PutBuff(thrd_ctx, tmpBuff, null);
            } catch (Exception e) {
                tmpBuff.CancelData();
                throw e;
            }
        }

    }


    private ByteBuffer CreateUsrListHeadBB(String _name, ByteBuffer _bb2) {

        ByteBuffer tmp_bb1 = ByteBuffer.allocate(4 + 4 + _name.length() + 4);
        int tmp_usr_list_len = 0;
        if (_bb2 != null) tmp_usr_list_len = _bb2.position();
        tmp_bb1.order(ByteOrder.LITTLE_ENDIAN);
        tmp_bb1.putInt(TumProtoConsts.tum3misc_userlist);
        tmp_bb1.putInt(_name.length());
        tmp_bb1.put(Tum3Util.StringToBytesRaw(_name));
        tmp_bb1.putInt(tmp_usr_list_len);
        return tmp_bb1;

    }


    private void Process_GetMiscInfos(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {

        if (!WasAuthorized) {
            throw new Exception("Not authorized in Process_GetMiscInfos");
        }

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
        tmpBB.limit(req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);

        String tmp_dbg_str = "";
        String[] tmp_info_names = null;
        try {
            StringBuffer tmp_buff = new StringBuffer();
            while (tmpBB.position() < req_trailing_len) tmp_buff.append((char)tmpBB.get());
            tmp_dbg_str = tmp_buff.toString();
            tmp_info_names = tmp_dbg_str.split(",");
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "WARNING: unexpected format request in Process_GetMiscInfos() ignored." + " Session: " + DebugTitle());
        }

        //System.out.println("[aq2j] DEBUG: Process_GetMiscInfos(): <" + tmp_dbg_str +">");
        ByteArrayOutputStreamX temp_storage = new ByteArrayOutputStreamX();
        for (int tmp_i = 0; tmp_i < tmp_info_names.length; tmp_i++) {
            String tmp_keyword = tmp_info_names[tmp_i];
            if (TUM3_KEYWORD_files.equals(tmp_keyword))
                Tum3CollateralUpdateHelper.LoadAllTo(db_name, temp_storage);
            else if (TUM3_KEYWORD_users.equals(tmp_keyword)) {
                ByteBuffer tmp_bb2 = Tum3Broadcaster.GetUserList(dbLink);
                ByteBuffer tmp_bb1 = CreateUsrListHeadBB(tmp_keyword, tmp_bb2);
                temp_storage.write(tmp_bb1.array());
                if (tmp_bb2 != null) if (tmp_bb2.position() > 0) temp_storage.write(tmp_bb2.array(), 0, tmp_bb2.position());
            } else ;
        }
        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, null);
        tmpBuff.InitSrvReply(REQUEST_TYPE_MISC_FETCH, temp_storage.size(), temp_storage.size());
        tmpBuff.putStream(temp_storage);
        try {
            PutBuff(thrd_ctx, tmpBuff, null);
        } catch (Exception e) {
            tmpBuff.CancelData();
            throw e;
        }

    }


    private void Process_AppUpdate(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        //System.out.println("[aq2j] DEBUG: Process_AppUpdate()");
        byte[] tmp_buff = null;
        if (app_helper != null) tmp_buff = app_helper.GetBody(found_look4ver);
        int tmp_fsize = 0;
        if (tmp_buff != null) tmp_fsize = tmp_buff.length;

        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx);
        tmpBuff.InitSrvReply(REQUEST_TYPE_PROGRAMFILECOMING, tmp_fsize, tmp_fsize);
        if (tmp_fsize > 0) tmpBuff.putBytes(tmp_buff);
        try {
            PutBuff(thrd_ctx, tmpBuff, ctx);
        } catch (Exception e) {
            tmpBuff.CancelData();
            throw e;
        }
    }


    private void Process_ConfigsSave(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {

        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_ConfigsSave." + " Session: " + DebugTitle());
            throw new Exception("no dbLink in Process_ConfigsSave");
        }

        String tmp_result = Tum3Db.CONST_MSG_ACCESS_DENIED;
        StringBuffer tmp_ext_result = new StringBuffer();

        if (UserPermissions.isSignListEditingAllowed()) {

            tmp_result = "Unknown error processing new configuration";

            ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
            tmpBB.limit(req_trailing_len);
            tmpBB.order(ByteOrder.LITTLE_ENDIAN);

            NameValueList tmp_list = new NameValueList();
            try {
                while ((tmpBB.position()+10) < req_trailing_len) {
                    int tmp_str_len = tmpBB.getInt();
                    if ((tmp_str_len < 2) || (tmp_str_len > 40) || ((tmpBB.position()+tmp_str_len+4) > req_trailing_len)) throw new Exception("[aq2j] WARNING: invalid conf name length in Process_ConfigsSave()");
                    StringBuffer tmp_conf_name = new StringBuffer();
                    for (int tmp_i=0; tmp_i < tmp_str_len; tmp_i++) tmp_conf_name.append((char)tmpBB.get()); // XXX FIXME! Simplify this.
                    if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("[aq2j] WARNING: invalid request found in Process_ConfigsSave()");
                    tmp_str_len = tmpBB.getInt();
                    //System.out.println("[DEBUG] tmp_str_len=" + tmp_str_len + ", position=" + tmpBB.position() + ", req_trailing_len=" + req_trailing_len);
                    if ((tmp_str_len < 1) || (tmp_str_len > 2000000) || ((tmpBB.position()+tmp_str_len) > req_trailing_len)) throw new Exception("[aq2j] WARNING: invalid conf size length in Process_ConfigsSave()");
                    byte[] tmp_arr = new byte[tmp_str_len];
                    tmpBB.get(tmp_arr);
                    String tmp_conf_body = Tum3Util.BytesToStringRaw(tmp_arr);
                    tmp_list.AddNameVal(tmp_conf_name.toString(), tmp_conf_body);
                }
                //System.out.println("[aq2j] DEBUG: Process_ConfigsSave():");
                //for (int tmp_k = 0; tmp_k < tmp_list.Count(); tmp_k++) 
                //System.out.println("[aq2j] DEBUG: <" + tmp_list.GetName(tmp_k) + ">:" + tmp_list.GetBody(tmp_k));

                tmp_result = Tum3SignalList.PutSignalList(db_index, tmp_list, tmp_ext_result);

            } catch (Exception e) {

                tmp_result = "Error processing new configuration: " + Tum3Util.getStackTrace(e);
                //System.out.println("[aq2j] WARNING: unexpected format request in Process_ConfigsSave() ignored (" + Tum3Util.getStackTrace(e) + ")");
            }
        }

        String tmp_final_output = "";
        if (RCompatVersion < 354) {
            tmp_final_output = tmp_result;
        } else {
            tmp_final_output = Tum3SignalList.ConfigUploadResult(tmp_result, tmp_ext_result);
        }

        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, null);
        tmpBuff.InitSrvReply(REQUEST_TYPE_CONFIGSUPLRES, tmp_final_output.length(), tmp_final_output.length());
        tmpBuff.putString(tmp_final_output);
        try {
            PutBuff(thrd_ctx, tmpBuff, null);
        } catch (Exception e) {
            tmpBuff.CancelData();
            throw e;
        }

    }

    private void Process_DensitySave(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {

        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_DensitySave." + " Session: " + DebugTitle());
            throw new Exception("no dbLink in Process_DensitySave");
        }

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
        tmpBB.limit(req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);

        String tmp_result = "Density save failed.";

        String tmp_attempted_shot = "(none)";
        int    tmp_attempted_id = 0;

        try {
            int tmp_str_len = tmpBB.get();
            if ((tmp_str_len < 7) || (tmp_str_len > 20) || ((tmpBB.position()+tmp_str_len+4) > req_trailing_len)) throw new Exception("[aq2j] WARNING: invalid name length in Process_DensitySave()");
            StringBuffer tmp_shot_name = new StringBuffer();
            for (int tmp_i=0; tmp_i < tmp_str_len; tmp_i++) tmp_shot_name.append((char)tmpBB.get()); // XXX FIXME! Simplify this.
            if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("[aq2j] WARNING: invalid request found in Process_DensitySave()");
            tmp_str_len = tmpBB.getInt();
            //System.out.println("[DEBUG] tmp_str_len=" + tmp_str_len + ", position=" + tmpBB.position() + ", req_trailing_len=" + req_trailing_len);
            if ((tmp_str_len < 1) || (tmp_str_len > 2000000)) throw new Exception("[aq2j] WARNING: invalid signal id in Process_DensitySave()");
            int tmp_body_size = req_trailing_len - tmpBB.position();
            byte[] tmp_upd_arr = new byte[tmp_body_size];
            tmpBB.get(tmp_upd_arr);
            tmp_attempted_shot = tmp_shot_name.toString();
            tmp_attempted_id = tmp_str_len;
            tmp_result = UpdateDensityData(tmp_shot_name.toString(), tmp_str_len, tmp_upd_arr);

        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "WARNING: unexpected format request in Process_DensitySave() ignored. " + " Session: " + DebugTitle() + " (" + Tum3Util.getStackTrace(e) + ")");
            tmp_result = "Density save failed with: " + e;
        }

        String tmp_comment_txt = tmp_result;
        if (tmp_result.isEmpty()) tmp_comment_txt = "success";
        Tum3Logger.DoLog(db_name, false, "Density update by " + DebugTitle() + " with result: " + tmp_comment_txt);

        if (tmp_result.length() > 0)
            _NewMessageBoxCompat(thrd_ctx, tmp_result, false);

    }

    private void _NewMessageBoxCompat(byte thrd_ctx, String the_text, boolean _with_logger) throws Exception {

        if (_with_logger) Tum3Logger.DoLog(db_name, true, the_text);
        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, null);
        tmpBuff.InitSrvReply(REQUEST_TYPE_INFORMATION_TEXT, the_text.length(), the_text.length());
        tmpBuff.putString(the_text);
        try {
            PutBuff(thrd_ctx, tmpBuff, null);
        } catch (Exception e) {
            tmpBuff.CancelData();
            throw e;
        }

    }

    private void Process_GetDirList(byte thrd_ctx, byte[] req_body, int req_trailing_len, RecycledBuffContext ctx) throws Exception {
        String[] tmp_strings = Tum3Util.FetchAsStrings(req_body, req_trailing_len, CONST_MAX_REQ_STRING_COUNT, 1);

        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_GetDirList." + " Session: " + DebugTitle());
            throw new Exception("no dbLink in Process_GetDirList");
        }

        ByteArrayOutputStreamX tmp_dir_buf = new ByteArrayOutputStreamX();
        for (int tmp_i=0; tmp_i < tmp_strings.length; tmp_i++)
            if (tmp_strings[tmp_i].length() > 0) {
                tmp_dir_buf.reset();
                //System.out.println("[aq2j] DEBUG: req dir='" + tmp_strings[tmp_i].substring(1) + "' " + ((int)tmp_strings[tmp_i].charAt(0)));
                dbLink.PackDirectory(tmp_strings[tmp_i].substring(1), (int)tmp_strings[tmp_i].charAt(0), tmp_dir_buf);

                OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx); // XXX FIXME! Check if ctx could be used erroneously more than once or remove the loop.
                tmpBuff.InitSrvReply(REQUEST_TYPE_DIRECTORYCOME, tmp_dir_buf.size(), tmp_dir_buf.size());
                tmpBuff.putStream(tmp_dir_buf);
                PutBuff(thrd_ctx, tmpBuff, ctx);
            }
    }

    private void Process_AgentInfo(byte[] req_body, int req_trailing_len) throws Exception {
        /*
  TAgentInfo = packed record
    RCompatVersion: longint;
    RLinkKilobits: longint;
  end;
         */
        ByteBuffer tmpBB = ByteBuffer.wrap(req_body, 0, req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);
        if (tmpBB.remaining() >= 4) RCompatVersion = tmpBB.getInt();
        if (tmpBB.remaining() >= 4) RLinkKilobits = tmpBB.getInt();
        if (tmpBB.remaining() >= 4) RCompatFlags = tmpBB.getInt();

        FDoModerateDownloadRate = (RCompatVersion >= 231);
        use_tracecome_x = (RCompatVersion >= 332);
        segmented_data_allowed = (RCompatVersion >= 373);
        if (RLinkKilobits == 0) RLinkKilobits = 100*1024;
        if (RLinkKilobits < 500) RLinkKilobits = 500;
        if (RLinkKilobits > 2*1024*1024) RLinkKilobits = 2*1024*1024;
        FModeratedDownloadBytes = RLinkKilobits << 6; // Reminder: this sets approx 6 Mbytes for 100Mbit link.

        //System.out.println("[aq2j] DEBUG: client version = " + RCompatVersion + "; kilobits = " + RLinkKilobits);
    }

    public int GetClientAppVer() {

        return RCompatVersion;

    }

    private void Process_FeatureSelect(byte[] req_body, int req_trailing_len) throws Exception {

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body, 0, req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);
        if (tmpBB.remaining() >= 4) FFeatureSelectWord = tmpBB.getInt();

        if (Tum3TrustedLegacy.isTrusted(db_index, CallerNetAddr())) {
            WasAuthorized = true;
            InitDbAccess();
        } else {
            LoginFailedAt = System.currentTimeMillis();
            LoginFailedState = true;
        }
        Tum3Logger.DoLog(db_name, false, "Legacy implicite login: " + DebugTitle() + " FFeatureSelectWord=" + FFeatureSelectWord + " auth_ok=" + WasAuthorized);
    }

    private void Process_UserLogin(byte thrd_ctx, byte[] req_body, int req_trailing_len, boolean simp_crypt) throws Exception {

        int tmp_src_ofs = 0;
        int tmp_src_len = req_trailing_len;
        if (simp_crypt) {
            if (req_trailing_len < 8) {
                Tum3Logger.DoLog(db_name, false, "Internal error: login request too short." + " Session: " + DebugTitle());
                throw new Exception("FATAL: login request too short");
            }
            int tmpRandSalt = req_body[4];
            tmp_src_ofs = 5;
            tmp_src_len = req_trailing_len - 5;
            for (int tmp_i = 0; tmp_i < tmp_src_len; tmp_i++) req_body[tmp_i+5] = (byte)(((short)decrypt_arr[((short)req_body[tmp_i+5] & (short)0xFF)] - (short)tmpRandSalt) & 0xFF);
        }

        String[] tmp_strings = Tum3Util.FetchAsStrings(req_body, tmp_src_len, 2, 0, tmp_src_ofs);
        OutgoingBuff tmpBuff = null;

        if (tmp_strings.length != 2) {
            Tum3Logger.DoLog(db_name, false, "Internal error: invalid login request format." + " Session: " + DebugTitle());
            throw new Exception("FATAL: invalid login request format");
        }
        //System.out.println("[DEBUG] tmp_src_ofs=" + tmp_src_ofs + "[0]=" + tmp_strings[0] +  "[1]=" + tmp_strings[1]);

        boolean tmp_ok = CheckValidLogin(tmp_strings[0], tmp_strings[1]);

        tmpBuff = GetBuff(thrd_ctx, null);
        tmpBuff.InitSrvReply(REQUEST_TYPE_USERLOGINREPLY, AuthorizedLogin.length()+1, AuthorizedLogin.length()+1);
        tmpBuff.putString(AuthorizedLogin);
        tmpBuff.putByte((byte)0);
        PutBuff(thrd_ctx, tmpBuff, null);

        if (tmp_ok) {
            WasAuthorized = true;
            InitDbAccess();
            if (null != dbLink) if (null != dbLink.GetMasterDb()) {
                Tum3Db tmp_mdb = dbLink.GetMasterDb();
                PrepareSecondaryLogin(tmp_mdb.getIndex(), tmp_mdb.DbName(), tmp_strings[0], tmp_strings[1]);
            }
        } else {
            LoginFailedAt = System.currentTimeMillis();
            LoginFailedState = true;
            //Owner.ShutdownSrvLink("[aq2j] Login rejected for username=" + tmp_strings[0]);
        }
    }

    private String GetPasString(ByteBuffer bb, int max_len) throws Exception {

        int tmp_str_len = bb.get(), tmp_remain = max_len - tmp_str_len;
        if (tmp_str_len > max_len) throw new Exception("Overflow in GetPasString(): " + max_len + " capacity and " + tmp_str_len + " length");
        char[] tmp_chars = new char[tmp_str_len];
        for (int tmp_i=0; tmp_i < tmp_str_len; tmp_i++) tmp_chars[tmp_i] = (char)bb.get();
        for (int tmp_i=0; tmp_i < tmp_remain; tmp_i++) bb.get();
        return new String(tmp_chars);

    }

    private void Process_UploadOne(byte thrd_ctx, byte[] req_body, int req_trailing_len, RecycledBuffContext ctx, boolean DataIsVolatile) throws Exception {

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body, 0, req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);
        String tmp_errstr = "Unknown error";
        boolean tmp_reply_sent = false;
        boolean tmp_log_done = false;

        //System.out.println("[DEBUG] Process_UploadOne()...");

        //TUploadHeader =  packed record
        //  ShotName: string[15];
        //  SignalId: longint;
        //  Reserved: array[1.. 12] of byte; 
        //end;

        if (tmpBB.remaining() < 32) {

            tmp_errstr = "Request size was considered too small to be valid";

        } else {

            try {

                String tmp_shot_name = GetPasString(tmpBB, 15);
                int tmp_signal_id = tmpBB.getInt();
                tmpBB.getInt(); // reserved
                tmpBB.getInt(); // reserved
                tmpBB.getInt(); // reserved

                //System.out.println("[DEBUG] Process_UploadOne(): " + tmp_shot_name + ", " + tmp_signal_id);

                int tmp_h_ofs = tmpBB.position();
                int tmp_hsize = tmpBB.getInt();

                if ((tmp_hsize < 12) || (tmp_hsize > tmpBB.remaining())) {

                    tmp_errstr = "HSize value is invalid";

                } else {

                    tmpBB.position(tmp_h_ofs);
                    tmpBB.limit(tmp_h_ofs + tmp_hsize);
                    ByteBuffer tmp_header = tmpBB.slice();
                    tmp_header.order(ByteOrder.LITTLE_ENDIAN);
                    tmpBB.limit(req_trailing_len);
                    tmpBB.position(tmp_h_ofs + tmp_hsize);
                    ByteBuffer tmp_body = tmpBB.slice();
                    tmp_body.order(ByteOrder.LITTLE_ENDIAN);
                    tmp_errstr = UploadOne(tmp_shot_name, tmp_signal_id, tmp_header, tmp_body, DataIsVolatile);
                    if (tmp_errstr.isEmpty())
                        Tum3Logger.DoLog(db_name, false, "Successfully stored " + tmp_shot_name + "." + tmp_signal_id + " from " + DebugTitle());
                    else
                        Tum3Logger.DoLog(db_name, false, "Failed storing " + tmp_shot_name + "." + tmp_signal_id + " from " + DebugTitle() + ": " + tmp_errstr);
                    tmp_log_done = true;
                    //if (DataIsVolatile) {
                    //System.out.println("[DEBUG] Process_UploadOne(): " + tmp_shot_name + ", " + tmp_signal_id + ": err=<" + tmp_errstr + ">");
                    //}
                }

                //QueueUploadResult(UploadHeader.ShotName, UploadHeader.SignalId, errstr);

                OutgoingBuff tmpBuff = tmpBuff = GetBuff(thrd_ctx, ctx);
                String tmp_err_str255 = Str255(tmp_errstr);
                tmpBuff.InitSrvReply(REQUEST_TYPE_TRACEUPLOADACK, 4 + tmp_shot_name.length() + 1 + tmp_err_str255.length() + 1,
                        4 + tmp_shot_name.length() + 1 + tmp_err_str255.length() + 1);
                tmpBuff.putPasString(tmp_shot_name);
                tmpBuff.putInt(tmp_signal_id);
                tmpBuff.putPasString(tmp_err_str255);
                PutBuff(thrd_ctx, tmpBuff, ctx);
                tmp_reply_sent = true;

            } catch (Exception e) {

                tmp_errstr = "Exception: " + Tum3Util.getStackTrace(e);

            }

        }

        if ((!tmp_reply_sent || !tmp_log_done) && (tmp_errstr.length() > 0)) {

            Tum3Logger.DoLog(db_name, true, "Fatal error in Process_UploadOne(): " + tmp_errstr + "; Session: " + DebugTitle());

        }

    }

    private void Process_DeleteOne(byte thrd_ctx, byte[] req_body, int req_trailing_len, RecycledBuffContext ctx) throws Exception {

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body, 0, req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);
        String tmp_errstr = "Unknown error";
        boolean tmp_reply_sent = false;
        boolean tmp_log_done = false;

        //System.out.println("[DEBUG] Process_DeleteOne()...");

        if (tmpBB.remaining() < 32) {

            tmp_errstr = "Request size was considered too small to be valid";

        } else {

            try {

                String tmp_shot_name = GetPasString(tmpBB, 15);
                int tmp_signal_id = tmpBB.getInt();
                tmpBB.getInt(); // reserved
                tmpBB.getInt(); // reserved
                tmpBB.getInt(); // reserved

                //System.out.println("[DEBUG] Process_DeleteOne(): " + tmp_shot_name + ", " + tmp_signal_id);

                tmp_errstr = DeleteOne(tmp_shot_name, tmp_signal_id);
                if (tmp_errstr.isEmpty())
                    Tum3Logger.DoLog(db_name, false, "Successfully deleted " + tmp_shot_name + "." + tmp_signal_id + " from " + DebugTitle());
                else
                    Tum3Logger.DoLog(db_name, false, "Failed deleting " + tmp_shot_name + "." + tmp_signal_id + " from " + DebugTitle() + ": " + tmp_errstr);
                tmp_log_done = true;

                OutgoingBuff tmpBuff = tmpBuff = GetBuff(thrd_ctx, ctx);
                String tmp_err_str255 = Str255(tmp_errstr);
                tmpBuff.InitSrvReply(REQUEST_TYPE_TRACEUPLOADACK, 4 + tmp_shot_name.length() + 1 + tmp_err_str255.length() + 1,
                        4 + tmp_shot_name.length() + 1 + tmp_err_str255.length() + 1);
                tmpBuff.putPasString(tmp_shot_name);
                tmpBuff.putInt(tmp_signal_id);
                tmpBuff.putPasString(tmp_err_str255);
                PutBuff(thrd_ctx, tmpBuff, ctx);
                tmp_reply_sent = true;

            } catch (Exception e) {

                tmp_errstr = "Exception: " + Tum3Util.getStackTrace(e);

            }

        }

        if ((!tmp_reply_sent || !tmp_log_done) && (tmp_errstr.length() > 0)) {

            Tum3Logger.DoLog(db_name, true, "Fatal error in Process_DeleteOne(): " + tmp_errstr + "; " + " Session: " + DebugTitle());

        }

    }

    private String Str255(String s) {
        if (s.length() > 254) s = s.substring(0, 251) + "...";
        return s;
    }

    private void Process_UploadEndHint() {

        //System.out.println("[DEBUG] Process_UploadEndHint()");
        flushWritingShot();

    }


    private void Process_Aq3(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {

        if ((UserPermissions != null) && (dbLink != null)) if (UserPermissions.isPwdOk() && UserPermissions.isAcquisControlAllowed()) {
            if (null == aq3hInstance) aq3hInstance = aq3h.getInstance(dbLink, db_index);
            if (null != aq3hInstance) {
                aq3hInstance.Aq3Request(thrd_ctx, this, req_body, req_trailing_len);
                return;
            }
        }
        aq3h.Aq3RequestReject(db_name, thrd_ctx, this, req_body, req_trailing_len);

    }

    public void aq3send_inline(byte thrd_ctx, int Aq3_error, int Aq3_code, int Aq3_seq, String the_body) throws Exception {

        OutgoingBuff tmpBuff = null;
        tmpBuff = GetBuff(thrd_ctx, null);
        tmpBuff.InitSrvReply(REQUEST_TYPE_AQ3_REP, the_body.length()+16, the_body.length()+16);
        tmpBuff.putInt(0);
        tmpBuff.putInt(Aq3_error);
        tmpBuff.putInt(Aq3_code);
        tmpBuff.putInt(Aq3_seq);
        tmpBuff.putString(the_body);
        PutBuff(thrd_ctx, tmpBuff, null);

    }

    public boolean aq3send_later(int Aq3_error, int Aq3_code, int Aq3_seq, String the_body, boolean _push, boolean _try_harder) throws Exception {
        // Note: aq3send_later() is only called from aq3h.run() context.

        if (CancellingLink) return false;

        OutgoingBuff tmpBuff = GetBuff(THRD_EXTERNAL, null, false, _try_harder);
        if (null == tmpBuff) {
            //System.out.println("[aq2j] GetBuff() failed in aq3send_later()");
            return false;
        }

        byte tmp_opcode = REQUEST_TYPE_AQ3_REP;
        if (_push) tmp_opcode = REQUEST_TYPE_AQ3_EVT;
        tmpBuff.InitSrvReply(tmp_opcode, the_body.length()+16, the_body.length()+16); // Reminder! OutBuffContinuator may not be passed in case of external thread as blocking would be broken then.
        tmpBuff.putInt(0);
        tmpBuff.putInt(Aq3_error);
        tmpBuff.putInt(Aq3_code);
        tmpBuff.putInt(Aq3_seq);
        tmpBuff.putString(the_body);

        //System.out.println("[DEBUG] aq3send_later: " + Aq3_error + ", " + Aq3_code + ", " + Aq3_seq + "(" + the_body.length() + ")");
        PutBuff(THRD_EXTERNAL, tmpBuff, null);

        //Wakeup(); // Note: PutBuff() should do notification as necessary!

        return true;
    }

    public boolean aq3send_later(int Aq3_error, int Aq3_code, int Aq3_seq, String the_body, boolean _push) throws Exception {

        return aq3send_later(Aq3_error, Aq3_code, Aq3_seq, the_body, _push, false);

    }

    private void TalkMessageServerBroadcaster(String thisMsg, String thisReceiverName, String thisEchoName) {

        Tum3Broadcaster.DistributeGeneralEvent(dbLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TALK, thisMsg), null, thisReceiverName, thisEchoName);

    }

    private String DoFlexTxtReq(String _ReqSeq, String _Action, String _Option, String _Body, StringBuilder _res_body) {

        if (TumProtoConsts.FLEXCMD_hotstart.equals(_Action)) {

            if (!Tum3cfg.isWriteable(db_index)) return Tum3Db.CONST_MSG_READONLY_NOW;
            if (!UserPermissions.isHotstartUploadAllowed()) return dbLink.CONST_MSG_ACCESS_DENIED;

            String tmp_hotstart_path = Tum3cfg.getParValue(db_index, true, Tum3cfg.TUM3_CFG_hotstart_path);
            if (tmp_hotstart_path.isEmpty()) return "No path defined for hostart files currently";

            //System.out.println("[DEBUG] DoFlexTxtReq(): working...");

            SectionList tmpSections = null;
            try {
                tmpSections = new SectionList(new StringList(_Body.split("\r\n")));
                for (int tmp_i = 0; tmp_i < tmpSections.Count(); tmp_i++) {
                    String tmp_fname = tmpSections.GetName(tmp_i).trim();
                    String tmp_fbody = tmpSections.GetBody(tmp_i).BuildString();
                    //System.out.println("[DEBUG] DoFlexTxtReq(): hotstart filename=" + tmp_fname);
                    if (!tmp_fname.isEmpty()) {
                        new File(tmp_hotstart_path + AuthorizedLogin).mkdir();
                        Tum3Util.WriteIniWithBak(tmp_hotstart_path + AuthorizedLogin + File.separator, tmp_fname, tmp_fbody, ".tx0", ".txt");
                        //System.out.println("[DEBUG] DoFlexTxtReq(): hotstart filename=" + tmp_fname + " wrote OK.");
                    }
                }
            } catch (Exception e) {
                return "Exception: " + e;
            }
            return "";
        }
        return "Unsupported";

    }

    private void Process_FlexTxtReq(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {

        //System.out.println("[DEBUG] Process_FlexTxtReq()...");

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
        tmpBB.limit(req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);

        if (req_trailing_len < (4*4 + 1)) {
            Tum3Logger.DoLog(db_name, false, "WARNING: length too small in Process_FlexTxtReq: " + req_trailing_len + "; Session: " + DebugTitle());
        } else
            try {
                int tmp_str_len = tmpBB.getInt();
                if ((tmpBB.position()+tmp_str_len) > req_trailing_len) throw new Exception("Unexpected ReqSeq in Process_FlexTxtReq()");
                byte[] tmp_arr = new byte[tmp_str_len];
                tmpBB.get(tmp_arr);
                String tmp_ReqSeq = Tum3Util.BytesToStringRaw(tmp_arr);

                if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("Unexpected ReqSeq size in Process_FlexTxtReq()");
                tmp_str_len = tmpBB.getInt();
                if ((tmpBB.position()+tmp_str_len) > req_trailing_len) throw new Exception("Unexpected Action in Process_FlexTxtReq()");
                tmp_arr = new byte[tmp_str_len];
                tmpBB.get(tmp_arr);
                String tmp_Action = Tum3Util.BytesToStringRaw(tmp_arr);

                if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("Unexpected Action size in Process_FlexTxtReq()");
                tmp_str_len = tmpBB.getInt();
                if ((tmpBB.position()+tmp_str_len) > req_trailing_len) throw new Exception("Unexpected Option in Process_FlexTxtReq()");
                tmp_arr = new byte[tmp_str_len];
                tmpBB.get(tmp_arr);
                String tmp_Option = Tum3Util.BytesToStringRaw(tmp_arr);

                //System.out.println("[DEBUG] Process_FlexTxtReq(): <" + tmp_ReqSeq + "><" + tmp_Action + "><" + tmp_Option + ">");
                if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("Unexpected Option size in Process_FlexTxtReq()");
                tmp_str_len = tmpBB.getInt();
                if ((tmpBB.position()+tmp_str_len) > req_trailing_len) throw new Exception("Unexpected Body in Process_FlexTxtReq()");
                tmp_arr = new byte[tmp_str_len];
                tmpBB.get(tmp_arr);
                String tmp_Body = Tum3Util.BytesToStringRaw(tmp_arr);

                StringBuilder tmp_res_body_buf = new StringBuilder();
                String tmp_res_msg = DoFlexTxtReq(tmp_ReqSeq, tmp_Action, tmp_Option, tmp_Body, tmp_res_body_buf);
                String tmp_res_body = tmp_res_body_buf.toString();
                OutgoingBuff tmpBuff = GetBuff(thrd_ctx, null);
                int tmp_size = 4 + tmp_ReqSeq.length() + 4 + tmp_res_msg.length() + 4 + tmp_res_body.length();
                tmpBuff.InitSrvReply(REQUEST_TYPE_FLEX_REPL, tmp_size, tmp_size);
                tmpBuff.putInt(tmp_ReqSeq.length());
                tmpBuff.putString(tmp_ReqSeq);
                tmpBuff.putInt(tmp_res_msg.length());
                tmpBuff.putString(tmp_res_msg);
                tmpBuff.putInt(tmp_res_body.length());
                tmpBuff.putString(tmp_res_body);
                PutBuff(thrd_ctx, tmpBuff, null);
            } catch (Exception e) {
                Tum3Logger.DoLog(db_name, false, "WARNING: exception in Process_FlexTxtReq: " + e + "; Session: " + DebugTitle());
            }

    }

    private void Process_TalkMsgX(byte[] req_body, int req_trailing_len) throws Exception {

        //System.out.println("[DEBUG] Process_TalkMsgX()...");
        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_TalkMsgX." + " Session: " + DebugTitle());
            throw new Exception("no dbLink in Process_TalkMsgX");
        }

        //String tmp_talk_str = Tum3Util.BytesToStringRaw(req_body, 0, req_trailing_len);

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
        tmpBB.limit(req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);

        while ((tmpBB.position()+12) < req_trailing_len) {
            try {
                int tmp_str_len = tmpBB.getInt();
                if ((tmp_str_len < 1) || ((tmpBB.position()+tmp_str_len) > req_trailing_len)) throw new Exception("Unexpected talk body in Process_TalkMsgX()");
                byte[] tmp_arr = new byte[tmp_str_len];
                tmpBB.get(tmp_arr);
                String tmp_txt_body = Tum3Util.BytesToStringRaw(tmp_arr);
                if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("Unexpected talk sender size in Process_TalkMsgX()");
                tmp_str_len = tmpBB.getInt();
                if ((tmpBB.position()+tmp_str_len) > req_trailing_len) throw new Exception("Unexpected talk sender in Process_TalkMsgX()");
                tmp_arr = new byte[tmp_str_len];
                tmpBB.get(tmp_arr);
                String tmp_txt_sender = Tum3Util.BytesToStringRaw(tmp_arr);
                if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("Unexpected talk receiver size in Process_TalkMsgX()");
                tmp_str_len = tmpBB.getInt();
                if ((tmpBB.position()+tmp_str_len) > req_trailing_len) throw new Exception("Unexpected talk receiver in Process_TalkMsgX()");
                tmp_arr = new byte[tmp_str_len];
                tmpBB.get(tmp_arr);
                String tmp_txt_receiver = Tum3Util.BytesToStringRaw(tmp_arr);

                //System.out.println("[DEBUG] Process_TalkMsgX(): <" + tmp_txt_body + "><" + tmp_txt_sender + "><" + tmp_txt_receiver + ">");
                String tmp_full_sender = "";
                if (!tmp_txt_sender.isEmpty()) tmp_full_sender = "(" + tmp_txt_sender + ")";
                tmp_full_sender = AuthorizedLoginPlus() + tmp_full_sender;
                if ("*".equals(tmp_txt_receiver)) tmp_txt_receiver = "";
                TalkMessageServerBroadcaster(tmp_full_sender + "> " + tmp_txt_body, tmp_txt_receiver, AuthorizedLoginPlus());

            } catch (Exception e) {
                Tum3Logger.DoLog(db_name, false, "WARNING: exception in Process_TalkMsgX: " + e + "; Session: " + DebugTitle());
            }
        }
    }

    private void Process_TalkMsg(byte[] req_body, int req_trailing_len) throws Exception {

        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_TalkMsg." + " Session: " + DebugTitle());
            throw new Exception("no dbLink in Process_TalkMsg");
        }

        String tmp_talk_str = Tum3Util.BytesToStringRaw(req_body, 0, req_trailing_len);
        TalkMessageServerBroadcaster("(" + AuthorizedLoginPlus() + ")" + tmp_talk_str, "", "");
    }

    private void TrySendTalkMessages(byte thrd_ctx) throws Exception {
        if (TrySendTalkMessages_int(thrd_ctx)) {
            GetTracesContinue(thrd_ctx, null);
        }
    }

    private boolean TrySendTalkMessages_int(byte thrd_ctx) throws Exception {

        boolean tmp_need_resume = false;
        int tmp_msg_count = 0;
        OutgoingBuff tmpBuff = null;
        synchronized(TalkMsgQueue) { tmp_msg_count = TalkMsgQueueFill; }
        do {
            if (tmp_msg_count <= 0) return tmp_need_resume;
            GeneralDbDistribEvent tmpEv = TalkMsgQueue[0];
            int tmp_ev_type = tmpEv.get_type();
            if (tmp_ev_type == tmpEv.DB_EV_TALK) {
                tmpBuff = GetBuff(thrd_ctx, null);
                String tmpMsg = tmpEv.get_str();
                tmpBuff.InitSrvReply(REQUEST_TYPE_TALKMSG_IN, tmpMsg.length(), tmpMsg.length());
                tmpBuff.putString(tmpMsg);
                PutBuff(thrd_ctx, tmpBuff, null);
            } else if (tmp_ev_type == tmpEv.DB_EV_USERLIST) {
                tmpBuff = GetBuff(thrd_ctx, null);
                ByteBuffer tmp_bb2 = tmpEv.get_bb();
                ByteBuffer tmp_bb1 = CreateUsrListHeadBB(TUM3_KEYWORD_users, tmp_bb2);
                int tmp_size = tmp_bb2.position() + tmp_bb1.position();
                tmpBuff.InitSrvReply(REQUEST_TYPE_MISC_FETCH, tmp_size, tmp_size);
                tmpBuff.putBytes(tmp_bb1.array(), 0, tmp_bb1.position());
                tmpBuff.putBytes(tmp_bb2.array(), 0, tmp_bb2.position());
                PutBuff(thrd_ctx, tmpBuff, null);
                //System.out.println("[DEBUG] DB_EV_USERLIST: " + tmp_size);
            } else if (tmp_ev_type == tmpEv.DB_EV_NEWSHOT) {
                String tmpMsg = tmpEv.get_str();
                tmpBuff = GetBuff(thrd_ctx, null);
                if (tmpMsg.length() > 100) tmpMsg = tmpMsg.substring(0, 100);
                tmpBuff.InitSrvReply(REQUEST_TYPE_TRACEINVALIDATEONE, 1 + 4*1 + tmpMsg.length(), 1 + 4*1 + tmpMsg.length());
                tmpBuff.putByte((byte)tmpMsg.length());
                tmpBuff.putString(tmpMsg);
                tmpBuff.putInt(0);
                PutBuff(thrd_ctx, tmpBuff, null);
                //System.out.println("[DEBUG] REQUEST_TYPE_TRACEINVALIDATEONE <" + tmpMsg + "> in " + db_name + " sent OK.");
            } else if ((tmp_ev_type == tmpEv.DB_EV_TRACEUPD) || (tmp_ev_type == tmpEv.DB_EV_TRACEUPD_ARR) || (tmp_ev_type == tmpEv.DB_EV_TRACEDEL_ARR)) {
                int tmp_id = 0, tmp_id_count = 1;
                int[] tmp_ids = null;
                if ((tmp_ev_type == tmpEv.DB_EV_TRACEUPD_ARR) || (tmp_ev_type == tmpEv.DB_EV_TRACEDEL_ARR)) {
                    tmp_ids = tmpEv.get_int_ar().clone(); // Arrays.copyOf(thisIds, thisIds.length);
                    tmp_id_count = tmp_ids.length;
                } else {
                    tmp_id = tmpEv.get_int();
                    tmp_ids = new int[1];
                    tmp_ids[0] = tmp_id;
                }
                String tmpMsg = tmpEv.get_str();

                //if ((tmp_ev_type == tmpEv.DB_EV_TRACEUPD_ARR) || (tmp_ev_type == tmpEv.DB_EV_TRACEUPD)) {
                //System.out.print("[DEBUG] " + CallerNetAddr() + " Invalidate ids " + tmpMsg + ": ");
                //for (int q=0; q < tmp_id_count; q++) System.out.print(tmp_ids[q] + ", ");
                //System.out.println(" ");
                //}
                int tmp_processed_count = 0;
                if (tmp_ev_type != tmpEv.DB_EV_TRACEDEL_ARR) tmp_processed_count = ResumeTraceRequests(tmpMsg, tmp_ids);
                tmp_need_resume = (tmp_processed_count > 0);
                //System.out.println("[DEBUG] tmp_id_count=" + tmp_id_count + " tmp_processed_count=" + tmp_processed_count);
                if (tmp_id_count > tmp_processed_count) {
                    tmpBuff = GetBuff(thrd_ctx, null);
                    if (tmpMsg.length() > 100) tmpMsg = tmpMsg.substring(0, 100);
                    byte tmp_notify_code = REQUEST_TYPE_TRACEINVALIDATEONE;
                    if (tmp_ev_type == tmpEv.DB_EV_TRACEDEL_ARR) tmp_notify_code = REQUEST_TYPE_TRACEREMOVED;
                    tmpBuff.InitSrvReply(tmp_notify_code, 1 + 4*(tmp_id_count-tmp_processed_count) + tmpMsg.length(), 1 + 4*(tmp_id_count-tmp_processed_count) + tmpMsg.length());
                    tmpBuff.putByte((byte)tmpMsg.length());
                    tmpBuff.putString(tmpMsg);
                    for (int tmp_i=0; tmp_i < tmp_id_count; tmp_i++)
                        if (tmp_ids[tmp_i] != 0) {
                            tmpBuff.putInt(tmp_ids[tmp_i]);
                            //System.out.print("{" + tmp_ids[tmp_i] + "}");
                        }
                    PutBuff(thrd_ctx, tmpBuff, null);
                    //System.out.println("[DEBUG] REQUEST_TYPE_TRACEINVALIDATEONE <" + tmpMsg + ">, " + (tmp_id_count - tmp_processed_count) + " sent OK.");
                }
            }
            synchronized(TalkMsgQueue) {
                for (int tmp_i=1; tmp_i<TalkMsgQueueFill; tmp_i++) TalkMsgQueue[tmp_i-1] = TalkMsgQueue[tmp_i];
                TalkMsgQueue[TalkMsgQueueFill-1] = null;
                TalkMsgQueueFill--;
                tmp_msg_count = TalkMsgQueueFill;
            }
        } while (0 != tmp_msg_count);
        return tmp_need_resume;

    }

    private boolean NeedToSendTalkMsg() {
        synchronized(TalkMsgQueue) { return (TalkMsgQueueFill > 0); }
    }

    public void AddGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, String thisReceiverName, String thisEchoName) {

        // We are usually interested in our db-originating events or masterdb-originating events.
        if ((origin_db != null) && (dbLink == null)) return; // Just in case.
        if (origin_db != null) if ((origin_db != dbLink) && (origin_db != dbLink.GetMasterDb()) && (origin_db.GetMasterDb() != dbLink)) return;

        int tmp_ev_type = ev.get_type();

        if ((RCompatFlags & 1) > 0) if ((tmp_ev_type != ev.DB_EV_NEWSHOT) || (origin_db != dbLink)) return; // Reminder: this is acquis-control link only, no need for most kinds of notifications.

        //if (tmp_ev_type == ev.DB_EV_NEWSHOT) System.out.println("[DEBUG] AddGeneralEvent: " + ev.get_str() + " in " + db_name);

        if ((tmp_ev_type == ev.DB_EV_NEWSHOT) && (origin_db != dbLink) && (origin_db != dbLink.GetMasterDb())) return; // Special case: do not accept DB_EV_NEWSHOT from slave to master.

        if (RCompatVersion == 0) { // This is a legacy acquisition node (most probably).
            if ((tmp_ev_type == ev.DB_EV_TALK) 
                    || (tmp_ev_type == ev.DB_EV_USERLIST)
                    || (tmp_ev_type == ev.DB_EV_TRACEUPD)
                    || (tmp_ev_type == ev.DB_EV_TRACEUPD_ARR)
                    || (tmp_ev_type == ev.DB_EV_TRACEDEL_ARR)
                    ) return;
            if ((tmp_ev_type == ev.DB_EV_NEWSHOT) && ((FFeatureSelectWord & 1) == 0)) return;
        }
        if (tmp_ev_type == ev.DB_EV_TALK) {
            boolean tmp_match = false;
            if (thisReceiverName == null) tmp_match = true;
            else if (thisReceiverName.isEmpty()) tmp_match = true;
            else if (thisReceiverName.equals(AuthorizedLoginPlus())) tmp_match = true;
            if (!tmp_match) if (thisEchoName != null) if (thisEchoName.equals(AuthorizedLoginPlus())) tmp_match = true;
            if (!tmp_match) return;
        }

        synchronized(TalkMsgQueue) {
            if (TalkMsgQueueFill < TalkMsgQueue.length) {
                //int tmp_count=0;
                //while ((tmp_count < 6) && (TalkMsgQueueFill < TalkMsgQueue.length)) {
                //tmp_count++;
                TalkMsgQueue[TalkMsgQueueFill] = ev;
                TalkMsgQueueFill++;
                //}
            } else {
                // REMINDER!!! This typically is running in some foreign thread!
                TalkMsgQueueOverflow = true;
                Tum3Logger.DoLog(db_name, true, "IMPORTANT: TalkMsgQueue overflow.");
            }
        }
        WakeupMain();

    }

    private void WakeupMain() {

        Owner.WakeupMain();

    }

    private void Process_PingHighlevel(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
        OutgoingBuff tmpBuff = null;
        tmpBuff = GetBuff(thrd_ctx, ctx);
        tmpBuff.InitSrvReply(REQUEST_TYPE_KEEPCONNECTED, 0, 0);
        PutBuff(thrd_ctx, tmpBuff, ctx);

    }

    private void Process_PingReply() {
        //System.out.println("[aq2j] DEBUG: got ping reply.");
        keepalive_sent_inline = false;
    }

    private void Process_DownloadResume(byte thrd_ctx) throws Exception {
        //System.out.println("[aq2j] DEBUG: Process_DownloadResume()");
        { // synchronized (PendingTraceList)
            FReplyQueue_size = 0;
            FModerateRequestWasSent = false;
            FModerateNeedSendRequest = false;
        }
        GetTracesContinue(thrd_ctx, null);
    }

    private void ExecuteReq(byte thrd_ctx, byte req_code, byte[] req_body, int req_trailing_len) throws Exception {

        if ((REQUEST_TYPE_REPORTAVAILVERSION == req_code) || (REQUEST_TYPE_REPORTAVAILVERSION_FULL == req_code) || (REQUEST_TYPE_REPORTAVAILVERSION_64 == req_code) || (REQUEST_TYPE_REPORTAVAILVERSION_FULL_64 == req_code)) 
            Process_ReportAvailVer(thrd_ctx, null, req_code);
        else if (REQUEST_TYPE_SENDMEPROGRAMFILE == req_code) Process_AppUpdate(thrd_ctx, null);
        else if ((REQUEST_TYPE_USERLOGIN == req_code) || (REQUEST_TYPE_USERLOGINX == req_code)) Process_UserLogin(thrd_ctx, req_body, req_trailing_len, (REQUEST_TYPE_USERLOGINX == req_code));
        else if ((REQUEST_TYPE_UPLOAD_ONE == req_code) || (REQUEST_TYPE_UPLOAD_ONE_VAR == req_code)) Process_UploadOne(thrd_ctx, req_body, req_trailing_len, null, REQUEST_TYPE_UPLOAD_ONE_VAR == req_code);
        else if (REQUEST_TYPE_DELETE_ONE_VAR == req_code) Process_DeleteOne(thrd_ctx, req_body, req_trailing_len, null);
        else if (REQUEST_TYPE_UPLOAD_END_HINT == req_code) Process_UploadEndHint();
        else if (REQUEST_TYPE_AGENT_INFO == req_code) Process_AgentInfo(req_body, req_trailing_len);
        else if (REQUEST_TYPE_FEATURESELECT == req_code) Process_FeatureSelect(req_body, req_trailing_len);
        else if (REQUEST_TYPE_DIRECTORYCALL == req_code) Process_GetDirList(thrd_ctx, req_body, req_trailing_len, null);
        else if (REQUEST_TYPE_ANYBODYTHERE == req_code) Process_PingHighlevel(thrd_ctx, null);
        else if (REQUEST_TYPE_KEEPCONNECTED == req_code) Process_PingReply();
        else if (REQUEST_TYPE_DOWNLOAD_RESUME == req_code) Process_DownloadResume(thrd_ctx);
        else if (REQUEST_TYPE_TRACECALL == req_code) Process_GetTrace(thrd_ctx, req_body, req_trailing_len, false);
        else if (REQUEST_TYPE_REFUSE    == req_code) Process_GetTrace(thrd_ctx, req_body, req_trailing_len, true);
        else if (REQUEST_TYPE_CONFIGSCALL == req_code) Process_GetConfigs(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_GET_MISC == req_code) Process_GetMiscInfos(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_CONFIGSSAVE == req_code) Process_ConfigsSave(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_DENSITY_UPD == req_code) Process_DensitySave(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_TALKMSG == req_code) Process_TalkMsg(req_body, req_trailing_len);
        else if (REQUEST_TYPE_TALKMSGX == req_code) Process_TalkMsgX(req_body, req_trailing_len);
        else if (REQUEST_TYPE_FLEX_TXT == req_code) Process_FlexTxtReq(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_AQ3_REQ == req_code) Process_Aq3(thrd_ctx, req_body, req_trailing_len);
        else {
            Tum3Logger.DoLog(db_name, true, "WARNING: unknown request, code=" + Integer.toHexString(req_code & 0xFF) + " len=" + req_trailing_len + ";" + " Session: " + DebugTitle());
        }
    }

    private int Bytes4int(byte b0, byte b1, byte b2, byte b3) {
        return ((int)b0 & 0xFF) + (((int)b1 & 0xFF) << 8) + (((int)b2 & 0xFF) << 16) + (((int)b3 & 0xFF) << 24);
    }

    public void SendToServer(byte thrd_ctx, ByteBuffer buf) throws Exception {
        try {
            SendToServerInternal(thrd_ctx, buf);
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, false, "FATAL: Closing in SendToServer because: " + Tum3Util.getStackTrace(e));
            Owner.ShutdownSrvLink("Exception in SrvLink.SendToServer: " + Tum3Util.getStackTrace(e));
            throw new Exception("[aq2j] FATAL: Closing in SendToServer because: " + e);
        }
    }

    public void SendOOBToServer(String buf) throws Exception {
        try {
            SendOOBToServerInternal(buf);
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, false, "FATAL: Closing in SendOOBToServer because: " + Tum3Util.getStackTrace(e));
            Owner.ShutdownSrvLink("Exception in SrvLink.SendOOBToServer: " + Tum3Util.getStackTrace(e));
            throw new Exception("[aq2j] FATAL: Closing in SendOOBToServer because: " + e);
        }
    }

    private void SendOOBToServerInternal(String buf) throws Exception {
        String tmp_str = buf;
        //System.out.println("[aq2j] DEBUG: SendOOBToServerInternal(): got '" + tmp_str + "'");
        String tmp_prev_str = oob_msg_remainder; // TODO!!! Verify if some synchronization is needed for oob_msg_remainder ?
        tmp_str = tmp_prev_str + tmp_str;
        StringBuffer tmp_buff = new StringBuffer(tmp_str);
        while (tmp_buff.length() >= 8) {
            String tmp_substr = tmp_buff.substring(0, 8);
            tmp_buff.delete(0, 8);
            String tmp_substr1 = tmp_substr.substring(0, 4);
            String tmp_substr2 = tmp_substr.substring(4, 8);
            int tmp_found_code = -1;
            try {
                tmp_found_code = Integer.parseInt(tmp_substr2);
            } catch (Exception ingored) { }
            if ((tmp_found_code >= 0) && (tmp_substr1.equals("REPL"))) {
                boolean tmp_dbg_confirmed = false;
                if (keepalive_sent_oob && (tmp_found_code == keepalive_code_sent)) {
                    keepalive_sent_oob = false;
                    last_client_activity_time = System.currentTimeMillis();
                    tmp_dbg_confirmed = true;
                }
                if (tmp_dbg_confirmed) {
                    //System.out.println("[aq2j] DEBUG: SendOOBToServerInternal(): confirmed by '" + tmp_substr + "'");
                }
            }
        }
        oob_msg_remainder = tmp_buff.toString();
        //if (count > 0) {
        //  UpdateLastClientActivityTime();
        //}
    }

    private String IntToStr4(int i) {
        StringBuffer tmp_str = new StringBuffer("" + i);
        while (tmp_str.length() < 4) tmp_str.insert(0, '0');
        tmp_str.insert(0, "REQU");
        return tmp_str.toString();
    }

    private String MakeOOBKeepAliveReq() {
        keepalive_code_sent = keepalive_code_next;
        keepalive_code_next++;
        if ((keepalive_code_next) >= 10000) keepalive_code_next = 0;
        keepalive_sent_oob = true;
        //keepalive_sent_time = System.currentTimeMillis();
        return IntToStr4(keepalive_code_sent); // "REQU0000";
    }

    private ShotWriteHelper currWritingShot() {

        if (null == currWritingShotHelper) currWritingShotHelper = new ShotWriteHelper(this);
        return currWritingShotHelper;

    }

    private void flushWritingShot() {

        try {
            if (null != currWritingShotHelper) currWritingShotHelper.flush();
        } catch (Exception e) { 
            Tum3Logger.DoLog(db_name, true, "flushWritingShot() error in " + " session " + DebugTitle() + ": " + Tum3Util.getStackTrace(e));
        }

    }

    public String UpdateDensityData(String _shot_name, int _signal_id, byte[] _upd_arr) {

        String tmp_name = "signal id <" + _signal_id + ">";
        String tmp_err_prefix = "Could not update " + tmp_name + " of " + _shot_name + ": ";
        Tum3SignalList tmpSignalList = Tum3SignalList.GetSignalList();
        int tmp_index = tmpSignalList.FindIndex(_signal_id);
        if ((tmp_index < 1) || (tmp_index > tmpSignalList.SignalCount())) return tmp_err_prefix + "signal id is not valid.";
        else {
            NameValueList tmp_entry = tmpSignalList.GetSignalEntry(tmp_index);
            tmp_name = tmp_entry.GetValueFor(const_signal_title, tmp_name);
            tmp_err_prefix = "Could not update " + tmp_name + " of " + _shot_name + ": ";
            if (!"1".equals(tmp_entry.GetValueFor(const_signal_is_density, ""))) return tmp_err_prefix + "not a density signal.";
        }

        Tum3Shot tmp_shot = null;
        try {
            tmp_shot = dbLink.getShot(_shot_name, true);
        } catch (Exception e) {
            return tmp_err_prefix + e;
        }
        if (tmp_shot == null) return tmp_err_prefix + CONST_MSG_SRVLINK_ERR01;
        if ((null == tmp_shot.GetDb()) || ((tmp_shot.GetDb() != dbLink) && (tmp_shot.GetDb() != dbLink.GetMasterDb()))) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + CONST_MSG_SRVLINK_ERR02;
        }
        Tum3Perms tmp_perm = UserPermissions;
        if (tmp_shot.GetDb() == dbLink.GetMasterDb())
            tmp_perm = MasterdbUserPermissions;

        if (!Tum3cfg.isWriteable(tmp_shot.GetDb().getIndex())) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + dbLink.CONST_MSG_READONLY_NOW;
        }

        if (null == tmp_perm) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + dbLink.CONST_MSG_ACCESS_DENIED + " (Access object not present)";
        }
        if (!tmp_perm.isDensityEditingAllowed()) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + dbLink.CONST_MSG_ACCESS_DENIED;
        }

        boolean tmp_ref_ok = false;
        String tmp_result = "Unknown error";
        try {
            ShotWriteHelper tmp_helper = currWritingShot();
            tmp_helper.setShot(tmp_shot);
            tmp_ref_ok = true;
            tmp_result = tmp_shot.UpdateDensityData(_signal_id, _upd_arr, tmp_helper);
        } catch (Exception e) {
            tmp_result = "Exception " + Tum3Util.getStackTrace(e);
        }
        if (!tmp_ref_ok) tmp_shot.ShotRelease();
        //if (tmp_helper != null) tmp_helper.flush(); // Note: normally this would rather hurt than help, because flush will happen later after some certain period.

        if (tmp_result.length() > 0)
            return tmp_err_prefix + tmp_result;
        else
            return "";

    }

    private String UploadOne(String _shot_name, int _signal_id, ByteBuffer _header, ByteBuffer _body, boolean DataIsVolatile) {

        String tmp_name = "signal id <" + _signal_id + ">";
        String tmp_err_prefix = "Could not store " + tmp_name + " of " + _shot_name + ": ";
        Tum3SignalList tmpSignalList = Tum3SignalList.GetSignalList();
        int tmp_index = tmpSignalList.FindIndex(_signal_id);
        if ((tmp_index < 1) || (tmp_index > tmpSignalList.SignalCount())) return tmp_err_prefix + "signal id is not valid.";
        else {
            NameValueList tmp_entry = tmpSignalList.GetSignalEntry(tmp_index);
            tmp_name = tmp_entry.GetValueFor(const_signal_title, tmp_name);
            tmp_err_prefix = "Could not store " + tmp_name + " of " + _shot_name + ": ";
            if (!Tum3SignalList.AllowExtUpload(tmp_entry)) return tmp_err_prefix + "not allowed";
        }

        Tum3Shot tmp_shot = null;
        try {
            tmp_shot = dbLink.getShot(_shot_name, true);
        } catch (Exception e) {
            return tmp_err_prefix + e;
        }
        if (tmp_shot == null) return tmp_err_prefix + CONST_MSG_SRVLINK_ERR01;
        if ((null == tmp_shot.GetDb()) || ((tmp_shot.GetDb() != dbLink) && (tmp_shot.GetDb() != dbLink.GetMasterDb()))) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + CONST_MSG_SRVLINK_ERR02;
        }
        Tum3Perms tmp_perm = UserPermissions;
        if (tmp_shot.GetDb() == dbLink.GetMasterDb())
            tmp_perm = MasterdbUserPermissions;

        if (!Tum3cfg.isWriteable(tmp_shot.GetDb().getIndex())) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + dbLink.CONST_MSG_READONLY_NOW;
        }

        boolean tmp_granted = false;
        if (tmp_perm != null) if (UserPermissions.isSignalUploadAllowed(_signal_id)) tmp_granted = true;
        if (!tmp_granted && Tum3TrustedLegacy.isTrusted(db_index, CallerNetAddr())) tmp_granted = true;
        if (!tmp_granted) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + dbLink.CONST_MSG_ACCESS_DENIED;
        }

        if (!(DataIsVolatile && dbLink.VolatilePathPresent())) if (!(new Tum3Time().GetCurrYMD()).equals(_shot_name.substring(0,6))) { // Restrict shot date to current unless volatile.
            tmp_shot.ShotRelease();
            return tmp_err_prefix + dbLink.CONST_MSG_INV_SHOT_NUMBER;
        }

        boolean tmp_ref_ok = false;
        String tmp_result = "Unknown error";
        try {
            ShotWriteHelper tmp_helper = currWritingShot();
            Tum3Shot.insertHostName(_header, "[" + DebugTitle() + "]");
            tmp_helper.setShot(tmp_shot);
            tmp_ref_ok = true;
            tmp_shot.putTrace(_signal_id, _header, _body, tmp_helper, DataIsVolatile);
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

    private String DeleteOne(String _shot_name, int _signal_id) {

        String tmp_name = "signal id <" + _signal_id + ">";
        String tmp_err_prefix = "Could not delete " + tmp_name + " of " + _shot_name + ": ";
        Tum3SignalList tmpSignalList = Tum3SignalList.GetSignalList();
        int tmp_index = tmpSignalList.FindIndex(_signal_id);
        if ((tmp_index < 1) || (tmp_index > tmpSignalList.SignalCount())) return tmp_err_prefix + "signal id is not valid.";
        else {
            NameValueList tmp_entry = tmpSignalList.GetSignalEntry(tmp_index);
            tmp_name = tmp_entry.GetValueFor(const_signal_title, tmp_name);
            tmp_err_prefix = "Could not delete " + tmp_name + " of " + _shot_name + ": ";
            if (!Tum3SignalList.AllowExtUpload(tmp_entry)) return tmp_err_prefix + "not allowed";
        }

        Tum3Shot tmp_shot = null;
        try {
            tmp_shot = dbLink.getShot(_shot_name, false);
        } catch (Exception e) {
            return tmp_err_prefix + e;
        }
        if (tmp_shot == null) return tmp_err_prefix + CONST_MSG_SRVLINK_ERR01;
        if ((null == tmp_shot.GetDb()) || (tmp_shot.GetDb() != dbLink)) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + CONST_MSG_SRVLINK_ERR02;
        }

        if (!Tum3cfg.isWriteable(db_index)) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + dbLink.CONST_MSG_READONLY_NOW;
        }

        boolean tmp_granted = false;
        if (UserPermissions != null) if (UserPermissions.isSignalUploadAllowed(_signal_id)) tmp_granted = true;
        //if (!tmp_granted && Tum3TrustedLegacy.isTrusted(db_index, CallerNetAddr())) tmp_granted = true; // Should not be allowed I think.
        if (!tmp_granted) {
            tmp_shot.ShotRelease();
            return tmp_err_prefix + dbLink.CONST_MSG_ACCESS_DENIED;
        }

        //if (!dbLink.VolatilePathPresent()) {
        //  return tmp_err_prefix + dbLink.CONST_MSG_INV_SHOT_NUMBER;
        //}

        boolean tmp_ref_ok = false;
        String tmp_result = "Unknown error";
        try {
            ShotWriteHelper tmp_helper = currWritingShot();
            tmp_helper.setShot(tmp_shot);
            tmp_ref_ok = true;
            tmp_shot.deleteTrace(_signal_id, tmp_helper);
            tmp_result = "";
        } catch (Exception e) {
            tmp_result = "Exception " + Tum3Util.getStackTrace(e);
        }
        if (!tmp_ref_ok) tmp_shot.ShotRelease();

        if (tmp_result.length() > 0)
            return tmp_err_prefix + tmp_result;
        else
            return "";

    }

    private void UpdateLastClientActivityTime() {
        last_client_activity_time = System.currentTimeMillis();
        //if (keepalive_sent_inline) { System.out.println(); System.out.println("[keepalive_sent_inline:=false]"); } // debug only!!!
    }

    private boolean OutBuffsEmpty() {

        synchronized(OutBuffFullLock) {
            return (0 == out_buffs_full_fill);
        }

    }

    private boolean NeedToRequestKeepalive(long sys_millis, boolean may_disconn) throws Exception {

        if (LoginFailedState) {
            if (may_disconn) {
                if ((sys_millis - LoginFailedAt) > CONST_LOGIN_FAIL_PENDING_SEC*1000)
                    throw new Exception("Incorrect username and/or password, aborting session (timeout).");
                if (OutBuffsEmpty())
                    throw new Exception("Incorrect username and/or password, aborting session (close).");
            }
            return false;
        }
        if (!WasAuthorized) if ((sys_millis - ConnectionStartedAt) > CONST_LOGIN_TIMEOUT_SEC*1000) {
            if (may_disconn) throw new Exception("Timeout waiting for login completion, aborting session.");
            return false;
        }
        //System.out.println("[aq2j] DEBUG: NeedToRequestKeepalive() sent=" + keepalive_sent + " elap=" + (sys_millis - last_client_activity_time) + " max=" + (2*CONST_KEEPALIVE_INTERVAL_SEC[db_index]*1000));
        if ((sys_millis - last_client_activity_time) > 2*CONST_KEEPALIVE_INTERVAL_SEC[db_index]*1000) {
            if (may_disconn) throw new Exception("Client does not reply to keepalive request, aborting session.");
            return false;
        }

        return ((sys_millis - last_client_activity_time) > CONST_KEEPALIVE_INTERVAL_SEC[db_index]*1000);
    }

    private boolean DoSimpleReq(byte thrd_ctx, byte the_code, RecycledBuffContext ctx) throws Exception {
        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx, false);
        if (null == tmpBuff) return false;
        tmpBuff.InitSrvReply(the_code, 0, 0);
        PutBuff(thrd_ctx, tmpBuff, ctx);
        return true;
    }

    private boolean DoSendKeepaliveInline(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
        //System.out.println("[aq2j] DEBUG: DoSendKeepaliveInline()");
        if (DoSimpleReq(thrd_ctx, REQUEST_TYPE_ANYBODYTHERE, ctx)) {
            keepalive_sent_inline = true;
            //keepalive_sent_time = System.currentTimeMillis();
            //System.out.println("[aq2j] DEBUG: DoSendKeepaliveInline() success.");
            return true;
        }
        return false;
    }

    private boolean DoSendDownloadPaused(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
        return DoSimpleReq(thrd_ctx, REQUEST_TYPE_DOWNLOAD_PAUSED, ctx);
        //System.out.println("[aq2j] DEBUG: DoSendDownloadPaused.");
    }

    private void SendToServerInternal(byte thrd_ctx, ByteBuffer buf) throws Exception {
        // This function may block until either all count is written or exception is raised.
        int tmp_rem_count;
        if (LoginFailedState) return;
        while ((buf.remaining() > 0) || ((curr_stage == STAGE_TRAILING_BODY) && (0 == curr_remaining_bytes))) {
            if (LoginFailedState) return;
            tmp_rem_count = buf.remaining(); //count;
            if (tmp_rem_count > curr_remaining_bytes) tmp_rem_count = curr_remaining_bytes;
            if (curr_stage == STAGE_SIGN_4BYTES) {
                //System.arraycopy(buf, tmp_pos, req_header, (4-curr_remaining_bytes), tmp_rem_count);
                //tmp_pos += tmp_rem_count;
                buf.get(req_header, (4-curr_remaining_bytes), tmp_rem_count);
                curr_remaining_bytes -= tmp_rem_count;
                if (curr_remaining_bytes == 0) {
                    if ((REQUEST_SIGN1 == req_header[3]) && (REQUEST_SIGN2 == req_header[2]) && (REQUEST_SIGN3 == req_header[1])) {
                        //System.out.println("[aq2j] DEBUG: signature OK.");
                        curr_req_code = req_header[0];
                        curr_stage = STAGE_TRAILING_LEN;
                        curr_remaining_bytes = 4;
                    } else {
                        Tum3Logger.DoLog(db_name, true, "WARNING: got invalid req signature in SendToServer(): " + Integer.toHexString(req_header[0] & 0xFF) + " " +  Integer.toHexString(req_header[1] & 0xFF) + " " +  Integer.toHexString(req_header[2] & 0xFF) + " " +  Integer.toHexString(req_header[3] & 0xFF) + " from " + DebugTitle());
                        throw new Exception("got invalid req signature in SendToServer()");
                    }
                }
            } else if (curr_stage == STAGE_TRAILING_LEN) {
                //System.arraycopy(buf, tmp_pos, req_size_holder, (4-curr_remaining_bytes), tmp_rem_count);
                //tmp_pos += tmp_rem_count;
                buf.get(req_size_holder, (4-curr_remaining_bytes), tmp_rem_count);
                curr_remaining_bytes -= tmp_rem_count;
                UpdateLastClientActivityTime();
                if (curr_remaining_bytes == 0) {
                    curr_req_trailing_len = Bytes4int(req_size_holder[0], req_size_holder[1], req_size_holder[2], req_size_holder[3]);
                    if ((curr_req_trailing_len < 0) || (curr_req_trailing_len > CONST_TRAILING_BYTES_LIMIT)) {
                        Tum3Logger.DoLog(db_name, true, "WARNING: invalid trailing len in SendToServer(): " + curr_req_trailing_len + ";" + " Session: " + DebugTitle());
                        throw new Exception("got invalid trailing len " + curr_req_trailing_len + " in SendToServer()");
                    }
                    if (PreverifyReq(curr_req_code, curr_req_trailing_len)) {
                        //System.out.println("[aq2j] DEBUG: trailing size = " + curr_req_trailing_len + " {" + req_size_holder[0] + "," + req_size_holder[1] + "," + req_size_holder[2] + "," + req_size_holder[3] + "}");
                        curr_stage = STAGE_TRAILING_BODY;
                        curr_remaining_bytes = curr_req_trailing_len;
                        if (req_body != null) if (curr_req_trailing_len > req_body.length) {
                            req_body = null;
                        }
                        if ((req_body == null) && (curr_req_trailing_len > 0)) req_body = new byte[curr_req_trailing_len];
                    } else {
                        Tum3Logger.DoLog(db_name, true, "WARNING: got invalid req signature in SendToServer(): " + Integer.toHexString(req_header[0] & 0xFF) + " " +  Integer.toHexString(req_header[1] & 0xFF) + " " +  Integer.toHexString(req_header[2] & 0xFF) + " " +  Integer.toHexString(req_header[3] & 0xFF) + "; Session: " + DebugTitle());
                        throw new Exception("got invalid req signature in SendToServer()");
                    }
                }
            } else if (curr_stage == STAGE_TRAILING_BODY) {
                if (tmp_rem_count > 0) {
                    //System.arraycopy(buf, tmp_pos, req_body, (curr_req_trailing_len - curr_remaining_bytes), tmp_rem_count);
                    //tmp_pos += tmp_rem_count;
                    buf.get(req_body, (curr_req_trailing_len - curr_remaining_bytes), tmp_rem_count);
                    curr_remaining_bytes -= tmp_rem_count;
                }
                if (curr_remaining_bytes == 0) {
                    curr_stage = STAGE_SIGN_4BYTES;
                    curr_remaining_bytes = 4;
                    //System.out.println("[aq2j] DEBUG: going to execute req_code=" + Integer.toHexString(curr_req_code & 0xFF) + " with length=" + curr_req_trailing_len);
                    ExecuteReq(thrd_ctx, curr_req_code, req_body, curr_req_trailing_len);
                }
            } else {
                Tum3Logger.DoLog(db_name, true, "Internal error: invalid curr_stage in SendToServer()" + "; Session: " + DebugTitle());
                throw new Exception("invalid curr_stage in SendToServer()");
            }
        }

    }

    public boolean ReadFromServer2(byte thrd_ctx, ClientWriter outbound, boolean hurry) throws Exception {
        // Returns true = "nothing left to do".
        try {
            return ReadFromServerInternal(thrd_ctx, outbound, hurry);
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, false, "FATAL: Closing in ReadFromServer because: " + Tum3Util.getStackTrace(e));
            Owner.ShutdownSrvLink("Exception in SrvLink.ReadFromServer2: " + Tum3Util.getStackTrace(e));
            throw new Exception("[aq2j] FATAL: Closing in ReadFromServer because: " + e);
        }
    }

    public void ClientReaderTick(byte thrd_ctx, ClientWriter outbound) throws Exception {
        // This function is guaranteed to be called by the main thread only.
        /*
        int tmp_next_sec = (int)(System.currentTimeMillis() >> 10);
        if (dbg_tick_count < 1000000) dbg_tick_count++;
        if (tmp_next_sec != dbg_last_sec) {
          dbg_last_sec = tmp_next_sec;
          System.out.print("[" + my_dbg_serial + ":" + dbg_tick_count + "]");
          dbg_tick_count = 0;
        }
         */
        //System.out.print("" + my_dbg_serial);

        if (NeedToSendTalkMsg() && outbound.isOpen()) TrySendTalkMessages(thrd_ctx); // Moved here from ReadFromServerInternal()

        if (TalkMsgQueueOverflow) {
            TalkMsgQueueOverflow = false;
            _NewMessageBoxCompat(thrd_ctx, "Internal error: TalkMsgQueue overflow. CONST_MAX_TALK_OUT_QUEUE needs to be increased.", true);
        }

        if (null != currWritingShotHelper) currWritingShotHelper.tick();

        NeedToRequestKeepalive(System.currentTimeMillis(), true);
        //System.out.print("[aq2j] ClientReaderTick(): tmp_need_req=" + tmp_need_req + ", need_req_keepalive=" + need_req_keepalive + ", keepalive_sent=" + keepalive_sent);

    }

    private boolean ReadFromServerInternal(byte thrd_ctx, ClientWriter outbound, boolean hurry) throws Exception {
        // Reminder. This is only called from the main thread (THRD_INTERNAL).
        //   However, OutBuffFullLock and OutBuffEmptyLock still should be protected
        //    by synchronized() because GetBuff() might be called from another thread due to aq3h.
        // Reminder: This is called by data consumer (sender) only when it is guaranteed ready to consume some data.
        // Reminder: due to elimination of multithreaded worker, blocking is never performed.
        // Returns true = "nothing left to do".

        //System.out.print("?");
        int tmp_filled_count = 0;
        boolean tmp_need_req_keepalive;
        //long tmp_dbg_t0, tmp_dbg_t1, tmp_dbg_t2, tmp_dbg_t3;
        //tmp_dbg_t0 = System.currentTimeMillis();

        tmp_need_req_keepalive = false;

        //System.out.println("[aq2j] ReadFromServerInternal(): need_req_keepalive=" + need_req_keepalive);
        if (!hurry && (!keepalive_sent_inline || (!keepalive_sent_oob && SupportOOB))) {
            tmp_need_req_keepalive = NeedToRequestKeepalive(System.currentTimeMillis(), false);
            //System.out.print("[aq2j] ReadFromServerInternal(): tmp_need_req=" + tmp_need_req + ", need_req_keepalive=" + need_req_keepalive + ", keepalive_sent=" + keepalive_sent);
        }

        if (/* !tmp_need_req_keepalive && */ (out_buff_now_sending == null)) // Removed the strange (or obsolete?) condition. What was that???
            synchronized(OutBuffFullLock) {
                if (0 < out_buffs_full_fill) {
                    out_buff_now_sending = out_buffs_full[0];
                    //System.out.println("[aq2j] ReadFromServerInternal(): new out_buff_now_sending");
                }
            }

        if (tmp_need_req_keepalive && outbound.isOpen()) {
            //System.out.println("[aq2j] ReadFromServerInternal(): sending keepalive...");
            if (SupportOOB && !keepalive_sent_oob)
                outbound.SendToClientAsOOB(MakeOOBKeepAliveReq());
            if (!keepalive_sent_inline) {
                boolean tmp_keepalive_sent_ok = DoSendKeepaliveInline(thrd_ctx, null); // Reminder: when hurry is set, it prohibits allocting yet more output here while waiting for buffers.
                //System.out.println();
                //System.out.println("[keepalive_sent_ok=" + tmp_keepalive_sent_ok + "]");
            }
        }

        if (out_buff_now_sending == null) {
            //System.out.print("A");
            //tmp_dbg_t1 = System.currentTimeMillis();
            //if ((tmp_dbg_t1 - tmp_dbg_t0) > 100) System.out.print("[A:" + (tmp_dbg_t1 - tmp_dbg_t0) + "]");
            return true;
        }

        if (!outbound.isOpen()) {
            //System.out.print("B");
            //tmp_dbg_t1 = System.currentTimeMillis();
            //if ((tmp_dbg_t1 - tmp_dbg_t0) > 100) System.out.print("[B:" + (tmp_dbg_t1 - tmp_dbg_t0) + "]");
            return true;
        }

        //System.out.print("C");
        //tmp_dbg_t1 = System.currentTimeMillis();
        //if ((tmp_dbg_t1 - tmp_dbg_t0) > 100) System.out.print("[C:" + (tmp_dbg_t1 - tmp_dbg_t0) + "]");
        tmp_filled_count = outbound.AcceptFrom(out_buff_now_sending); // out_buff_now_sending.SendTo(outbound);
        //tmp_dbg_t2 = System.currentTimeMillis();
        //if ((tmp_dbg_t2 - tmp_dbg_t1) > 100) System.out.print("<" + (tmp_dbg_t2 - tmp_dbg_t1) + ">");
        //System.out.print("[fil=" + tmp_filled_count + "]");
        if (0 == tmp_filled_count) throw new Exception("Internal error: outbound.AcceptFrom() did nothing");

        if (out_buff_now_sending.SentAll()) {
            //System.out.println("[aq2j] DEBUG: Sent completely buff size=" + out_buff_now_sending.SentCount());
            out_buff_now_sending.CancelData();
            boolean tmp_need_traces_continue = false;
            boolean tmp_out_buff_was_recycled = false;
            int tmp_dbg_hanging_out_trace_bytes = -1;
            int tmp_dbg_hanging_out_trace_number = -1;
            { // synchronized (PendingTraceList)
                int tmp_size = out_buff_now_sending.GetTraceSize();
                int tmp_number = out_buff_now_sending.GetTraceNumber();
                hanging_out_trace_bytes -= tmp_size;
                hanging_out_trace_number -= tmp_number;
                tmp_dbg_hanging_out_trace_bytes = hanging_out_trace_bytes;
                tmp_dbg_hanging_out_trace_number = hanging_out_trace_number;
                if ((tmp_size > 0) || (tmp_number > 0) || NoPauseOut()) tmp_need_traces_continue = true;
                //else System.out.print("[" + tmp_size + "/" + tmp_number + "]");
            }
            if (tmp_need_req_keepalive && outbound.isOpen() && !keepalive_sent_inline) {
                tmp_need_traces_continue = false;
                ctxRecycledReader.out_buff_for_send = out_buff_now_sending;
                tmp_out_buff_was_recycled = DoSendKeepaliveInline(thrd_ctx, ctxRecycledReader);
                //System.out.println(); 
                //System.out.println("[keepalive_sent_ok2=" + tmp_out_buff_was_recycled + "]");
            }
            //if (tmp_need_traces_continue) System.out.print("Y");
            //else System.out.print("N");
            //System.out.println("[aq2j] DEBUG: in ReadFromServer(): hng=" + tmp_dbg_hanging_out_trace_bytes + "," + tmp_dbg_hanging_out_trace_number);
            if (tmp_need_traces_continue) {
                ctxRecycledReader.out_buff_for_send = out_buff_now_sending;
                tmp_out_buff_was_recycled = GetTracesContinue(thrd_ctx, ctxRecycledReader);
                //System.out.println("[aq2j] tmp_out_buff_was_recycled=" + tmp_out_buff_was_recycled);
            }
            synchronized(OutBuffFullLock) { 
                for (int tmp_i=1; tmp_i < out_buffs_full_fill; tmp_i++)
                    out_buffs_full[tmp_i-1] = out_buffs_full[tmp_i];
                out_buffs_full_fill--;
                if (tmp_out_buff_was_recycled) {
                    out_buffs_full[out_buffs_full_fill] = out_buff_now_sending;
                    out_buffs_full_fill++;
                }
            }
            if (!tmp_out_buff_was_recycled) {
                synchronized(OutBuffEmptyLock) {
                    if (out_buffs_count <= CONST_OUT_BUFF_COUNT_MAX[db_index]) {
                        out_buffs_empty[out_buffs_empty_fill] = out_buff_now_sending;
                        out_buffs_empty_fill++;
                        OutBuffEmptyLock.notify();
                    } else {
                        out_buffs_count--;
                    }
                }
            }
            out_buff_now_sending = null;
            //System.out.println("[aq2j] ReadFromServerInternal(): out_buff_now_sending := null");
        }

        TrySendContinuationReq(thrd_ctx, null);

        //System.out.print("{-" + PendingTraceList.size() + "}");
        //if (out_buff_now_sending == null) System.out.print("s");
        //else                              System.out.print("S");
        //if (out_buffs_full_fill <= 0) System.out.print("f");
        //else                              System.out.print("F");
        //Tum3Util.SleepExactly(200);

        //tmp_dbg_t1 = System.currentTimeMillis();
        //if ((tmp_dbg_t1 - tmp_dbg_t0) > 100) System.out.print("[" + (tmp_dbg_t1 - tmp_dbg_t0) + "]");

        //if ((out_buff_now_sending != null) || (out_buffs_full_fill > 0)) System.out.println("[DEBUG] out_buffs_full_fill=" + out_buffs_full_fill);
        return ((out_buff_now_sending == null) && (out_buffs_full_fill <= 0));
    }

}
