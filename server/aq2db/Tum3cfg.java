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


import java.util.*;
import java.io.*;


public class Tum3cfg {

    private final static String TUM3_CONF_ENV = "TUM3CONF";
    //private boolean config_loaded_ok = false;
    private final Properties config_props = new Properties();

    private final static String TUM3_CFG_dblist = "dblist";
    public  final static String TUM3_CFG_db_root = "db_root"; // Moved from tum3db
    public  final static String TUM3_CFG_web_enabled = "web_enabled";
    public  final static String TUM3_CFG_tcp_enabled = "tcp_enabled";
    private final static String TUM3_CFG_shutdown_timeout = "shutdown_timeout";
    private final static String TUM3_CFG_is_writeable = "is_writeable";
    public  final static String TUM3_CFG_hotstart_path = "hotstart_path";
    private final static int CONST_DEF_SHUTDOWN_TIMEOUT = 2500;

    private List<DbCfg> db_configs = new ArrayList<DbCfg>();

    private static class LazyCfgHolder {
        public static Tum3cfg cfgInstance = new Tum3cfg();
    }

    public class DbCfg {

        private String db_name;
        private boolean db_web_enabled, db_tcp_enabled;
        private Properties db_props;

        public DbCfg(String _db_name, boolean _web_enabled, boolean _tcp_enabled, Properties _db_props) {

            db_name = _db_name;
            db_web_enabled = _web_enabled;
            db_tcp_enabled = _tcp_enabled;
            db_props = _db_props;
            //System.out.println("[DEBUG] added DbCfg: <" + _db_name + ">");

        }

    }

    public Tum3cfg() {

        boolean config_loaded_ok = false;
        Tum3Logger.println("Created Tum3cfg, ver " + a.CurrentVerNum);

        Tum3Logger.println("Locale: " + Locale.getDefault());
        /*
      try {
        FileOutputStream tmp_stream = new FileOutputStream(new File("C:\\SETUP.log"));
        tmp_stream.write(1);
      } catch (Exception e) {
        System.out.println("[aq2j] Example message: " + e);
      }
         */

        Tum3Time t = new Tum3Time();
        Tum3Logger.println("Tum3Time=<" + t.AsString() + ">");

        String config_path = System.getenv(TUM3_CONF_ENV);
        Tum3Logger.println("config_path=" + config_path);
        if (config_path.length() > 0) {
            try {
                //load a properties file
                config_props.load(new FileInputStream(config_path + "aq2j.properties"));
                config_loaded_ok = true;
            } catch (Exception e) {
                Tum3Logger.println("IMPORTANT: " + Tum3Util.getStackTrace(e));
            }
        }
        if (config_loaded_ok) {
            String tmp_db_s = config_props.getProperty(TUM3_CFG_dblist, "").trim();
            if (tmp_db_s.isEmpty()) {
                tmp_db_s = "aq2j";
                Tum3Logger.println("IMPORTANT: db list not specified in config file, adding one default db.");
            }
            String tmp_db_list[] = tmp_db_s.split(",");
            for (int tmp_i = 0; tmp_i < tmp_db_list.length; tmp_i++) tmp_db_list[tmp_i] = tmp_db_list[tmp_i].trim();
            for (int tmp_i = 0; tmp_i < tmp_db_list.length; tmp_i++) if (!tmp_db_list[tmp_i].isEmpty())
                try {
                    //System.out.println("[DEBUG] considering database name: <" + tmp_db_list[tmp_i] + ">");
                    Properties db_props = new Properties();
                    db_props.load(new FileInputStream(config_path + tmp_db_list[tmp_i] + ".properties"));
                    if (!db_props.getProperty(TUM3_CFG_db_root, "").isEmpty()) {
                        boolean tmp_web_enabled = "1".equals(db_props.getProperty(TUM3_CFG_web_enabled, "1").trim());
                        boolean tmp_tcp_enabled = "1".equals(db_props.getProperty(TUM3_CFG_tcp_enabled, "1").trim());
                        if (tmp_web_enabled || tmp_tcp_enabled) db_configs.add(new DbCfg(tmp_db_list[tmp_i], tmp_web_enabled, tmp_tcp_enabled, db_props));
                    }
                } catch (Exception e) {
                    Tum3Logger.println("Warning: config not present or invalid for database <" + tmp_db_list[tmp_i] + ">: " + e);
                }
        } else
            Tum3Logger.println("IMPORTANT: failed to load global config settings.");
    }

