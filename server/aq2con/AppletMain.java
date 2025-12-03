/*
 * Copyright 2011-2023 Nikolai Zhubr <zhubr@rambler.ru>
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
package aq2con;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.ArrayList;

import aq2db.*;
import aq2j.*;


public class AppletMain implements AppStopHook {

    private volatile boolean TerminateRequested = false;


    private static class VMShutdownHook extends Thread {
        public void run() {
            System.out.println("Test application ending...\n");
            AppStopHooker.ProcessAppStop();
        }
    }

    public AppletMain(String this_midlet_name) {
    }

    private void TestDate() {

        //Calendar cal1 = Calendar.getInstance();
        //Calendar cal2 = Calendar.getInstance(TimeZone.getTimeZone("GMT+3"));
        //System.out.println("In default TZ: " + ShowCal(cal1));
        //System.out.println("In -3 TZ: " + ShowCal(cal2));

        //Tum3Time t = new Tum3Time();
        //System.out.println("Tum3Time=<" + t.AsString() + ">");

    }

    private void DoWork() {
        System.out.println("Test application started :)\n");
        TestDate();
        Tum3cfg glb_cfg = Tum3cfg.getGlbInstance();
        Runtime.getRuntime().addShutdownHook(new VMShutdownHook());
        AppStopHooker.setAvailable();
        AppStopHooker.setTimeout(glb_cfg.getShutdownTimeout());

        AppStopHooker.AddHook(this);

        for (int tmp_i = 0; tmp_i < glb_cfg.getDbCount(); tmp_i++)
            if (glb_cfg.getDbTcpEnabled(tmp_i))
                new TumServTCP(new SessionProducerTcpStd(tmp_i)); // YYY

        while (!TerminateRequested) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) { }
        }

        AppStopHooker.RemoveHook(this);
    }

    public static void main(String args[]) {

        String my_name = "";
        if (args.length > 0) my_name = args[0];
        if (my_name == null) my_name = "";
        new AppletMain(my_name).DoWork();

    }

    public void do_app_destroy() {

        System.exit(0);

    }

    public void AppStopped() {

        TerminateRequested = true;

    }
}
