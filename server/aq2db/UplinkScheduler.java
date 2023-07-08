/*
 * Copyright 2022-2023 Nikolai Zhubr <zhubr@mail.ru>
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


public class UplinkScheduler extends Thread 
{

    private static final int QUICK_RETRIES_SECONDS = 60; // XXX Make it configurable eventually?
    private static final int SLOW_RETRIES_MINUTES = 60; // XXX Make it configurable eventually?
    private static final int QUICK_FAILS_LIMIT = 3; // XXX Make it configurable eventually?
    private static final int WAKEUP_PERIOD = 5000; // XXX Make it configurable eventually?

    private String db_name;
    private InterconInitiator initiator;
    private boolean is_connected = false;
    private int fail_count = 0, short_conns_count = 0;
    private long last_millis, last_connected;
    //private long discon_millis[] = new long[QUICK_FAILS_LIMIT];


    public UplinkScheduler(InterconInitiator _initiator, Tum3Db _db_link) {
        //_initiator.ConnectToServer();

        db_name = _db_link.DbName();
        initiator = _initiator;
        setDaemon(true);

    }

    private void setConnected(boolean new_val) {

        if (new_val == is_connected) return;
        is_connected = new_val;
        last_millis = System.currentTimeMillis();
        if (is_connected) fail_count = 0;
        else {
            if ((last_millis - last_connected) < QUICK_RETRIES_SECONDS*1000) {
                if (short_conns_count <= QUICK_FAILS_LIMIT) short_conns_count++;
            } else short_conns_count = 0;

//            for (int i = 0; i < (QUICK_FAILS_LIMIT-1); i++)
//                discon_millis[i] = discon_millis[i+1];
//            discon_millis[QUICK_FAILS_LIMIT-1] = last_millis;
        }

    }

    private void TryConnect() {

        try {
          Tum3Logger.DoLog(db_name, false, "Connecting to uplink server...");
          if (initiator.ConnectToServer()) {
              Tum3Logger.DoLog(db_name, false, "Connected to uplink server.");
              last_millis = System.currentTimeMillis();
              last_connected = last_millis;
              setConnected(true);
          } else {
              Tum3Logger.DoLog(db_name, false, "Connect to uplink server failed with: " + initiator.getDisconnReason());
              if (fail_count <= QUICK_FAILS_LIMIT) fail_count++;
              last_millis = System.currentTimeMillis();
              setConnected(false);
          }
        } catch (Exception e) {
              Tum3Logger.DoLog(db_name, false, "Connect to uplink server raised exception: " + e.toString());
              if (fail_count <= QUICK_FAILS_LIMIT) fail_count++;
              last_millis = System.currentTimeMillis();
              setConnected(false);
        }

    }

    private boolean isLongEnough() {

        if ((fail_count < QUICK_FAILS_LIMIT)
//         && ((discon_millis[0] + SLOW_RETRIES_MINUTES*60000) <= System.currentTimeMillis()))
         && (short_conns_count < QUICK_FAILS_LIMIT))
            return (System.currentTimeMillis() - last_millis) > (QUICK_RETRIES_SECONDS*1000);
        else return (System.currentTimeMillis() - last_millis) > (SLOW_RETRIES_MINUTES*60000);

    }

    @Override
    public void run() {

        last_millis = System.currentTimeMillis() - QUICK_RETRIES_SECONDS*1000*QUICK_FAILS_LIMIT*2;
        last_connected = last_millis - SLOW_RETRIES_MINUTES*60000*2;
        //for (int i = 0; i < QUICK_FAILS_LIMIT; i++)
        //    discon_millis[i] = last_millis - SLOW_RETRIES_MINUTES*60000*2;

        //Tum3Logger.DoLog(db_name, true, "[DEBUG] UplinkScheduler starting...");

        do {
            if (!is_connected && isLongEnough()) TryConnect();
            Tum3Util.SleepExactly(WAKEUP_PERIOD);
            if (initiator.csConnected == initiator.getConnStatus())
                setConnected(true);
            else if (initiator.csDisconnected == initiator.getConnStatus())
                setConnected(false);
        } while (true);

    }

    public void WorkStart() {

        start();

    }

}
