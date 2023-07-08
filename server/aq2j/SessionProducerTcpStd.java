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


public class SessionProducerTcpStd extends SessionProducerTcp {

    private int CONST_TCP_BUFF_SIZE_bytes;
    private static int CONST_MAX_INP_BUFF_BYTES_all[] = InitMaxInpBuffConst(); // should be per-db now.
    private int db_index;
    private String db_name;


    private static final int[] InitMaxInpBuffConst() {

        int tmp_arr[] = new int[Tum3cfg.getGlbInstance().getDbCount()];
        Tum3cfg cfg = Tum3cfg.getGlbInstance();
        for (int tmp_i = 0; tmp_i < tmp_arr.length; tmp_i++) {
            tmp_arr[tmp_i] = 1024 * Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_inp_buff_kbytes, CONST_MAX_INP_BUFF_KBYTES_default);
            Tum3Logger.DoLog(cfg.getDbName(tmp_i), false, "DEBUG: CONST_MAX_INP_BUFF_BYTES=" + tmp_arr[tmp_i]);
        }
        return tmp_arr;

    }

    public SessionProducerTcpStd(int _db_idx) {

        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);

    }

    public String getLogPrefixName() {

        return db_name;

    }

    public SrvLinkBase newSrvLink(SrvLinkOwner thisOwner) {

        return new SrvLink(db_index, thisOwner);

    }

    public int get_CONST_TCP_BUFF_SIZE() {
        return CONST_TCP_BUFF_SIZE_bytes;
    }

    public void CONST_TCP_BUFF_SIZE(int _default) {
        CONST_TCP_BUFF_SIZE_bytes = 1024 * Tum3cfg.getIntValue(db_index, true, TUM3_CFG_tcp_raw_out_buff_kbytes, _default);
    }

    public int CONST_TCP_DEF_LISTEN_PORT(int _default) {
        return Tum3cfg.getIntValue(db_index, true, TUM3_CFG_tcp_listen_port, _default);
    }

    public String CONST_TCP_DEF_LISTEN_IP(String _default) {
        return Tum3cfg.getParValue(db_index, true, TUM3_CFG_tcp_listen_ip, _default);
    }

    public int CONST_MAX_INP_BUFF_BYTES() {
      return CONST_MAX_INP_BUFF_BYTES_all[db_index];
    }

}

