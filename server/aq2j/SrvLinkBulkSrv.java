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


public class SrvLinkBulkSrv extends SrvLinkIntercon {


    private class ReplyGenerator implements UgcReplyHandler {

        private byte thrd_ctx;
        private RecycledBuffContext ctx;
        private int glb_fwd_id;

        public ReplyGenerator(byte _thrd_ctx, RecycledBuffContext _ctx, int _glb_fwd_id) {
            _thrd_ctx = thrd_ctx;
            _ctx = ctx;
            glb_fwd_id = _glb_fwd_id;
        }

        public void GenerateUgcReply(byte thrd_ctx, int _req_id, String _shot_name, String _err_msg, byte[] data) throws Exception {

            JSONObject jo = new JSONObject();
            //jo.put(JSON_NAME_function, JSON_FUNC_ugcfwd);
            //jo.put(JSON_NAME_glb_fwd_id, glb_fwd_id);
            //jo.put(JSON_NAME_req_id, _req_id);
            //jo.put(JSON_NAME_shot, _shot_name);
            jo.put(JSON_NAME_err_msg, _err_msg);
            if (null != data)
                jo.put(JSON_NAME_body, Tum3Util.BytesToStringRaw(data));
            Send_JSON(thrd_ctx, ctx, jo);
            //Tum3Logger.DoLog(getLogPrefixName(), false, "Forwarded ugc result for glb_fwd_id=" + glb_fwd_id + " with err_msg=<" + _err_msg + ">");
        }

    }

    private static volatile SrvLinkIntercon curr_instance[] = new SrvLinkIntercon[Tum3cfg.getGlbInstance().getDbCount()]; // YYY

    private final static TunedSrvLinkParsBulkSrv tuned_pars_bulksrv = new TunedSrvLinkParsBulkSrv(); // YYY


    private static class TunedSrvLinkParsBulkSrv extends SrvLinkBase.TunedSrvLinkPars {

        public void AssignStaticValues() { // YYY

            LINK_PARS_LABEL = "bulk srv";

            TUM3_CFG_idle_check_alive_delay = "idle_check_alive_bulk_s"; // YYY
            TUM3_CFG_max_out_buff_count = "max_out_buff_count_bulk_s"; // YYY
            TUM3_CFG_min_out_buff_kbytes = "min_out_buff_kbytes_bulk_s"; // YYY

            CONST_OUT_BUFF_COUNT_MAX_default = 6;
            CONST_KEEPALIVE_INTERVAL_SEC_default = 20;
            CONST_MIN_OUT_BUFF_default = 100;  // kbytes.
        }
    }

    public SrvLinkBulkSrv(int _db_idx, SrvLinkOwner thisOwner) {
        super(_db_idx, tuned_pars_bulksrv, thisOwner);
    }

    protected String InterconLabel() { return "bulk"; }

    private final static synchronized boolean setCurrent_intrn(int _db_index, SrvLinkIntercon instance) {

        if (null == curr_instance[_db_index]) {
            curr_instance[_db_index] = instance;
            return true;
        }
        return false;

    }

    protected boolean setCurrent(int _db_index, SrvLinkIntercon instance) {

        return setCurrent_intrn(_db_index, instance);

    }

    private static synchronized void resetCurrent(int _db_index, SrvLinkBulkSrv instance) {

        if (instance == curr_instance[_db_index]) curr_instance[_db_index] = null;

    }

    protected void InitDbAccess() {

        if (null == dbLink) {
            dbLink = Tum3Db.getDbInstance(db_index);
            //Tum3Broadcaster.addclient(dbLink, this);
        }

    }

    protected boolean CancelLinkIntrnl() {

        if (super.CancelLinkIntrnl()) return true;

        //dbLink.setOtherServerConnected(false); // YYY
        //flushWritingShot();

        if (dbLink != null) {
            //Tum3Broadcaster.release(dbLink, this);
            dbLink.releaseDbClient();
            dbLink = null;
        }
        resetCurrent(getDbIndex(), this); // YYY

        return false;
    }

    protected void onJSON_Intrnl(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        onJSON(thrd_ctx, ctx, jo);

        //if (WasAuthorized) {
        //    if (jo.has(JSON_NAME_server_info)) dbLink.setOtherServerInfo(jo.getString(JSON_NAME_server_info)); // YYY
        //    if (jo.has(JSON_NAME_userlist)) BroadcastUsrList(jo.getString(JSON_NAME_userlist));
        //}
    }

    public void AddGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, String thisReceiverName, String thisEchoName) {
    }

    protected void onJSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Got JSON function <" + jo.get(JSON_NAME_function) + ">");
        String tmp_func = jo.getString(JSON_NAME_function);

        if (JSON_FUNC_hello.equals(tmp_func)) {
            FinishJsonLoginReq(thrd_ctx, ctx, jo);
            return;
        }
        //super.onJSON(thrd_ctx, ctx, jo);
    }

}
