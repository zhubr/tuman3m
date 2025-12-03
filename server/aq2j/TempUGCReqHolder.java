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


public class TempUGCReqHolder {


    public final int glb_fwd_id;
    public final long start_millis = System.currentTimeMillis();
    public final int db_index;
    public final UgcReplyHandlerExt sender_link;
    public final String shot_num;
    public final int req_id;
    public final byte[] upd_arr;


    public TempUGCReqHolder(int _glb_fwd_id, int _db_index, UgcReplyHandlerExt _sender_link, String _shot_num, int _req_id, byte[] _upd_arr) {

            glb_fwd_id = _glb_fwd_id;
            db_index = _db_index;
            sender_link = _sender_link;
            shot_num = _shot_num;
            req_id = _req_id;
            upd_arr = _upd_arr;
        }

    public void Refuse(String err_txt) {

        sender_link.PostUgcReply(req_id, shot_num, err_txt, null);

    }
}
