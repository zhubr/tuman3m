/*
 * Copyright 2022-2024 Nikolai Zhubr <zhubr@mail.ru>
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


public class SrvLinkMetaSrv extends SrvLinkMeta {


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
            jo.put(JSON_NAME_function, JSON_FUNC_ugcfwd);
            jo.put(JSON_NAME_glb_fwd_id, glb_fwd_id);
            jo.put(JSON_NAME_req_id, _req_id);
            jo.put(JSON_NAME_shot, _shot_name);
            jo.put(JSON_NAME_err_msg, _err_msg);
            if (null != data)
                jo.put(JSON_NAME_body, Tum3Util.BytesToStringRaw(data));
            Send_JSON(thrd_ctx, ctx, jo);
            //Tum3Logger.DoLog(getLogPrefixName(), false, "Forwarded ugc result for glb_fwd_id=" + glb_fwd_id + " with err_msg=<" + _err_msg + ">");
        }

    }

    private static volatile SrvLinkIntercon curr_instance[] = new SrvLinkIntercon[Tum3cfg.getGlbInstance().getDbCount()]; // YYY
    private final Object perms_lock = new Object();
    private volatile long perms_millis = 0;
    private volatile boolean perms_loaded = false, perms_loading = false;
    private Tum3Perms.Tum3UgcUserlist curr_list = null;


    public SrvLinkMetaSrv(int _db_idx, SrvLinkOwner thisOwner) {
        super(_db_idx, thisOwner);
    }

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

    private static synchronized void resetCurrent(int _db_index, SrvLinkMetaSrv instance) {

        if (instance == curr_instance[_db_index]) curr_instance[_db_index] = null;

    }

    @Override
    protected boolean CancelLinkIntrnl() {

        if (super.CancelLinkIntrnl()) return true;
        resetCurrent(getDbIndex(), this); // YYY

        return false;
    }

    protected void onJSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] Got JSON function <" + jo.get(JSON_NAME_function) + ">");
        String tmp_func = jo.getString(JSON_NAME_function);

        if (JSON_FUNC_hello.equals(tmp_func)) {
            FinishJsonLoginReq(thrd_ctx, ctx, jo);
            return;
        }
        if (JSON_FUNC_ugcfwd.equals(tmp_func) && WasAuthorized) {
            int tmp_glb_fwd_id = jo.getInt(JSON_NAME_glb_fwd_id);
            String tmp_username = jo.getString(JSON_NAME_username);
            int tmp_req_id = jo.getInt(JSON_NAME_req_id);
            String tmp_shot = jo.getString(JSON_NAME_shot);
            byte[] tmp_arr = null;
            if (jo.has(JSON_NAME_body)) {
                String tmp_body = jo.getString(JSON_NAME_body);
                tmp_arr = Tum3Util.StringToBytesRaw(tmp_body);
            }
            //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] intercon ugc glb_fwd_id=" + tmp_glb_fwd_id + " req <" + tmp_username + "," + tmp_req_id + "," + tmp_shot + ">");

            String tmp_result = "Ugc request failed.";
            ReplyGenerator tmp_gen = new ReplyGenerator(thrd_ctx, ctx, tmp_glb_fwd_id);
            try {
                tmp_result = ExecuteUgc(thrd_ctx, ctx, tmp_shot, tmp_req_id, tmp_arr, tmp_username, tmp_gen);
            } catch (Exception e) {
                tmp_result = "Ugc request failed with: " + e;
            }

            if (!tmp_result.isEmpty()) {
                tmp_gen.GenerateUgcReply(thrd_ctx, tmp_req_id, tmp_shot, tmp_result, null);
            }
            return;
        }
        super.onJSON(thrd_ctx, ctx, jo);
    }

    private String ExecuteUgc(byte thrd_ctx, RecycledBuffContext ctx, String _shot_num, int _req_id, byte[] _upd_arr, String _username, ReplyGenerator _generator) throws Exception {

        String tmp_err_msg = "Unspecified error";

            if ((-1 == _req_id) || (-3 == _req_id)) {
//System.out.println("[DEBUG] Ugc request shot <" + _shot_num + "> id " + _req_id);
                return dbLink.GetUgcData(thrd_ctx, _generator, _req_id, _shot_num);
            } else {
//System.out.println("[DEBUG] Ugc update in shot " + _shot_num + " id " + _req_id);

                if (_username.isEmpty())
                    tmp_err_msg = "No username found for UGC update";
                else {

                    if (Tum3cfg.UgcEnabled(getDbIndex())) {

                        long tmp_now_millis = System.currentTimeMillis();
                        boolean tmp_load_now = false;
                        Tum3Perms.Tum3UgcUserlist next_list = null;
                        synchronized(perms_lock) {
                            if (!perms_loading && (!perms_loaded || ((tmp_now_millis - perms_millis) > 60*1000))) {
                                tmp_load_now = true;
                                perms_loading = true;
                            }
                        }
                        if (tmp_load_now) {
                            try {
                                next_list = Tum3Perms.getUgcUserlist(getDbIndex(), getLogPrefixName());
                            } finally {
                                synchronized(perms_lock) {
                                    perms_millis = tmp_now_millis;
                                    if (null != next_list) curr_list = next_list;
                                    perms_loading = false;
                                }
                            }
                        }

                        boolean tmp_CommentingAllowed = false, tmp_AddTagAllowed = false;
                        if (null != curr_list) {
                            Tum3Perms.Tum3UgcPerms tmp_ugc_perms = curr_list.get(_username);
                            if (null != tmp_ugc_perms) {
                                tmp_CommentingAllowed = tmp_ugc_perms.isCommentingAllowed;
                                tmp_AddTagAllowed = tmp_ugc_perms.isAddTagAllowed;
                            }
                        }
                        if (tmp_CommentingAllowed) {
                            tmp_err_msg = dbLink.UpdateUgcData(thrd_ctx, _username, tmp_AddTagAllowed, _generator, _req_id, _shot_num, _upd_arr);
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

    protected void SyncHandleAsyncEvent(byte thrd_ctx, int _ev_type, GeneralDbDistribEvent _ev) throws Exception {

        if ((_ev_type == _ev.DB_EV_UGC_SHOT_UPD) || (_ev_type == _ev.DB_EV_UGC_LIST_UPD)) {
            JSONObject jo = new JSONObject();
//if (_ev_type == _ev.DB_EV_UGC_SHOT_UPD)
// Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] DB_EV_UGC_SHOT_UPD int=" + _ev.get_int() + " shot=<" + _ev.get_str() + ">");
//if (_ev_type == _ev.DB_EV_UGC_LIST_UPD)
// Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] DB_EV_UGC_LIST_UPD int=" + _ev.get_int());
            jo.put(JSON_NAME_function, JSON_FUNC_ugcntfy);
            if (_ev_type == _ev.DB_EV_UGC_SHOT_UPD) {
                String tmpMsg = _ev.get_str();
                if (tmpMsg.length() > 100) tmpMsg = tmpMsg.substring(0, 100);
                jo.put(JSON_NAME_shot, tmpMsg);
                jo.put(JSON_NAME_req_id, -2);
            } else
                jo.put(JSON_NAME_req_id, _ev.get_int());
            Send_JSON(thrd_ctx, null, jo);
        } else {
            super.SyncHandleAsyncEvent(thrd_ctx, _ev_type, _ev);
        }
    }

    protected String makeHttpResponse(String _http_header) {

        parseHttpValues(_http_header);
        return makeSwitchToWsHttpHdr();

    } 
}
