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


import org.json.JSONObject;

import aq2db.*;
import aq2net.*;


public abstract class SrvLinkIntercon extends SrvLinkBase {


    protected final static String JSON_NAME_err_msg = "err_msg";
    protected final static String JSON_NAME_function = "function";
    protected final static String JSON_NAME_db = "db";
    protected final static String JSON_NAME_endpoint_type = "ep_type"; // YYY
    protected final static String JSON_NAME_username = "username";
    protected final static String JSON_NAME_password = "password";
    protected final static String JSON_NAME_body = "body";

    protected final static String JSON_FUNC_hello = "hello";
    protected final static String JSON_FUNC_welcome = "welcome";

    protected static volatile int dbg_serial = 0;
    public int my_dbg_serial = 0;

    protected volatile boolean WasAuthorized = false;
    protected long LoginFailedAt;
    protected boolean LoginFailedState = false;
    protected volatile String AuthorizedLogin = "";
    protected Tum3Db dbLink = null;

    protected final String db_name;


    public SrvLinkIntercon(int _db_idx, TunedSrvLinkPars _tuned_pars, SrvLinkOwner thisOwner) {

        super(_db_idx, _tuned_pars, thisOwner);
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        dbg_serial++;
        my_dbg_serial = dbg_serial;

    }

    abstract protected String InterconLabel();

    protected int getDbIndex() {

        return db_index;

    }

    public Tum3Db GetDb() {

        return dbLink;

    }

    public byte[] GetBinaryUsername() {

        return null;

    }

    protected final String AuthorizedLoginPlus() {

        if (WasAuthorized)
            return AuthorizedLogin + "(intercon)." + db_name; // YYY
        else
            return "(intercon)." + db_name; // YYY

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

    protected void Send_JSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx);

        String tmpMsg = jo.toString();

