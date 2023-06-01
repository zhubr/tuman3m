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
package aq2db;


public interface UgcReplyHandler {

    public void GenerateUgcReply(byte thrd_ctx, int _req_id, String _shot_name, String _err_msg, byte[] data) throws Exception;
    public void GenerateUgcReply(int _req_id, String _shot_name, String _err_msg, byte[] data) throws Exception;

}

