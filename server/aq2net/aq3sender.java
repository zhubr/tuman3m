/*
 * Copyright 2011-2021 Nikolai Zhubr <zhubr@mail.ru>
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
package aq2net;


import aq2db.*;


public interface aq3sender {

    public void aq3send_inline(byte thrd_ctx, int Aq3_error, int Aq3_code, int Aq3_seq, String the_body) throws Exception;
    public boolean aq3send_later(int Aq3_error, int Aq3_code, int Aq3_seq, String the_body, boolean _push) throws Exception;
    public boolean aq3send_later(int Aq3_error, int Aq3_code, int Aq3_seq, String the_body, boolean _push, boolean _try_harder) throws Exception;
    public String get_authorized_username();
    public Tum3Perms getUserPerms();
    public Tum3Perms getMasterdbPerms();
    public int GetClientAppVer();

    public void AddGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, String thisReceiverName, String thisEchoName);

}

