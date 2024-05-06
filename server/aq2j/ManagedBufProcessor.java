/*
 * Copyright 2023-2024 Nikolai Zhubr <zhubr@mail.ru>
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


public interface ManagedBufProcessor
{
    public void SendToServer(SrvLinkBase sLink, byte thrd_ctx, Object message) throws Exception;
}
