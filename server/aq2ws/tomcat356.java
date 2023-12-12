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
package aq2ws;

import java.io.*;
import java.nio.*;

import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Properties;

import java.net.URI;

import javax.servlet.http.*;
import javax.servlet.*;

import javax.websocket.*;
import javax.websocket.server.*;

//import java.util.Base64;
import org.apache.tomcat.util.codec.binary.Base64; // Could probably switch to regular java.util.Base64 if necessary.

import org.apache.tomcat.websocket.server.Constants; // Not strictly necessary, only used for prop names.
import org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator; // In theory should not be necessary, but using tomcat's default as a base seems good.

import javax.management.*;
import org.apache.catalina.Server;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;


import aq2db.*;
import aq2j.*;


public class tomcat356 extends GenericServlet implements ServerApplicationConfig, LifecycleListener {

    private static final String CONST_TUM3_DBURL_PARAM = "dburl-";
    private static final String CONST_TUM3_INTR_META_PARAM = "intercon_meta-";
    private static final String CONST_TUM3_INTR_BULK_PARAM = "intercon_bulk-";
    private static final String CONST_TUM3_ENABLE_TCP_PARAM = "enabletcp";
    private static final String CONST_INTERNAL_PAR_PASSING = "http_headers";
    private static final String CONST_INTERNAL_SESS_PRODUCER = "session_producer";
    private static final String CONST_INTERNAL_OWNER_INITIATOR = "owner_initiator";

    private final static int CONST_WS_BUFF_SIZE_default = 1;


    private static class CustomServerConfigurator extends DefaultServerEndpointConfigurator {

        private final static String[] wanted_headers = {"x-real-ip", "x-real-port", "x-real-user", "user-agent"};
        //private int db_index;
        private SessionProducerWeb session_producer;

        public CustomServerConfigurator(SessionProducerWeb _session_producer) {

            //db_index = _db_idx;
            session_producer = _session_producer;

        }

        @Override
        public void modifyHandshake(ServerEndpointConfig conf,
                HandshakeRequest req,
                HandshakeResponse resp) {
            Map<String, List<String>> tmp_http_headers = req.getHeaders();
            //System.out.println("[aq2j] DEBUG: HTTP headers=<" + req.getHeaders().toString() + ">");
            // [aq2j] DEBUG: HTTP headers=<{
            // x-real-ip=[192.168.0.91], 
            // x-real-port=[1420], 
            // x-real-user=[zhubr], 
            // authorization=[Basic emh1YnI6OTU2Mjc4MjM=], 
            // upgrade=[websocket], 
            // host=[127.0.0.1:8080], 
            // connection=[upgrade], 
            // sec-websocket-key=[bmYhPkpqYGpUTEJLaE5rMw==], 
            // sec-websocket-version=[13], 
            // user-agent=[LOOK4/1.0]
            // }>

            /* Reminder. In order to have such headers, do not forget to add to nginx.conf:

            proxy_set_header X-Real-ip $remote_addr;
            proxy_set_header X-Real-port $remote_port;
            proxy_set_header X-Real-user $remote_user;

             */
            LinkMgrWeb.SelectedHttpHeaders tmp_wanted_headers = new LinkMgrWeb.SelectedHttpHeaders();
            for (String tmp_st: wanted_headers)
                if (tmp_http_headers.containsKey(tmp_st)) try {
                    tmp_wanted_headers.put(tmp_st, tmp_http_headers.get(tmp_st).get(0));
                } catch (Exception ignored) {}
            conf.getUserProperties().put(CONST_INTERNAL_PAR_PASSING, tmp_wanted_headers);
            conf.getUserProperties().put(CONST_INTERNAL_SESS_PRODUCER, session_producer);
        }

    }

    private static class CustomClientConfigurator extends ClientEndpointConfig.Configurator {

        private String basic_auth;

