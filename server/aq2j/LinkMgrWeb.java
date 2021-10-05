/*
 * Copyright 2011-2021 Nikolai Zhubr <zhubr@mail.ru>
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


import aq2db.*;


import java.io.*;
import java.nio.*;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;


public final class LinkMgrWeb extends Thread implements SrvLinkOwner, AppStopHook {


    private final static String TUM3_CFG_ws_raw_out_buff_kbytes = "ws_raw_out_buff_kbytes";
    private final static int CONST_WS_SRC_WAKEUP_MILLIS = 1000;
    private final static int CONST_WS_DST_WAKEUP_MILLIS = 1000;
    private final static int CONST_WS_DST_WAKEUP_MILLIS_MIN = 100;
    private final static int CONST_WS_SND_WAKEUP_MILLIS = 1000;
    private final static int CONST_WS_OOB_QUEUE_LIM = 10;
    private final static int CONST_SEND_LOOP_LIMIT_MILLIS = 1000;
    private final static int INTRL_SOCK_FULL = 1;
    private final static int INTRL_DATA_FULL = 2;

    private int db_index;
    private String db_name;


    private static class ServerReader extends Thread implements ClientWriter {

        private SrvLink sLink;
        private LinkMgrWeb parentStreamer;
        private ClientWriterRaw raw_writer;
        private volatile byte[] tmpInputBuff;
        private volatile boolean tmpInputBuffBusy = false;
        private volatile int tmpInputBuffCount = 0;
        private ByteBuffer msgBb;
        private static final int CONST_WS_BUFF_SIZE_def = 512; // 512 Kbytes (very small default)
        private static int CONST_WS_BUFF_SIZE[] = InitWsBuffSizeConst(); // Should be per-db now.
        private volatile ArrayList<String> outOOBlist = new ArrayList<String>();
        private ArrayList<String> outOOBlist2 = new ArrayList<String>();
        private Object WaitObj = new Object();

        private static final int[] InitWsBuffSizeConst() {

            int tmp_arr[] = new int[Tum3cfg.getGlbInstance().getDbCount()];
            Tum3cfg cfg = Tum3cfg.getGlbInstance();
            for (int tmp_i = 0; tmp_i < tmp_arr.length; tmp_i++) {
                tmp_arr[tmp_i] = 1024 * Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_ws_raw_out_buff_kbytes, CONST_WS_BUFF_SIZE_def);
                Tum3Logger.DoLog(cfg.getDbName(tmp_i), false, "DEBUG: CONST_WS_BUFF_SIZE=" + tmp_arr[tmp_i]);
            }
            return tmp_arr;

        }

        public ServerReader (int _db_idx, LinkMgrWeb f, SrvLink l, ClientWriterRaw _o_raw) {
            sLink = l;
            raw_writer = _o_raw;
            parentStreamer = f;
            tmpInputBuff = new byte[CONST_WS_BUFF_SIZE[_db_idx]];
            msgBb = ByteBuffer.wrap(tmpInputBuff);
            this.setDaemon(true);
            this.start();
        }

        private void WaitBuffDone(int timeout) {

            parentStreamer.WaitMain(timeout);

        }

        private byte[] GetBuff() throws Exception {
            // Reminder! Called from different thread than self!

            if (tmpInputBuffBusy) throw new Exception("Internal error: tmpInputBuffBusy");
            return tmpInputBuff;
        }

        private void SendToClient(int byteCount) throws Exception {
            // Reminder! Called from different thread than self!

            synchronized(WaitObj) {
                if (tmpInputBuffBusy) throw new Exception("Internal error: still busy in SendToClient()");
                tmpInputBuffBusy = true;
                tmpInputBuffCount = byteCount;
                WaitObj.notify();
            }

        }

        public int AcceptFrom(OutBuffData src) throws Exception {

            int tmp_filled_count = src.SendToByteArray(GetBuff()); // XXX TODO!!! Optimize away GetBuff().
            SendToClient(tmp_filled_count); // Moved here from below.
            return tmp_filled_count;

        }

        public void SendToClientAsOOB(String oobMsg) throws Exception { 
            // Reminder! Called from different thread than self!

            //raw_writer.SendToClientAsOOB(oobMsg);
            synchronized(WaitObj) {
                if (outOOBlist.size() > CONST_WS_OOB_QUEUE_LIM) throw new Exception("OOB message queue full, maybe consider changing CONST_WS_OOB_QUEUE_LIM");
                outOOBlist.add(oobMsg);
                WaitObj.notify();
            }

        }

        public boolean BuffBusy() {
            // Reminder! Called from different thread than self!

            return tmpInputBuffBusy; // XXX I think synchronized is not strictly necessary here?

        }

        public void close() throws Exception {
            raw_writer.close();
        }

        public boolean isOpen() {
            return raw_writer.isOpen();
        }

        public void run() { // LinkMgrWeb.ServerReader.run() == THRD_SERVER_READER

            //System.out.println("Started server reader thread.");

            String tmp_reason = "Outbound stream closed";
            try {
                while (isOpen()) {
                    //sLink.ReadFromServer2(SrvLink.THRD_SERVER_READER, this, false);
                    //  Note. Writing to this "outbound" may block for an unknown 
                    //   period of time before it is pushed out fully, that is why 
                    //   it is desirable to keep it within such additional helper 
                    //   thread, so as to isolate this blocking point from the rest
                    //   of (non-blocking) processing.

                    boolean tmpBusy;
                    int tmpCount;
                    synchronized(WaitObj) {
                        try {
                            WaitObj.wait(CONST_WS_SND_WAKEUP_MILLIS);
                        } catch(Exception e) { }
                        if (outOOBlist.size() > 0) {
                            ArrayList<String> tmp_outOOBlist = outOOBlist;
                            outOOBlist = outOOBlist2;
                            outOOBlist2 = tmp_outOOBlist;
                        }
                        tmpBusy = tmpInputBuffBusy;
                        tmpCount = tmpInputBuffCount;
                    }               
                    while (outOOBlist2.size() > 0) {
                        raw_writer.SendToClientAsOOB(outOOBlist2.get(0));
                        outOOBlist2.remove(0);
                    }
                    if (tmpBusy) {
                        raw_writer.SendToClient(msgBb, tmpCount);
                        synchronized(WaitObj) {
                            tmpInputBuffBusy = false;
                        }
                        parentStreamer.WakeupMain();
                    }
                }
            } catch (Exception e) {
                //syscon.println("Parse excptn:" + e);
                tmp_reason = "Exception in ServerReader: " + e;
            }
            parentStreamer.ShutdownSrvLink(tmp_reason);
            //System.out.println("Stopped reading current stream, reason = " + parseError);
        }

    }

    public static class SelectedHttpHeaders extends HashMap<String, String> {

        public String safeGet(String key) {

            if (containsKey(key)) return get(key);
            else return "";

        }

    }

    private SrvLink sLink = null;
    private ServerReader srv_reader = null;
    private ClientWriterRaw o_raw;
    public boolean ready_local = false;
    private volatile boolean is_terminating = false;
    private String terminating_reason = "";

    private ArrayList<NetMsgSized> input_queue_curr = new ArrayList<NetMsgSized>();
    private ArrayList<NetMsgSized> input_queue_spare = new ArrayList<NetMsgSized>();
    private Object input_blocker = new Object();
    private Object input_stuck_blocker = new Object();
    private int input_queue_size = 0, input_queue_count = 0;

    private final static String TUM3_CFG_max_inp_buff_kbytes = "max_inp_buff_kbytes";
    private final static int CONST_MAX_INP_BUFF_KBYTES_default = 1;
    private volatile static int CONST_MAX_INP_BUFF_BYTES = 0;
    private final static int CONST_MAX_INP_OOB_CHARS = 16;
    private final static int CONST_INPUT_INTERMED_BYTES_LIMIT = 32*1024*1024; // XXX Test and tune.
    private final static int CONST_INPUT_INTERMED_MSGS_LIMIT = 400; // XXX Test and tune.

    private String transp_caller = "", transp_user = "", transp_agent = "";

    public LinkMgrWeb(int _db_idx, ClientWriterRaw _o_raw, SelectedHttpHeaders http_headers)
    {
        // "x-real-ip", "x-real-port", "x-real-user", "user-agent"
        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        String tmp_ip = http_headers.safeGet("x-real-ip"), tmp_port = http_headers.safeGet("x-real-port");
        if (!tmp_ip.isEmpty() && !tmp_port.isEmpty()) transp_caller = tmp_ip + ":" + tmp_port;
        transp_user = http_headers.safeGet("x-real-user");
        transp_agent = http_headers.safeGet("user-agent");
        //System.out.println("[aq2j] DEBUG: tmp_ip=" + tmp_ip + " tmp_port=" + tmp_port + " tmp_user=" + tmp_user + " tmp_agent=" + tmp_agent);
        sLink = new SrvLink(_db_idx, this);
        if (0 == CONST_MAX_INP_BUFF_BYTES) {
            CONST_MAX_INP_BUFF_BYTES = 1024 * Tum3cfg.getIntValue(db_index, true, TUM3_CFG_max_inp_buff_kbytes, CONST_MAX_INP_BUFF_KBYTES_default);
            Tum3Logger.DoLog(db_name, false, "DEBUG: CONST_MAX_INP_BUFF_BYTES=" + CONST_MAX_INP_BUFF_BYTES);
        }
        o_raw = _o_raw;
        this.start();
    }

    public String get_transp_caller() { return transp_caller; }

    public String get_transp_user() { return transp_user; }

    public String get_transp_agent() { return transp_agent; }

    public String get_transp_title() { return "ws"; }

    //public boolean SingleThread() {
    //  return false;
    //}


    public boolean SupportOOB() {
        return true;
    }

    private void SetTerminate(String reason) {

        synchronized(input_blocker) {
            is_terminating = true;
            if (terminating_reason.isEmpty()) terminating_reason = reason;
            input_blocker.notify();
        }

    }

    public void AppStopped() {

        SetTerminate("Application is shutting down.");

    }

    private boolean MustShutdownSrvLink() {

        return is_terminating;

    }

    public void ShutdownSrvLink(String reason) {

        SetTerminate(reason);
        //SetReady();

    }

    private abstract class NetMsgSized {

        public abstract int getSize();
        public abstract void DoHandle(LinkMgrWeb sender) throws Exception;

    }

    private class Msg_ReadOOBMsgFromClient extends NetMsgSized {

        private String r;

        public Msg_ReadOOBMsgFromClient(String _r) {
            r = _r;
        }

        public int getSize() {
            return r.length() >> 1;
        }

        public void DoHandle(LinkMgrWeb sender) throws Exception {
            sender.Do_ReadOOBMsgFromClient(r);
        }

    }
    public void ReadOOBMsgFromClient(String r) throws Exception {
        post(new Msg_ReadOOBMsgFromClient(r));
    }
    private void Do_ReadOOBMsgFromClient(String r) throws Exception {
        sLink.SendOOBToServer(r);
    }

    private class Msg_ReadFromClient extends NetMsgSized {

        private ByteBuffer message;

        public Msg_ReadFromClient(ByteBuffer _message) {
            message = _message;
        }

        public int getSize() {
            return message.remaining();
        }

        public void DoHandle(LinkMgrWeb sender) throws Exception {
            sender.Do_ReadFromClient(message);
        }

    }
    public void ReadFromClient(ByteBuffer message) throws Exception {
        post(new Msg_ReadFromClient(message));
    }
    private void Do_ReadFromClient(ByteBuffer message) throws Exception {
        sLink.SendToServer(SrvLink.THRD_INTERNAL, message);
    }

    public void post(NetMsgSized msg) { // XXX FIXME!!! Provide some interrupter callback for the waiting case!

        boolean tmp_stuck = false;
        do {
            synchronized (input_blocker) { 
                if ((input_queue_size >= CONST_INPUT_INTERMED_BYTES_LIMIT) || (input_queue_count >= CONST_INPUT_INTERMED_MSGS_LIMIT))
                    tmp_stuck = true;
                else {
                    input_queue_size += msg.getSize();
                    input_queue_count++;
                    input_queue_curr.add(msg);
                    input_blocker.notify();
                    tmp_stuck = false;
                }
            }
            if (tmp_stuck) synchronized (input_stuck_blocker) {
                try {
                    input_stuck_blocker.wait(CONST_WS_SRC_WAKEUP_MILLIS);
                } catch(Exception e) { }
            }
        } while (tmp_stuck);
    }

    private void HandleMessages(ArrayList<NetMsgSized> tmp_queue) throws Exception {
        while (tmp_queue.size() > 0) {
            tmp_queue.get(0).DoHandle(this);
            tmp_queue.remove(0);
        }
    }

    private void Tick() throws Exception {

        sLink.ClientReaderTick(SrvLink.THRD_INTERNAL, srv_reader);

    }

    public boolean WaitForOutputDone(int timeout) throws Exception {
        // Reminder. This is only called from the main thread (THRD_INTERNAL).

        //System.out.print("{" + sLink.my_dbg_serial + ": +WaitForOutputDone}");

        boolean tmp_socket_busy = (TrySendOut(true) & INTRL_SOCK_FULL) != 0;

        if (tmp_socket_busy) {
            //System.out.print("{" + sLink.my_dbg_serial + ": busy}");
            //if (sc_selector.select(timeout) > 0) {
            //  sc_selector.selectedKeys().clear();
            //}
            srv_reader.WaitBuffDone(timeout);
        }

        //System.out.print("{" + sLink.my_dbg_serial + ": -WaitForOutputDone}");
        return !tmp_socket_busy;

    }

    public void WakeupMain() {

        synchronized(input_blocker) { input_blocker.notifyAll(); }

    }

    protected void WaitMain(int millis) {

        synchronized(input_blocker) {
            try {
                input_blocker.wait(millis);
            } catch(Exception e) { }
        }

    }

    public void run()  // LinkMgrWeb.run() == THRD_INTERNAL
    {
        AppStopHooker.AddHook(this);

        String tmp_new_reason = "";
        try {

            sLink.DoLink();

            //System.out.println("[aq2j] DEBUG: Opening communication.");
            srv_reader = new ServerReader(db_index, this, sLink, o_raw);
            //SetReady();

            boolean tmp_data_pending = false;

            //System.out.println("[DEBUG] example text: tmp_data_pending");

            while (!MustShutdownSrvLink()) {

                boolean tmp_do_unblock_src = false;
                long tmp_wait_timeout = CONST_WS_DST_WAKEUP_MILLIS;
                if (tmp_data_pending) { 
                    tmp_wait_timeout = CONST_WS_DST_WAKEUP_MILLIS_MIN;
                    //System.out.println("[DEBUG] tmp_data_pending");
                }
                synchronized(input_blocker) {
                    if (input_queue_curr.isEmpty())
                        try {
                            input_blocker.wait(tmp_wait_timeout);
                        } catch(Exception e) { }
                    if (!input_queue_curr.isEmpty()) {
                        ArrayList<NetMsgSized> tmp_queue = input_queue_curr;
                        input_queue_curr = input_queue_spare;
                        input_queue_spare = tmp_queue;
                        if ((input_queue_size >= CONST_INPUT_INTERMED_BYTES_LIMIT) || (input_queue_count >= CONST_INPUT_INTERMED_MSGS_LIMIT)) tmp_do_unblock_src = true;
                        input_queue_size = 0;
                        input_queue_count = 0;
                    }
                }
                if (tmp_do_unblock_src) synchronized (input_stuck_blocker) { input_stuck_blocker.notify(); }

                if (MustShutdownSrvLink()) break;

                if (input_queue_spare.size() > 0) HandleMessages(input_queue_spare);

                if (MustShutdownSrvLink()) break;

                Tick(); // Moved here from above HandleMessages(), because some async events are added by HandleMessages().

                tmp_data_pending = (TrySendOut(false) & INTRL_DATA_FULL) != 0;
            }

        } catch (Exception e) {
            //System.out.println("[aq2j] DEBUG: Websocket exception " + e);
            tmp_new_reason = "Exception in LinkMgrWeb: " + Tum3Util.getStackTrace(e);
        }

        String tmp_prev_reason = "";
        synchronized(input_blocker) {
            tmp_prev_reason = terminating_reason;
        }
        if (tmp_prev_reason.isEmpty() && !tmp_new_reason.isEmpty())
            tmp_prev_reason = tmp_new_reason;

        Tum3Logger.DoLog(db_name, false, "Closing communication with ws/" + transp_caller + " (" + tmp_prev_reason + ")");
        sLink.CancelLink();  //CancelConnection();

        try {
            srv_reader.close();
        } catch (Exception ignored) { }

        srv_reader = null;

        AppStopHooker.RemoveHook(this);
    }

    private int TrySendOut(boolean hurry) throws Exception {
        // Reminder. This is only called from the main thread (THRD_INTERNAL).
        // Reminder. Result values: see comment in LinkMgrTcp.TrySendOut()

        boolean tmp_out_wait = true;
        long tmp_curr_millis, tmp_begin_millis = System.currentTimeMillis();
        boolean tmp_timeout = false;
        do {
            //if (hurry) System.out.println("[aq2j] outBB.remaining()=" + outBB.remaining());
            if (srv_reader.BuffBusy()) {
                tmp_out_wait = true;
                return INTRL_SOCK_FULL;
            } else {
                //if (!tmp_out_wait) System.out.println("[DEBUG] sLink.ReadFromServer2 was full!");
                tmp_out_wait = sLink.ReadFromServer2(SrvLink.THRD_INTERNAL, srv_reader, hurry);
                //if (!tmp_out_wait) System.out.println("[DEBUG] sLink.ReadFromServer2 now full!");
                //if (hurry) System.out.println("[aq2j] ReadFromServer2 returned " + tmp_out_wait);
            }
            tmp_curr_millis = System.currentTimeMillis();
            tmp_timeout = ((tmp_curr_millis - tmp_begin_millis) >= CONST_SEND_LOOP_LIMIT_MILLIS);
            // We go out of this loop if data for output is empty or buffer is too full.
        } while (!tmp_out_wait && !tmp_timeout && !MustShutdownSrvLink());

        // Reminder: because sLink.ReadFromServer2() can consume substantial time (to read data from disk etc.), timeouts here are normal, not error.
        if (!tmp_out_wait) return INTRL_DATA_FULL;
        else return 0;

        //if (hurry) System.out.println("[aq2j] exiting TrySendOut.");
        //return false;

    }


}
