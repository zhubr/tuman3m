/*
 * Copyright 2011-2023 Nikolai Zhubr <zhubr@mail.ru>
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


import java.util.*;
import java.nio.ByteBuffer;


public class GeneralDbDistribEvent {

    public final static int DB_EV_TALK = 1;
    public final static int DB_EV_NEWSHOT = 2;
    public final static int DB_EV_TRACEUPD = 3;
    public final static int DB_EV_TRACEUPD_ARR = 4;
    public final static int DB_EV_TRACEDEL_ARR = 5;
    public final static int DB_EV_USERLIST = 6;

    public final static int ID_WAS_WAITING = (1 << 30);
    public final static int ID_MASK = 0x1FFFFFFF;

    public final static int IS_MASTER_ONLY = 1;

    private int ev_type;
    private String ev_str;
    private int ev_int = 0; // YYY
    private int[] ev_int_ar;
    private ByteBuffer ev_bb;


    public GeneralDbDistribEvent(int _ev_type, String _ev_str) {

        ev_type = _ev_type;
        ev_str = _ev_str;

    }

    public GeneralDbDistribEvent(int _ev_type, ByteBuffer _ev_bb) {

        ev_type = _ev_type;
        ev_bb = _ev_bb;

    }

    public GeneralDbDistribEvent(int _ev_type, String _ev_str, int _ev_int) {

        ev_type = _ev_type;
        ev_str = _ev_str;
        ev_int = _ev_int;

    }

    public GeneralDbDistribEvent(int _ev_type, String _ev_str, List<Integer> _ev_int_ar) {

        ev_type = _ev_type;
        ev_str = _ev_str;
        ev_int_ar = new int[_ev_int_ar.size()];
        for (int tmp_i = 0; tmp_i < _ev_int_ar.size(); tmp_i++) ev_int_ar[tmp_i] = _ev_int_ar.get(tmp_i);

    }

    public int get_type() {

        return ev_type;

    }

    public String get_str() {

        return ev_str;

    }

    public int get_int() {

        return ev_int;

    }

    public int[] get_int_ar() {

        return ev_int_ar;

    }

    public ByteBuffer get_bb() {

        return ev_bb;

    }

}