        public CustomClientConfigurator(String _basic_auth) {

            basic_auth = _basic_auth;

        }

        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put("Authorization", Arrays.asList(basic_auth));
        }

    }

    private static class WsMessageHandlerText
    implements MessageHandler.Partial<String> {

        private Aq2WsEndpoint owner;

        private WsMessageHandlerText(Aq2WsEndpoint MessageHandler) {
            owner = MessageHandler;
        }

        @Override
        public void onMessage(String message, boolean last) {
            owner.onMessageText(message, last);
        }
    }

    private static class WsMessageHandlerBinary
    implements MessageHandler.Partial<ByteBuffer> {

        private Aq2WsEndpoint owner;

        private WsMessageHandlerBinary(Aq2WsEndpoint MessageHandler) {
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

    private static class ClientWriterWs implements ClientWriterRaw {

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

    private abstract static class Aq2WsEndpoint extends Endpoint {

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
                e.printStackTrace();
                onError(sess, e);
            }
        }

        public void onMessageBinary(ByteBuffer message, boolean last) {

            //System.out.println("[DEBUG] Aq2WsEndpoint.onMessageBinary()");
            try {
                lmWs.ReadFromClient(message);
            } catch (Exception e) {
                if (is_closing) return;
                e.printStackTrace();
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

    // Reminder! This class needs to be public because it is instantiated outside.
    public static class Aq2WsEndpointServer extends Aq2WsEndpoint {

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig) {
            //String tmp_val_str = "";
            //List<String> tmp_val_list = session.getRequestParameterMap().get("db"); // 192.168.0.99/dbtsk/wsconn?db=t308
            //if (null != tmp_val_list) tmp_val_str = tmp_val_list.get(0);
            //System.out.println("[DEBUG] Aq2WsEndpoint.onOpen(): db=<" + tmp_val_str + ">");
            LinkMgrWeb.SelectedHttpHeaders tmp_wanted_headers = (LinkMgrWeb.SelectedHttpHeaders) endpointConfig.getUserProperties().get(CONST_INTERNAL_PAR_PASSING);
            session_producer = (SessionProducerWeb)endpointConfig.getUserProperties().get(CONST_INTERNAL_SESS_PRODUCER);
            //db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
            //System.out.println("[DEBUG] Aq2WsEndpoint.onOpen(): db_index=<" + db_index + ">");
            sess = session;
            sess.getUserProperties().put(tomcat_x.BLOCKING_SEND_TIMEOUT_PROPERTY, (long)(-1));
            remoteEndpointBasic = session.getBasicRemote();
            lmWs = new LinkMgrWeb(session_producer, new ClientWriterWs(session), tmp_wanted_headers);
            session.addMessageHandler(new WsMessageHandlerText(this));
            session.addMessageHandler(new WsMessageHandlerBinary(this));
        }

    }

    // Reminder! This class needs to be public because it is instantiated outside.
    public static class Aq2WsEndpointClient extends Aq2WsEndpoint {

        private WsInterconInitiator owner_initiator;

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig) {
            //session.getUserProperties().put("endpoint", this); // do we really need this?

            owner_initiator = (WsInterconInitiator)endpointConfig.getUserProperties().get(CONST_INTERNAL_OWNER_INITIATOR);
            session_producer = (SessionProducerWeb)endpointConfig.getUserProperties().get(CONST_INTERNAL_SESS_PRODUCER);
            sess = session;
            sess.getUserProperties().put(tomcat_x.BLOCKING_SEND_TIMEOUT_PROPERTY, (long)(-1));
            remoteEndpointBasic = session.getBasicRemote();
            lmWs = new LinkMgrWebClient(session_producer, new ClientWriterWs(session));
            session.addMessageHandler(new WsMessageHandlerText(this));
            session.addMessageHandler(new WsMessageHandlerBinary(this));
        }

        @Override
        protected void notifySessionEnded(Session _session, String _the_reason) {

            owner_initiator.RawSessionEnded(_session, _the_reason);

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
                    CONST_INTERNAL_SESS_PRODUCER, session_producer);

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

    public void lifecycleEvent(LifecycleEvent event) {
        //System.out.println("[DEBUG] +lifecycleEvent: <" + event.getType() + ">");
        if (Lifecycle. /* CONFIGURE_STOP_EVENT */ STOP_EVENT.equals(event.getType())) {
            //System.out.println("[DEBUG] lifecycleEvent: ServiceShutdown");
            AppStopHooker.ProcessAppStop();
        }
        //System.out.println("[DEBUG] -lifecycleEvent: <" + event.getType() + ">");
    }

    private static abstract class TempUplinkCreator {

        protected abstract String _label();

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

    private static class TempUplinkCreatorMetaCli extends TempUplinkCreator {

        protected String _label() { return "meta"; }

        public SessionProducerWeb CreateProducer(int _i, String _username, String _password, String _serv_addr) {
            return new SessionProducerWebMetaUpl(_i, _username, _password, _serv_addr);
        }

        public void RegisterInitiator(int _i, InterconInitiator _initiator) {
            Tum3Db.getDbInstance(_i).setUplink(_initiator);
        }

    }

    private static class TempUplinkCreatorBulkCli extends TempUplinkCreator {

        protected String _label() { return "bulk"; }

        public SessionProducerWeb CreateProducer(int _i, String _username, String _password, String _serv_addr) {
            return new SessionProducerWebBulkCli(_i, _username, _password, _serv_addr);
        }

        public void RegisterInitiator(int _i, InterconInitiator _initiator) {
            Tum3Db.getDbInstance(_i).setUpBulk(_initiator);
        }

    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        Tum3cfg glb_cfg = Tum3cfg.getGlbInstance();

        try {
            MBeanServer mBeanServer = MBeanServerFactory.findMBeanServer(null).get(0);
            ObjectName name = new ObjectName("Catalina", "type", "Server");
            Server server = (Server) mBeanServer.getAttribute(name, "managedResource");
            server.addLifecycleListener(this);
            AppStopHooker.setAvailable();
            AppStopHooker.setTimeout(glb_cfg.getShutdownTimeout());
        } catch (Exception e) {
            Tum3Logger.DoLogGlb(true, "WARNING: lifecycleListener could not be added: " + e);
        }

        ServletContext servletContext = config.getServletContext();
        ServerContainer serverContainer = (ServerContainer) servletContext.getAttribute(
                Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE
                );
        //servletContext.addFilter(simpleFilterName, NewFilter.class);

        //System.out.println("[DEBUG] tomcat356.init() begin...");

        if (null == serverContainer) {
            Tum3Logger.DoLogGlb(true, "WARNING: tomcat356.init() serverContainer is null, can not create endpoint.");
            //throw new ServletException("Failed to obtain serverContainer object");
        } else {
            //System.out.println("[DEBUG] tomcat356.init() got serverContainer!");
            for (int tmp_i = 0; tmp_i < glb_cfg.getDbCount(); tmp_i++) {
                String tmp_db_name = glb_cfg.getDbName(tmp_i);
                if (tmp_db_name != null) if (!tmp_db_name.isEmpty()) {
                    if (glb_cfg.getDbWebEnabled(tmp_i)) {
                        //System.out.println("[DEBUG] " + tmp_i + ": tmp_db_name=<" + tmp_db_name + ">");
                        String tmp_url = getInitParameter(CONST_TUM3_DBURL_PARAM + tmp_db_name);
                        if (null == tmp_url) tmp_url = "";
                        if (tmp_url.isEmpty()) tmp_url = "/" + tmp_db_name;
                        //System.out.println("[DEBUG] tmp_db_name=<" + tmp_db_name + ">, tmp_url=<" + tmp_url + ">");
                        try {
                            ServerEndpointConfig.Builder tmpBuilder = ServerEndpointConfig.Builder.create(Aq2WsEndpointServer.class, tmp_url);
                            tmpBuilder.configurator(new CustomServerConfigurator(new SessionProducerWebStd(tmp_i)));
                            serverContainer.addEndpoint(tmpBuilder.build());
                            Tum3Logger.DoLog(tmp_db_name, false, "Info: created WS user endpoint <" + tmp_url + "> for database <" + tmp_db_name + ">");
                        } catch (DeploymentException e) {
                            throw new ServletException(e.toString());
                        }
                    }
                    if (glb_cfg.getDbDownlinkEnabled(tmp_i)) {
                        String tmp_url = getInitParameter(CONST_TUM3_INTR_META_PARAM + tmp_db_name);
                        if (null == tmp_url) tmp_url = "";
                        if (!tmp_url.isEmpty()) {
                            //System.out.println("[DEBUG] meta tmp_db_name=<" + tmp_db_name + ">, tmp_url=<" + tmp_url + ">");
                            try {
                                ServerEndpointConfig.Builder tmpBuilder = ServerEndpointConfig.Builder.create(Aq2WsEndpointServer.class, tmp_url);
                                tmpBuilder.configurator(new CustomServerConfigurator(new SessionProducerWebMeta(tmp_i)));
                                serverContainer.addEndpoint(tmpBuilder.build());
                                Tum3Logger.DoLog(tmp_db_name, false, "Info: created WS meta intercon endpoint <" + tmp_url + "> for database <" + tmp_db_name + ">");
                            } catch (DeploymentException e) {
                                throw new ServletException(e.toString());
                            }
                        }
                    }
                    if (glb_cfg.getDbDownBulkEnabled(tmp_i)) {
                        String tmp_url = getInitParameter(CONST_TUM3_INTR_BULK_PARAM + tmp_db_name);
                        if (null == tmp_url) tmp_url = "";
                        if (!tmp_url.isEmpty()) {
                            //System.out.println("[DEBUG] bulk tmp_db_name=<" + tmp_db_name + ">, tmp_url=<" + tmp_url + ">");
                            try {
                                ServerEndpointConfig.Builder tmpBuilder = ServerEndpointConfig.Builder.create(Aq2WsEndpointServer.class, tmp_url);
                                tmpBuilder.configurator(new CustomServerConfigurator(new SessionProducerWebBulkSrv(tmp_i)));
                                serverContainer.addEndpoint(tmpBuilder.build());
                                Tum3Logger.DoLog(tmp_db_name, false, "Info: created WS bulk intercon endpoint <" + tmp_url + "> for database <" + tmp_db_name + ">");
                            } catch (DeploymentException e) {
                                throw new ServletException(e.toString());
                            }
                        }
                    }
                    if (glb_cfg.getDbUplinkEnabled(tmp_i))
                        new TempUplinkCreatorMetaCli().DoCreate(tmp_i, tmp_db_name, Tum3cfg.TUM3_CFG_uplink_serv_addr); // YYY
                    if (glb_cfg.getDbUpBulkEnabled(tmp_i))
                        new TempUplinkCreatorBulkCli().DoCreate(tmp_i, tmp_db_name, Tum3cfg.TUM3_CFG_upbulk_serv_addr); // YYY
                }
            }
        }

        String tmp_tcp = getInitParameter(CONST_TUM3_ENABLE_TCP_PARAM);
        if (tmp_tcp != null) if (tmp_tcp.equals("1")) {
            Tum3Logger.DoLogGlb(false, "DEBUG: tomcat356.init() starting raw tcp...");
            for (int tmp_i = 0; tmp_i < glb_cfg.getDbCount(); tmp_i++) if (glb_cfg.getDbTcpEnabled(tmp_i))
                new TumServTCP(new SessionProducerTcpStd(tmp_i)); // YYY
        }

        //System.out.println("[DEBUG] tomcat356.init() end.");
    }

    public void destroy() {
        //System.out.println("[DEBUG] +tomcat356.destroy()");
        //try { Thread.sleep(1000); } catch (Exception ignored) {}
        //System.out.println("[DEBUG] -tomcat356.destroy()");
    }

    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        //System.out.println("[DEBUG] tomcat356.service()");
    }

    public Set<ServerEndpointConfig> getEndpointConfigs(
            Set<Class<? extends Endpoint>> scanned) {
        return new HashSet<>();
    }

    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
        Set<Class<?>> results = new HashSet<>();
        return results;
    }

}
