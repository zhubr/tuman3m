/*
 * Copyright 2011-2023 Nikolai Zhubr <zhubr@mail.ru>
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
    public  final static String TUM3_CFG_downlink_enabled = "downlink_enabled";
    public  final static String TUM3_CFG_downbulk_enabled = "downlink_bulk_enabled";
    public  final static String TUM3_CFG_uplink_enabled = "uplink_enabled";
    public  final static String TUM3_CFG_upbulk_enabled = "uplink_bulk_enabled";
    public  final static String TUM3_CFG_tcp_enabled = "tcp_enabled";

    public  final static String TUM3_CFG_uplink_serv_addr = "uplink_serv_addr";
    public  final static String TUM3_CFG_upbulk_serv_addr = "uplink_bulk_serv_addr"; // YYY
    public  final static String TUM3_CFG_uplink_trusted_keys = "uplink_trusted_keys";
    public  final static String TUM3_CFG_uplink_connect_timeout = "uplink_connect_timeout";
    public  final static String TUM3_CFG_uplink_credentials = "uplink_credentials";
    public  final static String TUM3_CFG_uplink_username = "username";
    public  final static String TUM3_CFG_uplink_password = "password";

    private final static String TUM3_CFG_shutdown_timeout = "shutdown_timeout";
    private final static String TUM3_CFG_is_writeable = "is_writeable";
    private final static String TUM3_CFG_warn_free_space_gb = "warn_free_space_gb"; // YYY
    private final static String TUM3_CFG_ugc_enabled = "ugc_enabled"; // YYY
    private final static String TUM3_CFG_ugc_uplinked = "ugc_uplinked"; // YYY
    public  final static String TUM3_CFG_hotstart_path = "hotstart_path";
    private final static int CONST_DEF_SHUTDOWN_TIMEOUT = 2500;

    private List<DbCfg> db_configs = new ArrayList<DbCfg>();

    private static class LazyCfgHolder {
        public static Tum3cfg cfgInstance = new Tum3cfg();
    }

    public class DbCfg {

        private String db_name;
        private boolean db_web_enabled, db_tcp_enabled, db_downlink_enabled, db_downbulk_enabled, db_uplink_enabled, db_upbulk_enabled, db_is_writeable; // YYY
        private Properties db_props;

        public DbCfg(String _db_name, boolean _web_enabled, boolean _tcp_enabled, boolean _downlink_enabled, boolean _downbulk_enabled, boolean _uplink_enabled, boolean _upbulk_enabled, boolean _is_writeable, Properties _db_props) {

            db_name = _db_name;
            db_web_enabled = _web_enabled;
            db_tcp_enabled = _tcp_enabled;
            db_downlink_enabled = _downlink_enabled;
            db_downbulk_enabled = _downbulk_enabled; // YYY
            db_is_writeable = _is_writeable; // YYY
            db_uplink_enabled = _uplink_enabled;
            db_upbulk_enabled = _upbulk_enabled; // YYY
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
                        boolean tmp_downlink_enabled = "1".equals(db_props.getProperty(TUM3_CFG_downlink_enabled, "0").trim());
                        boolean tmp_downbulk_enabled = "1".equals(db_props.getProperty(TUM3_CFG_downbulk_enabled, "0").trim()); // YYY
                        boolean tmp_uplink_enabled = "1".equals(db_props.getProperty(TUM3_CFG_uplink_enabled, "0").trim());
                        boolean tmp_upbulk_enabled = "1".equals(db_props.getProperty(TUM3_CFG_upbulk_enabled, "0").trim()); // YYY
                        boolean tmp_is_writeable = "1".equals(db_props.getProperty(TUM3_CFG_is_writeable, config_props.getProperty(TUM3_CFG_is_writeable, "0")).trim()); // YYY
                        if (tmp_downlink_enabled && tmp_uplink_enabled) {
                            tmp_downlink_enabled = false; // YYY
                            tmp_uplink_enabled = false; // YYY
                            Tum3Logger.println("IMPORTANT: uplink and downlink specified for database <" + tmp_db_list[tmp_i] + ">, had to disable both.");
                        }
                        if (tmp_downbulk_enabled && tmp_upbulk_enabled) {
                            tmp_downbulk_enabled = false;
                            tmp_upbulk_enabled = false;
                            Tum3Logger.println("IMPORTANT: uplink_bulk and downlink_bulk specified for database <" + tmp_db_list[tmp_i] + ">, had to disable both.");
                        }
                        if (tmp_is_writeable && (tmp_downlink_enabled || tmp_downbulk_enabled)) {
                            tmp_is_writeable = false; // YYY
                            Tum3Logger.println("Warning: disabling 'writeable' for database <" + tmp_db_list[tmp_i] + "> because it has a downlink.");
                        }
                        if (tmp_web_enabled || tmp_tcp_enabled || tmp_downlink_enabled || tmp_downbulk_enabled || tmp_uplink_enabled || tmp_upbulk_enabled) db_configs.add(new DbCfg(tmp_db_list[tmp_i], tmp_web_enabled, tmp_tcp_enabled, tmp_downlink_enabled, tmp_downbulk_enabled, tmp_uplink_enabled, tmp_upbulk_enabled, tmp_is_writeable, db_props));
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

    public boolean getDbDownlinkEnabled(int _db_idx) {

        return db_configs.get(_db_idx).db_downlink_enabled;

    }

    public boolean getDbDownBulkEnabled(int _db_idx) {

        return db_configs.get(_db_idx).db_downbulk_enabled;

    }

    public static boolean isWriteable(int _db_idx) {

        return getGlbInstance().db_configs.get(_db_idx).db_is_writeable && !Tum3Logger.BogusClockDetected(); // YYY

    }

    public boolean getDbUplinkEnabled(int _db_idx) {

        return db_configs.get(_db_idx).db_uplink_enabled;

    }

    public boolean getDbUpBulkEnabled(int _db_idx) {

        return db_configs.get(_db_idx).db_upbulk_enabled;

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

    public static int getWarnFreeSpaceGb(int _db_idx) { // YYY
        return getIntValue(_db_idx, true, TUM3_CFG_warn_free_space_gb, 0);
    }

    public static boolean UgcEnabled(int _db_idx) {
        return "1".equals(getParValue(_db_idx, true, TUM3_CFG_ugc_enabled, "0").trim());
    }

    public static boolean UgcUplinked(int _db_idx) {
        return "1".equals(getParValue(_db_idx, true, TUM3_CFG_ugc_uplinked, "0").trim());
    }

    public static boolean isGlbWriteable() {
        return "1".equals(getGlbParValue(TUM3_CFG_is_writeable).trim()) && !Tum3Logger.BogusClockDetected(); // YYY
    }

}
