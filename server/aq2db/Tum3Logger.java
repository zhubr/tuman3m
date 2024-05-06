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
package aq2db;


import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;


public class Tum3Logger implements Runnable, AppStopHook {


    private final static boolean const_strategy_neverblock = false;
    private final static int const_block_lines_flush = 200;
    private final static int const_block_lines_heavy = 1000;
    private final static int const_block_lines_limit = 2000;
    private final static int CONST_WRITER_WAKEUP_INTERVAL = 500;
    private final static int CONST_LOG_BLOCKER_WAKEUP_MILLIS = 100;
    private final static int CONST_LASTDATE_FLUSH_MINUTES = 120; // YYY
    private final static String log_format_string = "yyyy-MM-dd HH:mm:ss.SSS";
    private final static String log_fname_string = "yyyy-MM";
    private final static String log_fulldate_str = "yyyy-MM-dd"; // YYY
    private final static String const_log_prefix = "[aq2j] ";
    private final static String const_log_fname_prefix = "aq2j-"; // YYY
    private final static String const_global = "global";
    private final static String TUM3_CFG_log_path = "log_path";
    private final SimpleDateFormat log_time_format = new SimpleDateFormat(log_format_string);
    private final SimpleDateFormat log_fname_format = new SimpleDateFormat(log_fname_string);
    private final SimpleDateFormat log_fulldate_format = new SimpleDateFormat(log_fulldate_str); // YYY
    private final String newline = System.lineSeparator();

    private final String log_file_path;
    private String last_fname = "";
    private volatile boolean with_file;
    private final Object log_lock = new Object();
    private final Object room_avail_wait = new Object();
    private Calendar log_cal = Calendar.getInstance(), fname_cal = Calendar.getInstance();
    private volatile ArrayList<String> buff_filling = new ArrayList<String>(), buff_flushing = new ArrayList<String>();
    private volatile boolean error_reported = false;
    private volatile boolean suspension_reported = false;
    private volatile boolean is_terminating = false;

    private FileOutputStream fos = null;
    private OutputStreamWriter osw = null;

    private volatile AdditionalNotifier aux_warnings = null;
    private final Object aux_warnings_lock = new Object();

    private boolean lastdate_open = false, lastdate_stor_failed = false; // YYY
    private RandomAccessFile lastdate_raf; // YYY
    private static volatile boolean bogus_clock = false; // YYY
    private Calendar lastdate_cal = Calendar.getInstance(); // YYY
    private long lastdate_next_flush = 0; // YYY


    public final static boolean BogusClockDetected() { // YYY

        return bogus_clock;

    }

    public interface AdditionalNotifier {

        public void _NewMessageBox(String txt);

    }


    private static class Tum3LoggerWrapper {

        public Tum3Logger logger;

        public Tum3LoggerWrapper() {

            String tmp_path = Tum3cfg.getGlbParValue(TUM3_CFG_log_path);
            logger = new Tum3Logger(tmp_path);
            if (!tmp_path.isEmpty()) {
                new Thread(logger).start();
                logger.DoLog_internal("-", false, "Logging started, ver " + a.CurrentVerNum, const_global, false, false);
            }

        }

    }

    private static class LazyLgrHolder {

        private static Tum3LoggerWrapper lgrInstance = new Tum3LoggerWrapper();

    }

    public Tum3Logger(String _the_path) {

        log_file_path = _the_path;
        with_file = !log_file_path.isEmpty();

    }

    public void AppStopped() {

        is_terminating = true;

    }

    private void MaybeUpdateLastDate(long _curr_millis) {

        try {
            lastdate_raf.seek(0);
            lastdate_cal.setTimeInMillis(_curr_millis);
            String tmp_date_line = log_fulldate_format.format(lastdate_cal.getTime());
            byte tmp_b[] = Tum3Util.StringToBytesRaw(tmp_date_line);
            lastdate_raf.write(tmp_b);
            //System.out.println("[DEBUG] bum!");
            lastdate_raf.setLength(lastdate_raf.getFilePointer());
            lastdate_next_flush = _curr_millis + (long)60000 * CONST_LASTDATE_FLUSH_MINUTES;
        } catch (Exception e) {
            lastdate_stor_failed = true;
            DoLog_internal("-", true, "IMPORTANT! Logging of last date failed with <" + e.toString() + ">", const_global, false, false);
        }
    }

