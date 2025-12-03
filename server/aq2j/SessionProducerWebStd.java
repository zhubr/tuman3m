/*
 * Copyright 2022-2023 Nikolai Zhubr <zhubr@rambler.ru>
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


public class SessionProducerWebStd extends SessionProducerWeb {

    private static int CONST_WS_BUFF_SIZE[] = InitWsBuffSizeConst(); // Should be per-db now.

    private int db_index;
    private String db_name;


    private static final int[] InitWsBuffSizeConst() {

        int tmp_arr[] = new int[Tum3cfg.getGlbInstance().getDbCount()];
        Tum3cfg cfg = Tum3cfg.getGlbInstance();
        for (int tmp_i = 0; tmp_i < tmp_arr.length; tmp_i++) {
            tmp_arr[tmp_i] = 1024 * Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_ws_raw_out_buff_kbytes, CONST_WS_BUFF_SIZE_def);
            Tum3Logger.DoLog(cfg.getDbName(tmp_i), false, "DEBUG: CONST_WS_BUFF_SIZE=" + tmp_arr[tmp_i]);
        }
        return tmp_arr;

    }

    public SessionProducerWebStd(int _db_idx) {

        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);

    }

    public String getLogPrefixName() {

        return db_name;

    }

    public SrvLinkBase newSrvLink(SrvLinkOwner thisOwner) {

        return new SrvLink(db_index, thisOwner);

    }

    public int get_CONST_WS_BUFF_SIZE() {

        return CONST_WS_BUFF_SIZE[db_index];

    }

}

