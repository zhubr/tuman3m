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

import java.io.*;

import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.Properties;

import java.net.URI;

import javax.websocket.*;

//import java.util.Base64;
import org.apache.tomcat.util.codec.binary.Base64; // Could probably switch to regular java.util.Base64 if necessary.


import aq2db.*;
import aq2j.*;


public class InterconClnt_x {

    private static final String CONST_INTERNAL_OWNER_INITIATOR = "owner_initiator";

    // Reminder! This class needs to be public because it is instantiated outside.
    public static class Aq2WsEndpointClient extends Aq2WsBase.Aq2WsEndpoint {

        private WsInterconInitiator owner_initiator;

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig) {
            //session.getUserProperties().put("endpoint", this); // do we really need this?

            owner_initiator = (WsInterconInitiator)endpointConfig.getUserProperties().get(CONST_INTERNAL_OWNER_INITIATOR);
            session_producer = (SessionProducerWeb)endpointConfig.getUserProperties().get(Aq2WsBase.CONST_INTERNAL_SESS_PRODUCER);
            sess = session;
            sess.getUserProperties().put(tomcat_x.BLOCKING_SEND_TIMEOUT_PROPERTY, (long)(-1));
            remoteEndpointBasic = session.getBasicRemote();
            lmWs = new LinkMgrWebClient(session_producer, new Aq2WsBase.ClientWriterWs(session));
            session.addMessageHandler(new Aq2WsBase.WsMessageHandlerText(this));
            session.addMessageHandler(new Aq2WsBase.WsMessageHandlerBinary(this));
        }

