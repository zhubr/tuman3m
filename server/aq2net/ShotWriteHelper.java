/*
 * Copyright 2011-2024 Nikolai Zhubr <zhubr@mail.ru>
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


import aq2db.*;


public class ShotWriteHelper implements ShotChangeMonitor {

    private final static int CONST_SHOT_UPD_NTFY_INTERVAL = 1000; // Note: also see CONST_TCP_CLIENT_WAKEINTERVAL and CONST_OUT_BUFF_WAIT_WAKEUP_SEC.
    private final static int CONST_PREV_SHOT_PURGE_SECONDS = 30; // 600; // Isn't 600 sec too long?
    private String curr_name = "";
    private volatile Tum3Shot curr_shot;
    private boolean FCanceledByUser = false;
    private ArrayList<Integer> curr_modified_ids, curr_removed_ids;
    private long first_update = 0, last_update = 0;
    private Tum3Db dbCurrLink;
    private volatile Thread correct_thrd;
    private SrvLinkIntf myself;
    private int quick_count = 0;

    public ShotWriteHelper(SrvLinkIntf _myself) {

        myself = _myself;
        correct_thrd = Thread.currentThread();

    }

    public Tum3Shot setShot(Tum3Shot _the_shot) throws Exception {

        if (Thread.currentThread() != correct_thrd) {
            curr_shot = null;
            _the_shot.ShotRelease();
            throw new Exception("ShotWriteHelper thread jam in setShot()");
        }

        if (curr_name.equals(_the_shot.getName())) {
            _the_shot.ShotRelease();
            return curr_shot;
        }

        flush();

        curr_shot = _the_shot;
        FCanceledByUser = false;
        dbCurrLink = curr_shot.GetDb();
        curr_name = curr_shot.getName();
        curr_modified_ids = new ArrayList<Integer>();
        curr_removed_ids = new ArrayList<Integer>();
        first_update = System.currentTimeMillis();
        last_update = first_update;
        quick_count = 0;
        return curr_shot;

    }

    public void AddUpdatedId(int _id, boolean _hurry, boolean _was_waiting, boolean _was_removed) throws Exception {

        //System.out.println("[DEBUG] AddUpdatedId: " + _id);

        if (Thread.currentThread() != correct_thrd) throw new Exception("ShotWriteHelper thread jam in AddUpdatedId()");

        if (_was_removed) {

            curr_removed_ids.add(_id);

        } else {

            if (_was_waiting) _id |= GeneralDbDistribEvent.ID_WAS_WAITING;

            if (_hurry && (quick_count < 2)) {
                quick_count++;
                Tum3Broadcaster.DistributeGeneralEvent(dbCurrLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TRACEUPD, curr_name, _id), null); // Note: using "myself" prevents proper updates in aq2net client+server mode!
            } else {
                curr_modified_ids.add(_id); // XXX TODO. Maybe check if already included?
            }
        }

        last_update = System.currentTimeMillis();

    }

    public void tick() throws Exception {

        //System.out.println(System.currentTimeMillis() + "[DEBUG] ShotWriteHelper.tick()");
        if (Thread.currentThread() != correct_thrd) throw new Exception("ShotWriteHelper thread jam in tick()");

        if (curr_shot == null) return;

        if ((System.currentTimeMillis() - first_update) > CONST_SHOT_UPD_NTFY_INTERVAL) {
            PushModifiedIds();
            first_update = System.currentTimeMillis();
        }

        if ((System.currentTimeMillis() - last_update) > 1000 * CONST_PREV_SHOT_PURGE_SECONDS) flush();

    }

    private void PushModifiedIds() {

        if (curr_modified_ids != null) {
            if (curr_modified_ids.size() > 0) {
                //System.out.println("[DEBUG] PushModifiedIds: " + curr_modified_ids.size());
                //for (int tmp_j=0; tmp_j < curr_modified_ids.size(); tmp_j++) { System.out.print(curr_modified_ids.get(tmp_j) + ","); }
                Tum3Broadcaster.DistributeGeneralEvent(dbCurrLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TRACEUPD_ARR, curr_name, curr_modified_ids), null); // Note: using "myself" prevents proper updates in aq2net client+server mode!
                curr_modified_ids = new ArrayList<Integer>(); // XXX Why not just .clear() ?
            }
            quick_count = 0;
        }
        if (curr_removed_ids != null) if (curr_removed_ids.size() > 0) {
            Tum3Broadcaster.DistributeGeneralEvent(dbCurrLink, new GeneralDbDistribEvent(GeneralDbDistribEvent.DB_EV_TRACEDEL_ARR, curr_name, curr_removed_ids), null);
            curr_removed_ids = new ArrayList<Integer>();  // XXX Why not just .clear() ?
        }

    }

    public void flush() throws Exception {

        if (Thread.currentThread() != correct_thrd) throw new Exception("ShotWriteHelper thread jam in flush()");

        if (curr_shot != null) {
            curr_shot.ShotRelease();
            PushModifiedIds();
        }
        curr_shot = null;
        curr_modified_ids = null;
        curr_removed_ids = null;
        curr_name = "";
        dbCurrLink = null;

    }

    public void CancelByUser() {

        FCanceledByUser = true;

    }

    public boolean WasCanceledByUser() {

        return FCanceledByUser;

    }

}
