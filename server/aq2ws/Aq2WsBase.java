/*
 * Copyright 2011-2024 Nikolai Zhubr <zhubr@rambler.ru>
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
package aq2ws;

import java.nio.*;

import javax.websocket.*;


import aq2j.*;


public class Aq2WsBase {

    public final static int INTERCON_TYPE_META = 1, INTERCON_TYPE_BULK = 2; // YYY
    public static final String CONST_INTERNAL_SESS_PRODUCER = "session_producer";

    public abstract static class Aq2WsEndpoint extends Endpoint {

        protected Session sess;
        protected RemoteEndpoint.Basic remoteEndpointBasic;
        protected volatile LinkMgrWeb lmWs = null;
        //protected int db_index = -1;
        //protected String db_name = "?";
        protected SessionProducerWeb session_producer;
        protected volatile boolean is_closing = false;

        public void onMessageText(String message, boolean last) {

            //System.out.println("[DEBUG] Aq2WsEndpoint.onMessageText()");
            try {
                lmWs.ReadOOBMsgFromClient(message);
            } catch (Exception e) {
                if (is_closing) return;
                //e.printStackTrace(); // YYY
                onError(sess, e);
            }
        }

        public void onMessageBinary(ByteBuffer message, boolean last) {

            //System.out.println("[DEBUG] Aq2WsEndpoint.onMessageBinary()");
            try {
                lmWs.ReadFromClient(message);
            } catch (Exception e) {
                if (is_closing) return;
                //e.printStackTrace(); // YYY
                onError(sess, e);
            }
        }

        public void onClose(Session session, CloseReason closeReason) {
            String tmp_reason = "Closed normally";
            if (closeReason.getCloseCode() != CloseReason.CloseCodes.NORMAL_CLOSURE) {
                //System.out.println("[DEBUG] Aq2WsEndpoint.onClose(): code=" + closeReason.getCloseCode() + " msg=" + closeReason.getReasonPhrase());
                tmp_reason = closeReason.getReasonPhrase();
            }
            is_closing = true;
            lmWs.ShutdownSrvLink(tmp_reason);
            notifySessionEnded(session, tmp_reason); // YYY
        }

        public void onError(Session session, java.lang.Throwable throwable) {
            //System.out.println("[DEBUG] Aq2WsEndpoint.onError(): <" + throwable.toString() + ">");
            is_closing = true;
            lmWs.ShutdownSrvLink(throwable.toString());
            notifySessionEnded(session, throwable.toString()); // YYY
        }

        protected void notifySessionEnded(Session _session, String _the_reason) {
        }
    }

    public static class ClientWriterWs implements ClientWriterRaw {

        private Session sess;
        private RemoteEndpoint.Basic remoteEndpointBasic;

        public ClientWriterWs(Session session) {
            sess = session;
            remoteEndpointBasic = session.getBasicRemote();
        }

        public void SendToClient(ByteBuffer msgBb, int byteCount) throws Exception {
            msgBb.clear();
            msgBb.limit(byteCount);
            remoteEndpointBasic.sendBinary(msgBb, true);
        }

        public void SendToClientAsOOB(String oobMsg) throws Exception { 
            remoteEndpointBasic.sendText(oobMsg, true);
        }

        public void close() throws Exception {
            sess.close();
        }

        public boolean isOpen() {
            return sess.isOpen();
        }
    }

    public static class WsMessageHandlerText
    implements MessageHandler.Partial<String> {

        private Aq2WsEndpoint owner;

        public WsMessageHandlerText(Aq2WsEndpoint MessageHandler) {
            owner = MessageHandler;
        }

        @Override
        public void onMessage(String message, boolean last) {
            owner.onMessageText(message, last);
        }
    }

    public static class WsMessageHandlerBinary
    implements MessageHandler.Partial<ByteBuffer> {

        private Aq2WsEndpoint owner;

        public WsMessageHandlerBinary(Aq2WsEndpoint MessageHandler) {
            owner = MessageHandler;
        }

        @Override
        public void onMessage(ByteBuffer message, boolean last) {

            // Note. With tomcat 7 the passed ByteBuffer message is safe for 
            //  using also after this method exits, because it is an explicite 
            //  unique copy, see WsFrameBase.processDataBinary()). However,
            // https://docs.oracle.com/javaee/7/api/javax/websocket/OnMessage.html
            // advices definitely against it.

            owner.onMessageBinary(message, last);
        }
    }

}