    private void OpenLog() throws Exception {

        if (with_file && !lastdate_open && !lastdate_stor_failed && !bogus_clock)
            try {
              lastdate_stor_failed = true;
              lastdate_raf = new RandomAccessFile(log_file_path + const_log_fname_prefix + "lastdate.log", "rw"); // YYY
              lastdate_open = true;
              lastdate_stor_failed = false;
              if (lastdate_raf.length() >= 10) {
                  byte tmp_b[] = new byte[10];
                  String tmp_prev_date;
                  lastdate_raf.readFully(tmp_b);
                  tmp_prev_date = Tum3Util.BytesToStringRaw(tmp_b);
                  if ((tmp_prev_date.charAt(4) == '-') && (tmp_prev_date.charAt(7) == '-')) {
                      int tmp_year, tmp_month, tmp_day;
                      try {
                          tmp_year = Integer.parseInt(tmp_prev_date.substring(0, 4));
                          tmp_month = Integer.parseInt(tmp_prev_date.substring(5, 7));
                          tmp_day = Integer.parseInt(tmp_prev_date.substring(8, 10));
                          //System.out.println("[DEBUG] Last date log: tmp_year=" + tmp_year + " tmp_month=" + tmp_month + " tmp_day=" + tmp_day);
                          if (new Tum3Time(tmp_year, tmp_month, tmp_day, 0, 0, 0).IsInFuture()) {
                              //System.out.println("[DEBUG] Last date log: Bogus clock!");
                              bogus_clock = true;
                              lastdate_stor_failed = true;
                              lastdate_raf.close();
                              lastdate_raf = null;
                              lastdate_open = false;
                          }
                      } catch (Exception ignored) {}
                  }
              }
            } catch (Exception e) {
              lastdate_stor_failed = true;
              DoLog_internal("-", true, "IMPORTANT! Opening log of last date failed with <" + e.toString() + ">", const_global, false, false);
            }

        if (with_file && !bogus_clock) { // YYY
            fos = new FileOutputStream(log_file_path + const_log_fname_prefix + last_fname + ".log", true);
            osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        }

    }

    private void FlushLog() throws Exception {

        if ((null != osw) && !bogus_clock) { // YYY
            for (String tmp_st: buff_flushing) { osw.write(tmp_st); osw.write(newline); }
            osw.flush();
        }
        if (buff_filling.size() >= const_block_lines_heavy)
            synchronized(room_avail_wait) { room_avail_wait.notifyAll(); }

    }

    private void CloseLog() throws Exception {

        if (osw != null) {
            osw.close();
            osw = null;
        }
        if (fos != null) {
            fos.close();
            fos = null;
        }
    }

    private void DoSwapLists() {
        if (buff_filling.size() > 0) {
            ArrayList<String> tmp_queue = buff_filling;
            buff_filling = buff_flushing;
            buff_flushing = tmp_queue;
        }
    }

