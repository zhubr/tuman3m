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


import java.util.ArrayList;
import java.nio.ByteBuffer;

import aq2db.*;


public final class Tum3Broadcaster {

    private static final int CONST_MIN_USERLIST_BYTES = 1024;
    private static final int CONST_USERLIST_SPACER = 64; // YYY

    private static final ArrayList<SrvLinkIntf> clientList = new ArrayList<SrvLinkIntf>();
    private static byte[] aux_userlist = null; // YYY
    private static int aux_userlist_approx = CONST_MIN_USERLIST_BYTES;
    private static int main_userlist_approx = CONST_MIN_USERLIST_BYTES; // YYY


    private final static class ListWalker {

        public byte[] tmp_arr;
        public ByteBuffer tmpList2 = null;
        public int tmp_ofs = 0, tmp_full_size = 0, tmp_pass = 0;
        public boolean tmp_no_room = true;

        public ListWalker(int _expected_main, int _expected_aux) {

            tmp_arr = new byte[_expected_main];
            if (0 != _expected_aux) tmpList2 = ByteBuffer.allocate(_expected_aux);

        }

        public void Begin() {

            tmp_pass++;
            tmp_no_room = false;

        }


        private void DoWalk(Tum3Db relevant_db, boolean with_interlink) {

            // Reminder. This loop is supposed to complete quickly, no delays inside!
            for (SrvLinkIntf thisClient: clientList) {
                byte[] tmp_bin_name = thisClient.GetBinaryUsername();
                Tum3Db tmp_main_db = thisClient.GetDb();
                if ((tmp_bin_name != null) && (tmp_main_db != null)) {
                    if ((tmp_main_db == relevant_db) || (tmp_main_db.GetMasterDb() == relevant_db) || (tmp_main_db == relevant_db.GetMasterDb())) {
                        tmp_full_size += tmp_bin_name.length + 1;
                        if (main_userlist_approx < tmp_full_size) main_userlist_approx = tmp_full_size; // YYY
                        if ((tmp_ofs + tmp_bin_name.length + 1) <= tmp_arr.length) {
                            System.arraycopy(tmp_bin_name, 0, tmp_arr, tmp_ofs, tmp_bin_name.length);
                            tmp_ofs += tmp_bin_name.length;
                            tmp_arr[tmp_ofs] = (byte)',';
                            tmp_ofs ++;
                        } else
                            tmp_no_room = true;
                    } 
                }
            }
            if (null != aux_userlist) if (aux_userlist.length > 0) { // YYY
                if (with_interlink) {
                        tmp_full_size += aux_userlist.length + 1;
                        if ((tmp_ofs + aux_userlist.length + 1) <= tmp_arr.length) {
                            System.arraycopy(aux_userlist, 0, tmp_arr, tmp_ofs, aux_userlist.length);
                            tmp_ofs += aux_userlist.length;
                            tmp_arr[tmp_ofs] = (byte)',';
                            tmp_ofs ++;
                        } else
                            tmp_no_room = true;
                } else if (null != tmpList2) if (tmpList2.capacity() >= aux_userlist.length) {
                    tmpList2.put(aux_userlist); // YYY
                }
            }
        }


        public ByteBuffer MakeBB() {

            ByteBuffer tmp_bb = ByteBuffer.wrap(tmp_arr);
            tmp_bb.position(tmp_ofs);
            return tmp_bb;

        }

        public boolean Reallocate() {

            if ((tmp_full_size == 0) || (tmp_full_size > 10000)) return false;
            tmp_arr = new byte[tmp_full_size*2];
            //System.out.println("[aq2j] DEBUG: GetUserList() allocated buff " + tmp_arr.length);
            return true;

        }


    }

