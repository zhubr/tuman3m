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
import java.util.ArrayList;
import java.util.List;


public class NameValueList {


    private ArrayList<String> my_names;
    private ArrayList<String> my_values;


    public NameValueList() {
        my_names = new ArrayList<String>();
        my_values = new ArrayList<String>();
    }

    public NameValueList CreatePhoto() {

        NameValueList tmp_new = new NameValueList();
        for (int i = 0; i < my_names.size(); i++) {
            tmp_new.my_names.add(my_names.get(i));
            tmp_new.my_values.add(my_values.get(i));
        }
        return tmp_new;
    }

    public boolean ItemsEqual(NameValueList other) {

        if (other.my_names.size() != my_names.size()) return false;

        for (int tmp_i = 0; tmp_i < my_names.size(); tmp_i++) {
            String tmp_name = my_names.get(tmp_i);
            int tmp_other_idx = other.my_names.indexOf(tmp_name);
            if (tmp_other_idx < 0) return false;
            if (!other.my_values.get(tmp_other_idx).equals(my_values.get(tmp_i))) return false;
        }

        return true;

    }

    public int Count() {

        return my_names.size();

    }

    public String GetName(int i) {

        return my_names.get(i);

    }

    public String GetBody(int i) {

        return my_values.get(i);

    }

    public NameValueList(String... with_names) {
        my_names = new ArrayList<String>(Arrays.asList(with_names));
        my_values = new ArrayList<String>();
        while (my_values.size() < my_names.size()) { my_values.add(null); }
    }

    public void SetValueFor(String the_name, String the_value) {

        int tmp_i = my_names.indexOf(the_name);
        if (tmp_i >= 0) my_values.set(tmp_i, the_value);

    }

    public boolean AddNameVal(String the_name, String the_value) {
        // Returns true if the value was added or modified, false if no change happened.

        int tmp_j = my_names.indexOf(the_name);
        if (tmp_j >= 0) {
            String tmp_prev_val = my_values.get(tmp_j);
            my_values.set(tmp_j, the_value);
            return !the_value.equals(tmp_prev_val);
        } else {
            my_names.add(the_name);
            my_values.add(the_value);
            return true;
        }

    }

    public String GetValueFor(String the_name, String val_default) {

        String tmp_val = null;
        int tmp_i = my_names.indexOf(the_name);
        if (tmp_i >= 0) tmp_val = my_values.get(tmp_i);

        if (tmp_val != null) return tmp_val;
        else return val_default;

    }

    public boolean HasValueFor(String the_name) {

        return (my_names.indexOf(the_name) >= 0);

    }

    public static String HideSpecialChars(String src) {
        return src.replace(":", "#").replace(",", "`");
    }

    public static String UnhideSpecialChars(String src) {
        return src.replace("#", ":").replace("`", ",").replace("~", ",");
    }

    public void clear() {
        if (my_names != null) my_names.clear();
        if (my_values != null) my_values.clear();
    }

    public String BuildCSL() {
        // ArrayList<String> list_names, ArrayList<String> list_values
        if ((my_names == null) || (my_values == null)) return "";
        if (my_names.size() < 1) return "";

        StringBuilder tmp_result = new StringBuilder();
        for (int i = 0; i < my_names.size(); i++) {
            if (i > 0) tmp_result.append(",");
            tmp_result.append(my_names.get(i) + ":" + HideSpecialChars(my_values.get(i)));
        }

        return tmp_result.toString();
    }

    public String BuildCSL(String ... without) {
        // ArrayList<String> list_names, ArrayList<String> list_values
        if ((my_names == null) || (my_values == null)) return "";
        if (my_names.size() < 1) return "";

        StringBuilder tmp_result = new StringBuilder();
        for (int i = 0; i < my_names.size(); i++) {
            String tmp_name = my_names.get(i);
            if (Arrays.asList(without).indexOf(tmp_name) >= 0) continue;
            if (tmp_result.length() > 0) tmp_result.append(",");
            tmp_result.append(tmp_name + ":" + HideSpecialChars(my_values.get(i)));
        }

        return tmp_result.toString();
    }

    public void FillFrom(List<String> the_src, String the_section, boolean auto_add) {

        boolean tmp_in_section = false;

        for (String currSt: the_src) {
            String tmp_st = currSt.trim();
            if (tmp_st.length() > 0) {
                //System.out.println("[aq2j] DEBUG: LoadFrom: <" + tmp_st + ">");
                if (tmp_st.startsWith("[") && tmp_st.endsWith("]")) {
                    String tmp_sect = tmp_st.substring(1, tmp_st.length()-1);
                    tmp_in_section = tmp_sect.equals(the_section);
                    //System.out.println("[aq2j] DEBUG: tmp_sect=<" + tmp_sect + "> the_section=<" + the_section + "> tmp_in_section=" + tmp_in_section);
                } else if (tmp_in_section) {
                    int i_eq_sign = tmp_st.indexOf("=");
                    if (i_eq_sign >= 1) {
                        String tmp_name = tmp_st.substring(0, i_eq_sign);
                        String tmp_value = tmp_st.substring(i_eq_sign+1, tmp_st.length());
                        if (auto_add) AddNameVal(tmp_name, tmp_value);
                        else SetValueFor(tmp_name, tmp_value);
                    }
                }
            } 
        }

    }

    public void AddTo(StringBuffer theBuff, String thePrefix) {

        for (int i = 0; i < my_names.size(); i++)
            theBuff.append(thePrefix + my_names.get(i) + "=" + my_values.get(i) + HandyMisc.crlf);

    }

}
