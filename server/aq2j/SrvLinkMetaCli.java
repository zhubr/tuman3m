/*
 * Copyright 2022-2024 Nikolai Zhubr <zhubr@rambler.ru>
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


public class SrvLinkMetaCli extends SrvLinkMeta implements UplinkManager.UplinkOperations {


    private String username, password;
    private ArrayList<TempUGCReqHolder> ExecutingList = new ArrayList<TempUGCReqHolder>();
    private long last_checked = System.currentTimeMillis();


    public SrvLinkMetaCli(int _db_idx, SrvLinkOwner thisOwner, String _username, String _password) {
        super(_db_idx, thisOwner);
        username = _username;
        password = _password;
    }

    public String ExecuteUgc(TempUGCReqHolder holder) throws Exception {

        AddGeneralEvent(null, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_UGC_REQUEST, holder), null, null);
        return null; // "Sorry, not implented";

    }

    protected void SyncHandleAsyncEvent(byte thrd_ctx, int _ev_type, GeneralDbDistribEvent _ev) throws Exception {

        if (_ev_type == _ev.DB_EV_UGC_REQUEST) {
            TempUGCReqHolder tmp_holder = TempUGCReqHolder.class.cast(_ev.get_handler());
            UgcReplyHandlerExt the_link = tmp_holder.sender_link;
            // holder.req_id, holder.shot_num, the_link.get_authorized_username(), tmp_bb, the_link
            JSONObject jo = new JSONObject();
            jo.put(JSON_NAME_function, JSON_FUNC_ugcfwd);
            jo.put(JSON_NAME_glb_fwd_id, tmp_holder.glb_fwd_id);
            jo.put(JSON_NAME_username, the_link.get_authorized_username());
            jo.put(JSON_NAME_req_id, tmp_holder.req_id);
            jo.put(JSON_NAME_shot, tmp_holder.shot_num);
            if (null != tmp_holder.upd_arr)
                jo.put(JSON_NAME_body, Tum3Util.BytesToStringRaw(tmp_holder.upd_arr));
            Send_JSON(thrd_ctx, null, jo);
            while (ExecutingList.size() >= 2*UplinkManager.CONST_UGC_MAX_QUEUE) {
                TempUGCReqHolder tmp_holder2 = ExecutingList.get(0);
                ExecutingList.remove(0);
                tmp_holder2.Refuse("Uplink execution queue got too big");
            }
            ExecutingList.add(tmp_holder);
        } else {
            super.SyncHandleAsyncEvent(thrd_ctx, _ev_type, _ev);
        }
    }

    @Override
    public void ClientReaderTick(byte thrd_ctx, ClientWriter outbound) throws Exception {
        // This function is guaranteed to be called by the main thread only.

        long tmp_now_millis = System.currentTimeMillis();
        if ((tmp_now_millis - last_checked) > 1000) {
            for (int i = ExecutingList.size() - 1; i > 0; i--) if ((tmp_now_millis - ExecutingList.get(i).start_millis) > UplinkManager.CONST_UGC_FWD_TIMEOUT_SEC) {
                TempUGCReqHolder tmp_holder2 = ExecutingList.get(i);
                ExecutingList.remove(i);
                tmp_holder2.Refuse("Uplink request execution timed out");
            }
        }

        super.ClientReaderTick(thrd_ctx, outbound);
    }

    protected void UplinkConnExtra(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

        dbLink.setOtherServerConnected(true); // YYY
        ForceSendUserList(thrd_ctx, ctx); // YYY
        UplinkManager.setUplink(getDbIndex(), this); // YYY

    }

    protected void onJSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Got JSON function <" + jo.get(JSON_NAME_function) + ">");
        String tmp_func = jo.getString(JSON_NAME_function);

        if (JSON_FUNC_welcome.equals(tmp_func)) {
            JsonWelcomeHandler(thrd_ctx, ctx, jo, username);
            return;
        }
        if (JSON_FUNC_ugcfwd.equals(tmp_func) && WasAuthorized) {

            int tmp_glb_fwd_id = jo.getInt(JSON_NAME_glb_fwd_id);
            int tmp_req_id = jo.getInt(JSON_NAME_req_id);
            String tmp_shot = jo.getString(JSON_NAME_shot);
            String tmp_err_msg = jo.getString(JSON_NAME_err_msg);
            byte[] tmp_arr = null;
            if (jo.has(JSON_NAME_body)) {
                String tmp_body = jo.getString(JSON_NAME_body);
                tmp_arr = Tum3Util.StringToBytesRaw(tmp_body);
            }

            //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] searching tmp_glb_fwd_id=" + tmp_glb_fwd_id);
            int found_index = -1;
            for (int i = 0; i < ExecutingList.size(); i++) if (ExecutingList.get(i).glb_fwd_id == tmp_glb_fwd_id) {
                found_index = i;
                break;
            }
            //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] matching found_index=" + found_index);
            if (found_index >= 0) {
                TempUGCReqHolder tmp_holder = ExecutingList.get(found_index);
                //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] found matching ugc holder: i=" + found_index + ", req_id=<" + tmp_holder.req_id + ">");
                ExecutingList.remove(found_index);
                tmp_holder.sender_link.PostUgcReply(tmp_req_id, tmp_shot, tmp_err_msg, tmp_arr);
            }
            UplinkManager.DoneUgc(getDbIndex(), tmp_glb_fwd_id);
            return;
        }
        if (JSON_FUNC_ugcntfy.equals(tmp_func) && WasAuthorized) {

            int tmp_req_id = jo.getInt(JSON_NAME_req_id);
            String tmp_shot = "";
            if (jo.has(JSON_NAME_shot)) tmp_shot = jo.getString(JSON_NAME_shot);
            //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] JSON_FUNC_ugcntfy: req_id=" + tmp_req_id + " shot=<" + tmp_shot + ">");

            if (-2 == tmp_req_id)
                Tum3Broadcaster.DistributeGeneralEvent(dbLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_UGC_SHOT_UPD, tmp_shot), this);
            if (-4 == tmp_req_id)
                Tum3Broadcaster.DistributeGeneralEvent(dbLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_UGC_LIST_UPD, -4), this);

            return;
        }
        super.onJSON(thrd_ctx, ctx, jo);
    }

    @Override
    protected boolean CancelLinkIntrnl() {

        if (super.CancelLinkIntrnl()) return true;
        UplinkManager.resetUplink(getDbIndex(), this); // YYY

        return false;
    }

    public void DoLink() throws Exception {

        super.DoLink();

        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Sending hello...");
        SendHelloMsg(SrvLink.THRD_INTERNAL, null, username, password); // YYY
        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] sent hello.");

    }
}
