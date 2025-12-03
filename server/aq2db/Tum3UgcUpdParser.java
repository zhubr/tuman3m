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
package aq2db;

//import java.io.*;


public class Tum3UgcUpdParser {

    private StringList the_source;
    private String the_comment = "", the_tags = "";
    private StringList tags_list;
    private String user_name;


    public Tum3UgcUpdParser(String UserName, StringList _source) {

        user_name = UserName;
        the_source = _source;

        for (String tmp_str: the_source) {
          if      (tmp_str.startsWith("Comment=")) the_comment = tmp_str.substring("Comment=".length()).trim();
          else if (tmp_str.startsWith("Tags=")) the_tags = tmp_str.substring("Tags=".length()).trim();
        }
//Tum3Logger.DoLogGlb(true, "[DEBUG] Ugc parser <" + the_comment + "> <" + the_tags + ">");
        tags_list = new StringList(the_tags.split(","));
        for (int i = tags_list.size() - 1; i >= 0; i--) { // YYY
            tags_list.set(i, tags_list.get(i).trim());
//Tum3Logger.DoLogGlb(true, "[DEBUG] cleanup i=" + i + ", tag =<" + tags_list.get(i) + "> " + tags_list.get(i).length());
            if (tags_list.get(i).isEmpty()) tags_list.remove(i);
        }

        for (String tmp_str: tags_list) {
//Tum3Logger.DoLogGlb(true, "[DEBUG] Ugc parser tag=<" + tmp_str + ">");
        }
//Tum3Logger.DoLogGlb(true, "[DEBUG] tags_list.size()=" + tags_list.size());

        StringBuffer tmp_buff = new StringBuffer();
        for (int i = 0; i < tags_list.size(); i++) {
            tmp_buff.append(tags_list.get(i));
            if (i < (tags_list.size() - 1)) tmp_buff.append(", ");
        }
        the_tags = tmp_buff.toString();
    }

    public boolean mergeWith(StringList source) {

        boolean tmp_in_user = false, tmp_user_found = false;
        boolean tmp_seen_comment = false, tmp_seen_tags = false;
        boolean tmp_was_changed = false;
        int tmp_user_line0 = -1, tmp_user_line1 = -1;

        int i = 0; 
        while (i <= source.size()) {
            String tmp_str = (i < source.size()) ? source.get(i) : "[]";
            if (tmp_in_user) {
                if (tmp_str.startsWith("[")) {
                    tmp_in_user = false;
                    tmp_user_line1 = i;
                    if (!tmp_seen_comment && !the_comment.isEmpty()) {
                        source.add(i, "Comment=" + the_comment); i++;
                        tmp_was_changed = true;
                        tmp_seen_comment = true;
                    }
                    if (!tmp_seen_tags && !the_tags.isEmpty()) {
                        source.add(i, "Tags=" + the_tags); i++;
                        tmp_was_changed = true;
                        tmp_seen_tags = true;
                    }
                } else {
                    if (tmp_str.startsWith("Comment=")) {
                        tmp_seen_comment = true;
                        if (the_comment.isEmpty()) {
                            source.remove(i); i--; 
                            tmp_was_changed = true;
                        } else {
                            String tmp_new_str = "Comment=" + the_comment;
                            if (!tmp_new_str.equals(source.get(i))) {
                                source.set(i, tmp_new_str);
                                tmp_was_changed = true;
                            }
                        }
                    }
                    if (tmp_str.startsWith("Tags=")) {
                        tmp_seen_tags = true;
                        if (the_tags.isEmpty()) {
                            source.remove(i); i--; 
                            tmp_was_changed = true;
                        } else {
                            String tmp_new_str = "Tags=" + the_tags;
                            if (!tmp_new_str.equals(source.get(i))) {
                                source.set(i, tmp_new_str);
                                tmp_was_changed = true;
                            }
                        }
                    }
                }
            } else {
                if (tmp_str.trim().equals("[" + user_name + "]")) {
                    tmp_in_user = true;
                    tmp_user_found = true;
                    tmp_user_line0 = i;
                }
            }
            i++;
        }

        if (tmp_user_found) {
            boolean tmp_empty = true;
            for (i = tmp_user_line0 + 1; i < tmp_user_line1; i++)
                if (!source.get(i).trim().isEmpty()) {
                    tmp_empty = false;
                    break;
                }
            if (tmp_empty) {
                for (i = tmp_user_line0; i < tmp_user_line1; i++)
                    source.remove(tmp_user_line0);
                    tmp_was_changed = true;
            }
        }

        if (!tmp_user_found && (!the_comment.isEmpty() || !the_tags.isEmpty())) {

                    source.add("[" + user_name + "]");
                    if (!the_comment.isEmpty()) {
                        source.add("Comment=" + the_comment);
                        tmp_was_changed = true;
                    }
                    if (!the_tags.isEmpty()) {
                        source.add("Tags=" + the_tags);
                        tmp_was_changed = true;
                    }
        }

        return tmp_was_changed;
    }

    public StringList getTags() {

        //Tum3Logger.DoLogGlb(true, "[DEBUG] Tum3UgcUpdParser.getTags: count=" + tags_list.size());
        //for (int i = 0; i < tags_list.size(); i++) Tum3Logger.DoLogGlb(true, "[DEBUG] Tum3UgcUpdParser.getTags: tag=<" + tags_list.get(i) + ">");
        return tags_list;
    }

}
