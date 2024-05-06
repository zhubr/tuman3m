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


interface SrvLinkOwner
{
    public void ShutdownSrvLink(String reason);
    public boolean SupportOOB();
    public void WakeupMain();
    public boolean WaitForOutputDone(int timeout) throws Exception;

    public String get_transp_caller();
    public String get_transp_user();
    public String get_transp_agent();
    public String get_transp_title();
    public void set_transp_user(String new_user); // YYY
    public void set_transp_caller(String new_caller); // YYY

}
