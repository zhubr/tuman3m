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


import java.net.*;


public final class Tum3IpUtil {

    private final static int INADDR4SZ = 4;

    public static final byte[] textToNumericFormatV4(String src) throws Exception
    {
        if (src.isEmpty()) {
            throw new Exception("Empty value for ip address rejected");
        }

        byte[] res = new byte[INADDR4SZ];
        String[] s = src.split("\\.", -1);
        if (s.length != 4)
            throw new Exception("Invalid ip address format (" + src + ")");
        long val;
        try {
            for (int i = 0; i < 4; i++) {
                val = Integer.parseInt(s[i]);
                if (val < 0 || val > 0xff)
                    throw new Exception("Invalid numeric");
                res[i] = (byte) (val & 0xff);
            }
        } catch(Exception e) {
            throw new Exception("Invalid numeric in ip address (" + src + ")");
        }
        return res;
    }

    public static final InetAddress StrToInetAddress(String s) throws Exception {

        //if (s.matches("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$"))
        return InetAddress.getByAddress(textToNumericFormatV4(s));

        //throw new Exception("Invalid ip address <" + s + ">");
    }

}
