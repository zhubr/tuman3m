/*
 * Copyright 2011-2021 Nikolai Zhubr <zhubr@rambler.ru>
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
package aq3host;


import java.nio.*;

import aq2db.*;
import aq2net.*;

public class aq3h implements TumProtoConsts {

    private volatile boolean is_terminating = false;

    public aq3h(Tum3Db _dbLink, int _db_idx) {

    }

    public static aq3h getInstance(Tum3Db _dbLink, int _db_idx) {

        return null;

    }

    public void DeregisterLink(aq3sender the_link) {

    }

    private static void Aq3ReqParse(String _DbName, byte thrd_ctx, aq3sender the_link, byte[] req_body, int req_trailing_len, aq3h instance) throws Exception {

        if (req_trailing_len < 16) {
            Tum3Logger.DoLog(_DbName, false, "FATAL: invalid AQ3 request length");
            throw new Exception("FATAL: invalid AQ3 request length");
        }

        ByteBuffer tmp_bb = ByteBuffer.wrap(req_body);
        tmp_bb.limit(req_trailing_len);
        tmp_bb.order(ByteOrder.LITTLE_ENDIAN);
        int tmp_rsrv1, tmp_rsrv2, RAq3_code, RAq3_seq;
        tmp_rsrv1 = tmp_bb.getInt();
        tmp_rsrv2 = tmp_bb.getInt();
        RAq3_code = tmp_bb.getInt();
        RAq3_seq  = tmp_bb.getInt();

        if ((tmp_rsrv1 != 0) || (tmp_rsrv2 != 0)) {
            Tum3Logger.DoLog(_DbName, false, "FATAL: invalid AQ3 request header");
            throw new Exception("FATAL: invalid AQ3 request header");
        }

        the_link.aq3send_inline(thrd_ctx, 1, RAq3_code, RAq3_seq, "Acquisition is not available at this instance");
    }

    public void Aq3Request(byte thrd_ctx, aq3sender the_link, byte[] req_body, int req_trailing_len) throws Exception {

        Aq3ReqParse("dummy", thrd_ctx, the_link, req_body, req_trailing_len, this);

    }

    public static void Aq3RequestReject(String _DbName, byte thrd_ctx, aq3sender the_link, byte[] req_body, int req_trailing_len) throws Exception {

        Aq3ReqParse(_DbName, thrd_ctx, the_link, req_body, req_trailing_len, null);

    }

}
