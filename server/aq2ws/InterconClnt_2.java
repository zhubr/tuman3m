/*
 * Copyright 2011-2024 Nikolai Zhubr <zhubr@mail.ru>
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
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

import java.util.Properties;
import java.util.concurrent.ThreadFactory;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.CertificateFactory;
import javax.net.ssl.TrustManagerFactory;

import java.util.Base64;

import io.netty.util.concurrent.ThreadPerTaskExecutor;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.PoolArenaMetric;

import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;

import io.netty.channel.epoll.Native;
import io.netty.channel.epoll.EpollEventLoop;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslClientContext;
import io.netty.handler.ssl.SslContext;

import aq2db.*;
import aq2j.*;

// See also: https://netty.io/wiki/new-and-noteworthy-in-4.0.html
public class InterconClnt_x {

    private static final String CONST_INTERNAL_OWNER_INITIATOR = "owner_initiator";
    private static SslContext context[] = new SslContext[Tum3cfg.getGlbInstance().getDbCount()];

    static {
        String tmp_banner = "openssl version <" + OpenSsl.versionString() + ">, " + "kernel version: <" + Native.KERNEL_VERSION + ">";
        Tum3Logger.DoLogGlb(false, "Netty initialization: " + tmp_banner);

        Tum3cfg glb_cfg = Tum3cfg.getGlbInstance();

        for (int tmp_i = 0; tmp_i < glb_cfg.getDbCount(); tmp_i++) if (glb_cfg.getDbUplinkEnabled(tmp_i) || glb_cfg.getDbUpBulkEnabled(tmp_i)) {
            try {
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                String tmp_trusted_keys = Tum3cfg.getParValue(tmp_i, false, Tum3cfg.TUM3_CFG_uplink_trusted_keys);
                keyStore.setEntry("server", new TrustedCertificateEntry(CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(new File(tmp_trusted_keys)))), null);
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);

                context[tmp_i] = new OpenSslClientContext(trustManagerFactory); // XXX We will certainly need OpenSslClientContext and allocate it globally for reuse.
            } catch (Exception e) {
                Tum3Logger.DoLog(glb_cfg.getDbName(tmp_i), true, "Trusted keystore init failed with: " + e.toString());
            }
        }
    }

    private static PooledByteBufAllocator getBufPoolForMeta() {
        return BufPoolForMetaLazyHolder.INSTANCE;
    }

    private static PooledByteBufAllocator getBufPoolForBulk() {
        return BufPoolForBulkLazyHolder.INSTANCE;
    }

    private static class BufPoolForMetaLazyHolder {
        static final boolean dummy = printDbgMsg();
        static final PooledByteBufAllocator INSTANCE = new PooledByteBufAllocator(true);
        private static boolean printDbgMsg() {
            //Tum3Logger.DoLogGlb(false, "[DEBUG] BufPoolForMetaLazyHolder initializing...");
            return true;
        }
    }

    private static class BufPoolForBulkLazyHolder {
        static final boolean dummy = printDbgMsg();
        static final PooledByteBufAllocator INSTANCE = new PooledByteBufAllocator(true);
        private static boolean printDbgMsg() {
            //Tum3Logger.DoLogGlb(false, "[DEBUG] BufPoolForBulkLazyHolder initializing...");
            return true;
        }
    }

    private static class SimpleThreadFactory implements ThreadFactory {

        private final boolean daemon;

        public SimpleThreadFactory(boolean _daemon) {
            daemon = _daemon;
        }

        public Thread newThread(Runnable r) {

            Thread t = new Thread(r);
            try {
                if (t.isDaemon() != daemon) { t.setDaemon(daemon); }
            } catch (Exception ignored) {}
            return t;
        }
    }

    public static class ClientWriterNetty implements ClientWriterRaw {

        private final ChannelHandlerContext ctx;
        private final PooledByteBufAllocator outgoing_allocator;

        public ClientWriterNetty(ChannelHandlerContext _ctx, PooledByteBufAllocator _outgoing_allocator) {
            ctx = _ctx;
            outgoing_allocator = _outgoing_allocator;
        }

        private final static String doAllocStats(PooledByteBufAllocator allocator) {
            int activeAllocations = 0, allocations = 0, deallocations = 0;
            int activeBytes = 0;

            for (PoolArenaMetric poolArenaMetric : allocator.directArenas()) {
                activeAllocations += poolArenaMetric.numActiveAllocations();
                allocations += poolArenaMetric.numAllocations();
                deallocations += poolArenaMetric.numDeallocations();
                activeBytes += poolArenaMetric.numActiveBytes();
            }
            for (PoolArenaMetric poolArenaMetric : allocator.heapArenas()) {
                activeAllocations += poolArenaMetric.numActiveAllocations();
                allocations += poolArenaMetric.numAllocations();
                deallocations += poolArenaMetric.numDeallocations();
                activeBytes += poolArenaMetric.numActiveBytes();
            }
            return "active=" + activeAllocations + " alloced=" + allocations + " dealloced=" + deallocations +  " bytes=" + activeBytes;
        }

        public void SendToClient(ByteBuffer msgBb, int byteCount) throws Exception {
            msgBb.clear();
            msgBb.limit(byteCount);
            //remoteEndpointBasic.sendBinary(msgBb, true);

            //ByteBuf message = Unpooled.copiedBuffer(msgBb.array(), 0, byteCount);
            //ctx.writeAndFlush(message).awaitUninterruptibly(); // FIXME. Avoid blocking.

            ByteBuf message = outgoing_allocator.ioBuffer(byteCount); // YYY
            message.writeBytes(msgBb.array(), 0, byteCount); // YYY
            ctx.writeAndFlush(message); // YYY
            //Tum3Logger.DoLogGlb(false, "[DEBUG] outgoing_allocator.dumpStats: " + doAllocStats(outgoing_allocator));
        }

        public void SendToClientAsOOB(String oobMsg) throws Exception { 
            throw new Exception("ClientWriterNetty.SendToClientAsOOB() is not possible");
        }

        public void close() throws Exception {
            ctx.channel().close();
        }

        public boolean isOpen() {
            return ctx.channel().isOpen();
        }
    }

    public static class Aq2NettyClientHandler extends ChannelInboundHandlerAdapter implements ManagedBufProcessor {

        private NettyInterconInitiator owner_initiator;
        private SessionProducerWeb session_producer;
        protected volatile LinkMgrWeb lmWs = null;
        protected volatile boolean is_closing = false;

        public Aq2NettyClientHandler(NettyInterconInitiator _owner_initiator, SessionProducerWeb _session_producer) {
            owner_initiator = _owner_initiator;
            session_producer = _session_producer;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {

            super.channelActive(ctx); // YYY

            String tmp_redund_http = 
                "GET " + owner_initiator.destServerPath + " HTTP/1.1\r\nUpgrade: websocket\r\nConnection: upgrade\r\n"
              + "Host: " + owner_initiator.destServerIp + ":" + owner_initiator.destServerPort + "\r\n"
              + "X-WebSocket-Tum3Compat: 1\r\n"
              + "X-Real-user: " + owner_initiator.userName + "\r\n"
              + "Authorization: " + owner_initiator.basic_auth + "\r\n\r\n";
            //System.out.println("netty sent: [" + tmp_redund_http + "]\n");
            ByteBuf message = Unpooled.copiedBuffer(tmp_redund_http, StandardCharsets.UTF_8);
            ctx.writeAndFlush(message);

            lmWs = new LinkMgrWebClient(session_producer, new ClientWriterNetty(ctx, owner_initiator.outgoing_allocator), false, this);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String normal_close_txt = "I/O channel closed without any error indicated";
            try {
                if (null != lmWs) lmWs.ShutdownSrvLink(normal_close_txt);
                owner_initiator.RawSessionEnded(this, (null == lmWs) ? normal_close_txt : lmWs.getShutdownReason());
            } finally {
                super.channelInactive(ctx);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf byteBuf = (ByteBuf) msg;
            //System.out.println("netty got: [" + byteBuf.toString(StandardCharsets.UTF_8) + "]\n");
            try {
                lmWs.ReadFromClient(byteBuf, byteBuf.readableBytes()); // YYY
            } catch (Throwable cause) {
                byteBuf.release();
                if (is_closing) return;
                exceptionCaught(ctx, cause);
            }
        }

        public void SendToServer(SrvLinkBase sLink, byte thrd_ctx, Object msg) throws Exception {

            ByteBuf byteBuf = (ByteBuf) msg;

            //ByteBuffer tmp_buff = ByteBuffer.allocate(byteBuf.readableBytes());
            //byteBuf.getBytes(byteBuf.readerIndex(), tmp_buff.array());
            //byteBuf.release();

            try {
                ByteBuffer tmp_buff = byteBuf.nioBuffer(); // ByteBuffer.wrap(byteBuf.array(), byteBuf.arrayOffset()+byteBuf.readerIndex(), byteBuf.readableBytes()); // YYY
                sLink.SendToServer(thrd_ctx, tmp_buff);
            } finally {
                byteBuf.release();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Note. This method is the only way to obtain some detail about disconnection reason.
            if (null != lmWs) lmWs.ShutdownSrvLink(Tum3Util.getStackTrace(cause));
            ctx.channel().close();
        }
    }

    private static class NettyInterconInitiator implements InterconInitiator {
    // Note! Note! Note! Remember, this object is a multi-cycle signleton! It is not disposed but reused after a disconnect/failure!

        private final static int CONST_WS_UPLINK_CONN_TIMEOUT_def = 3000;

        private volatile byte conn_status = csDisconnected;
        private volatile String disconn_reason_msg = "";
        public final String userName, userPassword;
        public final String destServerPath;
        private String destServerIp = "";
        public final String basic_auth;
        private int destServerPort = 443;
        private int io_ms_timeout;
        private final SslContext this_context;
        private SessionProducerWeb session_producer;
        public final PooledByteBufAllocator outgoing_allocator;
        private EventLoopGroup group = null;

        public NettyInterconInitiator(PooledByteBufAllocator _outgoing_allocator, SslContext _context, String _destServer, String _userName, String _userPassword, int _timeout, SessionProducerWeb _session_producer) throws Exception {

            outgoing_allocator = _outgoing_allocator; // YYY
            //destServerPath = "";
            int tmp_i = _destServer.indexOf("/");
            if (tmp_i >= 0) {
                destServerPath = _destServer.substring(tmp_i);
                _destServer = _destServer.substring(0, tmp_i);
            } else throw new Exception("No path specified in uplink URL");
            tmp_i = _destServer.indexOf(":");
            if (tmp_i >= 0) {
                destServerIp = _destServer.substring(0, tmp_i);
                destServerPort = Integer.parseInt(_destServer.substring(tmp_i+1));
            } else destServerIp = _destServer;
            if (destServerIp.isEmpty()) throw new Exception("No ip address specified in uplink URL");

            this_context = _context;
            userName = _userName;
            userPassword = _userPassword;
            basic_auth = " Basic " + Base64.getEncoder().encodeToString((userName + ":" + userPassword).getBytes());
            io_ms_timeout = _timeout;

            session_producer = _session_producer;
        }

        public byte getConnStatus() {

            return conn_status; // Note. Could also consult session.isOpen() ?

        }

        private synchronized void setStatus(byte _new_status, String _the_reason) throws Exception {

            if (((_new_status == csConnecting) && (conn_status != csDisconnected))
             || ((_new_status == csConnected) && (conn_status != csConnecting))) 
                throw new Exception("Invalid state for requested operation");
            conn_status = _new_status;
            if ((_new_status == csDisconnected) && disconn_reason_msg.isEmpty()) disconn_reason_msg = _the_reason;
            else disconn_reason_msg = "";
            if ((_new_status == csDisconnected) && (null != group)) {
                //Tum3Logger.DoLogGlb(false, "[DEBUG] netty group shutdown initiated through setStatus()...");
                group.shutdownGracefully(); // YYY
                group = null; // YYY
            }
        }

        public String getDisconnReason() {

            return disconn_reason_msg;

        }

        public void RawSessionEnded(Aq2NettyClientHandler _handler, String _the_reason) {
            try {
              setStatus(csDisconnected, _the_reason);
            } catch (Exception ignored) { } // Note: this should never happen.
        }

        public boolean ConnectToServer() throws Exception {
            setStatus(csConnecting, "");
            try {
                if (null != group) group.shutdownGracefully(); // YYY
                group = new EpollEventLoop(null,
                new ThreadPerTaskExecutor( // Note: ThreadPerTaskExecutor is totally trivial so it can be used safely.
                new SimpleThreadFactory (true)
                ), 0,
                DefaultSelectStrategyFactory.INSTANCE.newSelectStrategy(), // Note: this is default for epoll and likely should not be modofied.
                RejectedExecutionHandlers.reject(), // Note: for our case this should not matter whatsoever.
                null, null);

                //Tum3Logger.DoLogGlb(false, "[DEBUG] new NettyInterconInitiator: group=" + group + " group.isShutdown()=" + group.isShutdown());
                //System.out.println("[DEBUG] new NettyInterconInitiator: group=" + group + " group.isShutdown()=" + group.isShutdown());
                //Tum3Logger.DoLogGlb(true, "[DEBUG] wsContainer=" + wsContainer + " clientEndpointConfig=" + clientEndpointConfig + " destURI=" + destURI);
                NettyInterconInitiator tmp_this = this;
                Bootstrap bootstrap = new Bootstrap() // XXX The Bootstrap class is basically unavoidable, because it performs some not-so-trivial book-keeping.
                    .group(group)
                    .channel(EpollSocketChannel.class) // XXX We will certainly need EpollSocketChannel objects, 1 per every connection.
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // Add SSL handler first to encrypt and decrypt everything.
                            ch.pipeline().addLast("ssl", this_context.newHandler(ch.alloc(), destServerIp, destServerPort));
                            // On top of the SSL handler, add the application logic.
                            ch.pipeline().addLast(new Aq2NettyClientHandler(tmp_this, session_producer));
                        }
                    });
                bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, io_ms_timeout);
                //Tum3Logger.DoLogGlb(false, "[DEBUG] netty connecting to " + destServerIp + ":" + destServerPort);
                ChannelFuture future = bootstrap.connect(destServerIp, destServerPort).sync();
                //Tum3Logger.DoLogGlb(false, "[DEBUG] netty successfully connected to " + destServerIp + ":" + destServerPort);

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

                setStatus(csDisconnected, tmp_msg);
                return false; //throw e;
            }
            setStatus(csConnected, "");
            return true;
        }

    }

    public static abstract class TempUplinkCreator {

        protected abstract int CreatorType(); // YYY
        protected final String _label() { return CreatorType() == Aq2WsBase.INTERCON_TYPE_META ? "meta" : "bulk"; }

        public abstract SessionProducerWeb CreateProducer(int _i, String _username, String _password, String _serv_addr);

        public abstract void RegisterInitiator(int _i, InterconInitiator _initiator);

        public void DoCreate(int tmp_i, String tmp_db_name, String _serv_addr_param) {
            try {
              String tmp_serv_addr = Tum3cfg.getParValue(tmp_i, false, _serv_addr_param);
              //String tmp_trusted_keys = Tum3cfg.getParValue(tmp_i, false, Tum3cfg.TUM3_CFG_uplink_trusted_keys); //  "/opt/aq2j/tum3trust.jks"
              String tmp_cred_filename = Tum3cfg.getParValue(tmp_i, false, Tum3cfg.TUM3_CFG_uplink_credentials); //  "/opt/tum3/cred_main.properties"
              int tmp_connect_timeout = Tum3cfg.getIntValue(tmp_i, false, Tum3cfg.TUM3_CFG_uplink_connect_timeout, NettyInterconInitiator.CONST_WS_UPLINK_CONN_TIMEOUT_def); // 3000

              if (!tmp_serv_addr.isEmpty() && !tmp_cred_filename.isEmpty()) {
                  Properties tmp_props = new Properties();
                  tmp_props.load(new FileInputStream(tmp_cred_filename));
                  String tmp_username = tmp_props.getProperty(Tum3cfg.TUM3_CFG_uplink_username, "").trim();
                  String tmp_password = tmp_props.getProperty(Tum3cfg.TUM3_CFG_uplink_password, "").trim();

                  InterconInitiator tmp_initiator = new NettyInterconInitiator(
                      CreatorType() == Aq2WsBase.INTERCON_TYPE_META ? getBufPoolForMeta() : getBufPoolForBulk(), // YYY
                      context[tmp_i],
                      tmp_serv_addr,
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
                  Tum3Logger.DoLog(tmp_db_name, true, "IMPORTANT: failed to create Netty " + _label() + " uplink endpoint, error: " + e.toString());
            }
        }
    }

}
