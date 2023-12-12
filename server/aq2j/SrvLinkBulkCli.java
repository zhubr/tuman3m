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

import org.json.*;

import aq2db.*;
import aq2net.*;


public class SrvLinkBulkCli extends SrvLinkIntercon implements UplinkBulkMgr.UplinkOperations {


    private String username, password;
    private ArrayList<TempUGCReqHolder> ExecutingList = new ArrayList<TempUGCReqHolder>();
    private long last_checked = System.currentTimeMillis();

    private final static TunedSrvLinkParsBulkCli tuned_pars_bulkcli = new TunedSrvLinkParsBulkCli(); // YYY


    private static class TunedSrvLinkParsBulkCli extends SrvLinkBase.TunedSrvLinkPars {

        public void AssignStaticValues() { // YYY

            LINK_PARS_LABEL = "bulk cli";

            TUM3_CFG_idle_check_alive_delay = "idle_check_alive_bulk_c"; // YYY
            TUM3_CFG_max_out_buff_count = "max_out_buff_count_bulk_c"; // YYY
            TUM3_CFG_min_out_buff_kbytes = "min_out_buff_kbytes_bulk_c"; // YYY

            CONST_OUT_BUFF_COUNT_MAX_default = 6;
            CONST_KEEPALIVE_INTERVAL_SEC_default = 20;
            CONST_MIN_OUT_BUFF_default = 100;  // kbytes.
        }
    }

    public SrvLinkBulkCli(int _db_idx, SrvLinkOwner thisOwner, String _username, String _password) {
        super(_db_idx, tuned_pars_bulkcli, thisOwner);
        username = _username;
        password = _password;
    }

    protected String InterconLabel() { return "bulk"; }

    public String ExecuteCmdLine(String _cmdline) throws Exception {

        //AddGeneralEvent(null, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_UGC_REQUEST, holder), null, null);
        return "Sorry, not implented";

    }

    @Override
    public void ClientReaderTick(byte thrd_ctx, ClientWriter outbound) throws Exception {
        // This function is guaranteed to be called by the main thread only.

        long tmp_now_millis = System.currentTimeMillis();
        //if ((tmp_now_millis - last_checked) > 1000) {
        //    for (int i = ExecutingList.size() - 1; i > 0; i--) if ((tmp_now_millis - ExecutingList.get(i).start_millis) > UplinkManager.CONST_UGC_FWD_TIMEOUT_SEC) {
        //        TempUGCReqHolder tmp_holder2 = ExecutingList.get(i);
        //        ExecutingList.remove(i);
        //        tmp_holder2.Refuse("Uplink request execution timed out");
        //    }
        //}

        super.ClientReaderTick(thrd_ctx, outbound);
    }

    protected void UplinkConnExtra(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {

                    //dbLink.setOtherServerConnected(true); // YYY
                    //ForceSendUserList(thrd_ctx, ctx); // YYY
                    UplinkBulkMgr.setUplink(getDbIndex(), this); // YYY

    }

    protected void onJSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Got JSON function <" + jo.get(JSON_NAME_function) + ">");
        String tmp_func = jo.getString(JSON_NAME_function);

        if (JSON_FUNC_welcome.equals(tmp_func)) {
            JsonWelcomeHandler(thrd_ctx, ctx, jo, username);
            return;
        }
/*
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
*/
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

        //dbLink.setOtherServerConnected(false); // YYY
        //flushWritingShot();

        if (dbLink != null) {
            //Tum3Broadcaster.release(dbLink, this);
            dbLink.releaseDbClient();
            dbLink = null;
        }

        UplinkBulkMgr.resetUplink(getDbIndex(), this);

        return false;
    }

    public void AddGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, String thisReceiverName, String thisEchoName) {
    }

    protected void onJSON_Intrnl(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        onJSON(thrd_ctx, ctx, jo);

        //if (WasAuthorized) {
        //    if (jo.has(JSON_NAME_server_info)) dbLink.setOtherServerInfo(jo.getString(JSON_NAME_server_info)); // YYY
        //    if (jo.has(JSON_NAME_userlist)) BroadcastUsrList(jo.getString(JSON_NAME_userlist));
        //}
    }

    public void DoLink() throws Exception {

        super.DoLink();

        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Sending hello...");
        SendHelloMsg(SrvLink.THRD_INTERNAL, null, username, password);
        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] sent hello.");

    }
}
