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

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import aq2db.*;

class TumConnSocket implements TumConnIntf {

    private Socket myConn;

    public TumConnSocket(Socket thisConn) {

        myConn = thisConn;

    }

    public String CallerName() {
        return myConn.getInetAddress().toString();
    }

    public OutputStream getOutputStream() throws IOException {
        return myConn.getOutputStream();
    }

    public InputStream getInputStream() throws IOException {
        return myConn.getInputStream();
    }

}

public class TumServTCP extends Thread implements AppStopHook {

    private volatile boolean TerminateRequested = false;
    private int CONST_TCP_DEF_LISTEN_PORT = 1000;
    private String CONST_TCP_DEF_LISTEN_IP = "";
    private int CONST_TCP_BUFF_SIZE = 128;
    private int CONST_TCP_LISTENER_WAKEINTERVAL = 1000;
    private ServerSocketChannel ssc;
    private Selector listen_selector;
    private SelectionKey key;
    private SessionProducerTcp session_producer;


    public TumServTCP(SessionProducerTcp _session_producer) {

        session_producer = _session_producer; // new SessionProducerStdTcp(_db_idx); // YYY
        session_producer.CONST_TCP_BUFF_SIZE(CONST_TCP_BUFF_SIZE);
        CONST_TCP_DEF_LISTEN_PORT = session_producer.CONST_TCP_DEF_LISTEN_PORT(CONST_TCP_DEF_LISTEN_PORT);
        CONST_TCP_DEF_LISTEN_IP = session_producer.CONST_TCP_DEF_LISTEN_IP(CONST_TCP_DEF_LISTEN_IP);

        setDaemon(true);
        start();

    }

    private void StartListening() throws Exception {

        Tum3Logger.DoLog(session_producer.getLogPrefixName(), false, "tcp listening at <" + CONST_TCP_DEF_LISTEN_IP + ":" + CONST_TCP_DEF_LISTEN_PORT + ">");
        Tum3Logger.DoLog(session_producer.getLogPrefixName(), false, "CONST_TCP_BUFF_SIZE=" + CONST_TCP_BUFF_SIZE);

        InetSocketAddress tmp_address;
        if (CONST_TCP_DEF_LISTEN_IP.isEmpty())
            tmp_address = new InetSocketAddress(CONST_TCP_DEF_LISTEN_PORT);
        else
            tmp_address = new InetSocketAddress(Tum3IpUtil.StrToInetAddress(CONST_TCP_DEF_LISTEN_IP), CONST_TCP_DEF_LISTEN_PORT);

        try {
            ssc = ServerSocketChannel.open();
            ssc.socket().bind(tmp_address);
            ssc.configureBlocking(false);
            listen_selector = Selector.open();
            key = ssc.register(listen_selector, SelectionKey.OP_ACCEPT);
            do {
                if (listen_selector.select(CONST_TCP_LISTENER_WAKEINTERVAL) > 0) {
                    listen_selector.selectedKeys().clear();
                    String tmp_caller = "(unknown)";
                    //tmp_caller = newConn.getInetAddress().toString();
                    SocketChannel sc = ssc.accept();
                    tmp_caller = (new StringBuilder(sc.socket().getInetAddress().getHostAddress())).append(":").append(sc.socket().getPort()).toString();
                    //System.out.println("Connection received from " + tmp_caller);
                    //sc.close();
                    try {
                        new LinkMgrTcp(session_producer, sc).start();
                    } catch (Exception e) {
                        Tum3Logger.DoLog(session_producer.getLogPrefixName(), true, "Failed to create LinkMgrTcp: " + e);
                        sc.close();
                    }
                } else {
                    //System.out.println("Listener tick.");
                }
            } while (!TerminateRequested);
            Tum3Logger.DoLog(session_producer.getLogPrefixName(), false, "Listening stopping normally...");
        } catch(Exception e){
            Tum3Logger.DoLog(session_producer.getLogPrefixName(), true, "TCP listening failed with: " + e);
        }
        try {
            //mainSocket.close();
            listen_selector.close();
        } catch(Exception e){ }
        listen_selector = null;
        try {
            ssc.close();
        } catch(Exception e){}
        ssc = null;
    }

    public void run() {

        AppStopHooker.AddHook(this);

        try {
            while (!TerminateRequested) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) { }
                StartListening();
            }
        } catch (Exception e) {
            Tum3Logger.DoLog(session_producer.getLogPrefixName(), true, "TCP listening will not be possible: " + e);
        }

        AppStopHooker.RemoveHook(this);

    }

    public void AppStopped() {

        TerminateRequested = true;

    }

}
