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


public class SessionProducerWebMetaUpl extends SessionProducerWebMeta {

    private String username, password, serv_addr;

    public SessionProducerWebMetaUpl(int _db_idx, String _username, String _password, String _serv_addr) {

        super(_db_idx);
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

    public SrvLinkBase newSrvLink(SrvLinkOwner thisOwner) {

        return new SrvLinkMetaCli(db_index, thisOwner, username, password);

    }

}