    public void run() {

        AppStopHooker.AddHook(this);

        while (!is_terminating) {

            try {
                CheckAndFlush();
            } catch (Exception e) {
                if (!error_reported) {
                    error_reported = true;
                    println("IMPORTANT: Logger error: " + Tum3Util.getStackTrace(e));
                }
            }

            if (is_terminating) break;

            synchronized(log_lock) {
                try {
                    log_lock.wait(CONST_WRITER_WAKEUP_INTERVAL);
                    DoSwapLists();
                } catch(Exception e) { }
            }

            if (lastdate_open && !lastdate_stor_failed && !bogus_clock) {
                long tmp_millis = System.currentTimeMillis();
                if (tmp_millis > lastdate_next_flush) MaybeUpdateLastDate(tmp_millis);
            }
        }

        if (lastdate_open && !lastdate_stor_failed && !bogus_clock)
            MaybeUpdateLastDate(System.currentTimeMillis());

        if (lastdate_open && (null != lastdate_raf))
            try {
                lastdate_open = false;
                lastdate_raf.close(); // YYY
            } catch (Exception e) {
                DoLog_internal("-", true, "IMPORTANT! Closing log of last date failed with <" + e.toString() + ">", const_global, false, true); // YYY
            }

        try {

            DoLog_internal("-", false, "Logging stopped, ver " + a.CurrentVerNum, const_global, false, true); // YYY
            //System.out.println("[DEBUG] Logger {Logging stopped.} passed, size=" + buff_filling.size());
            CheckAndFlush();
            synchronized(log_lock) { DoSwapLists(); } // YYY
            CheckAndFlush(); // YYY
            CloseLog();

        } catch (Exception e) {
            if (!error_reported) {
                error_reported = true;
                println("IMPORTANT: Logger error: " + Tum3Util.getStackTrace(e));
            }
        }

        AppStopHooker.RemoveHook(this);

    }

    private void CheckAndFlush() throws Exception {

        if (buff_flushing.size() > 0) {

            fname_cal.setTimeInMillis(System.currentTimeMillis());
            String tmp_needed_fname = log_fname_format.format(fname_cal.getTime());
            if (!tmp_needed_fname.equals(last_fname)) {
                CloseLog();
                last_fname = tmp_needed_fname;
                OpenLog();
                if (error_reported) {
                    Calendar tmp_cal = Calendar.getInstance();
                    SimpleDateFormat tmp_time_format = new SimpleDateFormat(log_format_string);
                    long tmp_thrd_id = Thread.currentThread().getId();
                    buff_flushing.add(0, 
                            tmp_time_format.format(tmp_cal.getTime())
                            + " % {" + tmp_thrd_id + "} % <-:" + const_global + "> % IMPORTANT: Please see logger error(s) in earlier log files."
                            );
                }
            }
            FlushLog();
            buff_flushing.clear();

        }

    }

    private void DoLog_internal(String _DbName, boolean _IsCritical, String _MsgText, String _MsgScope, boolean _disable_aux, boolean _disregard_terminating) {

        long tmp_millis = System.currentTimeMillis();
        long tmp_thrd_id = Thread.currentThread().getId();
        String tmp_full_line = null;
        boolean tmp_finished = false;

        if (is_terminating && !_disregard_terminating) {
            if (_IsCritical) {
                Calendar tmp_log_cal = Calendar.getInstance();
                tmp_log_cal.setTimeInMillis(tmp_millis);
                tmp_full_line = 
                    log_time_format.format(tmp_log_cal.getTime())
                    + " % {" + tmp_thrd_id + "} % <"+ _DbName + ":" + _MsgScope + "> % " + _MsgText;
                System.out.println(const_log_prefix + tmp_full_line); // YYY
            }
            return; // YYY
        }

        if (_IsCritical && !_disable_aux) synchronized(aux_warnings_lock) {
            if (aux_warnings != null) aux_warnings._NewMessageBox("<" + _MsgScope + "> " + Tum3Util.JavaStrToTum3Str(_MsgText));
        }

        synchronized(log_lock) {

            log_cal.setTimeInMillis(tmp_millis);

            tmp_full_line = 
                    log_time_format.format(log_cal.getTime())
                    + " % {" + tmp_thrd_id + "} % <"+ _DbName + ":" + _MsgScope + "> % " + _MsgText;

            if (_IsCritical) System.out.println(const_log_prefix + tmp_full_line);

            if (buff_filling.size() < const_block_lines_limit) {
                buff_filling.add(tmp_full_line);
                tmp_finished = true;
                if (buff_filling.size() > const_block_lines_flush)
                    log_lock.notify();
            } else if (const_strategy_neverblock) {
                tmp_finished = true;
                if (!error_reported) {
                    error_reported = true;
                    System.out.println(const_log_prefix + log_time_format.format(log_cal.getTime())
                            + " % {" + tmp_thrd_id + "} % <-:" + const_global + "> % IMPORTANT: Log buffer overflow, started dropping.");
                }
            }

        }

        if (tmp_finished || const_strategy_neverblock) return;

        while (!tmp_finished) {

            synchronized(room_avail_wait) {
                try {
                    room_avail_wait.wait(CONST_LOG_BLOCKER_WAKEUP_MILLIS); 
                    // Note. This is not exactly race-safe, i.e. buff_filling might 
                    //  already be empty at the time of entering wait state here, but
                    //  this is not very bad anyway because we only get here in case
                    //  our file writer is overloaded, so such extra delay to the caller
                    //  would ease the life of writer and therefore lead to more
                    //  balanced operation (at the cost of maybe slowing down application
                    //  to some extent).
                } catch(Exception e) { }
            }

            synchronized(log_lock) {
                if (buff_filling.size() < const_block_lines_limit) {
                    buff_filling.add(tmp_full_line);
                    tmp_finished = true;
                    if (!suspension_reported) {
                        suspension_reported = true;
                        log_cal.setTimeInMillis(tmp_millis);
                        tmp_full_line = 
                                log_time_format.format(log_cal.getTime())
                                + " % {" + tmp_thrd_id + "} % <-:" + const_global + "> % IMPORTANT: Log buffer overflow, started suspending callers.";
                        System.out.println(const_log_prefix + tmp_full_line);
                    }
                    if (buff_filling.size() > const_block_lines_flush)
                        log_lock.notify();
                }

            }

        }

    }

