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

import org.json.JSONObject;

import aq2db.*;
import aq2net.*;


public abstract class SrvLinkMeta extends SrvLinkBase implements TumProtoConsts, SrvLinkIntf {


    protected final static String JSON_NAME_function = "function";
    protected final static String JSON_NAME_db = "db";
    protected final static String JSON_NAME_username = "username";
    protected final static String JSON_NAME_password = "password";
    protected final static String JSON_NAME_body = "body";
    protected final static String JSON_NAME_receiver = "receiver";
    protected final static String JSON_NAME_userlist = "userlist";
    protected final static String JSON_NAME_req_id = "req_id";
    protected final static String JSON_NAME_shot = "shot";
    protected final static String JSON_NAME_glb_fwd_id = "glb_fwd_id";
    protected final static String JSON_NAME_err_msg = "err_msg";

    protected final static String JSON_FUNC_hello = "hello";
    protected final static String JSON_FUNC_welcome = "welcome";
    protected final static String JSON_FUNC_talk = "talk";
    protected final static String JSON_FUNC_users = "users";
    protected final static String JSON_FUNC_ugcfwd = "ugcfwd";
    protected final static String JSON_FUNC_ugcntfy = "ugcntfy";

    private static volatile int dbg_serial = 0;
    public int my_dbg_serial = 0;

    protected final static String TUM3_CFG_idle_check_alive_delay = "idle_check_alive_meta"; // YYY
    protected final static String TUM3_CFG_max_out_buff_count = "max_out_buff_count_meta"; // YYY
    private final static String TUM3_CFG_min_out_buff_kbytes = "min_out_buff_kbytes_meta"; // YYY

    private final static int CONST_OUT_BUFF_COUNT_MAX_default = 40; // YYY
    private final static int CONST_KEEPALIVE_INTERVAL_SEC_default = 20;

    private final static int CONST_MAX_TALK_OUT_QUEUE = 20;
    private final static int CONST_MAX_REQ_STRING_COUNT = 1000;

    private final static int CONST_MIN_OUT_BUFF_default = 10;  // kbytes. // YYY
    private static final int CONST_KEEPALIVE_INTERVAL_SEC[];
    private static final int CONST_OUT_BUFF_COUNT_MAX[];
    private static int CONST_MIN_OUT_BUFF[] = InitMinOutBuffConst();  // Should be per-db now.

    protected volatile boolean WasAuthorized = false;
    protected long LoginFailedAt;
    protected boolean LoginFailedState = false;
    protected volatile String AuthorizedLogin = "";
    protected Tum3Db dbLink = null;

    private volatile int FFeatureSelectWord = 0; // To be removed
    private int RCompatFlags = 0; // To be removed
    private volatile int RCompatVersion; // To be removed

    private int db_index;
    private String db_name;

