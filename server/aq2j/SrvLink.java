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

public class SrvLink extends SrvLinkBase implements UgcReplyHandlerExt, aq3sender {

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

    private final static int min_supported_app_ver = 428; // YYY

    private static volatile int dbg_serial = 0;
    public int my_dbg_serial = 0;

    private aq3h aq3hInstance = null;

    private final static TunedSrvLinkParsMain tuned_pars_main = new TunedSrvLinkParsMain(); // YYY

    private final static int CONST_MAX_TRACE_OUT_QUEUE_KBYTES_default = 1;
    private final static int CONST_MAX_TRACE_OUT_QUEUE_LEN_default = 10;

    private final static int CONST_MAX_TALK_OUT_QUEUE = 20;
    private final static int CONST_MAX_REQ_STRING_COUNT = 1000;

    private static final int CONST_MAX_TRACE_OUT_QUEUE_BYTES[];
    private static final int CONST_MAX_TRACE_OUT_QUEUE_LEN[];

    private boolean WasAuthorized = false;
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

    private volatile boolean NeedPushServerInfo = true; // YYY
    private volatile int RCompatVersion;
    private int RLinkKilobits, FModeratedDownloadBytes;
    private int RCompatFlags = 0;
    private volatile int FReplyQueue_size;
    private boolean FDoModerateDownloadRate;
    private volatile boolean FModerateNeedSendRequest, FModerateRequestWasSent;
    private boolean use_tracecome_x = false;

    private int found_look4ver = 0;
    private Tum3AppUpdateHelper app_helper = null;

    private String db_name;

