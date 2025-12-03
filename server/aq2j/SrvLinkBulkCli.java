/*
 * Copyright 2023-2024 Nikolai Zhubr <zhubr@rambler.ru>
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

import org.json.*;

import aq2db.*;
import aq2net.*;


public class SrvLinkBulkCli extends SrvLinkIntercon implements UplinkBulkMgr.UplinkOperations {


    private final static String TUM3_CFG_bup_idle_check_sec = "bup_idle_check_sec";
    private final static String TUM3_CFG_bup_get_list_max_sec = "bup_get_list_max_sec";
    private final static String TUM3_CFG_bup_signle_send_max_sec = "bup_signle_send_max_sec";
    private final static String TUM3_CFG_bup_max_portion_kb = "bup_max_portion_kb";
    private final static String TUM3_CFG_bup_kbytes_sec_limit = "bup_kbytes_sec_limit";

    private final static int[] ST_BUP_IDLE_CHECK_SECONDS;
    private final static int[] ST_BUP_GET_LIST_MAX_SECONDS;
    private final static int[] ST_BUP_SINGLE_SEND_MAX_SECONDS;
    private final static int[] ST_BUP_MAX_PORTION_KB;
    private final static int[] ST_BUP_KBYTES_SEC_LIMIT;

    private final static int BUP_IDLE_CHECK_SECONDS_default = 60;
    private final static int BUP_GET_LIST_MAX_SECONDS_default = 60;
    private final static int BUP_SINGLE_SEND_MAX_SECONDS_default = 30;
    private final static int BUP_MAX_PORTION_KB_default = 512;
    private final static int BUP_KBYTES_SEC_LIMIT_default = 0;

    private final int BUP_IDLE_CHECK_SECONDS;
    private final int BUP_GET_LIST_MAX_SECONDS;
    private final int BUP_SINGLE_SEND_MAX_SECONDS;
    private final int BUP_MAX_PORTION_KB;
    private final int BUP_KBYTES_SEC_LIMIT;
    private final int seg_limit; // FModeratedDownloadBytes;

    private final static int BUP_STAGE_IDLE = 0;
    private final static int BUP_STAGE_EXPECTING_LAST_LIST = 1;
    private final static int BUP_STAGE_EXPECTING_UPL_RESULT = 2;

    private String username, password;
    private ArrayList<TempUGCReqHolder> ExecutingList = new ArrayList<TempUGCReqHolder>();
    private long last_checked = System.currentTimeMillis();

    private final static TunedSrvLinkParsBulkCli tuned_pars_bulkcli = new TunedSrvLinkParsBulkCli();

    private volatile int curr_stage = BUP_STAGE_IDLE;
    private volatile long curr_stage_started = 0;
    private boolean is_volatile = false, some_was_processed = false;
    private String last_reset_date = null; // YYY

    private long segmented_Full = 0, segmented_Ofs = 0;
    private int segmented_chunk_size = 0;
    private Tum3Db.BupTransferContinuator curr_trnsfr_file = null;

    private long ratelim_bytes = 0, ratecalc_bytes = 0, ratelim_started_at = 0, ratecalc_started_at = 0;


    private static class TunedSrvLinkParsBulkCli extends SrvLinkBase.TunedSrvLinkPars {

        public void AssignStaticValues() {

            LINK_PARS_LABEL = "bulk cli";

            TUM3_CFG_idle_check_alive_delay = "idle_check_alive_bulk_c";
            TUM3_CFG_max_out_buff_count = "max_out_buff_count_bulk_c";
            TUM3_CFG_min_out_buff_kbytes = "min_out_buff_kbytes_bulk_c";

            CONST_OUT_BUFF_COUNT_MAX_default = 6;
            CONST_KEEPALIVE_INTERVAL_SEC_default = 20;
            CONST_MIN_OUT_BUFF_default = 100;  // kbytes.
        }
    }

    static {

        int tmp_db_count = Tum3cfg.getGlbInstance().getDbCount();
        Tum3cfg cfg = Tum3cfg.getGlbInstance();

        ST_BUP_IDLE_CHECK_SECONDS = new int[tmp_db_count];
        ST_BUP_GET_LIST_MAX_SECONDS = new int[tmp_db_count];
        ST_BUP_SINGLE_SEND_MAX_SECONDS = new int[tmp_db_count];
        ST_BUP_MAX_PORTION_KB = new int[tmp_db_count];
        ST_BUP_KBYTES_SEC_LIMIT = new int[tmp_db_count];

        for (int tmp_i = 0; tmp_i < tmp_db_count; tmp_i++) {
            String db_name = cfg.getDbName(tmp_i);
            ST_BUP_IDLE_CHECK_SECONDS[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_bup_idle_check_sec, BUP_IDLE_CHECK_SECONDS_default);
            ST_BUP_GET_LIST_MAX_SECONDS[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_bup_get_list_max_sec, BUP_GET_LIST_MAX_SECONDS_default);
            ST_BUP_SINGLE_SEND_MAX_SECONDS[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_bup_signle_send_max_sec, BUP_SINGLE_SEND_MAX_SECONDS_default);
            ST_BUP_MAX_PORTION_KB[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_bup_max_portion_kb, BUP_MAX_PORTION_KB_default);
            ST_BUP_KBYTES_SEC_LIMIT[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_bup_kbytes_sec_limit, BUP_KBYTES_SEC_LIMIT_default);

            Tum3Logger.DoLog(db_name, false, "DEBUG: BUP_IDLE_CHECK_SECONDS=" + ST_BUP_IDLE_CHECK_SECONDS[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: BUP_GET_LIST_MAX_SECONDS=" + ST_BUP_GET_LIST_MAX_SECONDS[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: BUP_SINGLE_SEND_MAX_SECONDS=" + ST_BUP_SINGLE_SEND_MAX_SECONDS[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: BUP_MAX_PORTION_KB=" + ST_BUP_MAX_PORTION_KB[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: BUP_KBYTES_SEC_LIMIT=" + ST_BUP_KBYTES_SEC_LIMIT[tmp_i]);
        }
    }

    public SrvLinkBulkCli(int _db_idx, SrvLinkOwner thisOwner, String _username, String _password) {
        super(_db_idx, tuned_pars_bulkcli, thisOwner);

        BUP_IDLE_CHECK_SECONDS = ST_BUP_IDLE_CHECK_SECONDS[db_index];
        BUP_GET_LIST_MAX_SECONDS = ST_BUP_GET_LIST_MAX_SECONDS[db_index];
        BUP_SINGLE_SEND_MAX_SECONDS = ST_BUP_SINGLE_SEND_MAX_SECONDS[db_index];
        BUP_MAX_PORTION_KB = ST_BUP_MAX_PORTION_KB[db_index];
        BUP_KBYTES_SEC_LIMIT = ST_BUP_KBYTES_SEC_LIMIT[db_index];

        seg_limit = 1024 * BUP_MAX_PORTION_KB;
        username = _username;
        password = _password;
    }

    protected String InterconLabel() { return "bulk"; }

    public String ExecuteCmdLine(String _cmdline) throws Exception {

        //AddGeneralEvent(null, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_UGC_REQUEST, holder), null, null);
        return "Sorry, not implented";

    }

    private void GetServLastList(byte thrd_ctx, RecycledBuffContext ctx, long tmp_now_millis) throws Exception {

        dbLink.setBupVisibleStatus("Finding lag...");
        JSONObject jo = new JSONObject();
        jo.put(JSON_NAME_function, JSON_FUNC_bup_get_last_list);
        curr_stage = BUP_STAGE_EXPECTING_LAST_LIST;
        curr_stage_started = tmp_now_millis;
        is_volatile = false;
        some_was_processed = false;
        Send_JSON(thrd_ctx, ctx, jo);
    }

    @Override
    public void ClientReaderTick(byte thrd_ctx, ClientWriter outbound) throws Exception {
        // This function is guaranteed to be called by the main thread only.

        CheckBupError();

        long tmp_now_millis = System.currentTimeMillis();
        if (null != dbLink) if (!dbLink.BupErrorPresent() && (BUP_STAGE_IDLE == curr_stage) && ((tmp_now_millis - curr_stage_started) > ((long)1000*(long)BUP_IDLE_CHECK_SECONDS))) {
            if (dbLink.enable_sync_raw) GetServLastList(thrd_ctx, null, tmp_now_millis);
            else {
                TryStartSynWork();
                ConsiderSendOrStop(thrd_ctx, null);
            }
        }
        if ((BUP_STAGE_EXPECTING_LAST_LIST == curr_stage) && ((tmp_now_millis - curr_stage_started) > ((long)1000*(long)BUP_GET_LIST_MAX_SECONDS))) {
            String tmp_err_txt = "No bup list from uplink in " + BUP_GET_LIST_MAX_SECONDS + " seconds.";
            Tum3Logger.DoLog(db_name, false, tmp_err_txt);
            ShutdownSrvLink(tmp_err_txt);
        }
        if ((BUP_STAGE_EXPECTING_UPL_RESULT == curr_stage) && ((tmp_now_millis - curr_stage_started) > ((long)1000*(long)BUP_SINGLE_SEND_MAX_SECONDS))) {
            String tmp_err_txt = "No bup fragment confirmation from uplink in " + BUP_SINGLE_SEND_MAX_SECONDS + " seconds.";
            Tum3Logger.DoLog(db_name, false, tmp_err_txt);
            ShutdownSrvLink(tmp_err_txt);
        }

        super.ClientReaderTick(thrd_ctx, outbound);
    }

    protected void UplinkConnExtra(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        //dbLink.setOtherServerConnected(true);
        //ForceSendUserList(thrd_ctx, ctx);
        UplinkBulkMgr.setUplink(getDbIndex(), this);

    }

    private void CleanupFileHandle(boolean _and_close_now) {

        if (null != curr_trnsfr_file) {
            try {
                curr_trnsfr_file.ForceRelease(_and_close_now); // close();
            } catch (Exception e) {
                Tum3Logger.DoLog(db_name, true, "Unexpected exception in ForceRelease(): " + Tum3Util.getStackTrace(e));
                dbLink.setBupVisibleError("Failure in ForceRelease", "Unexpected exception in ForceRelease(): " + e.toString());
                ShutdownSrvLink("Unexpected exception in ForceRelease(): " + e.toString());
            }
            if (_and_close_now) {
                curr_trnsfr_file = null;
                segmented_Full = 0;
                segmented_Ofs = 0;
            }
        }
    }

    protected void onJSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo, byte[] bin_att, int att_ofs, int att_len) throws Exception {

        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Got JSON function <" + jo.get(JSON_NAME_function) + ">");
        try {
            String tmp_func = jo.getString(JSON_NAME_function);

            if (JSON_FUNC_welcome.equals(tmp_func)) {
                JsonWelcomeHandler(thrd_ctx, ctx, jo, username);
                return;
            }
            if (JSON_FUNC_bup_rep_last_list.equals(tmp_func) && WasAuthorized && (BUP_STAGE_EXPECTING_LAST_LIST == curr_stage)) {
                if (jo.has(JSON_NAME_err_msg)) {
                    String tmp_err_msg = "Exception in bulk uplink: " + jo.getString(JSON_NAME_err_msg);
                    Tum3Logger.DoLog(db_name, true, tmp_err_msg);
                    dbLink.setBupVisibleError("Search failed at uplink", tmp_err_msg);
                    ShutdownSrvLink(tmp_err_msg);
                } else {
                    dbLink.BupResetFrom(Tum3Util.BytesToStringRaw(bin_att, att_ofs, att_len));
                    String tmp_reset_date = dbLink.BupLastResetDate();
                    if (null != last_reset_date) if (last_reset_date.equals(tmp_reset_date))
                        throw new Exception("Date <" + tmp_reset_date + "> reoccured, bad sympthom"); // YYY
                    last_reset_date = tmp_reset_date;
                    BeginSendFile(thrd_ctx, ctx, true);
                }
                return;
            }
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "Unexpected exception in onJSON(): " + Tum3Util.getStackTrace(e));
            dbLink.setBupVisibleError("Failed pushing from master", "Unexpected exception in onJSON(): " + e.toString());
            throw e;
        }
    }

    private boolean CheckBupError() {

        if (null != dbLink) if (dbLink.BupErrorPresent()) {
            if (BUP_STAGE_IDLE != curr_stage) {
                curr_stage = BUP_STAGE_IDLE;
                curr_stage_started = System.currentTimeMillis();
                dbLink.setBupVisibleStatus("Cannot continue");
            }
            return true;
        }
        return false;
    }

    private void BeginSendFile(byte thrd_ctx, RecycledBuffContext ctx, boolean first_after_search) throws Exception {

        CleanupFileHandle(true);
        if (CheckBupError()) return;
        curr_trnsfr_file = dbLink.BupNextToSend();
        if (null == curr_trnsfr_file) {
            if (is_volatile) {
                if (dbLink.BupContinueFromSyn()) curr_trnsfr_file = dbLink.BupNextToSend();
                if (null == curr_trnsfr_file) {
                    if (some_was_processed) Tum3Logger.DoLog(db_name, false, "Backup sync: volatile data complete.");
                    is_volatile = false;
                    some_was_processed = false;
                }
            } else {
                last_reset_date = null; // YYY
                if (first_after_search) {
                    dbLink.setBupVisibleStatus("Done all raw");
                    if (some_was_processed) Tum3Logger.DoLog(db_name, false, "Backup sync: raw data complete.");

                    TryStartSynWork();
                } else {
                    //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG BeginSendFile: raw files one portion was finished.");
                    GetServLastList(thrd_ctx, ctx, System.currentTimeMillis());
                    return;
                }
            }
        }
        ConsiderSendOrStop(thrd_ctx, ctx);
    }

    private void ConsiderSendOrStop(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        if (null == curr_trnsfr_file) {
            curr_stage = BUP_STAGE_IDLE;
            curr_stage_started = System.currentTimeMillis();
            dbLink.setBupVisibleStatus("Done all");
        } else {
            curr_trnsfr_file.ForceLock();
            segmented_Full = curr_trnsfr_file.getFullSizeX();
            segmented_Ofs = 0;
            if (segmented_Full > 0) {
                long tmp_seg_count = (segmented_Full + seg_limit - 1) / seg_limit;
                segmented_chunk_size = (int)((segmented_Full + tmp_seg_count - 1) / tmp_seg_count);
            } else segmented_chunk_size = 0;
            if (segmented_chunk_size > seg_limit) segmented_chunk_size = seg_limit;
            if (segmented_chunk_size <= 0) segmented_chunk_size = seg_limit;

            ContinueSendFile(thrd_ctx, ctx);
        }
    }

    private void TryStartSynWork() throws Exception {

        dbLink.BupResetSyn();
        boolean tmp_do_volatile = false;
        tmp_do_volatile = dbLink.BupContinueFromSyn();
        if (tmp_do_volatile) {
            if (Tum3Logger.BogusClockDetected()) {
                dbLink.setBupVisibleError("Blocked by bogus clock", "Volatile backup sync is blocked by bogus clock setting");
                tmp_do_volatile = false;
            } else {
                is_volatile = true;
                some_was_processed = false;
                curr_trnsfr_file = dbLink.BupNextToSend();
            }
        }
    }

    private void ContinueSendFile(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx);

        long tmp_seg_size = segmented_Full - segmented_Ofs;
        if (tmp_seg_size > segmented_chunk_size) tmp_seg_size = segmented_chunk_size;
        if (tmp_seg_size < 0) throw new Exception("Internal error: tmp_seg_size=" + tmp_seg_size);
        int tmp_this_trace_len = (int)tmp_seg_size;
        boolean tmp_is_last_seg = (segmented_Ofs + tmp_seg_size) >= segmented_Full; // segmented_data.IsLastSegment()
        tmpBuff.SetSegment(segmented_Ofs, tmp_this_trace_len, tmp_is_last_seg);
        segmented_Ofs += tmp_seg_size;
        if (tmp_is_last_seg) CleanupFileHandle(false);
        //if (true) throw new Exception("Test exceptn 4");

        long tmp_curr_millis = System.currentTimeMillis();
        ratecalc_bytes += tmp_this_trace_len + 150; // Kind of approx brutto.
        long tmp_delta_r = (tmp_curr_millis - ratecalc_started_at);
        if (tmp_delta_r >= 2000) { // Probably 2 seconds averaging should be ok.
            double tmp_rate;
            if (tmp_delta_r < 600000) tmp_rate = ratecalc_bytes * 0.9536743164e-3 / ((int)tmp_delta_r); // Convert bytes/millis to mbytes/sec approx. 1e-3
            else tmp_rate = -1.0;
            //Tum3Logger.DoLog(db_name, false, "[aq2j] DEBUG: rate: bytes=" + ratecalc_bytes + " millis=" + tmp_delta_r);
            dbLink.setBupVisibleRate(tmp_rate);
            ratecalc_bytes = 0;
            ratecalc_started_at = tmp_curr_millis;
        }

        if (0 != BUP_KBYTES_SEC_LIMIT) {
            ratelim_bytes += tmp_this_trace_len + 150; // Kind of approx brutto.
            if ((ratelim_bytes >> 10) > BUP_KBYTES_SEC_LIMIT) {
                long tmp_expected_millis = Math.round((ratelim_bytes >> 10) * 1000.0 / BUP_KBYTES_SEC_LIMIT);
                if ((tmp_expected_millis >= 1) && (tmp_expected_millis < 1000000)) {
                    long tmp_delta_t = (tmp_curr_millis - ratelim_started_at);
                    if (tmp_delta_t < (int)tmp_expected_millis) {
                        int tmp_sleep_ms = (int)tmp_expected_millis - (int)tmp_delta_t;
                        if (tmp_sleep_ms > 2000) tmp_sleep_ms = 2000; // Should not sleep too long.
                        Tum3Util.SleepExactly(tmp_sleep_ms);
                    }
                }
                ratelim_bytes = 0;
                tmp_curr_millis = System.currentTimeMillis();
                ratelim_started_at = tmp_curr_millis;
            }
        }

        InitFPartBuff(is_volatile, tmpBuff, REQUEST_TYPE_FPART_TRNSF, 0, curr_trnsfr_file, curr_trnsfr_file.ShotName(), curr_trnsfr_file.FileName(), curr_trnsfr_file.getFullSizeX(), tmpBuff.getSegOfs());

        //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: shot_name=" + curr_trnsfr_file.ShotName() + " file_name=" + curr_trnsfr_file.FileName() + " HFullSize=" + curr_trnsfr_file.getFullSizeX() + " HSegOffset=" + tmpBuff.getSegOfs());

        try {
            PutBuff(thrd_ctx, tmpBuff, ctx);
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "WARNING: exception in PutBuff(). Session: " + DebugTitle());
            tmpBuff.CancelData();
            throw e;
        }

        curr_stage = BUP_STAGE_EXPECTING_UPL_RESULT;
        curr_stage_started = tmp_curr_millis;
    }

    @Override
    protected void UnexpectedServerError(Exception e) {

        Tum3Logger.DoLog(db_name, true, "Backup sync unexpected error at master: " + Tum3Util.getStackTrace(e));
        dbLink.setBupVisibleError("Unexpected error at master", "Backup sync unexpected error at master: " + e.toString());
        super.UnexpectedServerError(e);

    }

    protected void InitDbAccess() {

        if (null == dbLink) {
            dbLink = Tum3Db.getDbInstance(db_index);
            //Tum3Broadcaster.addclient(dbLink, this);
        }

    }

    @Override
    protected boolean CancelLinkIntrnl() {

        if (super.CancelLinkIntrnl()) return true;

        //dbLink.setOtherServerConnected(false);

        CleanupFileHandle(true);

        if (dbLink != null) {
            dbLink.setBupVisibleStatus("Connection closed");
            //Tum3Broadcaster.release(dbLink, this);
            dbLink.releaseDbClient();
            dbLink = null;
        }

        UplinkBulkMgr.resetUplink(getDbIndex(), this);

        return false;
    }

    public void AddGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, String thisReceiverName, String thisEchoName) {
    }

    protected void onJSON_Intrnl(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo, byte[] bin_att, int att_ofs, int att_len) throws Exception {

        onJSON(thrd_ctx, ctx, jo, bin_att, att_ofs, att_len);

    }

    public void DoLink() throws Exception {

        super.DoLink();

        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Sending hello...");
        SendHelloMsg(SrvLink.THRD_INTERNAL, null, username, password);
        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] sent hello.");

    }

    private void Process_BupFileUpResult(byte thrd_ctx, RecycledBuffContext ctx, byte[] req_body, int req_trailing_len) throws Exception {

        try {
            if (dbLink == null) {
                Tum3Logger.DoLog(db_name, true, "Internal error: no dbLink in Process_BupFileUpResult." + " Session: " + DebugTitle());
                throw new Exception("no dbLink in Process_BupFileUpResult");
            }
            if (req_trailing_len < fixed_fpart_header_size) throw new Exception("Packet too small in Process_BupFileUpResult");

            String tmp_shot_name = bytes2string(req_body, 0);
            String tmp_file_name = bytes2string(req_body, 16);

            ByteBuffer tmpBB = ByteBuffer.wrap(req_body);
            tmpBB.limit(req_trailing_len);
            tmpBB.order(ByteOrder.LITTLE_ENDIAN);

            tmpBB.position(16+16);
            tmpBB.getInt(); // Reserved (SignalId)
            int HAccessOptions = tmpBB.getInt(); // Vol/Nonvol (getEditedByte)
            long HFullSize = tmpBB.getLong();
            long HSegOffset = tmpBB.getLong();
            tmpBB.getLong(); // Reserved.

            if (BUP_STAGE_EXPECTING_UPL_RESULT == curr_stage) {
                if (!tmp_shot_name.equals(curr_trnsfr_file.ShotName()) || !tmp_file_name.equals(curr_trnsfr_file.FileName()) || (HFullSize != curr_trnsfr_file.getFullSizeX())) throw new Exception("Process_BupFileUpResult: unexpected values (" + tmp_shot_name + " != " + curr_trnsfr_file.ShotName() + ")");
                String tmp_srv_err_txt = "";
                int tmp_err_txt_len = req_trailing_len - fixed_fpart_header_size;
                if (tmp_err_txt_len > 0) {
                    tmp_srv_err_txt = Tum3Util.BytesToStringRaw(req_body, fixed_fpart_header_size, tmp_err_txt_len);
                    String tmp_err_long = "Storing failed at uplink: " + tmp_srv_err_txt;
                    dbLink.setBupVisibleError("Storing failed at uplink", tmp_err_long);
                    Tum3Logger.DoLog(db_name, true, tmp_err_long);
                    throw new Exception(tmp_err_long);
                }
                if (HSegOffset != segmented_Ofs) throw new Exception("Process_BupFileUpResult: mismatch (" + HSegOffset + " != " + segmented_Ofs + ")");
                //if (true) throw new Exception("Test exceptn 6");

                //Tum3Logger.DoLog(db_name, true, "[aq2j] DEBUG: shot_name=" + curr_trnsfr_file.ShotName() + " file_name=" + curr_trnsfr_file.FileName() + " HFullSize=" + curr_trnsfr_file.getFullSizeX() + " wrote to HSegOffset=" + HSegOffset + " successfully.");

                if (HSegOffset >= curr_trnsfr_file.getFullSizeX()) {
                    some_was_processed = true;
                    if (is_volatile) dbLink.BupVolFileSuccess(tmp_shot_name, tmp_file_name, 0 == curr_trnsfr_file.getFullSizeX());
                    BeginSendFile(thrd_ctx, ctx, false);
                } else ContinueSendFile(thrd_ctx, ctx);
            }
        } catch (Exception e) {
            Tum3Logger.DoLog(db_name, true, "Unexpected exception in Process_BupFileUpResult(): " + Tum3Util.getStackTrace(e));
            dbLink.setBupVisibleError("Invalid reply from backup", "Unexpected exception in Process_BupFileUpResult(): " + e.toString());
            throw e;
        }
    }

    protected void ExecuteReq(byte thrd_ctx, byte req_code, byte[] req_body, int req_trailing_len) throws Exception {

        if (REQUEST_TYPE_FPART_CNFRM == req_code) Process_BupFileUpResult(thrd_ctx, null, req_body, req_trailing_len);
        else super.ExecuteReq(thrd_ctx, req_code, req_body, req_trailing_len);

    }
}
