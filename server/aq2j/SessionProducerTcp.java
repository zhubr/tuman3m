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
package aq2j;


import aq2db.*;


public abstract class SessionProducerTcp {

    protected final static int CONST_MAX_INP_BUFF_KBYTES_default = 128;
    protected final static String TUM3_CFG_max_inp_buff_kbytes = "max_inp_buff_kbytes";
    protected final static String TUM3_CFG_tcp_listen_port = "tcp_listen_port";
    protected final static String TUM3_CFG_tcp_listen_ip = "tcp_listen_ip";
    protected final static String TUM3_CFG_tcp_raw_out_buff_kbytes = "tcp_raw_out_buff_kbytes";

    public abstract int get_CONST_TCP_BUFF_SIZE();

    public abstract void CONST_TCP_BUFF_SIZE(int _default);

    public abstract int CONST_TCP_DEF_LISTEN_PORT(int _default);

    public abstract String CONST_TCP_DEF_LISTEN_IP(String _default);

    public abstract int CONST_MAX_INP_BUFF_BYTES();

    public abstract String getLogPrefixName();

    public abstract SrvLinkBase newSrvLink(SrvLinkOwner thisOwner);

}

