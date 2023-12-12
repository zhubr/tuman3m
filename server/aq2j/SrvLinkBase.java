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
package aq2j;


import java.io.*;
import java.nio.*;
import java.util.*;

import aq2db.*;
import aq2net.*;
import aq3host.*;


public abstract class SrvLinkBase implements TumProtoConsts, SrvLinkIntf {


    private final static int CONST_TRAILING_BYTES_LIMIT = 1024*1024*256; // XXX TODO!!! Make this configurable?
    private final static int CONST_LOGIN_FAIL_PENDING_SEC = 15;
    private final static int CONST_LOGIN_TIMEOUT_SEC = 15;
    private final static int CONST_OUT_BUFF_WAIT_WAKEUP_SEC = 1000;

    // Note very nice to have it in base class but still.
    protected final static String TUM3_CFG_max_out_queue_kbytes = "max_out_queue_kbytes";
    protected final static String TUM3_CFG_max_out_queue_len = "max_out_queue_len";

    private final static int STAGE_SIGN_4BYTES = 0;
    private final static int STAGE_TRAILING_LEN = 1;
    private final static int STAGE_TRAILING_BODY = 2;

    public final static byte THRD_EXTERNAL = 4;
    public final static byte THRD_INTERNAL = 5;
    private final static byte THRD_UNKNOWN = 0;

    private int curr_stage = STAGE_SIGN_4BYTES;
    private int curr_remaining_bytes = 4;
    private int curr_req_trailing_len = 0;
    private byte curr_req_code = 0;
    private byte[] req_body = null, req_header = new byte[4], req_size_holder = new byte[4];
    private volatile boolean SupportOOB;

    private SrvLinkOwner Owner = null;
    private volatile boolean keepalive_sent_oob = false;
    private volatile int keepalive_code_sent;
    private volatile String oob_msg_remainder = "";
    private int keepalive_code_next;
    private volatile long last_client_activity_time;
    private long ConnectionStartedAt;

    private OutgoingBuff out_buff_now_sending = null;
    private Object OutBuffFullLock = new Object(), OutBuffEmptyLock = new Object();
    private volatile int out_buffs_empty_fill, out_buffs_full_fill;
    private volatile OutgoingBuff[] out_buffs_empty, out_buffs_full;
    private int out_buffs_count = 0;
    private RecycledBuffContext ctxRecycledReader = new RecycledBuffContext();
    private volatile boolean CancellingLink = false;

    protected volatile int TalkMsgQueueFill = 0;
    protected volatile GeneralDbDistribEvent[] TalkMsgQueue;
    protected volatile boolean TalkMsgQueueOverflow = false;
    protected boolean tmp_need_resume; // YYY

    // Should better be private or something.
    protected volatile int hanging_out_trace_bytes=0, hanging_out_trace_number=0; // Should better be private?
    protected volatile boolean keepalive_sent_inline = false; // Should better be private?

    protected final int db_index; // YYY Moved here from descendants.
    protected final TunedSrvLinkPars tuned_pars; // YYY


    public abstract static class TunedSrvLinkPars {

        protected String LINK_PARS_LABEL = ""; // YYY

        protected String TUM3_CFG_idle_check_alive_delay;
        protected String TUM3_CFG_max_out_buff_count;
        protected String TUM3_CFG_min_out_buff_kbytes;

        protected int CONST_OUT_BUFF_COUNT_MAX_default;
        protected int CONST_KEEPALIVE_INTERVAL_SEC_default;
        protected int CONST_MIN_OUT_BUFF_default;  // kbytes.

        protected final int CONST_MIN_OUT_BUFF[];
        protected final int CONST_OUT_BUFF_COUNT_MAX[];
        protected final int CONST_KEEPALIVE_INTERVAL_SEC[];

        public abstract void AssignStaticValues(); // YYY

