/*
 * Copyright 2025 Nikolai Zhubr <zhubr@rambler.ru>
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
package compatdef;


import java.util.Properties;


public abstract class CompatBase {

    protected CompatBase() {}
    public abstract void AllocAltShotNum(String shot_num) throws Exception;
    public abstract void ApplyAltShotNum(String shot_num);
    public abstract boolean QuicklyCheck(String shot_num, int signal_id, boolean is_volatile, boolean was_deleted);
    public abstract void ProcessNewData(String shot_num, int signal_id, boolean is_volatile, boolean was_deleted, String file_name);

}