        @Override
        protected void notifySessionEnded(Session _session, String _the_reason) {

            owner_initiator.RawSessionEnded(_session, _the_reason);

        }
    }

    public static class CustomClientConfigurator extends ClientEndpointConfig.Configurator {

        private String basic_auth;

        public CustomClientConfigurator(String _basic_auth) {

            basic_auth = _basic_auth;

        }

        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put("Authorization", Arrays.asList(basic_auth));
        }

    }

    private static class WsInterconInitiator implements InterconInitiator {

        private final static int CONST_WS_UPLINK_CONN_TIMEOUT_def = 3000;

        private String destServer = "", destTrustedCerts = "", userName = "", userPassword = "";
        private URI destURI;
        private ClientEndpointConfig clientEndpointConfig;
        private WebSocketContainer wsContainer;
        private SessionProducerWeb session_producer;

        private volatile byte conn_status = csDisconnected;
        private volatile Session session = null;
        private volatile String disconn_reason_msg = "";


        public WsInterconInitiator(String _destServer, String _destTrustedCerts, String _userName, String _userPassword, int _timeout, SessionProducerWeb _session_producer) throws Exception {

            destServer = _destServer;
            destTrustedCerts = _destTrustedCerts;
            userName = _userName;
            userPassword = _userPassword;

            session_producer = _session_producer;

            String basic_auth = " Basic " + Base64.encodeBase64String((userName + ":" + userPassword).getBytes());

            wsContainer = ContainerProvider.getWebSocketContainer();

            clientEndpointConfig = ClientEndpointConfig.Builder.create().configurator(
                new CustomClientConfigurator(basic_auth)
            ).build();

            clientEndpointConfig.getUserProperties().put(
                    Aq2WsBase.CONST_INTERNAL_SESS_PRODUCER, session_producer);

            clientEndpointConfig.getUserProperties().put(
                    CONST_INTERNAL_OWNER_INITIATOR, this);

            clientEndpointConfig.getUserProperties().put(
                tomcat_x.SSL_TRUSTSTORE_PROPERTY, destTrustedCerts);

            clientEndpointConfig.getUserProperties().put(
                tomcat_x.IO_TIMEOUT_MS_PROPERTY, "" + _timeout);

            if (!tomcat_x.WS_AUTHENTICATION_USER_NAME.isEmpty()) 
            clientEndpointConfig.getUserProperties().put(
                tomcat_x.WS_AUTHENTICATION_USER_NAME, userName);

            if (!tomcat_x.WS_AUTHENTICATION_PASSWORD.isEmpty()) 
              clientEndpointConfig.getUserProperties().put(
                tomcat_x.WS_AUTHENTICATION_PASSWORD, userPassword);

            destURI = new URI("wss://" + destServer);
        }

        private synchronized void setStatus(byte _new_status, String _the_reason) throws Exception {

            if (((_new_status == csConnecting) && (conn_status != csDisconnected))
             || ((_new_status == csConnected) && (conn_status != csConnecting))) 
                throw new Exception("Invalid state for requested operation");
            conn_status = _new_status;
            if ((_new_status == csDisconnected) && disconn_reason_msg.isEmpty()) disconn_reason_msg = _the_reason;
            else disconn_reason_msg = "";
        }

        public byte getConnStatus() {

            return conn_status; // Note. Could also consult session.isOpen() ?

        }

        public String getDisconnReason() {

            return disconn_reason_msg;

        }

        public boolean ConnectToServer() throws Exception {

            setStatus(csConnecting, "");
            try {
                //Tum3Logger.DoLogGlb(true, "[DEBUG] wsContainer=" + wsContainer + " clientEndpointConfig=" + clientEndpointConfig + " destURI=" + destURI);
                session = wsContainer.connectToServer(
                    Aq2WsEndpointClient.class,
                    clientEndpointConfig,
                    destURI
                );
            } catch (Exception e) {

                //Tum3Logger.DoLogGlb(true, "[DEBUG] connectToServer ex backtrace: " + Tum3Util.getStackTrace(e));
                Throwable tmp_e = e;
                String tmp_msg = tmp_e.getMessage();
                if (null == tmp_msg) tmp_msg = tmp_e.toString();
                if (null != e.getCause()) {
                    tmp_e = e.getCause();
                    if (null == tmp_e.getMessage()) tmp_msg = tmp_e.toString();
                    else tmp_msg = tmp_e.getMessage();
                }

                setStatus(csDisconnected, tmp_msg); // YYY
                return false; //throw e;
            }
            setStatus(csConnected, "");
            return true;
        }

        public void RawSessionEnded(Session _session, String _the_reason) {
            try {
              setStatus(csDisconnected, _the_reason);
            } catch (Exception ignored) { } // Note: this should never happen.
        }

    }

    public static abstract class TempUplinkCreator {

        protected abstract int CreatorType(); // YYY
        protected final String _label() { return CreatorType() == Aq2WsBase.INTERCON_TYPE_META ? "meta" : "bulk"; } // YYY

        public abstract SessionProducerWeb CreateProducer(int _i, String _username, String _password, String _serv_addr);

        public abstract void RegisterInitiator(int _i, InterconInitiator _initiator);

        public void DoCreate(int tmp_i, String tmp_db_name, String _serv_addr_param) {
            try {
              String tmp_serv_addr = Tum3cfg.getParValue(tmp_i, false, _serv_addr_param);
              String tmp_trusted_keys = Tum3cfg.getParValue(tmp_i, false, Tum3cfg.TUM3_CFG_uplink_trusted_keys); //  "/opt/aq2j/tum3trust.jks"
              String tmp_cred_filename = Tum3cfg.getParValue(tmp_i, false, Tum3cfg.TUM3_CFG_uplink_credentials); //  "/opt/tum3/cred_main.properties"
              int tmp_connect_timeout = Tum3cfg.getIntValue(tmp_i, false, Tum3cfg.TUM3_CFG_uplink_connect_timeout, WsInterconInitiator.CONST_WS_UPLINK_CONN_TIMEOUT_def); // 3000

              if (!tmp_serv_addr.isEmpty() && !tmp_trusted_keys.isEmpty() && !tmp_cred_filename.isEmpty()) {
                  Properties tmp_props = new Properties();
                  tmp_props.load(new FileInputStream(tmp_cred_filename));
                  String tmp_username = tmp_props.getProperty(Tum3cfg.TUM3_CFG_uplink_username, "").trim();
                  String tmp_password = tmp_props.getProperty(Tum3cfg.TUM3_CFG_uplink_password, "").trim();

                  InterconInitiator tmp_initiator = new WsInterconInitiator(
                      tmp_serv_addr, tmp_trusted_keys,
                      tmp_username, tmp_password,
                      tmp_connect_timeout,
                      CreateProducer(tmp_i, tmp_username, tmp_password, tmp_serv_addr)
                  );
                  RegisterInitiator(tmp_i, tmp_initiator);
                  Tum3Logger.DoLog(tmp_db_name, true, "Info: creating " + _label() + " uplink caller for " + tmp_serv_addr);
              } else {
                  Tum3Logger.DoLog(tmp_db_name, true, "Warning: " + _label() + " uplink configuration looks incomplete, skipping.");
              }
            } catch (Exception e) {
                  Tum3Logger.DoLog(tmp_db_name, true, "IMPORTANT: failed to create WS " + _label() + " uplink endpoint, error: " + e.toString());
            }
        }
    }
}
