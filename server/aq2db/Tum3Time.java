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
package aq2db;


import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;


public class Tum3Time {

    public final int year, month, day, hour, minute, second;

    public Tum3Time() {

        Calendar c = Calendar.getInstance(/* TimeZone.getTimeZone("GMT+3") */);
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH) + 1;
        day = c.get(Calendar.DAY_OF_MONTH);
        hour = c.get(Calendar.HOUR_OF_DAY);
        minute = c.get(Calendar.MINUTE);
        second = c.get(Calendar.SECOND);

        //System.out.println("year.month.day=<" + year + "." + month + "." + day + "> hour.minute.second=<" + hour + "." + minute + "." + second + ">");

    }

    public Tum3Time(int _year, int _month, int _day, int _hour, int _minute, int _second) {

        year = _year;
        month = _month;
        day = _day;
        hour = _hour;
        minute = _minute;
        second = _second;

    }

    public boolean IsInFuture() {

        int tmp_year, tmp_month, tmp_day;
        Calendar c = Calendar.getInstance(/* TimeZone.getTimeZone("GMT+3") */);
        tmp_year = c.get(Calendar.YEAR);
        tmp_month = c.get(Calendar.MONTH) + 1;
        tmp_day = c.get(Calendar.DAY_OF_MONTH);

        if (year > tmp_year) return true;
        if ((year == tmp_year) && (month > tmp_month)) return true;
        if ((year == tmp_year) && (month == tmp_month) && (day > tmp_day)) return true;

        return false;

    }

    public String AsString() {

        return year + "." + month + "." + day + "." + hour + "." + Tum3Util.IntToStr2(minute) + "." + Tum3Util.IntToStr2(second);

    }

    public String AsDateString() {

        return year + "-" + month + "-" + day;

    }

    public static String CurrentTimeAsString() {

        return new Tum3Time().AsString();

    }

    public static String CurrentDateAsString() {

        return new Tum3Time().AsDateString();

    }

    public static Tum3Time CreateFromString(String str) {

        if (str == null) return null;
        if (str.isEmpty()) return null;
        try {
            String[] tmp_strs = str.split("\\."); // Reminder: in regex dot needs to be escaped!
            //System.out.println("[DEBUG] CreateFromString: " + str + "; " + tmp_strs.length);
            if (tmp_strs.length != 6) return null;
            int[] tmp_vals = new int[6];
            for (int i = 0; i < 6; i++) tmp_vals[i] = Integer.parseInt(tmp_strs[i]);
            Calendar c = Calendar.getInstance(/* TimeZone.getTimeZone("GMT+3") */);
            c.set(Calendar.YEAR, tmp_vals[0]);
            c.set(Calendar.MONTH, tmp_vals[1] - 1);
            c.set(Calendar.DAY_OF_MONTH, tmp_vals[2]);
            c.set(Calendar.HOUR_OF_DAY, tmp_vals[3]);
            c.set(Calendar.MINUTE, tmp_vals[4]);
            c.set(Calendar.SECOND, tmp_vals[5]);
            return new Tum3Time(tmp_vals[0], tmp_vals[1], tmp_vals[2], tmp_vals[3], tmp_vals[4], tmp_vals[5]);
        } catch (Exception ignored) {
            //System.out.println("Exception: " + e);
        }
        return null;
    }

    public String GetCurrYMD() {

        return Tum3Util.IntToStr2(year % 100) + Tum3Util.IntToStr2(month) + Tum3Util.IntToStr2(day);

    }

}
