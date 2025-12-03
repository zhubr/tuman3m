/*
 * Copyright 2022-2023 Nikolai Zhubr <zhubr@rambler.ru>
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

import java.util.concurrent.atomic.AtomicInteger;

import aq2db.*;
import aq2net.*;


public class UplinkBulkMgr {


    private static class LazyUplinkManagerHolder {

        public static UplinkBulkMgr Instance = new UplinkBulkMgr();

    }

    public interface UplinkOperations {

        public String ExecuteCmdLine(String _cmdline) throws Exception;

    }

    private volatile UplinkOperations curr_instance[] = new UplinkOperations[Tum3cfg.getGlbInstance().getDbCount()]; // YYY
    private final Object inst_lock[] = new Object[Tum3cfg.getGlbInstance().getDbCount()]; // YYY
    private final Object queue_lock[] = new Object[Tum3cfg.getGlbInstance().getDbCount()]; // YYY
    private AtomicInteger glb_fwd_id_counter = new AtomicInteger(0);


    public UplinkBulkMgr() {
        for (int db_index = 0; db_index < Tum3cfg.getGlbInstance().getDbCount(); db_index++) {
            inst_lock[db_index] = new Object();
            queue_lock[db_index] = new Object();
        }
    }

    private UplinkOperations getUplink_intrn(int _db_index) {

        synchronized (inst_lock[_db_index]) {
            return curr_instance[_db_index];
        }

    }

    public static UplinkOperations getUplink(int _db_index) {

        return LazyUplinkManagerHolder.Instance.getUplink_intrn(_db_index);

    }

    private void setUplink_intrn(int _db_index, UplinkOperations instance) throws Exception {

        synchronized (inst_lock[_db_index]) {
            if (null == curr_instance[_db_index]) {
                curr_instance[_db_index] = instance;
            }
        }

    }

    public static void setUplink(int _db_index, UplinkOperations instance) throws Exception {

        LazyUplinkManagerHolder.Instance.setUplink_intrn(_db_index, instance);

    }

    private void resetUplink_intrn(int _db_index, UplinkOperations instance) {

        synchronized (inst_lock[_db_index]) {
            if (instance == curr_instance[_db_index]) curr_instance[_db_index] = null;
        }

    }

    public static void resetUplink(int _db_index, UplinkOperations instance) {

        LazyUplinkManagerHolder.Instance.resetUplink_intrn(_db_index, instance);

    }

    // Reminder. This method gets called in heavily multithreaded context.
    private String ExecuteCmdLine_intrn(int _db_index, UgcReplyHandlerExt _the_link, String _cmdline) throws Exception {

        UplinkOperations tmp_uplink = getUplink(_db_index);
        if (null == tmp_uplink)
            return "Uplink not found";
        else {
            return "Commands not implemented yet";
            // XXX TODO.
/*
            TempUGCReqHolder tmp_new_holder = new TempUGCReqHolder(glb_fwd_id_counter.incrementAndGet(), _db_index, _the_link, _shot_num, _req_id, _upd_arr);
            String tmp_err_msg = tmp_uplink.ExecuteCmdLine(tmp_new_holder);
            if (null != tmp_err_msg) if (!tmp_err_msg.isEmpty())
                return tmp_err_msg;
            TempUGCReqHolder old_req = null;
            if (null != old_req) {
                old_req.Refuse("Uplink queue overflow");
            }
            return "";
*/
        }
    }

    private void DoneCmdline_intrn(int _db_index, String _cmdresult) {

        synchronized (queue_lock[_db_index]) {
            // XXX TODO.
        }
    }

    public static String ExecuteCmdLine(int _db_index, UgcReplyHandlerExt _the_link, String _cmdline) throws Exception {

        return LazyUplinkManagerHolder.Instance.ExecuteCmdLine_intrn(_db_index, _the_link, _cmdline);

    }

    public static void DoneCmdline(int _db_index, String _cmdresult) throws Exception {

        LazyUplinkManagerHolder.Instance.DoneCmdline_intrn(_db_index, _cmdresult);

    }

}
