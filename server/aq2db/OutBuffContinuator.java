/*
 * Copyright 2011-2024 Nikolai Zhubr <zhubr@rambler.ru>
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


public interface OutBuffContinuator {

    public long getFullSizeX();

    public void ForceXByte();

    public long getPos();

    public byte getEditedByte();

    public void EnsureOfs(long _seg_ofs) throws Exception;

    public int ReadTo(byte[] buff, int ofs, int count) throws Exception;

    public boolean withTrailingStatus();

    public void close() throws Exception;

    public boolean PleaseWait();

    public boolean WithWarning();

    public void AddUser();

}