        protected TunedSrvLinkPars() {

            AssignStaticValues();

            Tum3cfg cfg = Tum3cfg.getGlbInstance();
            int tmp_db_count = cfg.getDbCount();

            CONST_MIN_OUT_BUFF = new int[tmp_db_count];
            CONST_OUT_BUFF_COUNT_MAX = new int[tmp_db_count];
            CONST_KEEPALIVE_INTERVAL_SEC = new int[tmp_db_count];

            for (int tmp_i = 0; tmp_i < tmp_db_count; tmp_i++) {
                String db_name = cfg.getDbName(tmp_i);
                CONST_MIN_OUT_BUFF[tmp_i] = 1024*Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_min_out_buff_kbytes, CONST_MIN_OUT_BUFF_default);
                CONST_OUT_BUFF_COUNT_MAX[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_out_buff_count, CONST_OUT_BUFF_COUNT_MAX_default);
                CONST_KEEPALIVE_INTERVAL_SEC[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_idle_check_alive_delay, CONST_KEEPALIVE_INTERVAL_SEC_default);

                Tum3Logger.DoLog(db_name, false, "DEBUG: " + LINK_PARS_LABEL + " CONST_MIN_OUT_BUFF=" + CONST_MIN_OUT_BUFF[tmp_i]);
                Tum3Logger.DoLog(db_name, false, "DEBUG: " + LINK_PARS_LABEL + " CONST_OUT_BUFF_COUNT_MAX=" + CONST_OUT_BUFF_COUNT_MAX[tmp_i]);
                Tum3Logger.DoLog(db_name, false, "DEBUG: " + LINK_PARS_LABEL + " CONST_KEEPALIVE_INTERVAL_SEC=" + CONST_KEEPALIVE_INTERVAL_SEC[tmp_i]);
            }
        }
    }

    public SrvLinkBase(int _db_idx, TunedSrvLinkPars _tuned_pars, SrvLinkOwner thisOwner) {

        db_index = _db_idx; // YYY
        tuned_pars = _tuned_pars; // YYY
        Owner = thisOwner;
        SupportOOB = thisOwner.SupportOOB();
        synchronized(OutBuffEmptyLock) {
            out_buffs_empty = new OutgoingBuff[getOutBuffCountMax()+2];
            out_buffs_empty_fill = 0;
        }
        synchronized(OutBuffFullLock) {
            out_buffs_full = new OutgoingBuff[getOutBuffCountMax()+2];
            out_buffs_full_fill = 0;
        }
        ConnectionStartedAt = System.currentTimeMillis();

    }

    protected class RecycledBuffContext {

        public OutgoingBuff out_buff_for_send = null;

    }

    protected abstract String getLogPrefixName();
 
    protected abstract void ExecuteReq(byte thrd_ctx, byte req_code, byte[] req_body, int req_trailing_len) throws Exception;

    protected abstract boolean getLoginFailedState();

    protected abstract long getLoginFailedAt();

    protected abstract boolean getWasAuthorized();

    private int getKeepaliveTimeoutVal() {

        return tuned_pars.CONST_KEEPALIVE_INTERVAL_SEC[db_index]; // YYY

    }

    private int getOutBuffCountMax() {

        return tuned_pars.CONST_OUT_BUFF_COUNT_MAX[db_index];

    }

    protected abstract boolean NoPauseOut();

    private OutgoingBuff newOutgoingBuff() {

        return new OutgoingBuff(tuned_pars.CONST_MIN_OUT_BUFF[db_index]); // YYY

    }



    public void SendOOBToServer(String buf) throws Exception {
        try {
            SendOOBToServerInternal(buf);
        } catch (Exception e) {
            Tum3Logger.DoLog(getLogPrefixName(), false, "FATAL: Closing in SendOOBToServer because: " + Tum3Util.getStackTrace(e));
            ShutdownSrvLink("Exception in SrvLink.SendOOBToServer: " + Tum3Util.getStackTrace(e));
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
    }

    public void SendToServer(byte thrd_ctx, ByteBuffer buf) throws Exception {
        try {
            SendToServerInternal(thrd_ctx, buf);
        } catch (Exception e) {
            Tum3Logger.DoLog(getLogPrefixName(), false, "FATAL: Closing in SendToServer because: " + Tum3Util.getStackTrace(e));
            ShutdownSrvLink("Exception in SrvLink.SendToServer: " + Tum3Util.getStackTrace(e));
            throw new Exception("[aq2j] FATAL: Closing in SendToServer because: " + e);
        }
    }


    private int Bytes4int(byte b0, byte b1, byte b2, byte b3) {
        return ((int)b0 & 0xFF) + (((int)b1 & 0xFF) << 8) + (((int)b2 & 0xFF) << 16) + (((int)b3 & 0xFF) << 24);
    }


    protected boolean PreverifyReq(byte req_code, int req_trailing_len) {
        // XXX TODO!!! Actually implement some checks.
        return true;
    }


    private void SendToServerInternal(byte thrd_ctx, ByteBuffer buf) throws Exception {
        // This function may block until either all count is written or exception is raised.
        int tmp_rem_count;
        if (getLoginFailedState()) return;
        while ((buf.remaining() > 0) || ((curr_stage == STAGE_TRAILING_BODY) && (0 == curr_remaining_bytes))) {
            if (getLoginFailedState()) return;
            tmp_rem_count = buf.remaining(); //count;
            if (tmp_rem_count > curr_remaining_bytes) tmp_rem_count = curr_remaining_bytes;
            if (curr_stage == STAGE_SIGN_4BYTES) {
                //System.arraycopy(buf, tmp_pos, req_header, (4-curr_remaining_bytes), tmp_rem_count);
                //tmp_pos += tmp_rem_count;
                buf.get(req_header, (4-curr_remaining_bytes), tmp_rem_count);
                curr_remaining_bytes -= tmp_rem_count;
                if (curr_remaining_bytes == 0) {
                    if ((TumProtoConsts.REQUEST_SIGN1 == req_header[3]) && (TumProtoConsts.REQUEST_SIGN2 == req_header[2]) && (TumProtoConsts.REQUEST_SIGN3 == req_header[1])) {
                        //System.out.println("[aq2j] DEBUG: signature OK.");
                        curr_req_code = req_header[0];
                        curr_stage = STAGE_TRAILING_LEN;
                        curr_remaining_bytes = 4;
                    } else {
                        Tum3Logger.DoLog(getLogPrefixName(), true, "WARNING: got invalid req signature in SendToServer(): " + Integer.toHexString(req_header[0] & 0xFF) + " " +  Integer.toHexString(req_header[1] & 0xFF) + " " +  Integer.toHexString(req_header[2] & 0xFF) + " " +  Integer.toHexString(req_header[3] & 0xFF) + " from " + DebugTitle());
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
                        Tum3Logger.DoLog(getLogPrefixName(), true, "WARNING: invalid trailing len in SendToServer(): " + curr_req_trailing_len + ";" + " Session: " + DebugTitle());
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
                        Tum3Logger.DoLog(getLogPrefixName(), true, "WARNING: got invalid req signature in SendToServer(): " + Integer.toHexString(req_header[0] & 0xFF) + " " +  Integer.toHexString(req_header[1] & 0xFF) + " " +  Integer.toHexString(req_header[2] & 0xFF) + " " +  Integer.toHexString(req_header[3] & 0xFF) + "; Session: " + DebugTitle());
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
                Tum3Logger.DoLog(getLogPrefixName(), true, "Internal error: invalid curr_stage in SendToServer()" + "; Session: " + DebugTitle());
                throw new Exception("invalid curr_stage in SendToServer()");
            }
        }

    }

    public boolean ReadFromServer2(byte thrd_ctx, ClientWriter outbound, boolean hurry) throws Exception {
        // Returns true = "nothing left to do".
        try {
            return ReadFromServerInternal(thrd_ctx, outbound, hurry);
        } catch (Exception e) {
            Tum3Logger.DoLog(getLogPrefixName(), false, "FATAL: Closing in ReadFromServer because: " + Tum3Util.getStackTrace(e));
            ShutdownSrvLink("Exception in SrvLink.ReadFromServer2: " + Tum3Util.getStackTrace(e));
            throw new Exception("[aq2j] FATAL: Closing in ReadFromServer because: " + e);
        }
    }

    protected boolean NeedToRequestKeepalive(long sys_millis, boolean may_disconn) throws Exception {

        if (getLoginFailedState()) {
            if (may_disconn) {
                if ((sys_millis - getLoginFailedAt()) > CONST_LOGIN_FAIL_PENDING_SEC*1000)
                    throw new Exception("Incorrect authorization provided, aborting session (timeout).");
                if (OutBuffsEmpty())
                    throw new Exception("Incorrect authorization provided, aborting session (close).");
            }
            return false;
        }
        if (!getWasAuthorized()) if ((sys_millis - ConnectionStartedAt) > CONST_LOGIN_TIMEOUT_SEC*1000) {
            if (may_disconn) throw new Exception("Timeout waiting for login completion, aborting session.");
            return false;
        }
        //System.out.println("[aq2j] DEBUG: NeedToRequestKeepalive() sent=" + keepalive_sent + " elap=" + (sys_millis - last_client_activity_time) + " max=" + (2*CONST_KEEPALIVE_INTERVAL_SEC[db_index]*1000));
        if ((sys_millis - last_client_activity_time) > 2 * getKeepaliveTimeoutVal() *1000) {
            if (may_disconn) throw new Exception("Remote agent does not reply to keepalive request, aborting session.");
            return false;
        }

        return ((sys_millis - last_client_activity_time) > getKeepaliveTimeoutVal() * 1000);
    }

    private boolean OutBuffsEmpty() {

        synchronized(OutBuffFullLock) {
            return (0 == out_buffs_full_fill);
        }

    }

    private boolean ReadFromServerInternal(byte thrd_ctx, ClientWriter outbound, boolean hurry) throws Exception {
        // Reminder. This is only called from the main thread (THRD_INTERNAL).
        //   However, OutBuffFullLock and OutBuffEmptyLock still should be protected
        //    by synchronized() because GetBuff() might be called from another thread due to aq3h.
        // Reminder: This is called by data consumer (sender) only when it is guaranteed ready to consume some data.
        // Reminder: due to elimination of multithreaded worker, blocking is never performed.
        // Returns true = "nothing left to do".

        int tmp_filled_count = 0;
        boolean tmp_need_req_keepalive = false;

        //System.out.println("[aq2j] ReadFromServerInternal(): need_req_keepalive=" + need_req_keepalive);
        if (!hurry && (!keepalive_sent_inline || (!keepalive_sent_oob && SupportOOB))) {
            tmp_need_req_keepalive = NeedToRequestKeepalive(System.currentTimeMillis(), false);
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
            return true;
        }

        if (!outbound.isOpen()) {
            return true;
        }

        tmp_filled_count = outbound.AcceptFrom(out_buff_now_sending); // out_buff_now_sending.SendTo(outbound);
        if (0 == tmp_filled_count) throw new Exception("Internal error: outbound.AcceptFrom() did nothing");

        if (out_buff_now_sending.SentAll()) {
            //System.out.println("[aq2j] DEBUG: Sent completely buff size=" + out_buff_now_sending.SentCount());
            out_buff_now_sending.CancelData();
            boolean tmp_need_traces_continue = false;
            boolean tmp_out_buff_was_recycled = false;
            int tmp_dbg_hanging_out_trace_bytes = -1;
            int tmp_dbg_hanging_out_trace_number = -1;
            //{ synchronized (PendingTraceList)
                int tmp_size = out_buff_now_sending.GetTraceSize();
                int tmp_number = out_buff_now_sending.GetTraceNumber();
                hanging_out_trace_bytes -= tmp_size;
                hanging_out_trace_number -= tmp_number;
                tmp_dbg_hanging_out_trace_bytes = hanging_out_trace_bytes;
                tmp_dbg_hanging_out_trace_number = hanging_out_trace_number;
                if ((tmp_size > 0) || (tmp_number > 0) || NoPauseOut()) tmp_need_traces_continue = true; // Reminder. This is to just reconsider pushing more data, but it will not necessarily happen yet as more specific checks will be performed.
            //}
            if (tmp_need_req_keepalive && outbound.isOpen() && !keepalive_sent_inline) {
                tmp_need_traces_continue = false;
                ctxRecycledReader.out_buff_for_send = out_buff_now_sending;
                tmp_out_buff_was_recycled = DoSendKeepaliveInline(thrd_ctx, ctxRecycledReader);
            }
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
                    if (out_buffs_count <= getOutBuffCountMax()) {
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

        return ((out_buff_now_sending == null) && (out_buffs_full_fill <= 0));
    }

    // Reminder: result 'false' also means that there is no job to continue.
    protected boolean GetTracesContinue(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        return false;

    }

    private String MakeOOBKeepAliveReq() {
        keepalive_code_sent = keepalive_code_next;
        keepalive_code_next++;
        if ((keepalive_code_next) >= 10000) keepalive_code_next = 0;
        keepalive_sent_oob = true;
        //keepalive_sent_time = System.currentTimeMillis();
        return IntToStr4(keepalive_code_sent); // "REQU0000";
    }

    private boolean DoSendKeepaliveInline(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
        //System.out.println("[aq2j] DEBUG: DoSendKeepaliveInline()");
        if (DoSimpleReq(thrd_ctx, TumProtoConsts.REQUEST_TYPE_ANYBODYTHERE, ctx)) {
            keepalive_sent_inline = true;
            //keepalive_sent_time = System.currentTimeMillis();
            //System.out.println("[aq2j] DEBUG: DoSendKeepaliveInline() success.");
            return true;
        }
        return false;
    }

    protected boolean DoSimpleReq(byte thrd_ctx, byte the_code, RecycledBuffContext ctx) throws Exception {
        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx, false);
        if (null == tmpBuff) return false;
        tmpBuff.InitSrvReply(the_code, 0, 0);
        PutBuff(thrd_ctx, tmpBuff, ctx);
        return true;
    }

    protected void PutBuff(byte thrd_ctx, OutgoingBuff buff, RecycledBuffContext ctx) throws Exception {
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

    public String CallerNetAddr() {

        return Owner.get_transp_title() + "*" + Owner.get_transp_caller(); // YYY

    }

    public String get_authorized_username() {

        return "";

    }

    protected void ShutdownSrvLink(String reason) {

        Owner.ShutdownSrvLink(reason);

    }

    protected void TrySendContinuationReq(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
    }

    public String DebugTitle() {

        String tmp_st = Owner.get_transp_user();
        if (tmp_st.isEmpty() && !get_authorized_username().isEmpty()) tmp_st = get_authorized_username(); // YYY
        if (!tmp_st.isEmpty()) tmp_st = tmp_st + "@";
        tmp_st = tmp_st + CallerNetAddr();
        return tmp_st;

    }

    protected String get_transp_user() {

        return Owner.get_transp_user();

    }

    protected void WakeupMain() {

        Owner.WakeupMain();

    }

    private String IntToStr4(int i) {
        StringBuffer tmp_str = new StringBuffer("" + i);
        while (tmp_str.length() < 4) tmp_str.insert(0, '0');
        tmp_str.insert(0, "REQU");
        return tmp_str.toString();
    }

    protected OutgoingBuff GetBuff(byte thrd_ctx, RecycledBuffContext ctx, boolean may_block) throws Exception {
        return GetBuff(thrd_ctx, ctx, may_block, false);
    }

    protected OutgoingBuff GetBuff(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
        return GetBuff(thrd_ctx, ctx, true, false);
    }

    protected OutgoingBuff GetBuff(byte thrd_ctx, RecycledBuffContext ctx, boolean may_block, boolean try_harder) throws Exception {
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
                while ((tmp_buff == null) && !CancellingLink && ((System.currentTimeMillis() - tmp_wait_started_at) < getKeepaliveTimeoutVal() * 1000)) {
                    synchronized(OutBuffEmptyLock) {
                        if (out_buffs_empty_fill > 0) {
                            tmp_buff = out_buffs_empty[out_buffs_empty_fill - 1];
                            out_buffs_empty_fill--;
                        } else if ((out_buffs_count < getOutBuffCountMax()) || (try_harder && ((out_buffs_count < (getOutBuffCountMax()+2))))) {
                            tmp_buff = newOutgoingBuff(); // XXX TODO!!! Not very good - allocating memory while holding a lock.
                            out_buffs_count++;
                        } 
                    }
                    if (tmp_buff == null) {
                        if (may_block) {
                            tmp_wait_started = true;
                            //  try {
                            //    OutBuffEmptyLock.wait(CONST_OUT_BUFF_WAIT_WAKEUP_SEC); // Reminder! In single-thread arrangement simply waiting on OutBuffEmptyLock is not very usefull because there might be no other thread to wait for!
                            //  } catch(Exception e) { }
                            //if (tmp_ready) Tum3Logger.DoLog(getLogPrefixName(), true, "Unexpected in GetBuff: tmp_ready but no free buffers." + " Session: " + DebugTitle());
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
                        if ((out_buffs_count < getOutBuffCountMax()) || (try_harder && ((out_buffs_count < (getOutBuffCountMax()+2))))) {
                            tmp_buff = newOutgoingBuff(); // XXX TODO. Better avoid new in synchronized.
                            out_buffs_count++;
                        }
                    }
                }

            if ((tmp_buff == null) && may_block) Tum3Logger.DoLog(getLogPrefixName(), true, "DEBUG: " + DebugTitle() + " GetBuff() timeout. Consider increasing conn alive check delay and/or max output buff count. Session: " + DebugTitle() + "; " + Tum3Util.getStackTraceAuto());
            //if (tmp_wait_started) System.out.print("[" + my_dbg_serial + ": GetBuff done " + (System.currentTimeMillis() - tmp_wait_started_at) + "]");
            //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> GetBuff(" + dbg_text + "): found out buff, is_null=" + (tmp_buff == null));
        }
        //ctx.out_buff_now_filling = tmp_buff;
        //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> GetBuff(" + dbg_text + "): out_buff_now_filling := tmp_buff");
        //System.out.print("-");
        return tmp_buff;
    }

    protected void RefuseBuff(byte thrd_ctx, RecycledBuffContext ctx, OutgoingBuff theBuff) {
        // Note: might eventually be called from 2 threads, so need to respect thread context.

        //ctx.out_buff_now_filling = null;

        if (null != ctx) {
            ctx.out_buff_for_send = theBuff;
        } else {
            synchronized(OutBuffEmptyLock) {
                if (out_buffs_count <= getOutBuffCountMax()) {
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

    protected boolean CancelLinkIntrnl() {

        OutgoingBuff[] tmp_buffs = null;
        synchronized(OutBuffFullLock) { 
            if (CancellingLink) return true;
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

        return false;

    }

    public void CancelLink() {

        CancelLinkIntrnl();

    }

    private void UpdateLastClientActivityTime() {
        last_client_activity_time = System.currentTimeMillis();
        //if (keepalive_sent_inline) { System.out.println(); System.out.println("[keepalive_sent_inline:=false]"); } // debug only!!!
    }

    public void DoLink() throws Exception {

        UpdateLastClientActivityTime();
        ConnectionStartedAt = System.currentTimeMillis();

    }

    public void ClientReaderTick(byte thrd_ctx, ClientWriter outbound) throws Exception {
        // This function is guaranteed to be called by the main thread only.

        if (NeedToSendTalkMsg() && outbound.isOpen()) TrySendTalkMessages(thrd_ctx); // Moved here from ReadFromServerInternal()

        if (TalkMsgQueueOverflow) {
            TalkMsgQueueOverflow = false;
            _NewMessageBoxCompat(thrd_ctx, "Internal error: TalkMsgQueue overflow (CONST_MAX_TALK_OUT_QUEUE needs to be increased) in session " + DebugTitle(), true);
            //Tum3Logger.DoLog(db_name, true, "Internal error: TalkMsgQueue overflow (CONST_MAX_TALK_OUT_QUEUE needs to be increased) in session " + DebugTitle());
        }

        NeedToRequestKeepalive(System.currentTimeMillis(), true);
        //System.out.print("[aq2j] ClientReaderTick(): tmp_need_req=" + tmp_need_req + ", need_req_keepalive=" + need_req_keepalive + ", keepalive_sent=" + keepalive_sent);
    }

    protected void _NewMessageBoxCompat(byte thrd_ctx, String the_text, boolean _with_logger) throws Exception {

        Tum3Logger.DoLog(getLogPrefixName(), true, the_text);

    }

    private boolean NeedToSendTalkMsg() {
        if (null == TalkMsgQueue) return false; // YYY
        else synchronized(TalkMsgQueue) { return (TalkMsgQueueFill > 0); }
    }

    protected boolean isCancellingLink() {

        return CancellingLink;

    }

    protected String GetPasString(ByteBuffer bb, int max_len) throws Exception {

        int tmp_str_len = bb.get(), tmp_remain = max_len - tmp_str_len;
        if (tmp_str_len > max_len) throw new Exception("Overflow in GetPasString(): " + max_len + " capacity and " + tmp_str_len + " length");
        char[] tmp_chars = new char[tmp_str_len];
        for (int tmp_i=0; tmp_i < tmp_str_len; tmp_i++) tmp_chars[tmp_i] = (char)bb.get();
        for (int tmp_i=0; tmp_i < tmp_remain; tmp_i++) bb.get();
        return new String(tmp_chars);

    }

    protected String Str255(String s) {
        if (s.length() > 254) s = s.substring(0, 251) + "...";
        return s;
    }

    protected void SyncHandleAsyncEvent(byte thrd_ctx, int _ev_type, GeneralDbDistribEvent _ev) throws Exception {
    }

    private void TrySendTalkMessages(byte thrd_ctx) throws Exception {
        if (TrySendTalkMessages_int(thrd_ctx)) {
            GetTracesContinue(thrd_ctx, null);
        }
    }


    private boolean TrySendTalkMessages_int(byte thrd_ctx) throws Exception {

        tmp_need_resume = false;
        int tmp_msg_count = 0;
        if (null == TalkMsgQueue) return tmp_need_resume; // YYY
        synchronized(TalkMsgQueue) { tmp_msg_count = TalkMsgQueueFill; }
        do {
            if (tmp_msg_count <= 0) return tmp_need_resume;
            GeneralDbDistribEvent tmpEv = TalkMsgQueue[0];
            SyncHandleAsyncEvent(thrd_ctx, tmpEv.get_type(), tmpEv); // YYY
            synchronized(TalkMsgQueue) {
                for (int tmp_i=1; tmp_i<TalkMsgQueueFill; tmp_i++) TalkMsgQueue[tmp_i-1] = TalkMsgQueue[tmp_i];
                TalkMsgQueue[TalkMsgQueueFill-1] = null;
                TalkMsgQueueFill--;
                tmp_msg_count = TalkMsgQueueFill;
            }
        } while (0 != tmp_msg_count);
        return tmp_need_resume;

    }

    public void SetFlag() {
    }
}