        tmpBuff.InitSrvReply(REQUEST_TYPE_JSON, tmpMsg.length(), tmpMsg.length());
        tmpBuff.putString(tmpMsg);
        PutBuff(thrd_ctx, tmpBuff, ctx);

    }

    protected void SendHelloMsg(byte thrd_ctx, RecycledBuffContext ctx, String _username, String _password) throws Exception {

        JSONObject jo = new JSONObject();
        jo.put(JSON_NAME_function, JSON_FUNC_hello);
        jo.put(JSON_NAME_db, getLogPrefixName());
        jo.put(JSON_NAME_endpoint_type, InterconLabel()); // YYY
        jo.put(JSON_NAME_username, _username); // YYY
        jo.put(JSON_NAME_password, _password); // YYY

        Send_JSON(thrd_ctx, ctx, jo);
    }

    protected final void Process_PingHighlevel(byte thrd_ctx, RecycledBuffContext ctx) throws Exception {
        OutgoingBuff tmpBuff = null;
        tmpBuff = GetBuff(thrd_ctx, ctx);
        tmpBuff.InitSrvReply(REQUEST_TYPE_KEEPCONNECTED, 0, 0);
        PutBuff(thrd_ctx, tmpBuff, ctx);

    }

    protected final void Process_PingReply() {
        //System.out.println("[aq2j] DEBUG: got ping reply.");
        keepalive_sent_inline = false;
    }

    protected void ExtendJsonLoginReply(JSONObject jo2) { }

    protected void FinishJsonLoginReq(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception {

            String tmp_db = jo.getString(JSON_NAME_db);
            String tmp_login = jo.getString(JSON_NAME_username);
            String tmp_password = jo.getString(JSON_NAME_password);
            String tmp_ep_name = jo.getString(JSON_NAME_endpoint_type); // YYY
            if (null == tmp_ep_name) tmp_ep_name = "";
            //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] intercon login with <" + tmp_db + "," + tmp_login + "," + tmp_password + ">");
            if (!tmp_ep_name.isEmpty() && !InterconLabel().equals(tmp_ep_name))
                Tum3Logger.DoLog(getLogPrefixName(), true, "Warning: endpoint type mismatch: " + InterconLabel() + " expected but " + tmp_ep_name + " was attempted."); // YYY
            else if (getLogPrefixName().equals(tmp_db) && get_transp_user().equals(tmp_login))
                if (Tum3Perms.CheckMetaPwdIsCorrect(getDbIndex(), tmp_db, tmp_login, tmp_password)) // YYY
                    if (setCurrent(getDbIndex(), this)) { // YYY
                        Tum3Logger.DoLog(getLogPrefixName(), false, "Intercon " + InterconLabel() + " auth success.");
                        WasAuthorized = true;
                        AuthorizedLogin = tmp_login;
                        InitDbAccess();
                        dbLink.setOtherServerConnected(true); // YYY

                        JSONObject jo2 = new JSONObject();
                        jo2.put(JSON_NAME_function, JSON_FUNC_welcome);
                        jo2.put(JSON_NAME_db, getLogPrefixName());
                        jo2.put(JSON_NAME_username, tmp_login);
                        ExtendJsonLoginReply(jo2);
                        Send_JSON(thrd_ctx, ctx, jo2);
                    } else {
                        Tum3Logger.DoLog(getLogPrefixName(), true, "Warning: duplicate or unsupported " + InterconLabel() + " intercon auth attempted."); // YYY
                    }
            if (!WasAuthorized) {
                Tum3Logger.DoLog(getLogPrefixName(), true, "Intercon " + InterconLabel() + " auth failed.");
                LoginFailedAt = System.currentTimeMillis();
                LoginFailedState = true;
                ShutdownSrvLink("Intercon (" + InterconLabel() + ") authorization rejected");
            }
    }

    protected void UplinkConnExtra(byte thrd_ctx, RecycledBuffContext ctx) throws Exception { } // YYY

    protected void JsonWelcomeHandler(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo, String _username) throws Exception {

            String tmp_db = jo.getString(JSON_NAME_db);
            String tmp_login = jo.getString(JSON_NAME_username);
            //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] intercon welcome with <" + tmp_db + "," + tmp_login + ">");
            if (getLogPrefixName().equals(tmp_db) && _username.equals(tmp_login)) { // YYY
                    Tum3Logger.DoLog(getLogPrefixName(), false, "Intercon " + InterconLabel() + " auth success.");
                    WasAuthorized = true;
                    AuthorizedLogin = tmp_login;
                    InitDbAccess();
                    UplinkConnExtra(thrd_ctx, ctx);
            } else {
                    Tum3Logger.DoLog(getLogPrefixName(), true, "Intercon " + InterconLabel() + " registration failed (mismatching values found: db=" + tmp_db + ", username=" + tmp_login + ")"); // YYY
            }
            if (!WasAuthorized) {
                Tum3Logger.DoLog(getLogPrefixName(), true, "Intercon " + InterconLabel() + " registration failed.");
                LoginFailedAt = System.currentTimeMillis();
                LoginFailedState = true;
                ShutdownSrvLink("Intercon " + InterconLabel() + " registration did not match the requested values"); // YYY
            }
    }

    abstract protected void onJSON_Intrnl(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo) throws Exception;

    abstract protected void InitDbAccess();

    protected boolean setCurrent(int _db_index, SrvLinkIntercon instance) { return false; } // YYY

    protected void Process_JSON(byte thrd_ctx, RecycledBuffContext ctx, byte[] req_body, int req_trailing_len) throws Exception {

        String tmp_json_str = Tum3Util.BytesToStringRaw(req_body, 0, req_trailing_len);
        //JSONObject jo = new JSONObject(tmp_json_str);
        onJSON_Intrnl(thrd_ctx, ctx, new JSONObject(tmp_json_str));

    }

    protected void ExecuteReq(byte thrd_ctx, byte req_code, byte[] req_body, int req_trailing_len) throws Exception {

        if (REQUEST_TYPE_JSON == req_code) Process_JSON(thrd_ctx, null, req_body, req_trailing_len);
        else if (REQUEST_TYPE_ANYBODYTHERE == req_code) Process_PingHighlevel(thrd_ctx, null);
        else if (REQUEST_TYPE_KEEPCONNECTED == req_code) Process_PingReply();
        else {
            Tum3Logger.DoLog(db_name, true, "WARNING: unknown request in " + InterconLabel() + " intercon, code=" + Integer.toHexString(req_code & 0xFF) + " len=" + req_trailing_len + ";" + " Session: " + DebugTitle());
        }
    }

    protected boolean NoPauseOut() {

        return true;

    }

}