    static {

        int tmp_db_count = Tum3cfg.getGlbInstance().getDbCount();
        Tum3cfg cfg = Tum3cfg.getGlbInstance();

        CONST_OUT_BUFF_COUNT_MAX = new int[tmp_db_count];
        CONST_KEEPALIVE_INTERVAL_SEC = new int[tmp_db_count];

        for (int tmp_i = 0; tmp_i < tmp_db_count; tmp_i++) {
            String db_name = cfg.getDbName(tmp_i);
            CONST_OUT_BUFF_COUNT_MAX[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_out_buff_count, CONST_OUT_BUFF_COUNT_MAX_default);
            CONST_KEEPALIVE_INTERVAL_SEC[tmp_i] = Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_idle_check_alive_delay, CONST_KEEPALIVE_INTERVAL_SEC_default);

            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_OUT_BUFF_COUNT_MAX=" + CONST_OUT_BUFF_COUNT_MAX[tmp_i]);
            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_KEEPALIVE_INTERVAL_SEC=" + CONST_KEEPALIVE_INTERVAL_SEC[tmp_i]);
        }
    }

    public SrvLinkMeta(int _db_idx, SrvLinkOwner thisOwner) {
        super(thisOwner);
        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        dbg_serial++;
        my_dbg_serial = dbg_serial;
        TalkMsgQueue = new GeneralDbDistribEvent[CONST_MAX_TALK_OUT_QUEUE];
    }

    protected int getDbIndex() {

        return db_index;

    }

    private final static int[] InitMinOutBuffConst() {

        int tmp_arr[] = new int[Tum3cfg.getGlbInstance().getDbCount()];
        Tum3cfg cfg = Tum3cfg.getGlbInstance();
        for (int tmp_i = 0; tmp_i < tmp_arr.length; tmp_i++) {
            tmp_arr[tmp_i] = 1024*Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_min_out_buff_kbytes, CONST_MIN_OUT_BUFF_default);
            Tum3Logger.DoLog(cfg.getDbName(tmp_i), false, "DEBUG: CONST_MIN_OUT_BUFF=" + tmp_arr[tmp_i]);
        }
        return tmp_arr;

    }

    protected OutgoingBuff newOutgoingBuff() {

        return new OutgoingBuff(CONST_MIN_OUT_BUFF[db_index]);

    }

    protected void InitDbAccess() {

        if (null == dbLink) {
            dbLink = Tum3Db.getDbInstance(db_index);
            Tum3Broadcaster.addclient(dbLink, this);
        }

    }

    public Tum3Db GetDb() {

        return dbLink;

    }

    protected boolean CancelLinkIntrnl() {

        if (super.CancelLinkIntrnl()) return true;

        Tum3Broadcaster.intercon_users(dbLink, this, null); // YYY

        //flushWritingShot();

        if (dbLink != null) {
            Tum3Broadcaster.release(dbLink, this);
            dbLink.releaseDbClient();
            dbLink = null;
        }

        return false;

    }

    private void TalkMessageServerBroadcaster(String thisMsg, String thisReceiverName, String thisEchoName) {

        Tum3Broadcaster.DistributeGeneralEvent(dbLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TALK, thisMsg), null, thisReceiverName, thisEchoName);

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

    protected void SyncHandleAsyncEvent(byte thrd_ctx, int _ev_type, GeneralDbDistribEvent _ev) throws Exception {

        if (_ev_type == _ev.DB_EV_TALK) {
            JSONObject jo = new JSONObject();
            jo.put(JSON_NAME_function, JSON_FUNC_talk);
            jo.put(JSON_NAME_body, _ev.get_str());
            jo.put(JSON_NAME_receiver, _ev.get_str2());
            Send_JSON(thrd_ctx, null, jo);
        } else if (_ev_type == _ev.DB_EV_USERLIST) {
            JSONObject jo = new JSONObject();
            jo.put(JSON_NAME_function, JSON_FUNC_users);
            ByteBuffer tmp_bb2 = _ev.get_bb();
            jo.put(JSON_NAME_userlist, Tum3Util.BytesToStringRaw(tmp_bb2.array(), 0, tmp_bb2.position())); // YYY
            Send_JSON(thrd_ctx, null, jo);
            //System.out.println("[DEBUG] DB_EV_USERLIST: " + tmp_size);
        }
    }

    public void AddGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, String thisReceiverName, String thisEchoName) {

        // We are usually interested in our db-originating events or masterdb-originating events.
        if ((origin_db != null) && (dbLink == null)) return; // Just in case.
        if (origin_db != null) if ((origin_db != dbLink) && (origin_db != dbLink.GetMasterDb()) && (origin_db.GetMasterDb() != dbLink)) return;

        int tmp_ev_type = ev.get_type();

        if ((tmp_ev_type == ev.DB_EV_NEWSHOT) && (origin_db != dbLink)) return; // YYY Special case: never allow DB_EV_NEWSHOT between master/slave directly.

        if (!WasAuthorized) return; // depends on event type?

        if ((tmp_ev_type != ev.DB_EV_TALK) 
         && (tmp_ev_type != ev.DB_EV_USERLIST) 
         && (tmp_ev_type != ev.DB_EV_UGC_REQUEST) 
         && (tmp_ev_type != ev.DB_EV_UGC_LIST_UPD) 
         && (tmp_ev_type != ev.DB_EV_UGC_SHOT_UPD)) return;

        synchronized(TalkMsgQueue) {
            if (TalkMsgQueueFill < TalkMsgQueue.length) {
                TalkMsgQueue[TalkMsgQueueFill] = ev;
                TalkMsgQueueFill++;
            } else {
                // REMINDER!!! This typically is running in some foreign thread!
                TalkMsgQueueOverflow = true;
                Tum3Logger.DoLog(db_name, true, "IMPORTANT: TalkMsgQueue overflow.");
            }
        }
        WakeupMain();

    }

    protected void FillUserList(JSONObject jo) {

        ByteBuffer tmp_bb2 = Tum3Broadcaster.GetUserList(dbLink, false);
        String tmp_usrlst = "";
        if (null != tmp_bb2) tmp_usrlst = Tum3Util.BytesToStringRaw(tmp_bb2.array(), 0, tmp_bb2.position());
            jo.put(JSON_NAME_userlist, tmp_usrlst);
    }

    protected void ForceSendUserList(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        JSONObject jo2 = new JSONObject();
        jo2.put(JSON_NAME_function, JSON_FUNC_users);
        FillUserList(jo2); // YYY
        Send_JSON(thrd_ctx, ctx, jo2);

    }

    private void Process_JSON(byte thrd_ctx, RecycledBuffContext ctx, byte[] req_body, int req_trailing_len) throws Exception {

        String tmp_json_str = Tum3Util.BytesToStringRaw(req_body, 0, req_trailing_len);
        //JSONObject jo = new JSONObject(tmp_json_str);
        onJSON(thrd_ctx, ctx, new JSONObject(tmp_json_str));

    }

    protected void onJSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        if (JSON_FUNC_talk.equals(jo.getString(JSON_NAME_function))) {
            if (WasAuthorized) {
                String tmp_body = jo.getString(JSON_NAME_body);
                String tmp_receiver = jo.getString(JSON_NAME_receiver);
                //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Got intercon talk <" + tmp_body + ">");

                Tum3Broadcaster.DistributeGeneralEvent(dbLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TALK, tmp_body), this, tmp_receiver, null);
            }
            return;
        }
        if (JSON_FUNC_users.equals(jo.getString(JSON_NAME_function))) {
            if (WasAuthorized)
                BroadcastUsrList(jo.getString(JSON_NAME_userlist));
            return;
        }

    }

    protected void BroadcastUsrList(String _usrlst) {

        byte[] tmp_body = Tum3Util.StringToBytesRaw(_usrlst);
        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Got intercon userlist <" + _usrlst + ">");

        Tum3Broadcaster.intercon_users(dbLink, this, tmp_body);
    }

    protected void Send_JSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx);

        String tmpMsg = jo.toString();

        tmpBuff.InitSrvReply(REQUEST_TYPE_JSON, tmpMsg.length(), tmpMsg.length());
        tmpBuff.putString(tmpMsg);
        PutBuff(thrd_ctx, tmpBuff, ctx);

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

        if (REQUEST_TYPE_JSON == req_code) Process_JSON(thrd_ctx, null, req_body, req_trailing_len);
        else if (REQUEST_TYPE_ANYBODYTHERE == req_code) Process_PingHighlevel(thrd_ctx, null);
        else if (REQUEST_TYPE_KEEPCONNECTED == req_code) Process_PingReply();
        else {
            Tum3Logger.DoLog(db_name, true, "WARNING: unknown request, code=" + Integer.toHexString(req_code & 0xFF) + " len=" + req_trailing_len + ";" + " Session: " + DebugTitle());
        }
    }

    protected int getKeepaliveTimeoutVal() {

        return CONST_KEEPALIVE_INTERVAL_SEC[db_index];

    }

    protected int getOutBuffCountMax() {

        return CONST_OUT_BUFF_COUNT_MAX[db_index];

    }

    public void ClientReaderTick(byte thrd_ctx, ClientWriter outbound) throws Exception {
        // This function is guaranteed to be called by the main thread only.

        //if (null != currWritingShotHelper) currWritingShotHelper.tick();

        super.ClientReaderTick(thrd_ctx, outbound);
    }

    protected boolean NoPauseOut() {

        return true;

    }

    public byte[] GetBinaryUsername() {

        return null;

    }

    private String AuthorizedLoginPlus() {

        if (WasAuthorized)
            return AuthorizedLogin + "(intercon)." + db_name; // YYY
        else
            return "(intercon)." + db_name; // YYY

    }

}