    public static Tum3cfg getGlbInstance() {

        return LazyCfgHolder.cfgInstance;

    }

    public int getDbCount() {

        return db_configs.size();

    }

    public String getDbName(int _db_idx) {

        //String tmp_db_list[] = {"aq2j", "dbtest1"};
        //return tmp_db_list[_db_idx];
        return db_configs.get(_db_idx).db_name;

    }

    public int getDbIndex(String _db_name) {

        for (int i = 0; i < db_configs.size(); i++)
            if (db_configs.get(i).db_name.equals(_db_name))
                return i;

        return -1;

    }

    public boolean getDbWebEnabled(int _db_idx) {

        return db_configs.get(_db_idx).db_web_enabled;

    }

    public boolean getDbTcpEnabled(int _db_idx) {

        return db_configs.get(_db_idx).db_tcp_enabled;

    }

    public String getParValue(String ParName, String DefVal) {
        try {
            return config_props.getProperty(ParName, DefVal);
        } catch (Exception e) {
            return DefVal;
        }
    }

    private String getParValue_priv(int _db_idx, boolean _inherit_glb, String ParName, String DefVal) {
        try {
            if (_inherit_glb)
                return db_configs.get(_db_idx).db_props.getProperty(ParName, config_props.getProperty(ParName, DefVal));
            else
                return db_configs.get(_db_idx).db_props.getProperty(ParName, DefVal);
        } catch (Exception e) {
            return DefVal;
        }
    }

    public static String getParValue(int _db_idx, boolean _inherit_glb, String ParName, String DefVal) {
        return getGlbInstance().getParValue_priv(_db_idx, _inherit_glb, ParName, DefVal);
    }

    public static String getParValue(int _db_idx, boolean _inherit_glb, String ParName) {
        return getGlbInstance().getParValue_priv(_db_idx, _inherit_glb, ParName, "");
    }

    private String getGlbParValue_priv(String ParName, String DefVal) {
        try {
            return config_props.getProperty(ParName, DefVal);
        } catch (Exception e) {
            return DefVal;
        }
    }

    public static String getGlbParValue(String ParName) {
        return getGlbInstance().getGlbParValue_priv(ParName, "");
    }

    public String getParValue(String ParName) {
        return getParValue(ParName, "");
    }

    public int getIntValue(String ParName, int DefVal) {
        String tmp_str = getParValue(ParName, "" + DefVal);
        try {
            return Integer.parseInt(tmp_str);
        } catch (Exception e) {
            return DefVal;
        }
    }

    public static int getIntValue(int _db_idx, boolean _inherit_glb, String ParName, int DefVal) {
        String tmp_str = getParValue(_db_idx, _inherit_glb, ParName, "" + DefVal);
        try {
            return Integer.parseInt(tmp_str);
        } catch (Exception e) {
            return DefVal;
        }
    }

    public int getShutdownTimeout() {
        return getIntValue(TUM3_CFG_shutdown_timeout, CONST_DEF_SHUTDOWN_TIMEOUT);
    }

    public static boolean isWriteable(int _db_idx) {
        return "1".equals(getParValue(_db_idx, true, TUM3_CFG_is_writeable, "0").trim());
    }

    public static boolean isGlbWriteable() {
        return "1".equals(getGlbParValue(TUM3_CFG_is_writeable).trim());
    }

}
