/*
 * Copyright 2025 Nikolai Zhubr <zhubr@rambler.ru>
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
package compatwrk;


import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.io.*;

import compatdef.CompatBase;
import compatdef.CompatLogger;


public class CompatWorker extends CompatBase {

    private CompatLogger logger;
    private Properties config_props;

    private String num_fname_internal = "", num_fname_public = "", data_out_path = "";
    private String curr_shot_num_main = "", curr_shot_num_alt = "";
    private RandomAccessFile nf_internal;
    private int last_alt_num = 0;


    public CompatWorker(CompatLogger _logger, Properties _config_props) {

        logger = _logger;
        config_props = _config_props;
        logger.DoLog(false, "[DEBUG] CompatWorker loaded.");

        num_fname_internal = config_props.getProperty("num_fname_internal", "");
        num_fname_public = config_props.getProperty("num_fname_public", "");
        data_out_path = config_props.getProperty("data_out_path", "");

        InitIntrnlFile();

    }

    private void nf_internal_close() {

        try { nf_internal.close(); } catch (Exception ignored) {}
        nf_internal = null;

    }

    private byte[] IntAsBinLine(int the_num) {

        return StandardCharsets.US_ASCII.encode("" + the_num + "\r\n").array();

    }

    private void PublicNumFlush() {

        if ((num_fname_public.isEmpty()) || (0 == last_alt_num)) return;

        RandomAccessFile nf_public = null;
        try {
            nf_public = new RandomAccessFile(num_fname_public, "rw");
            nf_public.setLength(0);
            nf_public.write(IntAsBinLine(last_alt_num));
            nf_public.close();
            nf_public = null;
        } catch (Exception e) {
            logger.DoLog(true, "File write error in public alternative shot num storage (" + num_fname_public + "): " + e.toString());
        }
        if (null == nf_public) return;
        try { nf_public.close(); } catch (Exception ignored) {}
    }

    private void IntrnlNumFlush() {

        if (null == nf_internal) return;

        boolean tmp_ok = false;
        try {
            nf_internal.setLength(0);
            nf_internal.write(IntAsBinLine(last_alt_num));
            tmp_ok = true;
        } catch (Exception e) {
            logger.DoLog(true, "Failed writing internal alternative shot num storage (" + num_fname_internal + "): " + e.toString());
        }
        if (!tmp_ok) {
            nf_internal_close();
        }
    }

    private void InitIntrnlFile() {

        try {
            nf_internal = new RandomAccessFile(num_fname_internal, "rw");
        } catch (Exception e) {
            logger.DoLog(true, "File for internal alternative shot num storage is not valid (" + num_fname_internal + ")");
        }
        if (null == nf_internal) return;
        //System.out.println("[DEBUG] compat: file is open now.");

        long tmp_length = -1;
        try { tmp_length = nf_internal.length(); } catch (Exception ignored) {}
        //System.out.println("[DEBUG] compat: file len = " + tmp_length);

        boolean tmp_read_ok = false, tmp_need_flush = false;
        if (tmp_length == 0) {
            last_alt_num = 10000;
            tmp_read_ok = true;
            tmp_need_flush = true;
        } else {

            if ((tmp_length > 12) || (tmp_length < 0)) {
                logger.DoLog(true, "File for internal alternative shot num storage looks too strange (" + num_fname_internal + ")");
            } else {
                try {
                    String tmp_line = nf_internal.readLine();
                    last_alt_num = Integer.parseInt(tmp_line);
                    tmp_read_ok = (last_alt_num > 0) && (last_alt_num < 99999);
                } catch (Exception e) {
                    logger.DoLog(true, "Error parsing internal alternative shot num storage (" + num_fname_internal + ")");
                }
            }
            if (!tmp_read_ok) {
                nf_internal_close();
                return;
            }
        }
        if (tmp_need_flush) IntrnlNumFlush();
        logger.DoLog(false, "Starting alternative shot numbering at " + last_alt_num);
    }

    public void AllocAltShotNum(String shot_num) throws Exception {

        if ((null == nf_internal) || (0 == last_alt_num)) return;

        last_alt_num++;
        IntrnlNumFlush();
        PublicNumFlush();
        curr_shot_num_main = shot_num;
        curr_shot_num_alt = "" + last_alt_num;

    }

    public void ApplyAltShotNum(String shot_num) {

        if (data_out_path.isEmpty() || curr_shot_num_main.isEmpty() || curr_shot_num_alt.isEmpty()) return;
        if (!curr_shot_num_main.equals(shot_num)) return;

        String num_tname_public = data_out_path + curr_shot_num_alt + ".txt";
        RandomAccessFile tf_public = null;
        try {
            tf_public = new RandomAccessFile(num_tname_public, "rw");
            tf_public.setLength(0);
            tf_public.write(StandardCharsets.US_ASCII.encode("" + curr_shot_num_main + "\r\n").array());
            tf_public.close();
            tf_public = null;
        } catch (Exception e) {
            logger.DoLog(true, "File write error in alternative shot num ref file (" + num_tname_public + "): " + e.toString());
        }
        if (null == tf_public) return;
        try { tf_public.close(); } catch (Exception ignored) {}

    }

    public boolean QuicklyCheck(String shot_num, int signal_id, boolean is_volatile, boolean was_deleted) {
        // TODO.
        return false;
    }

    public void ProcessNewData(String shot_num, int signal_id, boolean is_volatile, boolean was_deleted, String file_name) {
        // TODO.
    }

}