    static {

        int tmp_db_count = Tum3cfg.getGlbInstance().getDbCount();
        Tum3cfg cfg = Tum3cfg.getGlbInstance();

        CONST_MAX_TRACE_OUT_QUEUE_BYTES = new int[tmp_db_count];
        CONST_MAX_TRACE_OUT_QUEUE_LEN = new int[tmp_db_count];

        for (int tmp_i = 0; tmp_i < tmp_db_count; tmp_i++) {
            String db_name = cfg.getDbName(tmp_i);
            CONST_MAX_TRACE_OUT_QUEUE_BYTES[tmp_i] = 1024 * Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_out_queue_kbytes, CONST_MAX_TRACE_OUT_QUEUE_KBYTES_default);
            CONST_MAX_TRACE_OUT_QUEUE_LEN[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_out_queue_len, CONST_MAX_TRACE_OUT_QUEUE_LEN_default);

            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_MAX_TRACE_OUT_QUEUE_BYTES=" + CONST_MAX_TRACE_OUT_QUEUE_BYTES[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_MAX_TRACE_OUT_QUEUE_LEN=" + CONST_MAX_TRACE_OUT_QUEUE_LEN[tmp_i]);
        }
    }

    private static class TunedSrvLinkParsMain extends SrvLinkBase.TunedSrvLinkPars {

        public void AssignStaticValues() { // YYY

            //System.out.println("[aq2j] DEBUG: now in TunedSrvLinkParsMain() constructor.");
            LINK_PARS_LABEL = "";

            TUM3_CFG_idle_check_alive_delay = "idle_check_alive_delay"; // YYY Moved here from static.
            TUM3_CFG_max_out_buff_count = "max_out_buff_count"; // YYY Moved here from static.
            TUM3_CFG_min_out_buff_kbytes = "min_out_buff_kbytes"; // YYY Moved here from static.

            CONST_OUT_BUFF_COUNT_MAX_default = 10; // YYY Moved here from static.
            CONST_KEEPALIVE_INTERVAL_SEC_default = 20; // YYY Moved here from static.
            CONST_MIN_OUT_BUFF_default = 1;  // kbytes. // YYY Moved here from static.
        }
    }

    public SrvLink(int _db_idx, SrvLinkOwner thisOwner) {
        super(_db_idx, tuned_pars_main, thisOwner);
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        dbg_serial++;
        my_dbg_serial = dbg_serial;
        TalkMsgQueue = new GeneralDbDistribEvent[CONST_MAX_TALK_OUT_QUEUE];
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

    public byte[] GetBinaryUsername() {

        return bin_username;

    }

    public String get_authorized_username() {

        return AuthorizedLogin;

    }

    protected boolean CancelLinkIntrnl() {

        if (super.CancelLinkIntrnl()) return true;

        if (aq3hInstance != null) aq3hInstance.DeregisterLink(this); // YYY Moved here from above "return true"
        flushWritingShot(); // YYY Moved here from above "return true"

        if (null != segmented_data) 
            try {
                Segmented_data_cancel();
            } catch (Exception e) {
                Tum3Logger.DoLog(db_name, true, "WARNING: exception in Segmented_data_cancel()." + " Session: " + DebugTitle() + "; " + Tum3Util.getStackTrace(e)); // YYY
            }
        if (dbLink != null) {
            Tum3Broadcaster.release(dbLink, this);
            dbLink.releaseDbClient();
            dbLink = null;
        }

        return false;

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

    private String ExecuteUgc(byte thrd_ctx, String _shot_num, int _req_id, byte[] _upd_arr) throws Exception {

        String tmp_err_msg = "Unspecified error";

        if (Tum3cfg.UgcUplinked(db_index)) {

            return UplinkManager.ExecuteUgc(db_index, this, _shot_num, _req_id, _upd_arr);

        } else {

            if ((-1 == _req_id) || (-3 == _req_id)) {
//System.out.println("[DEBUG] Ugc request shot <" + _shot_num + "> id " + _req_id);
                return dbLink.GetUgcData(thrd_ctx, this, _req_id, _shot_num);
            } else {
//System.out.println("[DEBUG] Ugc update in shot " + _shot_num + " id " + _req_id);

                if (AuthorizedLogin.isEmpty())
                    tmp_err_msg = "No username found for UGC update";
                else {

                    if (Tum3cfg.UgcEnabled(db_index)) {

                        boolean tmp_granted = false;
                        if (UserPermissions != null) if (UserPermissions.isCommentingAllowed()) tmp_granted = true;
                        if (tmp_granted) {
                            tmp_err_msg = dbLink.UpdateUgcData(thrd_ctx, AuthorizedLogin, UserPermissions.isAddTagAllowed(), this, _req_id, _shot_num, _upd_arr);
                        } else {
                            tmp_err_msg = dbLink.CONST_MSG_ACCESS_DENIED;
                        }

                    } else {
                        tmp_err_msg = dbLink.CONST_MSG_READONLY_NOW;
                    }
                }

                return tmp_err_msg; // YYY
            }
        }
    }

    public void GenerateUgcReply(byte thrd_ctx, int _req_id, String _shot_name, String _err_msg, byte[] data) throws Exception {

        GenerateUgcReplyIntl(thrd_ctx, _req_id, _shot_name, _err_msg, data);

    }

    public void PostUgcReply(int _req_id, String _shot_name, String _err_msg, byte[] data) {

        //Tum3Logger.DoLog(db_name, true, "[DEBUG] SrvLink.PostUgcReply: <" + _shot_name + "><" + _req_id + "> username=" + AuthorizedLogin);
        ByteBuffer tmp_bb = null;
        if (null != data) tmp_bb = ByteBuffer.wrap(data);
        AddGeneralEvent(null, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_UGC_RESPONSE, _req_id, _shot_name, _err_msg, tmp_bb), null, null);

    }

    private void GenerateUgcReplyIntl(byte thrd_ctx, int _req_id, String _shot_name, String _err_msg, byte[] data) throws Exception {

        int tmp_trailing_count = 0;
        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, null);
        String tmp_err_str255 = Str255(_err_msg);
        if (null != data) tmp_trailing_count = data.length;
        tmpBuff.InitSrvReply(REQUEST_TYPE_UGC_REP,
                4 + _shot_name.length() + 1 + tmp_err_str255.length() + 1 + tmp_trailing_count,
                4 + _shot_name.length() + 1 + tmp_err_str255.length() + 1 + tmp_trailing_count);
        tmpBuff.putPasString(_shot_name);
        tmpBuff.putInt(_req_id);
        tmpBuff.putPasString(tmp_err_str255);
        if (tmp_trailing_count > 0) tmpBuff.putBytes(data);
        PutBuff(thrd_ctx, tmpBuff, null);

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

    protected boolean NoPauseOut() {

        return 
                (!(FModerateNeedSendRequest || FModerateRequestWasSent) || !FDoModerateDownloadRate)
                &&
                ((hanging_out_trace_bytes < CONST_MAX_TRACE_OUT_QUEUE_BYTES[db_index]) && (hanging_out_trace_number < CONST_MAX_TRACE_OUT_QUEUE_LEN[db_index]));

    }

    protected boolean GetTracesContinue(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

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
                    } else if (tmp_trace_data.getFullSizeX() <= Integer.MAX_VALUE) { // YYY
                        tmp_this_trace_len = (int)tmp_trace_data.getFullSizeX();
                        tmpBuff.SetSegment(0, tmp_this_trace_len, true);
                    } else throw new Exception("Attempted to put > 2Gb in one go."); // YYY

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


    private void Segmented_data_cancel() throws Exception {

        segmented_TraceReq = null;
        segmented_data.close();
        segmented_data = null;
        segmented_Full = 0;
        segmented_Ofs = 0;

    }

    protected void TrySendContinuationReq(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

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


    private ByteBuffer CreateUsrListHeadBB(String _name, int _usr_list_len) {

        ByteBuffer tmp_bb1 = ByteBuffer.allocate(4 + 4 + _name.length() + 4);
        tmp_bb1.order(ByteOrder.LITTLE_ENDIAN);
        tmp_bb1.putInt(TumProtoConsts.tum3misc_userlist);
        tmp_bb1.putInt(_name.length());
        tmp_bb1.put(Tum3Util.StringToBytesRaw(_name));
        tmp_bb1.putInt(_usr_list_len); // YYY
        return tmp_bb1;

    }


    private void Process_ReqFiles(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {

        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_ReqFiles." + " Session: " + DebugTitle());
            throw new Exception("no dbLink in Process_ReqFiles");
        }

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
        tmpBB.limit(req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);

        String tmp_file_names = "";
        try {
            StringBuffer tmp_buff = new StringBuffer();
            while (tmpBB.position() < req_trailing_len) tmp_buff.append((char)tmpBB.get());
            tmp_file_names = tmp_buff.toString();
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "WARNING: unexpected format request in Process_ReqFiles() ignored." + " Session: " + DebugTitle());
        }

        //System.out.println("[aq2j] DEBUG: Process_ReqFiles(): <" + tmp_file_names +">");
        StringList tmp_req_file_names = new StringList(tmp_file_names.split("\r\n"));
        if (tmp_req_file_names.size() > 0) {

            int tmp_size = 0;
            ByteArrayOutputStreamX temp_storage = new ByteArrayOutputStreamX();
            Tum3CollateralUpdateHelper.LoadPerRequestTo(db_name, temp_storage, tmp_req_file_names);

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
                ByteBuffer tmp_bb2 = Tum3Broadcaster.GetUserList(dbLink, true);
                int tmp_list_len = 0;
                if (null != tmp_bb2) tmp_list_len = tmp_bb2.position(); // YYY
                ByteBuffer tmp_bb1 = CreateUsrListHeadBB(tmp_keyword, tmp_list_len);
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

        String tmp_attempted_shot = "(unknown)";
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
            tmp_result = UpdateDensityData(tmp_attempted_shot, tmp_attempted_id, tmp_upd_arr); // YYY

        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "WARNING: unexpected format request in Process_DensitySave() ignored. " + " Session: " + DebugTitle() + " (" + Tum3Util.getStackTrace(e) + ")");
            tmp_result = "Density save failed with: " + e;
        }

        String tmp_comment_txt = tmp_result;
        boolean tmp_save_ok = tmp_result.isEmpty(); // YYY
        if (tmp_save_ok) tmp_comment_txt = "success";
        if (!tmp_save_ok) { // YYY
          Tum3Logger.DoLog(db_name, false, "Density update (in shot " + tmp_attempted_shot + " id " + tmp_attempted_id + ") by " + DebugTitle() + " with result: " + tmp_comment_txt);
          _NewMessageBoxCompat(thrd_ctx, tmp_result, false);
        }
    }

    private void Process_Ugc(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {

        if (dbLink == null) {
            Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_Ugc." + " Session: " + DebugTitle());
            throw new Exception("no dbLink in Process_Ugc");
        }

        ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
        tmpBB.limit(req_trailing_len);
        tmpBB.order(ByteOrder.LITTLE_ENDIAN);

        String tmp_result = "Ugc request failed.";

        String tmp_attempted_shot = "(unknown)";
        int    tmp_attempted_id = 0;

        try {
            int tmp_str_len = tmpBB.get();
            if ((tmp_str_len > 20) || ((tmpBB.position()+tmp_str_len+4) > req_trailing_len)) throw new Exception("[aq2j] WARNING: invalid name length in Process_Ugc()");
            StringBuffer tmp_shot_name = new StringBuffer();
            for (int tmp_i=0; tmp_i < tmp_str_len; tmp_i++) tmp_shot_name.append((char)tmpBB.get()); // XXX FIXME! Simplify this.
            if ((tmpBB.position()+4) > req_trailing_len) throw new Exception("[aq2j] WARNING: invalid request found in Process_Ugc()");
            int tmp_req_id = tmpBB.getInt();
            //System.out.println("[DEBUG] tmp_req_id=" + tmp_req_id + ", position=" + tmpBB.position() + ", req_trailing_len=" + req_trailing_len);
            if (((tmp_req_id < 1) || (tmp_req_id > 2000000)) && (tmp_req_id != -1) && (tmp_req_id != -3)) throw new Exception("[aq2j] WARNING: invalid request id in Process_Ugc()");
            if (((tmp_req_id >= -1) && (tmp_str_len < 7)) || ((tmp_req_id == -3) && (tmp_str_len != 0))) throw new Exception("[aq2j] WARNING: invalid name length in Process_Ugc()"); // YYY
            int tmp_body_size = req_trailing_len - tmpBB.position();
            byte[] tmp_upd_arr = new byte[tmp_body_size];
            tmpBB.get(tmp_upd_arr);
            tmp_attempted_shot = tmp_shot_name.toString();
            tmp_attempted_id = tmp_req_id;
            tmp_result = ExecuteUgc(thrd_ctx, tmp_attempted_shot, tmp_attempted_id, tmp_upd_arr);

        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "WARNING: unexpected format request in Process_Ugc() ignored. " + " Session: " + DebugTitle() + " (" + Tum3Util.getStackTrace(e) + ")");
            tmp_result = "Ugc request failed with: " + e;
        }

        if (!tmp_result.isEmpty()) GenerateUgcReplyIntl(thrd_ctx, tmp_attempted_id, tmp_attempted_shot, tmp_result, null); // YYY

        //if (!tmp_result.isEmpty())
        //  Tum3Logger.DoLog(db_name, false, "Ugc request by " + DebugTitle() + " with result: " + tmp_result);
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

    private void Process_AgentInfo(byte thrd_ctx, byte[] req_body, int req_trailing_len) throws Exception {
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

        FDoModerateDownloadRate = true; // (RCompatVersion >= 231); // YYY
        use_tracecome_x = true; // (RCompatVersion >= 332); // YYY
        segmented_data_allowed = true; // (RCompatVersion >= 373); // YYY
        if (RLinkKilobits == 0) RLinkKilobits = 100*1024;
        if (RLinkKilobits < 500) RLinkKilobits = 500;
        if (RLinkKilobits > 2*1024*1024) RLinkKilobits = 2*1024*1024;
        FModeratedDownloadBytes = RLinkKilobits << 6; // Reminder: this sets approx 6 Mbytes for 100Mbit link.

        String tmp_critical_msg = ""; // YYY
        if ((RCompatVersion > 0) && (RCompatVersion < min_supported_app_ver))
            tmp_critical_msg = tmp_critical_msg + "IMPORTANT! This program version is critically outdated! It may not work correctly. Please update.\r\n";
        if (Tum3Logger.BogusClockDetected()) // YYY
            tmp_critical_msg = tmp_critical_msg + "IMPORTANT! Server reports wrong clock setting. Database functionality will be limited.\r\n";
        if (!tmp_critical_msg.isEmpty())
            _NewMessageBoxCompat(thrd_ctx, tmp_critical_msg, false); // YYY

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
//System.out.println("[DEBUG] InitDbAccess done.");
            ConsiderPushServerInfo(thrd_ctx);
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

    private void ConsiderPushServerInfo(byte thrd_ctx) throws Exception {

        if (NeedPushServerInfo && (RCompatVersion > 428) && (null != dbLink)) {
            NeedPushServerInfo = false; // YYY
            //Tum3Logger.DoLog(db_name, true, "[DEBUG] PushServerFriendlyInfo()");
            PushServerFriendlyInfo(thrd_ctx, dbLink.getServerInfo()); // YYY
        }
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

                OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx); // YYY
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

                OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx);
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

        if (isCancellingLink()) return false;

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

        Tum3Broadcaster.DistributeGeneralEvent(dbLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TALK, thisMsg, thisReceiverName), null, thisReceiverName, thisEchoName);

    }

    private String DoFlexTxtReq(String _ReqSeq, String _Action, String _Option, String _Body, StringBuilder _res_body) {

        if (TumProtoConsts.FLEXCMD_hotstart.equals(_Action)) {

            if (!dbLink.isWriteable || !Tum3cfg.isWriteable(db_index)) return Tum3Db.CONST_MSG_READONLY_NOW; // YYY
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

    protected void SyncHandleAsyncEvent(byte thrd_ctx, int _ev_type, GeneralDbDistribEvent _ev) throws Exception {

        OutgoingBuff tmpBuff = null;

        if (_ev_type == _ev.DB_EV_UGC_RESPONSE) { // YYY
            //System.out.println("[DEBUG] DB_EV_UGC_RESPONSE: <" + _ev.get_str() + "><" + _ev.get_int() + ">");
            byte[] tmp_data_arr = null;
            if (_ev.get_bb() != null) tmp_data_arr = _ev.get_bb().array();
            GenerateUgcReplyIntl(thrd_ctx, _ev.get_int(), _ev.get_str(), _ev.get_str2(), tmp_data_arr);
        } else if (_ev_type == _ev.DB_EV_TALK) {
            tmpBuff = GetBuff(thrd_ctx, null);
            String tmpMsg = _ev.get_str();
            tmpBuff.InitSrvReply(REQUEST_TYPE_TALKMSG_IN, tmpMsg.length(), tmpMsg.length());
            tmpBuff.putString(tmpMsg);
            PutBuff(thrd_ctx, tmpBuff, null);
        } else if (_ev_type == _ev.DB_EV_USERLIST) {
            tmpBuff = GetBuff(thrd_ctx, null);
            ByteBuffer tmp_bb2 = _ev.get_bb();
            ByteBuffer tmp_bb_aux = _ev.get_bb2(); // YYY
            int tmp_list_len = 0;
            if (null != tmp_bb2)    tmp_list_len += tmp_bb2.position(); // YYY
            if (null != tmp_bb_aux) tmp_list_len += tmp_bb_aux.position(); // YYY
            ByteBuffer tmp_bb1 = CreateUsrListHeadBB(TUM3_KEYWORD_users, tmp_list_len);
            int tmp_size = tmp_list_len + tmp_bb1.position(); // YYY
            tmpBuff.InitSrvReply(REQUEST_TYPE_MISC_FETCH, tmp_size, tmp_size);
            tmpBuff.putBytes(tmp_bb1.array(), 0, tmp_bb1.position());
            tmpBuff.putBytes(tmp_bb2.array(), 0, tmp_bb2.position());
            if (null != tmp_bb_aux) 
                tmpBuff.putBytes(tmp_bb_aux.array(), 0, tmp_bb_aux.position()); // YYY
            PutBuff(thrd_ctx, tmpBuff, null);
            //System.out.println("[DEBUG] DB_EV_USERLIST: " + tmp_size);
        } else if (_ev_type == _ev.DB_EV_NEWSHOT) {
            String tmpMsg = _ev.get_str();
            tmpBuff = GetBuff(thrd_ctx, null);
            if (tmpMsg.length() > 100) tmpMsg = tmpMsg.substring(0, 100);
            tmpBuff.InitSrvReply(REQUEST_TYPE_TRACEINVALIDATEONE, 1 + 4*1 + tmpMsg.length(), 1 + 4*1 + tmpMsg.length());
            tmpBuff.putByte((byte)tmpMsg.length());
            tmpBuff.putString(tmpMsg);
            tmpBuff.putInt(0);
            PutBuff(thrd_ctx, tmpBuff, null);
            //System.out.println("[DEBUG] REQUEST_TYPE_TRACEINVALIDATEONE <" + tmpMsg + "> in " + db_name + " sent OK.");
        } else if (_ev_type == _ev.DB_EV_UGC_SHOT_UPD) {
            String tmpMsg = _ev.get_str();
            tmpBuff = GetBuff(thrd_ctx, null);
            if (tmpMsg.length() > 100) tmpMsg = tmpMsg.substring(0, 100);
            tmpBuff.InitSrvReply(REQUEST_TYPE_UGC_REP, 4 + tmpMsg.length() + 1 + 0 + 1, 4 + tmpMsg.length() + 1 + 0 + 1);
            tmpBuff.putPasString(tmpMsg);
            tmpBuff.putInt(-2);
            tmpBuff.putPasString("");
            PutBuff(thrd_ctx, tmpBuff, null);
            //System.out.println("[DEBUG] REQUEST_TYPE_UGC_REP <" + tmpMsg + "> in " + db_name + " sent OK.");
        } else if (_ev_type == _ev.DB_EV_UGC_LIST_UPD) { // YYY
            tmpBuff = GetBuff(thrd_ctx, null);
            tmpBuff.InitSrvReply(REQUEST_TYPE_UGC_REP, 4 + 0 + 1 + 0 + 1, 4 + 0 + 1 + 0 + 1);
            tmpBuff.putPasString("");
            tmpBuff.putInt(_ev.get_int());
            tmpBuff.putPasString("");
            PutBuff(thrd_ctx, tmpBuff, null);
            //System.out.println("[DEBUG] REQUEST_TYPE_UGC_REP in " + db_name + " sent OK.");
        } else if ((_ev_type == _ev.DB_EV_TRACEUPD) || (_ev_type == _ev.DB_EV_TRACEUPD_ARR) || (_ev_type == _ev.DB_EV_TRACEDEL_ARR)) {
            int tmp_id = 0, tmp_id_count = 1;
            int[] tmp_ids = null;
            if ((_ev_type == _ev.DB_EV_TRACEUPD_ARR) || (_ev_type == _ev.DB_EV_TRACEDEL_ARR)) {
                tmp_ids = _ev.get_int_ar().clone(); // Arrays.copyOf(thisIds, thisIds.length);
                tmp_id_count = tmp_ids.length;
            } else {
                tmp_id = _ev.get_int();
                tmp_ids = new int[1];
                tmp_ids[0] = tmp_id;
            }
            String tmpMsg = _ev.get_str();

            //if ((_ev_type == _ev.DB_EV_TRACEUPD_ARR) || (_ev_type == _ev.DB_EV_TRACEUPD)) {
            //System.out.print("[DEBUG] " + CallerNetAddr() + " Invalidate ids " + tmpMsg + ": ");
            //for (int q=0; q < tmp_id_count; q++) System.out.print(tmp_ids[q] + ", ");
            //System.out.println(" ");
            //}
            int tmp_processed_count = 0;
            if (_ev_type != _ev.DB_EV_TRACEDEL_ARR) tmp_processed_count = ResumeTraceRequests(tmpMsg, tmp_ids);
            tmp_need_resume = (tmp_processed_count > 0);
            //System.out.println("[DEBUG] tmp_id_count=" + tmp_id_count + " tmp_processed_count=" + tmp_processed_count);
            if (tmp_id_count > tmp_processed_count) {
                tmpBuff = GetBuff(thrd_ctx, null);
                if (tmpMsg.length() > 100) tmpMsg = tmpMsg.substring(0, 100);
                byte tmp_notify_code = REQUEST_TYPE_TRACEINVALIDATEONE;
                if (_ev_type == _ev.DB_EV_TRACEDEL_ARR) tmp_notify_code = REQUEST_TYPE_TRACEREMOVED;
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
    }

    public void AddGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, String thisReceiverName, String thisEchoName) {

        //if (null != ev) if (ev.get_type() == GeneralDbDistribEvent.DB_EV_UGC_RESPONSE) Tum3Logger.DoLogGlb(true, "[DEBUG] AddGeneralEvent (DB_EV_UGC_RESPONSE)");

        // We are usually interested in our db-originating events or masterdb-originating events.
        if ((origin_db != null) && (dbLink == null)) return; // Just in case.
        if (origin_db != null) if ((origin_db != dbLink) && (origin_db != dbLink.GetMasterDb()) && (origin_db.GetMasterDb() != dbLink)) return;

        int tmp_ev_type = ev.get_type();

        if ((RCompatFlags & 1) > 0) if ((tmp_ev_type != ev.DB_EV_NEWSHOT) || (origin_db != dbLink)) return; // Reminder: this is acquis-control link only, no need for most kinds of notifications.

        //if (tmp_ev_type == ev.DB_EV_NEWSHOT) System.out.println("[DEBUG] AddGeneralEvent: " + ev.get_str() + " in " + db_name);

        if ((tmp_ev_type == ev.DB_EV_NEWSHOT) && (origin_db != dbLink) /* && (origin_db != dbLink.GetMasterDb()) */ ) return; // YYY Special case: never allow DB_EV_NEWSHOT between master/slave directly.

        if (RCompatVersion == 0) { // This is a legacy acquisition node (most probably).
            if ((tmp_ev_type == ev.DB_EV_TALK) 
                    || (tmp_ev_type == ev.DB_EV_USERLIST)
                    || (tmp_ev_type == ev.DB_EV_TRACEUPD)
                    || (tmp_ev_type == ev.DB_EV_TRACEUPD_ARR)
                    || (tmp_ev_type == ev.DB_EV_TRACEDEL_ARR)
                    ) return;
            if ((tmp_ev_type == ev.DB_EV_NEWSHOT) && ((FFeatureSelectWord & 1) == 0) || (ev.get_int() == ev.IS_MASTER_ONLY)) return; // YYY
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

    protected boolean getLoginFailedState() {

        return LoginFailedState;

    }

    protected long getLoginFailedAt() {

        return LoginFailedAt;

    }

    protected boolean getWasAuthorized() {

        return WasAuthorized;

    }

    protected String getLogPrefixName() {

        return db_name;

    }

    protected void ExecuteReq(byte thrd_ctx, byte req_code, byte[] req_body, int req_trailing_len) throws Exception {

        if ((REQUEST_TYPE_REPORTAVAILVERSION == req_code) || (REQUEST_TYPE_REPORTAVAILVERSION_FULL == req_code) || (REQUEST_TYPE_REPORTAVAILVERSION_64 == req_code) || (REQUEST_TYPE_REPORTAVAILVERSION_FULL_64 == req_code)) 
            Process_ReportAvailVer(thrd_ctx, null, req_code);
        else if (REQUEST_TYPE_SENDMEPROGRAMFILE == req_code) Process_AppUpdate(thrd_ctx, null);
        else if ((REQUEST_TYPE_USERLOGIN == req_code) || (REQUEST_TYPE_USERLOGINX == req_code)) Process_UserLogin(thrd_ctx, req_body, req_trailing_len, (REQUEST_TYPE_USERLOGINX == req_code));
        else if ((REQUEST_TYPE_UPLOAD_ONE == req_code) || (REQUEST_TYPE_UPLOAD_ONE_VAR == req_code)) Process_UploadOne(thrd_ctx, req_body, req_trailing_len, null, REQUEST_TYPE_UPLOAD_ONE_VAR == req_code);
        else if (REQUEST_TYPE_DELETE_ONE_VAR == req_code) Process_DeleteOne(thrd_ctx, req_body, req_trailing_len, null);
        else if (REQUEST_TYPE_UPLOAD_END_HINT == req_code) Process_UploadEndHint();
        else if (REQUEST_TYPE_AGENT_INFO == req_code) Process_AgentInfo(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_FEATURESELECT == req_code) Process_FeatureSelect(req_body, req_trailing_len);
        else if (REQUEST_TYPE_DIRECTORYCALL == req_code) Process_GetDirList(thrd_ctx, req_body, req_trailing_len, null);
        else if (REQUEST_TYPE_ANYBODYTHERE == req_code) Process_PingHighlevel(thrd_ctx, null);
        else if (REQUEST_TYPE_KEEPCONNECTED == req_code) Process_PingReply();
        else if (REQUEST_TYPE_DOWNLOAD_RESUME == req_code) Process_DownloadResume(thrd_ctx);
        else if (REQUEST_TYPE_TRACECALL == req_code) Process_GetTrace(thrd_ctx, req_body, req_trailing_len, false);
        else if (REQUEST_TYPE_REFUSE    == req_code) Process_GetTrace(thrd_ctx, req_body, req_trailing_len, true);
        else if (REQUEST_TYPE_CONFIGSCALL == req_code) Process_GetConfigs(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_REQUEST_FILES == req_code) Process_ReqFiles(thrd_ctx, req_body, req_trailing_len); 
        else if (REQUEST_TYPE_GET_MISC == req_code) Process_GetMiscInfos(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_CONFIGSSAVE == req_code) Process_ConfigsSave(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_DENSITY_UPD == req_code) Process_DensitySave(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_UGC_REQ == req_code) Process_Ugc(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_TALKMSG == req_code) Process_TalkMsg(req_body, req_trailing_len);
        else if (REQUEST_TYPE_TALKMSGX == req_code) Process_TalkMsgX(req_body, req_trailing_len);
        else if (REQUEST_TYPE_FLEX_TXT == req_code) Process_FlexTxtReq(thrd_ctx, req_body, req_trailing_len);
        else if (REQUEST_TYPE_AQ3_REQ == req_code) Process_Aq3(thrd_ctx, req_body, req_trailing_len);
        else {
            Tum3Logger.DoLog(db_name, true, "WARNING: unknown request, code=" + Integer.toHexString(req_code & 0xFF) + " len=" + req_trailing_len + ";" + " Session: " + DebugTitle());
        }
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
            tmp_name = tmp_entry.GetValueFor(Tum3SignalList.const_signal_title, tmp_name);
            tmp_err_prefix = "Could not update " + tmp_name + " of " + _shot_name + ": ";
            if (!"1".equals(tmp_entry.GetValueFor(Tum3SignalList.const_signal_is_density, ""))) return tmp_err_prefix + "not a density signal.";
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

        if (!tmp_shot.isWriteable || !Tum3cfg.isWriteable(db_index)) { // YYY
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
            tmp_name = tmp_entry.GetValueFor(Tum3SignalList.const_signal_title, tmp_name);
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

        if (!tmp_shot.isWriteable || !Tum3cfg.isWriteable(db_index)) { // YYY
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
            tmp_name = tmp_entry.GetValueFor(Tum3SignalList.const_signal_title, tmp_name);
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

        if (!tmp_shot.isWriteable || !Tum3cfg.isWriteable(db_index)) { // YYY
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

    private boolean DoSendDownloadPaused(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
        return DoSimpleReq(thrd_ctx, REQUEST_TYPE_DOWNLOAD_PAUSED, ctx);
        //System.out.println("[aq2j] DEBUG: DoSendDownloadPaused.");
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

        if (null != currWritingShotHelper) currWritingShotHelper.tick();
        ConsiderPushServerInfo(thrd_ctx); // YYY

        super.ClientReaderTick(thrd_ctx, outbound);
    }

    protected void _NewMessageBoxCompat(byte thrd_ctx, String the_text, boolean _with_logger) throws Exception {

        if (_with_logger) Tum3Logger.DoLog(getLogPrefixName(), true, the_text);
        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, null);
        tmpBuff.InitSrvReply(TumProtoConsts.REQUEST_TYPE_INFORMATION_TEXT, the_text.length(), the_text.length());
        tmpBuff.putString(the_text);
        try {
            PutBuff(thrd_ctx, tmpBuff, null);
        } catch (Exception e) {
            tmpBuff.CancelData();
            throw e;
        }
    }

    private void PushServerFriendlyInfo(byte thrd_ctx, String the_text) throws Exception {

        //System.out.print("[DEBUG] PushServerFriendlyInfo()");
        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, null);
        tmpBuff.InitSrvReply(TumProtoConsts.REQUEST_TYPE_SERVERINFO, the_text.length(), the_text.length());
        tmpBuff.putString(the_text);
        try {
            PutBuff(thrd_ctx, tmpBuff, null);
        } catch (Exception e) {
            tmpBuff.CancelData();
            throw e;
        }
    }

    public void SetFlag() {

        NeedPushServerInfo = true;

    }
}
