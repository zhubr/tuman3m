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
package aq2db;


import java.util.ArrayList;


public class AppStopHooker {


    private volatile boolean is_available = false;
    private volatile int shutdown_timeout;
    private volatile ArrayList<AppStopHook> hook_list = new ArrayList<AppStopHook>();


    private static class LazyHookerHolder {
        public static AppStopHooker hooker_instance = new AppStopHooker();
    }

    private static AppStopHooker getInstance() {
        return LazyHookerHolder.hooker_instance;
    }

    public static void setAvailable() {
        getInstance().is_available = true;
    }

    public static void setTimeout(int timeout) {
        getInstance().shutdown_timeout = timeout;
    }

    public static boolean isAvailable() {
        return getInstance().is_available;
    }

    private synchronized void AddHookInternal(AppStopHook hook) {

        hook_list.add(hook);

    }

    private synchronized void RemoveHookInternal(AppStopHook hook) {

        if (hook != null) hook_list.remove(hook);

    }

    private synchronized void ProcessAppStopInternal() {

        for (AppStopHook hook: hook_list) { hook.AppStopped(); }

    }

    private synchronized boolean AllUnhooked() {

        return hook_list.size() == 0;

    }

    public static void AddHook(AppStopHook hook) {

        getInstance().AddHookInternal(hook);

    }

    public static void RemoveHook(AppStopHook hook) {

        getInstance().RemoveHookInternal(hook);

    }

    public static void ProcessAppStop() {

        //System.out.println("[DEBUG] +ProcessAppStop()...");
        getInstance().ProcessAppStopInternal();

        long millis0 = System.currentTimeMillis();
        int timeout = getInstance().shutdown_timeout;
        boolean ok = false;
        //System.out.println("[DEBUG] ProcessAppStop() #02...");
        do {
            //System.out.println("[DEBUG] ProcessAppStop() #03...");
            try { Thread.sleep(500); } catch (Exception ignored) { }
            //System.out.println("[DEBUG] ProcessAppStop() #04...");
            if (getInstance().AllUnhooked()) {
                //System.out.println("[DEBUG] ProcessAppStop() all unhooked!");
                ok = true;
                break;
            }
        } while ((System.currentTimeMillis() - millis0) < timeout);
        if (ok) ; // System.out.println("[DEBUG] -ProcessAppStop() success.");
        else    System.out.println("[DEBUG] -ProcessAppStop() FAILED!");

    }

}
