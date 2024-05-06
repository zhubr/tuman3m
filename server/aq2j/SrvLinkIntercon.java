/*
 * Copyright 2023-2024 Nikolai Zhubr <zhubr@mail.ru>
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

import java.util.*;
import java.text.SimpleDateFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.json.JSONObject;

import aq2db.*;
import aq2net.*;


public abstract class SrvLinkIntercon extends SrvLinkBase {


    protected final static String TUM3_CFG_debug_ignore_db_name = "debug_ignore_db_name";

    protected final static String JSON_NAME_err_msg = "err_msg";
    protected final static String JSON_NAME_function = "function";
    protected final static String JSON_NAME_db = "db";
    protected final static String JSON_NAME_endpoint_type = "ep_type"; // YYY
    protected final static String JSON_NAME_username = "username";
    protected final static String JSON_NAME_password = "password";
    protected final static String JSON_NAME_body = "body";

    protected final static String JSON_FUNC_hello = "hello";
    protected final static String JSON_FUNC_welcome = "welcome";

    protected final static String JSON_FUNC_bup_get_last_list = "bup_get_last_list";
    protected final static String JSON_FUNC_bup_rep_last_list = "bup_rep_last_list";

    protected static volatile int dbg_serial = 0;
    public int my_dbg_serial = 0;

    protected volatile boolean WasAuthorized = false;
    protected long LoginFailedAt;
    protected boolean LoginFailedState = false;
    protected volatile String AuthorizedLogin = "";
    protected Tum3Db dbLink = null;

    protected final String db_name;
    private final boolean debug_ignore_db_name; // YYY


    protected static final int fixed_fpart_header_size = 16 + 16 + 4+4 + 8+8+8;


    protected static int byte2uint(byte b) {

        int i = b;
        if (i < 0) i = (0xFF & i) | 0x80;
        return i;

    }

    protected static String bytes2string(byte src[], int ofs) {

        return Tum3Util.BytesToStringRaw(src, ofs+1, byte2uint(src[ofs]));

    }

    public SrvLinkIntercon(int _db_idx, TunedSrvLinkPars _tuned_pars, SrvLinkOwner thisOwner) {

        super(_db_idx, _tuned_pars, thisOwner);
        allowPreHttp(); // YYY
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        debug_ignore_db_name = "1".equals(Tum3cfg.getParValue(db_index, false, TUM3_CFG_debug_ignore_db_name, "")); // YYY
        if (debug_ignore_db_name) Tum3Logger.DoLog(db_name, true, "DEBUG: debug_ignore_db_name was set to 1, be very carefull!");
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

        Send_JSON(thrd_ctx, ctx, jo, null, 0, 0);

    }

    protected void Send_JSON(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo, byte[] attachment, int att_offset, int att_length) throws Exception {

        OutgoingBuff tmpBuff = GetBuff(thrd_ctx, ctx);

        String tmpMsg = jo.toString();

        byte tmp_opcode = REQUEST_TYPE_JSON;
        int tmp_len = tmpMsg.length();
        if (null != attachment) {
            tmp_opcode = REQUEST_TYPE_JSON_WBIN;
            tmp_len += 8 + att_length;
        }
        tmpBuff.InitSrvReply(tmp_opcode, tmp_len, tmp_len);
        if (null != attachment) {
            tmpBuff.putInt(0);
            tmpBuff.putInt(tmpMsg.length());
        }
        tmpBuff.putString(tmpMsg);
        if (null != attachment) tmpBuff.putBytes(attachment, att_offset, att_length);

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

    protected void InitFPartBuff(boolean _is_volatile, OutgoingBuff _tmpBuff, byte _opcode, int _header_extra_size, OutBuffContinuator _continuator, String _shot_name, String _file_name, long _full_size, long _curr_seg_ofs) throws Exception {

        _tmpBuff.InitSrvReply(_opcode, fixed_fpart_header_size+_header_extra_size, fixed_fpart_header_size+_header_extra_size, _continuator);

        _tmpBuff.putString(Tum3Util.StringToPasString(_shot_name, 15));
        _tmpBuff.putString(Tum3Util.StringToPasString(_file_name, 15));

        _tmpBuff.putInt(0); // Reserved (SignalId)
        int HAccessOptions = _is_volatile? 0x01 : 0x00; // Vol/Nonvol (getEditedByte)
        _tmpBuff.putInt(HAccessOptions);

        _tmpBuff.putLong(_full_size);
        _tmpBuff.putLong(_curr_seg_ofs);
        _tmpBuff.putLong(0); // Reserved.
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
            //Tum3Logger.DoLog(getLogPrefixName(), true, "[DEBUG] got intercon login request with <" + tmp_ep_name + "," + tmp_db + "," + tmp_login + "," + tmp_password + "> get_transp_user()=" + get_transp_user());
            if (!tmp_ep_name.isEmpty() && !InterconLabel().equals(tmp_ep_name))
                Tum3Logger.DoLog(getLogPrefixName(), true, "Warning: endpoint type mismatch: " + InterconLabel() + " expected but " + tmp_ep_name + " was attempted."); // YYY
            else if ((getLogPrefixName().equals(tmp_db) || debug_ignore_db_name) && get_transp_user().equals(tmp_login)) // YYY
                if (Tum3Perms.CheckMetaPwdIsCorrect(getDbIndex(), tmp_db, tmp_login, tmp_password)) // YYY
                    if (setCurrent(getDbIndex(), this)) { // YYY
                        Tum3Logger.DoLog(getLogPrefixName(), false, "Intercon " + InterconLabel() + " auth success.");
                        WasAuthorized = true;
                        AuthorizedLogin = tmp_login;
                        InitDbAccess();

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
            if ((getLogPrefixName().equals(tmp_db) || debug_ignore_db_name) && _username.equals(tmp_login)) { // YYY
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

    abstract protected void onJSON_Intrnl(byte thrd_ctx, RecycledBuffContext ctx, JSONObject jo, byte[] bin_att, int att_ofs, int att_len) throws Exception;

    abstract protected void InitDbAccess();

    protected boolean setCurrent(int _db_index, SrvLinkIntercon instance) { return false; } // YYY

    protected void Process_JSON(byte thrd_ctx, RecycledBuffContext ctx, byte[] req_body, int req_trailing_len, boolean with_attachment) throws Exception {

        int tmp_json_len = req_trailing_len, tmp_json_ofs = 0, tmp_att_len = 0;
        if (with_attachment) {
            if (req_trailing_len < 8) throw new Exception("JSON_WBIN packet of insufficient size encountered");
            ByteBuffer tmpBB = ByteBuffer.wrap(req_body, 0, req_trailing_len);
            tmpBB.order(ByteOrder.LITTLE_ENDIAN);
            tmpBB.getInt(); // Reserved
            tmp_json_len = tmpBB.getInt();
            tmp_json_ofs = 8;
            tmp_att_len = req_trailing_len - tmp_json_ofs - tmp_json_len;
            if (tmp_att_len < 0)  throw new Exception("JSON_WBIN packet with oversized text encountered");
        }
        String tmp_json_str = Tum3Util.BytesToStringRaw(req_body, tmp_json_ofs, tmp_json_len);
        if (with_attachment) onJSON_Intrnl(thrd_ctx, ctx, new JSONObject(tmp_json_str), req_body, tmp_json_ofs+tmp_json_len, tmp_att_len);
        else                 onJSON_Intrnl(thrd_ctx, ctx, new JSONObject(tmp_json_str), null, 0, 0);

    }

    protected void ExecuteReq(byte thrd_ctx, byte req_code, byte[] req_body, int req_trailing_len) throws Exception {

        if ((REQUEST_TYPE_JSON == req_code) || (REQUEST_TYPE_JSON_WBIN == req_code)) Process_JSON(thrd_ctx, null, req_body, req_trailing_len, (REQUEST_TYPE_JSON_WBIN == req_code)); // YYY
        else if (REQUEST_TYPE_ANYBODYTHERE == req_code) Process_PingHighlevel(thrd_ctx, null);
        else if (REQUEST_TYPE_KEEPCONNECTED == req_code) Process_PingReply();
        else {
            Tum3Logger.DoLog(db_name, true, "WARNING: unknown request in " + InterconLabel() + " intercon, code=" + Integer.toHexString(req_code & 0xFF) + " len=" + req_trailing_len + ";" + " Session: " + DebugTitle());
        }
    }

    protected boolean NoPauseOut() {

        return true;

    }

    protected void parseHttpValues(String _http_header) {

        if (_http_header.startsWith("GET ")) {
            //Tum3Logger.DoLog(getLogPrefixName(), false, "[DEBUG] got http GET...");
            String tmp_real_ip = "", tmp_real_port = "";
            for (String tmp_line: _http_header.split("\r\n")) {
                //Tum3Logger.DoLog(getLogPrefixName(), false, "[DEBUG] got http GET: <" + tmp_line + ">");
                if (tmp_line.startsWith("X-Real-user:"))
                    set_transp_user(tmp_line.substring(12).trim());
                else if (tmp_line.startsWith("X-Real-ip:"))
                    tmp_real_ip = tmp_line.substring(10).trim();
                else if (tmp_line.startsWith("X-Real-port:"))
                    tmp_real_ip = tmp_line.substring(12).trim();
            }
            if (!tmp_real_ip.isEmpty() || !tmp_real_port.isEmpty())
                set_transp_caller(tmp_real_ip + ":" + tmp_real_port);
        }
    }

    protected String makeSwitchToWsHttpHdr() {

        return "HTTP/1.1 101 Switching Protocols\r\n"
             + "Upgrade: websocket\r\nConnection: upgrade\r\n"
             + "X-WebSocket-Tum3Compat: 1\r\n"
             + "Date: " + (new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)).format(new Date(System.currentTimeMillis())) + "\r\n\r\n";
    }
}
