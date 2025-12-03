/*
 * Copyright 2025 Nikolai Zhubr <zhubr@mail.ru>
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
package aq3svccompat;

import java.util.*;
import java.io.*;
import java.nio.*;

import aq2db.AppStopHook;
import aq2db.AppStopHooker;

import compatdef.CompatBase;
import compatdef.CompatLogger;
import compatwrk.CompatWorker;

import aq2db.*;


public class CompatRunner implements Runnable, AppStopHook {

    private Object WaitObj = new Object();
    private volatile boolean is_terminating = false;
    private Thread my_thread;

    private ArrayList<IntrnlMsgAny> cmd_queue_curr = new ArrayList<IntrnlMsgAny>();
    private ArrayList<IntrnlMsgAny> cmd_queue_spare = new ArrayList<IntrnlMsgAny>();

    private LoggerWrapper logger;
    private Tum3Db dbLink;
    private CompatBase worker;

    static public class LoggerWrapper implements CompatLogger {

        private String db_name;

        public LoggerWrapper(String _db_name) {
            db_name = _db_name;
        }

        public void DoLog(boolean _IsCritical, String _MsgText) {
            Tum3Logger.DoLog(db_name, true, _MsgText);
        }
    }

    abstract class IntrnlMsgAny {

        public abstract void DoHandle();

    }

    private class MsgDataChanged extends IntrnlMsgAny {

        String shot_num, file_name;
        int signal_id;
        boolean is_volatile, was_deleted;

        public MsgDataChanged(String _shot_num, int _signal_id, boolean _is_volatile, boolean _was_deleted, String _file_name) {

            shot_num = _shot_num;
            signal_id = _signal_id;
            is_volatile = _is_volatile;
            was_deleted = _was_deleted;
            file_name = _file_name;

        }

        public void DoHandle() {

            worker.ProcessNewData(shot_num, signal_id, is_volatile, was_deleted, file_name);

        }

    }

    public CompatRunner(Tum3Db _dbLink, Properties config_props) {

        dbLink = _dbLink;
        logger = new LoggerWrapper(dbLink.DbName());
        worker = new CompatWorker(logger, config_props);
        my_thread = new Thread(this);
        my_thread.start();

    }

    public void run() {

        logger.DoLog(false, "Created CompatRunner.");

        AppStopHooker.AddHook(this);

        while (!is_terminating) {

            synchronized(WaitObj) {
                try {
                    WaitObj.wait(500);
                    if (cmd_queue_curr.size() > 0) {
                        ArrayList<IntrnlMsgAny> tmp_queue = cmd_queue_curr;
                        cmd_queue_curr = cmd_queue_spare;
                        cmd_queue_spare = tmp_queue;
                    }
                } catch(Exception e) { }
            }

            if (is_terminating) break;

            //Tick();

            if (is_terminating) break;

            if (cmd_queue_spare.size() > 0) HandleMessages(cmd_queue_spare);
        }

        AppStopHooker.RemoveHook(this);

        logger.DoLog(false, "Exiting CompatRunner normally.");
    }

    private void post(IntrnlMsgAny msg) {
        synchronized(WaitObj) {
            cmd_queue_curr.add(msg);
            WaitObj.notify();
        }
    }

    private void HandleMessages(ArrayList<IntrnlMsgAny> tmp_queue) {
        while (tmp_queue.size() > 0) {
            tmp_queue.get(0).DoHandle();
            tmp_queue.remove(0);
        }
    }

    private void Terminate() { // YYY

        is_terminating = true;
        synchronized(WaitObj) {
            WaitObj.notify();
        }

    }

    public void AppStopped() {

        is_terminating = true;

    }

    public void AllocAltShotNum(String shot_num) throws Exception {

        worker.AllocAltShotNum(shot_num);

    }

    public void ApplyAltShotNum(String shot_num) throws Exception {

        worker.ApplyAltShotNum(shot_num);

    }

    public void CompatProcessData(String shot_num, int signal_id, boolean is_volatile, boolean was_deleted, String file_name) {

        if (!worker.QuicklyCheck(shot_num, signal_id, is_volatile, was_deleted)) return;
        post(new MsgDataChanged(shot_num, signal_id, is_volatile, was_deleted, file_name));

    }

}
