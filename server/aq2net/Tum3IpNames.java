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
package aq2net;


import java.util.Arrays;
import java.util.ArrayList;

import aq2db.*;


public final class Tum3IpNames {


    private final static String TUM3_CFG_ip_names = "ip_names";
    private final static String const_sect_IPNames = "IPNames";
    private NameValueList theList = new NameValueList();


    public Tum3IpNames() {

        try {
            String tmp_ip_fname = Tum3cfg.getGlbParValue(TUM3_CFG_ip_names);
            //System.out.println("[aq2j] tmp_ip_fname=" + tmp_ip_fname);

            if (!tmp_ip_fname.isEmpty()) {
                StringList tmp_s = StringList.readFromFile(tmp_ip_fname);
                //System.out.println("[aq2j] tmp_s.size()=" + tmp_s.size());
                theList.FillFrom(tmp_s, const_sect_IPNames, true);
            }
            //for (int i = 0; i < theList.Count(); i++)
            //  System.out.println("[DEBUG] <" + theList.GetName(i) + "> = <" + theList.GetBody(i) + ">");
        } catch (Exception e) {
            Tum3Logger.DoLogGlb(false, "WARNING loading ip names: " + Tum3Util.getStackTrace(e));
        }
        if (theList.Count() < 1)
            Tum3Logger.DoLogGlb(false, "WARNING: ip names list is empty.");

    }


    private final static class LazyTum3IpNames {
        public static Tum3IpNames instance = new Tum3IpNames();
    }


    public final static Tum3IpNames getInstance() {

        return LazyTum3IpNames.instance;

    }

    public final static String FindIpFriendlyName(String _addr) {

        return getInstance().FindIpFriendlyName_int(_addr);

    }

    private final String FindIpFriendlyName_int(String _addr) {

        try {
            int j1 = _addr.indexOf(":");
            if ((j1 < 1) || (j1 > (_addr.length()-2))) return _addr;

            String st0 = _addr.substring(0, j1);
            String st0p = _addr.substring(j1+1, _addr.length());

            for (int i = 0; i < theList.Count(); i++) {
                String st1 = theList.GetName(i);
                int j2 = st1.indexOf(":");
                if (j1 == j2)
                    if (st0.equals(st1.substring(0, j2))) {
                        String st1p = st1.substring(j2+1, st1.length());
                        if (st1p.isEmpty() || "*".equals(st1p) || st0p.equals(st1p)) {
                            String st = theList.GetBody(i);
                            if (st1p.isEmpty())
                                st = st + ":" + st0p;
                            return st;
                        }
                    }
            }
        } catch (Exception e) {

            Tum3Logger.DoLogGlb(true, "WARNING: <" + Tum3Util.getStackTrace(e) + ">");

        }

        return _addr;

    } 

}
