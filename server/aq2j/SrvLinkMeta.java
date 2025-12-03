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

import org.json.JSONObject;

import aq2db.*;
import aq2net.*;


public abstract class SrvLinkMeta extends SrvLinkIntercon {


    protected final static String JSON_NAME_receiver = "receiver";
    protected final static String JSON_NAME_userlist = "userlist";
    protected final static String JSON_NAME_req_id = "req_id";
    protected final static String JSON_NAME_shot = "shot";
    protected final static String JSON_NAME_glb_fwd_id = "glb_fwd_id";
    protected final static String JSON_NAME_server_info = "server_info";
    protected final static String JSON_NAME_sync_info = "sync_info";

    protected final static String JSON_FUNC_talk = "talk";
    protected final static String JSON_FUNC_users = "users";
    protected final static String JSON_FUNC_ugcfwd = "ugcfwd";
    protected final static String JSON_FUNC_ugcntfy = "ugcntfy";

    private final static int CONST_MAX_TALK_OUT_QUEUE = 20;
    private final static int CONST_MAX_REQ_STRING_COUNT = 1000;

    protected volatile boolean NeedPushServerInfo = true; // YYY

    private final static TunedSrvLinkParsMeta tuned_pars_meta = new TunedSrvLinkParsMeta(); // YYY


    private static class TunedSrvLinkParsMeta extends SrvLinkBase.TunedSrvLinkPars {

        public void AssignStaticValues() { // YYY

            LINK_PARS_LABEL = "meta";

            TUM3_CFG_idle_check_alive_delay = "idle_check_alive_meta"; // YYY
            TUM3_CFG_max_out_buff_count = "max_out_buff_count_meta"; // YYY
            TUM3_CFG_min_out_buff_kbytes = "min_out_buff_kbytes_meta"; // YYY

            CONST_OUT_BUFF_COUNT_MAX_default = 40;
            CONST_KEEPALIVE_INTERVAL_SEC_default = 20;
            CONST_MIN_OUT_BUFF_default = 10;  // kbytes.
        }
    }

    public SrvLinkMeta(int _db_idx, SrvLinkOwner thisOwner) {
        super(_db_idx, tuned_pars_meta, thisOwner);
        TalkMsgQueue = new GeneralDbDistribEvent[CONST_MAX_TALK_OUT_QUEUE];
    }

    protected String InterconLabel() { return "meta"; }

    protected void InitDbAccess() {

        if (null == dbLink) {
            dbLink = Tum3Db.getDbInstance(db_index);
            Tum3Broadcaster.addclient(dbLink, this);
        }

    }

    protected boolean CancelLinkIntrnl() {

        if (super.CancelLinkIntrnl()) return true;

        Tum3Broadcaster.intercon_users(dbLink, this, null);
        dbLink.setOtherServerConnected(false); // YYY

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

    protected void ExtendJsonLoginReply(JSONObject jo2) {

        dbLink.setOtherServerConnected(true); // YYY
        FillUserList(jo2); // YYY
        jo2.put(JSON_NAME_server_info, dbLink.getThisServerInfo()); // YYY

    }

    protected void ForceSendUserList(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        ForceSendVariousSrvInfo(thrd_ctx, ctx, true);

    }

    protected void ForceSendVariousSrvInfo(byte thrd_ctx, RecycledBuffContext ctx, boolean with_usrlist) throws Exception {

        JSONObject jo2 = new JSONObject();
        jo2.put(JSON_NAME_function, JSON_FUNC_users);
        if (with_usrlist) FillUserList(jo2); // YYY
        jo2.put(JSON_NAME_server_info, dbLink.getThisServerInfo()); // YYY
        if (dbLink.upbulk_enabled) jo2.put(JSON_NAME_sync_info, dbLink.getThisServerInfoSync()); // YYY
        Send_JSON(thrd_ctx, ctx, jo2);

    }

    protected void onJSON_Intrnl(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo, byte[] bin_att, int att_ofs, int att_len) throws Exception {

        onJSON(thrd_ctx, ctx, jo);

        if (WasAuthorized) {
            String tmp_sync_info = "", tmp_server_info = ""; // YYY
            boolean tmp_do = false;
            if (dbLink.downbulk_enabled && jo.has(JSON_NAME_sync_info)) { tmp_sync_info = jo.getString(JSON_NAME_sync_info); tmp_do = true; } // YYY
            if (jo.has(JSON_NAME_server_info)) { tmp_server_info = jo.getString(JSON_NAME_server_info); tmp_do = true; }
            //if (tmp_do) Tum3Logger.DoLog(getLogPrefixName(), false, "[DEBUG] setOtherServerAllInfo <<-- " + jo.getString(JSON_NAME_function) + " sync_info=" + jo.has(JSON_NAME_sync_info) + " server_info=" + jo.has(JSON_NAME_server_info));
            if (tmp_do) dbLink.setOtherServerAllInfo(tmp_server_info, tmp_sync_info, this); // dbLink.setOtherServerInfo(tmp_server_info); // YYY
            if (jo.has(JSON_NAME_userlist)) BroadcastUsrList(jo.getString(JSON_NAME_userlist));
        }
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

    }

    protected void BroadcastUsrList(String _usrlst) {

        byte[] tmp_body = Tum3Util.StringToBytesRaw(_usrlst);
        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Got intercon userlist <" + _usrlst + ">");

        Tum3Broadcaster.intercon_users(dbLink, this, tmp_body);
    }

    @Override
    public void SetFlag() {

        NeedPushServerInfo = true;

    }

    private void ConsiderPushServerInfo(byte thrd_ctx) throws Exception {

        if (NeedPushServerInfo && (null != dbLink)) {
            NeedPushServerInfo = false; // YYY
            ForceSendVariousSrvInfo(thrd_ctx, null, false); // YYY
        }
    }

    public void ClientReaderTick(byte thrd_ctx, ClientWriter outbound) throws Exception {
        // This function is guaranteed to be called by the main thread only.

        //if (null != currWritingShotHelper) currWritingShotHelper.tick();

        ConsiderPushServerInfo(thrd_ctx); // YYY

        super.ClientReaderTick(thrd_ctx, outbound);
    }

}
