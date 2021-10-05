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
package aq2ws;

import java.io.*;
import java.nio.*;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.List;

import javax.servlet.http.*;
import javax.servlet.*;

import javax.websocket.*;
import javax.websocket.server.*;

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

    private static final String BLOCKING_SEND_TIMEOUT_PROPERTY =
            "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT"; // Note: this is borrowed from tomcat 9.

    //private static final String CONST_TUM3_WS_URL_PARAM = "wsurl"; // removed, not used anymore.
    private static final String CONST_TUM3_DBURL_PARAM = "dburl-";
    private static final String CONST_TUM3_ENABLE_TCP_PARAM = "enabletcp";
    private static final String CONST_INTERNAL_PAR_PASSING = "http_headers";
    private static final String CONST_INTERNAL_DB_INDEX = "db_index";

    private final static int CONST_WS_BUFF_SIZE_default = 1;


    public static class CustomConfigurator extends DefaultServerEndpointConfigurator {

        private final static String[] wanted_headers = {"x-real-ip", "x-real-port", "x-real-user", "user-agent"};
        private int db_index;

        public CustomConfigurator(int _db_idx) {

            db_index = _db_idx;

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
            conf.getUserProperties().put(CONST_INTERNAL_DB_INDEX, new Integer(db_index));
        }

    }

    private static class EchoMessageHandlerText
    implements MessageHandler.Partial<String> {

        private Aq2WsEndpoint owner;

        private EchoMessageHandlerText(Aq2WsEndpoint MessageHandler) {
            owner = MessageHandler;
        }

        @Override
        public void onMessage(String message, boolean last) {
            owner.onMessageText(message, last);
        }
    }

    private static class EchoMessageHandlerBinary
    implements MessageHandler.Partial<ByteBuffer> {

        private Aq2WsEndpoint owner;

        private EchoMessageHandlerBinary(Aq2WsEndpoint MessageHandler) {
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

    public static class Aq2WsEndpoint extends Endpoint {

        private Object InBinDataLock = new Object();
        private volatile long PrevThrdId = 0;
        private volatile boolean FirstCall = true;
        private volatile boolean warning_done = false;
        private Session sess;
        private RemoteEndpoint.Basic remoteEndpointBasic;
        private volatile LinkMgrWeb lmWs = null;
        private int db_index = -1;
        private String db_name = "?";
        private volatile boolean is_closing = false;

        public void onOpen(Session session, EndpointConfig endpointConfig) {
            //String tmp_val_str = "";
            //List<String> tmp_val_list = session.getRequestParameterMap().get("db"); // 192.168.0.99/dbtsk/wsconn?db=t308
            //if (null != tmp_val_list) tmp_val_str = tmp_val_list.get(0);
            //System.out.println("[DEBUG] Aq2WsEndpoint.onOpen(): db=<" + tmp_val_str + ">");
            LinkMgrWeb.SelectedHttpHeaders tmp_wanted_headers = (LinkMgrWeb.SelectedHttpHeaders) endpointConfig.getUserProperties().get(CONST_INTERNAL_PAR_PASSING);
            db_index = ((Integer)endpointConfig.getUserProperties().get(CONST_INTERNAL_DB_INDEX)).intValue();
            db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
            //System.out.println("[DEBUG] Aq2WsEndpoint.onOpen(): db_index=<" + db_index + ">");
            sess = session;
            sess.getUserProperties().put(BLOCKING_SEND_TIMEOUT_PROPERTY, (long)(-1));
            remoteEndpointBasic = session.getBasicRemote();
            lmWs = new LinkMgrWeb(db_index, new ClientWriterWs(session), tmp_wanted_headers);
            session.addMessageHandler(new EchoMessageHandlerText(this));
            session.addMessageHandler(new EchoMessageHandlerBinary(this));
        }

        public void onMessageText(String message, boolean last) {

            //System.out.println("[DEBUG] Aq2WsEndpoint.onMessageText()");
            synchronized(InBinDataLock) {
                long tmp_thrd_id = Thread.currentThread().getId();
                if (FirstCall) FirstCall = false;
                else {
                    if (!warning_done)
                        if (tmp_thrd_id != PrevThrdId) {
                            warning_done = true;
                            Tum3Logger.DoLog(db_name, true, "IMPORTANT: Please use protocol=org.apache.coyote.http11.Http11Protocol, otherwise websocket may not work properly.");
                        }
                }
                PrevThrdId = tmp_thrd_id;
                try {
                    lmWs.ReadOOBMsgFromClient(message);
                } catch (Exception e) {
                    if (is_closing) return;
                    e.printStackTrace();
                    onError(sess, e);
                }
            }
            /*
            try {
                if (remoteEndpointBasic != null) {
                    remoteEndpointBasic.sendText(message, last);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
             */
        }

        public void onMessageBinary(ByteBuffer message, boolean last) {

            //System.out.println("[DEBUG] Aq2WsEndpoint.onMessageBinary()");
            synchronized(InBinDataLock) {
                long tmp_thrd_id = Thread.currentThread().getId();
                //System.out.println("[aq2j] DEBUG: onBinaryData() at " + Thread.currentThread().getId());
                if (FirstCall) FirstCall = false;
                else {
                    if (!warning_done)
                        if (tmp_thrd_id != PrevThrdId) {
                            warning_done = true;
                            Tum3Logger.DoLog(db_name, true, "IMPORTANT: Please use protocol=org.apache.coyote.http11.Http11Protocol, otherwise websocket may not work properly.");
                        }
                }
                PrevThrdId = tmp_thrd_id;
                try {
                    lmWs.ReadFromClient(message);
                } catch (Exception e) {
                    if (is_closing) return;
                    e.printStackTrace();
                    onError(sess, e);
                }
            }
            /*
            try {
                if (remoteEndpointBasic != null) {
                    remoteEndpointBasic.sendBinary(message, last);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
             */
        }

        public void onClose(Session session, CloseReason closeReason) {
            String tmp_reason = "";
            if (closeReason.getCloseCode() != CloseReason.CloseCodes.NORMAL_CLOSURE) {
                //System.out.println("[DEBUG] Aq2WsEndpoint.onClose(): code=" + closeReason.getCloseCode() + " msg=" + closeReason.getReasonPhrase());
                tmp_reason = closeReason.getReasonPhrase();
            }
            is_closing = true;
            lmWs.ShutdownSrvLink(tmp_reason);
        }

        public void onError(Session session, java.lang.Throwable throwable) {
            //System.out.println("[DEBUG] Aq2WsEndpoint.onError(): <" + throwable.toString() + ">");
            is_closing = true;
            lmWs.ShutdownSrvLink(throwable.toString());
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
            for (int tmp_i = 0; tmp_i < glb_cfg.getDbCount(); tmp_i++) if (glb_cfg.getDbWebEnabled(tmp_i)) {
                String tmp_db_name = glb_cfg.getDbName(tmp_i);
                if (tmp_db_name != null) if (!tmp_db_name.isEmpty()) {
                    //System.out.println("[DEBUG] " + tmp_i + ": tmp_db_name=<" + tmp_db_name + ">");
                    String tmp_url = getInitParameter(CONST_TUM3_DBURL_PARAM + tmp_db_name);
                    if (null == tmp_url) tmp_url = "";
                    if (tmp_url.isEmpty()) tmp_url = "/" + tmp_db_name;
                    //System.out.println("[DEBUG] tmp_db_name=<" + tmp_db_name + ">, tmp_url=<" + tmp_url + ">");
                    try {
                        ServerEndpointConfig.Builder tmpBuilder = ServerEndpointConfig.Builder.create(Aq2WsEndpoint.class, tmp_url);
                        tmpBuilder.configurator(new CustomConfigurator(tmp_i));
                        serverContainer.addEndpoint(tmpBuilder.build());
                        Tum3Logger.DoLog(tmp_db_name, false, "Info: created WS endpoint <" + tmp_url + "> for database <" + tmp_db_name + ">");
                    } catch (DeploymentException e) {
                        throw new ServletException(e.toString());
                    }
                }
            }
        }

        String tmp_tcp = getInitParameter(CONST_TUM3_ENABLE_TCP_PARAM);
        if (tmp_tcp != null) if (tmp_tcp.equals("1")) {
            Tum3Logger.DoLogGlb(false, "DEBUG: tomcat356.init() starting raw tcp...");
            for (int tmp_i = 0; tmp_i < glb_cfg.getDbCount(); tmp_i++) if (glb_cfg.getDbTcpEnabled(tmp_i))
                new TumServTCP(tmp_i);
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
