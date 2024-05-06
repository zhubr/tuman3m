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


import java.nio.ByteBuffer;


public class LinkMgrWebClient extends LinkMgrWeb {


    public LinkMgrWebClient(SessionProducerWeb _session_producer, ClientWriterRaw _o_raw)
    {
        this(_session_producer, _o_raw, true, null);
    }

    public LinkMgrWebClient(SessionProducerWeb _session_producer, ClientWriterRaw _o_raw, boolean _oob_supported, ManagedBufProcessor _mng_buf_processor)
    {
        super(_session_producer, _o_raw, null, _oob_supported, _mng_buf_processor);
    }

}
