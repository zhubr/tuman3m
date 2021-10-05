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
package aq2j;


import java.nio.ByteBuffer;


public interface ClientWriterRaw
{
    public void SendToClient(ByteBuffer msgBb, int byteCount) throws Exception;
    public void SendToClientAsOOB(String oobMsg) throws Exception;
    public void close() throws Exception;
    public boolean isOpen();
}
