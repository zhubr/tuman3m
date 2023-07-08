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


public interface InterconInitiator
{
    public final static byte csDisconnected = 0;
    public final static byte csConnecting = 1;
    public final static byte csConnected = 2;

    public byte getConnStatus();
    public String getDisconnReason();
    public boolean ConnectToServer() throws Exception;

}
