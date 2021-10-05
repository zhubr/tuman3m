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


import java.util.ArrayList;


public class SectionList {


    private ArrayList<StringList> theBodies = new ArrayList<StringList>();
    private StringList theNames = new StringList();


    public SectionList() {

        super();

    }

    public SectionList(StringList the_src) {

        super();
        AddFile(the_src);

    }

    public StringList GetByName(String the_name) {

        int tmp_i = theNames.indexOf(the_name);
        if (tmp_i >= 0) return theBodies.get(tmp_i);
        else return null;

    }

    public StringList GetBody(int i) {

        return theBodies.get(i);

    }

    public String GetName(int i) {

        return theNames.get(i);

    }

    public int Count() {

        return theNames.size();

    }

    public void AddSection(String the_name, StringList the_src) {

        int tmp_j = theNames.indexOf(the_name);
        if (tmp_j < 0) {
            theNames.add(the_name);
            theBodies.add(the_src);
        } else {
            theBodies.set(tmp_j, the_src);
        }

    }

    public void AddFile(StringList the_src) {

        String tmp_sect_name = "";
        StringList tmp_sect_body = null;

        for (int tmp_i = 0; tmp_i < the_src.size(); tmp_i++) {
            String tmp_st = the_src.get(tmp_i).trim();
            if (tmp_st.length() > 0) if (!tmp_st.startsWith(";")) {
                if (tmp_st.startsWith("[") && tmp_st.endsWith("]")) {
                    String tmp_sect = tmp_st.substring(1, tmp_st.length()-1).trim();
                    if (tmp_sect.length() > 0) {
                        tmp_sect_name = tmp_sect;
                        int tmp_j = theNames.indexOf(tmp_sect_name);
                        if (tmp_j < 0) {
                            theNames.add(tmp_sect_name);
                            tmp_sect_body = new StringList();
                            theBodies.add(tmp_sect_body);
                        } else {
                            tmp_sect_body = theBodies.get(tmp_j);
                        }
                    }
                } else if (tmp_sect_body != null) {

                    tmp_sect_body.add(tmp_st);

                }
            } 
        }

    }

}
