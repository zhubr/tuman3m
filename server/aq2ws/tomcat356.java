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

    private static final String CONST_TUM3_DBURL_PARAM = "dburl-";
    private static final String CONST_TUM3_INTR_META_PARAM = "intercon_meta-";
    private static final String CONST_TUM3_INTR_BULK_PARAM = "intercon_bulk-";
    private static final String CONST_TUM3_ENABLE_TCP_PARAM = "enabletcp";
    private static final String CONST_INTERNAL_PAR_PASSING = "http_headers";

    private static final String TUM3_CFG_downlink_meta_tcp = "downlink_meta_tcp"; // YYY
    private static final String TUM3_CFG_downlink_bulk_tcp = "downlink_bulk_tcp"; // YYY

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
            conf.getUserProperties().put(Aq2WsBase.CONST_INTERNAL_SESS_PRODUCER, session_producer);
        }

    }

    // Reminder! This class needs to be public because it is instantiated outside.
    public static class Aq2WsEndpointServer extends Aq2WsBase.Aq2WsEndpoint {

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig) {
            //String tmp_val_str = "";
            //List<String> tmp_val_list = session.getRequestParameterMap().get("db"); // 192.168.0.99/dbtsk/wsconn?db=t308
            //if (null != tmp_val_list) tmp_val_str = tmp_val_list.get(0);
            //System.out.println("[DEBUG] Aq2WsEndpoint.onOpen(): db=<" + tmp_val_str + ">");
            LinkMgrWeb.SelectedHttpHeaders tmp_wanted_headers = (LinkMgrWeb.SelectedHttpHeaders) endpointConfig.getUserProperties().get(CONST_INTERNAL_PAR_PASSING);
            session_producer = (SessionProducerWeb)endpointConfig.getUserProperties().get(Aq2WsBase.CONST_INTERNAL_SESS_PRODUCER);
            //db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
            //System.out.println("[DEBUG] Aq2WsEndpoint.onOpen(): db_index=<" + db_index + ">");
            sess = session;
            sess.getUserProperties().put(tomcat_x.BLOCKING_SEND_TIMEOUT_PROPERTY, (long)(-1));
            remoteEndpointBasic = session.getBasicRemote();
            lmWs = new LinkMgrWeb(session_producer, new Aq2WsBase.ClientWriterWs(session), tmp_wanted_headers);
            session.addMessageHandler(new Aq2WsBase.WsMessageHandlerText(this));
            session.addMessageHandler(new Aq2WsBase.WsMessageHandlerBinary(this));
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

    public static class TempUplinkCreatorMetaCli extends InterconClnt_x.TempUplinkCreator {

        protected int CreatorType() {
            return Aq2WsBase.INTERCON_TYPE_META;
        }

        public SessionProducerWeb CreateProducer(int _i, String _username, String _password, String _serv_addr) {
            return new SessionProducerWebMetaUpl(_i, _username, _password, _serv_addr);
        }

        public void RegisterInitiator(int _i, InterconInitiator _initiator) {
            Tum3Db.getDbInstance(_i).setUplink(_initiator);
        }

    }

    public static class TempUplinkCreatorBulkCli extends InterconClnt_x.TempUplinkCreator {

        protected int CreatorType() {
            return Aq2WsBase.INTERCON_TYPE_BULK;
        }

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
                        String tmp_tcp_addr = glb_cfg.getParValue(tmp_i, false, TUM3_CFG_downlink_meta_tcp); // YYY
                        if (!tmp_tcp_addr.isEmpty()) {
                            try {
                                new TumServTCP(new SessionProducerTcpMeta(tmp_i, tmp_tcp_addr)); // YYY
                                Tum3Logger.DoLog(tmp_db_name, false, "Info: created TCP meta intercon endpoint <" + tmp_tcp_addr + "> for database <" + tmp_db_name + ">");
                            } catch (Exception e) {
                                Tum3Logger.DoLog(tmp_db_name, true, "WARNING: TCP meta intercon endpoint <" + tmp_tcp_addr + "> failed for database <" + tmp_db_name + ">");
                            }
                        }
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
                        String tmp_tcp_addr = glb_cfg.getParValue(tmp_i, false, TUM3_CFG_downlink_bulk_tcp); // YYY
                        if (!tmp_tcp_addr.isEmpty()) {
                            try {
                                new TumServTCP(new SessionProducerTcpBulk(tmp_i, tmp_tcp_addr)); // YYY
                                Tum3Logger.DoLog(tmp_db_name, false, "Info: created TCP bulk intercon endpoint <" + tmp_tcp_addr + "> for database <" + tmp_db_name + ">");
                            } catch (Exception e) {
                                Tum3Logger.DoLog(tmp_db_name, true, "WARNING: TCP bulk intercon endpoint <" + tmp_tcp_addr + "> failed for database <" + tmp_db_name + ">");
                            }
                        }
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
                new TumServTCP(new SessionProducerTcpStd(tmp_i));
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
