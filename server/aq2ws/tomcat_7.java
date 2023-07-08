/*
 * Copyright 2022-2023 Nikolai Zhubr <zhubr@mail.ru>
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

// This file is for use with Tomcat 7. It should be renamed to tomcat_x.java before compiling.

package aq2ws;


import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;


public class tomcat_x {

    public static final String SSL_TRUSTSTORE_PROPERTY = WsWebSocketContainer.SSL_TRUSTSTORE_PROPERTY;
    public static final String IO_TIMEOUT_MS_PROPERTY = WsWebSocketContainer.IO_TIMEOUT_MS_PROPERTY;
    public static final String BLOCKING_SEND_TIMEOUT_PROPERTY = WsRemoteEndpointImplBase.BLOCKING_SEND_TIMEOUT_PROPERTY;
    public static final String WS_AUTHENTICATION_USER_NAME = "";
    public static final String WS_AUTHENTICATION_PASSWORD = "";

}
