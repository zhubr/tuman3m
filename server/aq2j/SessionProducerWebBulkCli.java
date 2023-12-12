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


public class SessionProducerWebBulkCli extends SessionProducerWeb {

    private final static String TUM3_CFG_ws_raw_bulk_out_buff_kbytes = "ws_raw_bulk_c_out_buff_kbytes";
    private static final int CONST_WS_BULK_BUFF_SIZE_def = 1024; // Kbytes

    private static int CONST_WS_BUFF_SIZE[] = InitWsBuffSizeConst(); // Should be per-db now.

    protected int db_index;
    protected String db_name;

    private String username, password, serv_addr;


    private static final int[] InitWsBuffSizeConst() {

        int tmp_arr[] = new int[Tum3cfg.getGlbInstance().getDbCount()];
        Tum3cfg cfg = Tum3cfg.getGlbInstance();
        for (int tmp_i = 0; tmp_i < tmp_arr.length; tmp_i++) {
            tmp_arr[tmp_i] = 1024 * Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_ws_raw_bulk_out_buff_kbytes, CONST_WS_BULK_BUFF_SIZE_def);
            Tum3Logger.DoLog(cfg.getDbName(tmp_i), false, "DEBUG: CONST_WS_BULK_BUFF_SIZE=" + tmp_arr[tmp_i]);
        }
        return tmp_arr;

    }

    public SessionProducerWebBulkCli(int _db_idx, String _username, String _password, String _serv_addr) {

        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);
        username = _username;
        password = _password;
        serv_addr = _serv_addr;

    }

    public String getTargetAddr() {

        return serv_addr;

    }

    public String getUserName() {

        return username;

    }

    public String getLogPrefixName() {

        return db_name;

    }

    public int get_CONST_WS_BUFF_SIZE() {

        return CONST_WS_BUFF_SIZE[db_index];

    }

    public SrvLinkBase newSrvLink(SrvLinkOwner thisOwner) {

        return new SrvLinkBulkCli(db_index, thisOwner, username, password);

    }

}