    private static Tum3Logger getInstance() {

        return LazyLgrHolder.lgrInstance.logger;

    }

    public static final void DoLog(String _DbName, boolean _IsCritical, String _MsgText) {

        DoLog(_DbName, _IsCritical, _MsgText, const_global);

    }

    public static final void DoLogGlb(boolean _IsCritical, String _MsgText) {

        DoLogGlb(_IsCritical, _MsgText, const_global);

    }

    public static final void DoLogRestricted(String _DbName, boolean _IsCritical, String _MsgText) {

        getInstance().DoLog_internal(_DbName, _IsCritical, _MsgText, const_global, true, false);

    }

    public static final void DoLog(String _DbName, boolean _IsCritical, String _MsgText, String _MsgScope) {

        getInstance().DoLog_internal(_DbName, _IsCritical, _MsgText, _MsgScope, false, false);

    }

    public static final void DoLogGlb(boolean _IsCritical, String _MsgText, String _MsgScope) {

        DoLog("-", _IsCritical, _MsgText, _MsgScope);

    }

    public static final void SetAdditionalNotifier(AdditionalNotifier new_notifier) {

        getInstance().SetAdditionalNotifier_internal(new_notifier);

    }

    public static final void RemoveAdditionalNotifier(AdditionalNotifier old_notifier) {

        getInstance().RemoveAdditionalNotifier_internal(old_notifier);

    }

    public void SetAdditionalNotifier_internal(AdditionalNotifier new_notifier) {

        synchronized(aux_warnings_lock) {

            aux_warnings = new_notifier;

        }

    }

    public void RemoveAdditionalNotifier_internal(AdditionalNotifier old_notifier) {

        synchronized(aux_warnings_lock) {

            if (old_notifier == aux_warnings) aux_warnings = null;

        }

    }

    public static final void println(String _MsgText) {

        Calendar log_cal = Calendar.getInstance();
        SimpleDateFormat log_time_format = new SimpleDateFormat(log_format_string);
        long tmp_thrd_id = Thread.currentThread().getId();

        String tmp_full_line = 
                const_log_prefix + log_time_format.format(log_cal.getTime())
                + " % {" + tmp_thrd_id + "} % <-:" + const_global + "> % " + _MsgText;

        System.out.println(tmp_full_line);

    }

}
