/*
 * Copyright 2011-2023 Nikolai Zhubr <zhubr@mail.ru>
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
import java.net.StandardSocketOptions;
import java.nio.channels.*;


public final class LinkMgrTcp extends Thread implements SrvLinkOwner, ClientWriter, AppStopHook {
    private SrvLinkBase sLink = null;
    private volatile SocketChannel sc;
    private volatile Selector sc_selector; // YYY
    private SelectionKey key;
    private int key_base = SelectionKey.OP_READ;
    private byte[] tmpOutputBuff;
    private int dbg_count = 0;
    private boolean tmp_recheck_sock = false;

    public boolean ready_local = false;
    private volatile boolean is_terminating = false;
    private String terminating_reason = "";

    private byte[] inpWebBuff;
    private ByteBuffer inpBB, outBB;

    private final static int CONST_MAX_INP_OOB_CHARS = 16;

    private int CONST_TCP_BUFF_SIZE = 1;
    private final static int CONST_TCP_CLIENT_WAKEINTERVAL = 1000;
    private final static int CONST_TCP_CLIENT_WAKEINTERVAL_MIN = 100;
    private final static int CONST_SEND_LOOP_LIMIT_MILLIS = 1000;
    private final static int INTRL_SOCK_FULL = 1;
    private final static int INTRL_DATA_FULL = 2;

    private String transp_caller = "";
    SessionProducerTcp session_producer;


    public LinkMgrTcp(SessionProducerTcp _session_producer, SocketChannel this_sc) throws Exception
    {
        session_producer = _session_producer;
        CONST_TCP_BUFF_SIZE = session_producer.get_CONST_TCP_BUFF_SIZE(); // _tcp_buff_size;
        String tmp_ip = this_sc.socket().getInetAddress().getHostAddress().toString();
        String tmp_port = this_sc.socket().getPort() + "";
        if (!tmp_ip.isEmpty() && !tmp_port.isEmpty()) transp_caller = tmp_ip + ":" + tmp_port;

        sLink = session_producer.newSrvLink(this); // new SrvLink(_db_idx, this); // YYY

        sc = this_sc;
        sc_selector = Selector.open();

    }

    public String get_transp_caller() { return transp_caller; }

    public String get_transp_user() { return ""; }

    public String get_transp_agent() { return ""; }

    public String get_transp_title() { return "tcp"; }

    private void SetTerminate(String reason) {

        synchronized(terminating_reason) {
            is_terminating = true;
            if (terminating_reason.isEmpty()) terminating_reason = reason;
            //  WaitObj2.notify();
        }

    }

    private boolean MustShutdownSrvLink() {

        //synchronized(WaitObj2) { 
        return is_terminating; 
        //}

    }

    public void ShutdownSrvLink(String reason) {

        if (null == reason) reason = ""; // YYY
        if (reason.isEmpty()) reason = "Empty disconnect reason: " + Tum3Util.getStackTraceAuto(); // YYY
        SetTerminate(reason);

    }

    private void Tick() throws Exception {

        sLink.ClientReaderTick(SrvLink.THRD_INTERNAL, this);

    }

    public void WakeupMain() {

        // Note: unlike notify(), the wakeup() is statefull,
        //  so that in case called with no select() yet waiting,
        //  it will cause next select() to return immediately.
        // This creates potential risk of runaway loop.

        try { // YYY Note. Sometimes sc_selector might be null (already or yet)
            if (!is_terminating) // YYY Note. This check is not synchronized, so it might ocasionally miss. In such case exception will be caught anyway.
                sc_selector.wakeup();
        } catch (Exception ignored) { }
        //String tmp_dbg_ops = "<" + sLink.my_dbg_serial + ":?>";
        //System.out.print(tmp_dbg_ops);

    }

    public void AppStopped() {

        SetTerminate("Application is shutting down.");
        interrupt();

    }

    public boolean isOpen() {
        if (is_terminating || (null == sc)) return false;
        return sc.isConnected(); // sess.isOpen();
    }

    private int ReadFromClient() throws IOException {
        int tmpBytesCount = 0;
        int tmp_last_count = 0;
        do {
            try {
                //System.out.println("[aq2j] DEBUG: ReadFromClient() +");
                inpBB.clear();
                tmpBytesCount = sc.read(inpBB);
                if (tmpBytesCount < 0) { // End-of-file sign.
                    tmpBytesCount = 0;
                    inpBB.limit(0);
                    tmp_last_count = 0;
                    throw new IOException("EOF indicated");
                }
                inpBB.position(0);
                inpBB.limit(tmpBytesCount);
                tmp_last_count = tmpBytesCount;
                //System.out.println("[aq2j] DEBUG: ReadFromClient() - (" + tmpBytesCount + ")");
            } catch (IOException e) {
                //System.out.println("[aq2j] DEBUG: ReadFromClient() raised Exception: " + e);
                SetTerminate("Error reading from client: " + e);
                throw e;
            }

            //System.out.print("[" + tmpBytesCount + "]"); Tum3Util.SleepExactly(500);
            if (tmpBytesCount > 0) {
                try {
                    sLink.SendToServer(SrvLink.THRD_INTERNAL, inpBB);
                } catch(Exception e) {
                    ShutdownSrvLink("Exception in LinkMgrTcp: " + e);
                }
            }
        } while (tmpBytesCount > 0);
        return tmp_last_count;
    }

    private byte[] GetBuff() throws Exception {
        if (outBB.remaining() > 0)
            throw new Exception("[aq2j] FATAL: internal error: GetBuff() while not empty yet.");
        return tmpOutputBuff;
    }

    private void SendToClient(int byteCount) throws Exception {

        //dataOutputStream.write(tmpOutputBuff, 0, byteCount);
        //dataOutputStream.flush();

        if (byteCount <= 0) return;
        outBB.clear();
        outBB.limit(byteCount);
        //System.out.println("[aq2j] outBB.limit=" + byteCount);
        WriteOutBB();
        if (outBB.remaining() <= 0) {
            key.interestOps(key_base);
        } else {
            key.interestOps(key_base | SelectionKey.OP_WRITE);
        }
    }

    public int AcceptFrom(OutBuffData src) throws Exception {

        int tmp_filled_count = src.SendToByteArray(GetBuff()); // XXX TODO!!! Optimize away GetBuff().
        SendToClient(tmp_filled_count); // Moved here from below.
        tmp_recheck_sock = true;
        return tmp_filled_count;

    }

    private final void WriteOutBB() throws Exception {
        int tmp_dbg_out = sc.write(outBB);
        //System.out.print("(outBB: " + tmp_dbg_out + " sent," + outBB.remaining() + " remains)");
        //Tum3Util.SleepExactly(50); // debug only!
    }

    //public boolean SingleThread() {
    //  return true;
    //}

    public boolean SupportOOB() {
        return false;
    }

    public void SendToClientAsOOB(String oobMsg) throws Exception {
        Tum3Logger.DoLog(session_producer.getLogPrefixName(), true, "FATAL: internal error: SendToClientAsOOB() is not supported here.");
        throw new Exception("[aq2j] FATAL: internal error: SendToClientAsOOB() is not supported here.");
    }

    public void close() throws Exception {
        ShutdownSrvLink("Closed");
    }

    public boolean WaitForOutputDone(int timeout) throws Exception {
        // Reminder. This is only called from the main thread (THRD_INTERNAL).

        //System.out.print("{" + sLink.my_dbg_serial + ": +WaitForOutputDone}");
        boolean tmp_socket_busy = (TrySendOut(0, true) & INTRL_SOCK_FULL) != 0;

        if (tmp_socket_busy) {
            //System.out.print("{" + sLink.my_dbg_serial + ": busy}");
            if (sc_selector.select(timeout) > 0) {
                //String tmp_dbg_ops = "<" + sLink.my_dbg_serial + ":";
                //if (key.isReadable()) tmp_dbg_ops = tmp_dbg_ops + "r";
                //if (key.isWritable()) tmp_dbg_ops = tmp_dbg_ops + "W";
                //tmp_dbg_ops = tmp_dbg_ops + ">";
                //System.out.print(tmp_dbg_ops);
                sc_selector.selectedKeys().clear();
            }
        }

        //System.out.print("{" + sLink.my_dbg_serial + ": -WaitForOutputDone}");
        return !tmp_socket_busy;

    }

    public void run()
    {
        AppStopHooker.AddHook(this);

        String tmp_new_reason = "";
        try {

            inpWebBuff = new byte[session_producer.CONST_MAX_INP_BUFF_BYTES()];
            inpBB = ByteBuffer.wrap(inpWebBuff);
            tmpOutputBuff = new byte[CONST_TCP_BUFF_SIZE];
            outBB = ByteBuffer.wrap(tmpOutputBuff);
            outBB.limit(0);

            sc.configureBlocking(false);
            sc.setOption(StandardSocketOptions.SO_SNDBUF, CONST_TCP_BUFF_SIZE);
            sc.setOption(StandardSocketOptions.SO_RCVBUF, session_producer.CONST_MAX_INP_BUFF_BYTES());
            key = sc.register(sc_selector, SelectionKey.OP_READ);

            boolean tmp_data_pending = false;

            sLink.DoLink(); // YYY Moved a bit down, closer to "while".

            while (!MustShutdownSrvLink()) {
                int tmp_last_read;
                long tmp_wait_timeout = CONST_TCP_CLIENT_WAKEINTERVAL;
                if (tmp_data_pending) { 
                    tmp_wait_timeout = CONST_TCP_CLIENT_WAKEINTERVAL_MIN;
                    //System.out.print("&");
                }
                if (sc_selector.select(tmp_wait_timeout) > 0) {
                    //String tmp_dbg_ops = "<" + sLink.my_dbg_serial + ":";
                    //if (key.isReadable()) tmp_dbg_ops = tmp_dbg_ops + "r";
                    //if (key.isWritable()) tmp_dbg_ops = tmp_dbg_ops + "W";
                    //tmp_dbg_ops = tmp_dbg_ops + ">";
                    //System.out.print(tmp_dbg_ops);
                    //boolean tmp_readable = key.isReadable();
                    sc_selector.selectedKeys().clear();
                }

                //System.out.print("(Wrk)");

                if (key.isReadable()) {
                    ReadFromClient();
                    //System.out.println("tmp_last_read=" + tmp_last_read);
                }

                Tick();

                tmp_data_pending = (TrySendOut(SelectionKey.OP_READ, false) & INTRL_DATA_FULL) != 0;

            }

        } catch (Exception e) {
            tmp_new_reason = "Exception in LinkMgrTcp: " + Tum3Util.getStackTrace(e);
        }

        String tmp_prev_reason = "";
        synchronized(terminating_reason) {
            tmp_prev_reason = terminating_reason;
        }
        if (tmp_prev_reason.isEmpty() && !tmp_new_reason.isEmpty())
            tmp_prev_reason = tmp_new_reason;

        Tum3Logger.DoLog(session_producer.getLogPrefixName(), false, "Closing communication with tcp*" + transp_caller + " (" + tmp_prev_reason + ")");
        sLink.CancelLink();

        try {
            sc_selector.close();
        } catch (Exception ignored) { }
        sc_selector = null;

        try {
            sc.close();
        } catch (Exception ignored) { }
        sc = null;

        AppStopHooker.RemoveHook(this);
    }

    private int TrySendOut(int _key_base, boolean hurry) throws Exception {
        // Reminder. This is only called from the main thread (THRD_INTERNAL).
        // Reminder. Result values: INTRL_SOCK_FULL signals that data consumer 
        //      (sender) is unable to consume more data at the moment, 
        //      otherwise INTRL_DATA_FULL might signal to skip normal delay 
        //      before continueing, 
        //      0 means standard behaviour.

        //System.out.print("+");
        key_base = _key_base;
        key.interestOps(key_base);
        boolean tmp_data_done = false, tmp_sock_done = true;
        boolean tmp_recheck_data = false;
        long tmp_curr_millis, tmp_begin_millis = System.currentTimeMillis();
        boolean tmp_timeout = false;
        int tmp_loop_counter = 0;
        int tmp_result;
        do {
            //if (hurry) System.out.println("[aq2j] outBB.remaining()=" + outBB.remaining());
            tmp_loop_counter++;
            tmp_recheck_sock = false;
            if (outBB.remaining() > 0) {
                //System.out.print("W");
                WriteOutBB();
                if (outBB.remaining() <= 0) {
                    //if (hurry) System.out.println("[aq2j] Done outBB.");
                    key.interestOps(key_base);
                    tmp_sock_done = true;
                    tmp_recheck_data = true;
                    //tmp_out_wait = false;
                } else {
                    tmp_sock_done = false;
                    //tmp_out_wait = true;
                    key.interestOps(key_base | SelectionKey.OP_WRITE);
                    //if (hurry) System.out.println("[aq2j] Not yet sent outBB, need wait.");
                    //System.out.print("-");
                    tmp_result = INTRL_SOCK_FULL;
                    return tmp_result;
                }
            } else {
                // Reminder: ReadFromServer2()->AcceptFrom()->SendToByteArray(outBB)
                tmp_recheck_data = false;
                tmp_data_done = sLink.ReadFromServer2(SrvLink.THRD_INTERNAL, this, hurry);
                //if (tmp_out_wait) System.out.print("R");
                //else              System.out.print("r");
                //if (hurry) System.out.println("[aq2j] ReadFromServer2 returned " + tmp_out_wait);
            }
            tmp_curr_millis = System.currentTimeMillis();
            tmp_timeout = ((tmp_curr_millis - tmp_begin_millis) >= CONST_SEND_LOOP_LIMIT_MILLIS);
            // We go out of this loop if data for output is empty or buffer is too full.
        } while (((!tmp_data_done || tmp_recheck_data) || (!tmp_sock_done || tmp_recheck_sock)) && !tmp_timeout && !MustShutdownSrvLink());

        // Reminder: because sLink.ReadFromServer2() can consume substantial time (to read data from disk etc.), timeouts here are normal, not error.
        //if (tmp_timeout || (tmp_loop_counter > 200)) 
        //  Tum3Logger.DoLog(session_producer.getLogPrefixName(), true, "LOCKUP WARNING: counter=" + tmp_loop_counter + "; " + Tum3Util.getStackTraceAuto());

        //if (hurry) System.out.println("[aq2j] exiting TrySendOut.");
        //System.out.print("-");
        if (!tmp_sock_done) tmp_result = INTRL_SOCK_FULL; // Note. Seems unnecessary, but just in case.
        else if (!tmp_data_done) tmp_result = INTRL_DATA_FULL;
        else tmp_result = 0;
        return tmp_result;

    }

}
