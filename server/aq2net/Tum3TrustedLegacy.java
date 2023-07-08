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


import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;


import aq2db.*;


public final class Tum3TrustedLegacy {


    private final static String TUM3_CFG_trusted_legacy_prefix = "trusted_legacy_prefix";
    private List<String> prefix_list = null;

    private final static Object creation_locks[] = InitLocks();
    private final static Tum3TrustedLegacy instances_per_db[] = new Tum3TrustedLegacy[Tum3cfg.getGlbInstance().getDbCount()];


    private Tum3TrustedLegacy(int _db_idx) {

        String tmp_prefix_list = Tum3cfg.getParValue(_db_idx, true, TUM3_CFG_trusted_legacy_prefix).trim();

        if (tmp_prefix_list.isEmpty()) return;

        prefix_list = Arrays.asList(tmp_prefix_list.split(","));

        for (int i = 0; i < prefix_list.size(); i++) // YYY
            if (prefix_list.get(i).startsWith("tcp/")) prefix_list.set(i, "tcp*" + prefix_list.get(i).substring(4)); // YYY

        //for (int i = 0; i < prefix_list.size(); i++) System.out.println("[DEBUG] Tum3TrustedLegacy: prefix " + i + " = <" + prefix_list.get(i) + ">");

    }

    private static Object[] InitLocks() {

        Object tmp_instances[] = new Object[Tum3cfg.getGlbInstance().getDbCount()];
        for (int tmp_i = 0; tmp_i < tmp_instances.length; tmp_i++)
            tmp_instances[tmp_i] = new Object();
        return tmp_instances;

    }

    public final boolean isTrusted_internal(String the_addr) {

        //System.out.println("[DEBUG] isTrusted_internal: checking <" + the_addr + ">...");

        if (null == prefix_list) return false;

        for (int i = 0; i < prefix_list.size(); i++)
            if (!prefix_list.get(i).isEmpty() && the_addr.startsWith(prefix_list.get(i)))
                return true;

        return false;

    }

    public final static boolean isTrusted(int _db_idx, String the_addr) {

        synchronized (creation_locks[_db_idx]) {
            if (null == instances_per_db[_db_idx]) 
                instances_per_db[_db_idx] = new Tum3TrustedLegacy(_db_idx);
        }
        return instances_per_db[_db_idx].isTrusted_internal(the_addr);

    }

}
