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


public abstract class SessionProducerWeb {

    protected final static String TUM3_CFG_ws_raw_out_buff_kbytes = "ws_raw_out_buff_kbytes";
    protected static final int CONST_WS_BUFF_SIZE_def = 512; // 512 Kbytes (very small default)


    public abstract String getLogPrefixName();

    public abstract SrvLinkBase newSrvLink(SrvLinkOwner thisOwner);

    public abstract int get_CONST_WS_BUFF_SIZE();

}