    public final static void DistributeGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, SrvLinkIntf except_this) {

        DistributeGeneralEvent(origin_db, ev, except_this, null, null);

    }

    public final static void DistributeGeneralEvent(Tum3Db origin_db, GeneralDbDistribEvent ev, SrvLinkIntf except_this, String thisReceiverName, String thisEchoName) {

        synchronized(clientList) { 
            // Reminder. This loop is supposed to complete quickly, no delays inside!
            for (SrvLinkIntf thisClient: clientList) 
                if (except_this == thisClient) {
                    //System.out.println("[aq2j] DEBUG: DistributeGeneralEvent() skipped " + thisClient.DebugTitle());
                } else {
                    thisClient.AddGeneralEvent(origin_db, ev, thisReceiverName, thisEchoName);
                    //System.out.println("[aq2j] DEBUG: DistributeGeneralEvent() processed " + thisClient.DebugTitle());
                }
        }
    }

    public final static void DistributeFlag(Tum3Db origin_db) { // YYY

        synchronized(clientList) { 
            // Reminder. This loop is supposed to complete quickly, no delays inside!
            for (SrvLinkIntf thisClient: clientList) 
                if (thisClient.GetDb() == origin_db) {
                    thisClient.SetFlag();
                    //System.out.println("[aq2j] DEBUG: DistributeFlag() processed " + thisClient.DebugTitle());
                }
        }
    }

    public final static ByteBuffer GetUserList(Tum3Db relevant_db, boolean _with_aux) {

        int tmp_expected_length; // YYY
        synchronized(clientList) { // YYY
            tmp_expected_length = main_userlist_approx;
            if (_with_aux) tmp_expected_length += aux_userlist_approx;
        }

        ListWalker tmpWalker = new ListWalker(tmp_expected_length + CONST_USERLIST_SPACER, 0);

        while ((tmpWalker.tmp_pass < 2) && tmpWalker.tmp_no_room) {
            tmpWalker.Begin();
            synchronized(clientList) {
                tmpWalker.DoWalk(relevant_db, _with_aux); // YYY
            }

            if (!tmpWalker.tmp_no_room) return tmpWalker.MakeBB();
            if (!tmpWalker.Reallocate()) return null;
        }

        return null;

    }

    public final static int GetClientCount() {

        synchronized(clientList) { 
            return clientList.size();
        }

    }

    private final static void CompleteListNotification(Tum3Db origin_db, ListWalker tmpWalker, SrvLinkIntf thisClient, boolean with_interlink) {

        if (null != tmpWalker) {
            if (tmpWalker.tmp_no_room)
                if (tmpWalker.Reallocate()) {
                    tmpWalker.Begin();
                    synchronized(clientList) {
                        tmpWalker.DoWalk(origin_db, with_interlink);
                    }
                }
            if (!tmpWalker.tmp_no_room) {
                //ByteBuffer tmp_new_list = tmpWalker.MakeBB();
                Tum3Broadcaster.DistributeGeneralEvent(origin_db, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_USERLIST,
                    tmpWalker.MakeBB(), tmpWalker.tmpList2), thisClient); // YYY
            }
        }
    }

    public final static void addclient(Tum3Db origin_db, SrvLinkIntf thisClient) {

        int tmp_expected_length, tmp_expected_length2; // YYY
        synchronized(clientList) { // YYY
            tmp_expected_length = main_userlist_approx;
            tmp_expected_length2 = aux_userlist_approx;
        }

        ListWalker tmpWalker = null;
        if (thisClient.GetBinaryUsername() != null) tmpWalker = new ListWalker(tmp_expected_length + CONST_USERLIST_SPACER, tmp_expected_length2 + CONST_USERLIST_SPACER);

        if (null != tmpWalker) tmpWalker.Begin();
        synchronized(clientList) {
            clientList.add(thisClient); 
            if (null != tmpWalker) tmpWalker.DoWalk(origin_db, false);
        }
        CompleteListNotification(origin_db, tmpWalker, null, false);
    }

    public final static void release(Tum3Db origin_db, SrvLinkIntf thisClient) {

        int tmp_expected_length, tmp_expected_length2; // YYY
        synchronized(clientList) { // YYY
            tmp_expected_length = main_userlist_approx;
            tmp_expected_length2 = aux_userlist_approx;
        }

        ListWalker tmpWalker = null;
        if (thisClient.GetBinaryUsername() != null) tmpWalker = new ListWalker(tmp_expected_length + CONST_USERLIST_SPACER, tmp_expected_length2 + CONST_USERLIST_SPACER);

        if (null != tmpWalker) tmpWalker.Begin();
        synchronized(clientList) {
            clientList.remove(thisClient); 
            if (null != tmpWalker) tmpWalker.DoWalk(origin_db, false);
        }
        CompleteListNotification(origin_db, tmpWalker, null, false);

    }

    public final static void intercon_users(Tum3Db origin_db, SrvLinkIntf thisClient, byte[] _aux_userlist) { // YYY

        int tmp_expected_length; // YYY
        synchronized(clientList) { // YYY
            tmp_expected_length = main_userlist_approx + aux_userlist_approx;
        }

        ListWalker tmpWalker = new ListWalker(tmp_expected_length + CONST_USERLIST_SPACER, 0);

        tmpWalker.Begin();
        synchronized(clientList) { 
            aux_userlist = _aux_userlist;
            if (null != aux_userlist) if (aux_userlist_approx < aux_userlist.length) aux_userlist_approx = aux_userlist.length; // YYY
            tmpWalker.DoWalk(origin_db, true);
        }
        CompleteListNotification(origin_db, tmpWalker, thisClient, true);
    }


}
