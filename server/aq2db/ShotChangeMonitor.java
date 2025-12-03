/*
 * Copyright 2011-2021 Nikolai Zhubr <zhubr@rambler.ru>
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


public interface ShotChangeMonitor {

    public void AddUpdatedId(int _id, boolean _hurry, boolean _was_waiting, boolean _was_removed) throws Exception;

}

