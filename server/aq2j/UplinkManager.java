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


public class UplinkManager {

    public final static int CONST_UGC_MAX_QUEUE = 100; // XXX TODO Make this user-configurable?
    public final static int CONST_UGC_FWD_TIMEOUT_SEC = 8; // XXX TODO Make this user-configurable?


    private static class LazyUplinkManagerHolder {

        public static UplinkManager Instance = new UplinkManager();

    }

    public interface UplinkOperations {

        public String ExecuteUgc(TempUGCReqHolder holder) throws Exception;

    }

    private final class ReqTimeoutChecker extends Thread {

        public ReqTimeoutChecker() {

            setDaemon(true);
            start();
        }

        public void run() {

            try {
                while (true) {
                    Tum3Util.SleepExactly(1000);
                    //Tum3Logger.DoLogGlb(true, "ReqTimeoutChecker TICK!!!!!!!!!");
                    long tmp_now_millis = System.currentTimeMillis();
                    for (int db_index = 0; db_index < Tum3cfg.getGlbInstance().getDbCount(); db_index++) {
                        int tmp_out_count = 0;
                        synchronized (queue_lock[db_index]) {
                            for (int j = 0; j < queue_len[db_index]; j++)
                                if ((tmp_now_millis - queue[db_index][j].start_millis) > 1000*2*CONST_UGC_FWD_TIMEOUT_SEC) {
                                    queue_out[tmp_out_count] = queue[db_index][j];
                                    tmp_out_count++;
                                    for (int i = j+1; i < queue_len[db_index]; i++)
                                        queue[db_index][i-1] = queue[db_index][i];
                                    queue_len[db_index]--;
                                }
                        }
                        for (int i = 0; i < tmp_out_count; i++) {
                            //Tum3Logger.DoLogGlb(true, "[DEBUG] ReqTimeoutChecker refusing <" + queue_out[i].shot_num + "><" + queue_out[i].req_id + ">");
                            queue_out[i].Refuse("Uplink forward timed out");
                            queue_out[i] = null;
                        }
                    }
                }
            } catch (Exception e) {
                Tum3Logger.DoLogGlb(true, "IMPORTANT: ReqTimeoutChecker has died with exception " + Tum3Util.getStackTrace(e));
            }
        }

    }

    private volatile UplinkOperations curr_instance[] = new UplinkOperations[Tum3cfg.getGlbInstance().getDbCount()]; // YYY
    private final Object inst_lock[] = new Object[Tum3cfg.getGlbInstance().getDbCount()]; // YYY
    private final Object queue_lock[] = new Object[Tum3cfg.getGlbInstance().getDbCount()]; // YYY
    private final TempUGCReqHolder queue[][] = new TempUGCReqHolder[Tum3cfg.getGlbInstance().getDbCount()][CONST_UGC_MAX_QUEUE];
    private final TempUGCReqHolder queue_out[] = new TempUGCReqHolder[CONST_UGC_MAX_QUEUE];
    private final int queue_len[] = new int[Tum3cfg.getGlbInstance().getDbCount()];
    private final ReqTimeoutChecker checker = new ReqTimeoutChecker();
    private AtomicInteger glb_fwd_id_counter = new AtomicInteger(0);


    public UplinkManager() {
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
    private String ExecuteUgc_intrn(int _db_index, UgcReplyHandlerExt _the_link, String _shot_num, int _req_id, byte[] _upd_arr)  throws Exception {

        UplinkOperations tmp_uplink = getUplink(_db_index);
        if (null == tmp_uplink)
            return "Uplink not found";
        else {
            TempUGCReqHolder tmp_new_holder = new TempUGCReqHolder(glb_fwd_id_counter.incrementAndGet(), _db_index, _the_link, _shot_num, _req_id, _upd_arr);
            String tmp_err_msg = tmp_uplink.ExecuteUgc(tmp_new_holder /* _the_link, _shot_num, _req_id, _upd_arr */);
            if (null != tmp_err_msg) if (!tmp_err_msg.isEmpty())
                return tmp_err_msg;
            TempUGCReqHolder old_req = null;
            synchronized (queue_lock[_db_index]) {

                if (queue_len[_db_index] >= CONST_UGC_MAX_QUEUE) {
                    old_req = queue[_db_index][0];
                    for (int i = 1; i < queue_len[_db_index]; i++)
                        queue[_db_index][i-1] = queue[_db_index][i];
                    queue_len[_db_index]--;
                }
                queue[_db_index][queue_len[_db_index]] = tmp_new_holder;
                queue_len[_db_index]++;

            }
            if (null != old_req) {
                old_req.Refuse("Uplink queue overflow");
            }
            return "";
        }
    }

    private void DoneUgc_intrn(int _db_index, int _glb_fwd_id) {

        TempUGCReqHolder old_req = null;
        synchronized (queue_lock[_db_index]) {
            for (int j = 0; j < queue_len[_db_index]; j++)
                if (queue[_db_index][j].glb_fwd_id == _glb_fwd_id) {
                    old_req = queue[_db_index][j];
                    for (int i = j+1; i < queue_len[_db_index]; i++)
                        queue[_db_index][i-1] = queue[_db_index][i];
                    queue_len[_db_index]--;
                    break;
                }
        }
    }

    public static String ExecuteUgc(int _db_index, UgcReplyHandlerExt _the_link, String _shot_num, int _req_id, byte[] _upd_arr) throws Exception {

        return LazyUplinkManagerHolder.Instance.ExecuteUgc_intrn(_db_index, _the_link, _shot_num, _req_id, _upd_arr);

    }

    public static void DoneUgc(int _db_index, int _glb_fwd_id) throws Exception {

        LazyUplinkManagerHolder.Instance.DoneUgc_intrn(_db_index, _glb_fwd_id);

    }

}
