/*
 * Copyright 2022-2024 Nikolai Zhubr <zhubr@rambler.ru>
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


public class SessionProducerTcpBulk extends SessionProducerTcp {

    protected final static String TUM3_CFG_max_bulk_inp_buff_kbytes = "max_bulk_inp_buff_kbytes";
    protected final static int BULK_MAX_INP_BUFF_KBYTES_default = 2048; // YYY
    protected final static String TUM3_CFG_tcp_bulk_out_buff_kbytes = "tcp_bulk_out_buff_kbytes";
    protected final int CONST_TCP_BUFF_SIZE_default = 128;

    private int CONST_TCP_BUFF_SIZE_bytes;
    private static int CONST_MAX_INP_BUFF_BYTES_all[] = InitMaxInpBuffConst(); // should be per-db now.
    private final int db_index;
    private final String db_name;
    private final String listen_ip;
    private final int listen_port;


    private static final int[] InitMaxInpBuffConst() {

        int tmp_arr[] = new int[Tum3cfg.getGlbInstance().getDbCount()];
        Tum3cfg cfg = Tum3cfg.getGlbInstance();
        for (int tmp_i = 0; tmp_i < tmp_arr.length; tmp_i++) {
            tmp_arr[tmp_i] = 1024 * Tum3cfg.getIntValue(tmp_i, true, TUM3_CFG_max_bulk_inp_buff_kbytes, BULK_MAX_INP_BUFF_KBYTES_default);
            Tum3Logger.DoLog(cfg.getDbName(tmp_i), false, "DEBUG: CONST_MAX_INP_BUFF_BYTES=" + tmp_arr[tmp_i]);
        }
        return tmp_arr;

    }

    public SessionProducerTcpBulk(int _db_idx, String _listen_addr) throws Exception {

        ParseListenAddr tmp_listen = new ParseListenAddr(_listen_addr);
        listen_ip = tmp_listen.listen_ip;
        listen_port = tmp_listen.listen_port;
        db_index = _db_idx;
        db_name = Tum3cfg.getGlbInstance().getDbName(db_index);

    }

    public String getLogPrefixName() {

        return db_name;

    }

    public SrvLinkBase newSrvLink(SrvLinkOwner thisOwner) {

        return new SrvLinkBulkSrv(db_index, thisOwner);

    }

    public int get_CONST_TCP_BUFF_SIZE() {
        return CONST_TCP_BUFF_SIZE_bytes;
    }

    public void CONST_TCP_BUFF_SIZE(int _default) {
        CONST_TCP_BUFF_SIZE_bytes = 1024 * Tum3cfg.getIntValue(db_index, false, TUM3_CFG_tcp_bulk_out_buff_kbytes, CONST_TCP_BUFF_SIZE_default);
    }

    public int CONST_TCP_DEF_LISTEN_PORT(int _default) {
        return listen_port;
    }

    public String CONST_TCP_DEF_LISTEN_IP(String _default) {
        return listen_ip;
    }

    public int CONST_MAX_INP_BUFF_BYTES() {
      return CONST_MAX_INP_BUFF_BYTES_all[db_index];
    }

}

