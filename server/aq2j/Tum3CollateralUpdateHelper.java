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
package aq2j;


import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


import aq2db.*;
import aq2net.*;


public class Tum3CollateralUpdateHelper {

    private final static long MAX_COLLATERAL_FILE = 256 * 1024;

    private final static String TUM3_collateral_overwrite_path = "collateral_overwrite_path";
    private final static String TUM3_collateral_no_overwrite_path = "collateral_no_overwrite_path";
    private String Path_overwrite = Tum3cfg.getGlbParValue(TUM3_collateral_overwrite_path);
    private String Path_no_overwrite = Tum3cfg.getGlbParValue(TUM3_collateral_no_overwrite_path);


    private static class LazyCollateralHolder {

        public static Tum3CollateralUpdateHelper Instance = new Tum3CollateralUpdateHelper();

    }

    public static void LoadAllTo(String _DbName, ByteArrayOutputStream storage) {

        LazyCollateralHolder.Instance.internal_LoadAllTo(_DbName, storage);

    }

    private void LoadCategory(String _DbName, int _type_code, String _path, ByteArrayOutputStream _storage) {

        File tmp_dir = new File(_path);
        if (tmp_dir.isDirectory()) {
            int max_size = 0;
            byte[] tmp_arr = null;
            ByteBuffer tmp_buff = null;
            File[] dir_files = tmp_dir.listFiles();

            for (File tmp_file: dir_files)
                if (tmp_file.isFile() && (tmp_file.getName().length() >= 3) && (tmp_file.length() <= MAX_COLLATERAL_FILE)) {
                    int tmp_brutto_size = 4 + 4 + 4*tmp_file.getName().length() + 4 + (int)tmp_file.length();
                    if (max_size < tmp_brutto_size) max_size = tmp_brutto_size;
                }

            if (max_size > 0) {
                tmp_arr = new byte[max_size];
                tmp_buff = ByteBuffer.wrap(tmp_arr);
                tmp_buff.order(ByteOrder.LITTLE_ENDIAN);
            }

            for (File tmp_file: dir_files)
                if (tmp_file.isFile() && (tmp_file.getName().length() >= 3) && (tmp_file.length() <= max_size)) {
                    String tmp_name = tmp_file.getName();
                    //System.out.println("[aq2j] DEBUG: found collateral: '" + tmp_name + "'");
                    FileInputStream fis = null;
                    try { 
                        fis = new FileInputStream(tmp_file);
                        int tmp_file_size = (int)tmp_file.length();
                        byte[] tmp_raw_name = Tum3Util.StringToBytesRaw(tmp_name);
                        tmp_buff.clear();
                        tmp_buff.putInt(_type_code);
                        tmp_buff.putInt(tmp_raw_name.length);
                        tmp_buff.put(tmp_raw_name);
                        tmp_buff.putInt(tmp_file_size);

                        int tmp_filled = tmp_buff.position(), tmp_len = 0;
                        if (tmp_file_size > 0)
                            while((tmp_len = fis.read(tmp_arr, tmp_filled, max_size - tmp_filled)) != -1) tmp_filled += tmp_len;
                        if ((tmp_filled - tmp_buff.position()) != tmp_file_size) throw new Exception("reported size does not match read size (" + (tmp_filled - tmp_buff.position()) + " != " + tmp_file_size + ")");
                        _storage.write(tmp_arr, 0, tmp_filled);

                    } catch (Exception e) {
                        Tum3Logger.DoLog(_DbName, false, "WARNING: collateral file read error in '" + tmp_name + "' with: " + e);
                    }
                    if (fis != null) 
                        try {
                            fis.close();
                        } catch (Exception e) {
                            Tum3Logger.DoLog(_DbName, false, "WARNING: collateral file close error in '" + tmp_name + "' with: " + e);
                        }
                }
        }
    }

    private void internal_LoadAllTo(String _DbName, ByteArrayOutputStream storage) {

        //System.out.println("[aq2j] Tum3CollateralUpdateHelper.internal_LoadAllTo()");
        try {
            if (!Path_overwrite.isEmpty()) LoadCategory(_DbName, TumProtoConsts.tum3misc_file_overwrite, Path_overwrite, storage);
            if (!Path_no_overwrite.isEmpty()) LoadCategory(_DbName, TumProtoConsts.tum3misc_file_no_overwrite, Path_no_overwrite, storage);
        } catch (Exception e) {
            Tum3Logger.DoLog(_DbName, true, "WARNING: collateral file processing exception: " + Tum3Util.getStackTrace(e));
        }

    }


}
