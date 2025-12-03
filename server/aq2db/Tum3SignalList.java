/*
 * Copyright 2011-2023 Nikolai Zhubr <zhubr@rambler.ru>
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
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.*;


public class Tum3SignalList {

    private final static String CONST_GRPS_CMN = "grps_cmn";
    private final static String CONST_SGNL_CMN = "sgnl_cmn";
    private final static String CONST_FMLS_CMN = "fmls_cmn";

    private final static String TUM3_CFG_db_conf_root = "db_conf_root";

    private final static String const_signal_allow_ext_upl = "AllowExtUpl";

    public final static String const_signal_title = "Title";
    public final static String const_signal_is_density = "IsDensity";

    private static volatile Tum3SignalList SignalList = null; // Got it separated from db.
    private static final Object SignalListQuickLock = new Object(); // moved from Tum3Db and made static
    private static final Object SignalListStoreLock = new Object(); // moved from Tum3Db and made static


    public static boolean AllowExtUpload(NameValueList _signal_entry) {

        if (null != _signal_entry)
            return "1".equals(_signal_entry.GetValueFor(const_signal_allow_ext_upl, ""));

        return false;   
    }

    static class ListHolder {

        private String base_sect_name;
        private ArrayList<NameValueList> theList = new ArrayList<NameValueList>();
        private int CommonRev = 0, LocalRev = 0;
        private int[] IdAccelArr;
        private int IdAccelMin, IdAccelMax;
        private boolean NeedSave = false;

        public ListHolder(String the_base_sect_name) {

            base_sect_name = the_base_sect_name;

        }

        private void CopyFrom(ListHolder src) {

            theList.clear();
            for (int tmp_i = 0; tmp_i < src.theList.size(); tmp_i++)
                theList.add(src.theList.get(tmp_i).CreatePhoto());
            CommonRev = src.CommonRev;
            LocalRev = src.LocalRev;
            IdAccelArr = null;
            if (src.IdAccelArr != null) {
                IdAccelArr = Arrays.copyOf (src.IdAccelArr, src.IdAccelArr.length);
            }
            IdAccelMin = src.IdAccelMin;
            IdAccelMax = src.IdAccelMax;

        }

        private boolean UpdateRevision(ListHolder old) throws Exception {

            if (!CompareItemList(old)) {
                if (CommonRev != old.CommonRev) throw new Exception("Can not apply the update because of revision conflict. Please try to reload first.");
                        CommonRev++;
                        LocalRev = 0;
                        return true;
            } else {
                CommonRev = old.CommonRev;
                LocalRev = old.LocalRev;
                return false;
            }
        }

        private boolean CompareItemList(ListHolder old) {

            if (old.theList.size() != theList.size()) return false;

            for (int tmp_i = 0; tmp_i < theList.size(); tmp_i++)
                if (!theList.get(tmp_i).ItemsEqual(old.theList.get(tmp_i))) return false;

            return true;

        }

        private void RebuildAccel() {
            int tmp_min = 2000000000, tmp_max = -1;
            for (NameValueList item: theList) {
                String tmp_id_str = item.GetValueFor("ID", "");
                if (tmp_id_str.length() > 0) {
                    try {
                        int tmp_id = Integer.parseInt(tmp_id_str);
                        if ((tmp_id <= 0) || (tmp_id >= 10000)) throw new Exception("Invalid signal ID");
                        if (tmp_min > tmp_id) tmp_min = tmp_id;
                        if (tmp_max < tmp_id) tmp_max = tmp_id;
                    } catch (Exception e) {
                        Tum3Logger.DoLogGlb(true, "WARNING: Invalid ID in signal list: <" + tmp_id_str + ">, ignored");
                    }
                }
            }
            if (tmp_min > tmp_min) {
                IdAccelArr = null;
                IdAccelMin = 0;
                IdAccelMax = 0;
                return;
            }
            IdAccelArr = new int[tmp_max - tmp_min + 1];
            IdAccelMin = tmp_min;
            IdAccelMax = tmp_max;

            for (int tmp_i = 0; tmp_i < theList.size(); tmp_i++) {
                NameValueList item = theList.get(tmp_i);
                String tmp_id_str = item.GetValueFor("ID", "");
                if (tmp_id_str.length() > 0) {
                    try {
                        int tmp_id = Integer.parseInt(tmp_id_str);
                        if ((tmp_id <= 0) || (tmp_id >= 10000)) throw new Exception("Invalid signal ID");
                        IdAccelArr[tmp_id - IdAccelMin] = tmp_i + 1;
                    } catch (Exception e) {
                    }
                }
            }
        }

        public int IndexById(int SignalId) {

            if ((IdAccelArr == null) || (SignalId < IdAccelMin) || (SignalId > IdAccelMax)) return 0;
            return IdAccelArr[SignalId - IdAccelMin];

        }

        private String GenerateTextConf(boolean _withBody) {

            StringBuffer theBuff = new StringBuffer();
            if (_withBody) {
                theBuff.append("[" + base_sect_name + "]" + HandyMisc.crlf);
                for (int tmp_i = 0; tmp_i < theList.size(); tmp_i++)
                    theList.get(tmp_i).AddTo(theBuff, (tmp_i+1) + ".");
            }

            theBuff.append("[" + base_sect_name + "_rev" + "]" + HandyMisc.crlf);
            theBuff.append("CommonRev=" + CommonRev + HandyMisc.crlf);
            theBuff.append("LocalRev=" + LocalRev + HandyMisc.crlf);

            return theBuff.toString();

        }

        private void ParseFrom(StringList the_sect_rev, StringList the_sect_body) throws Exception {

            for (String currSt: the_sect_rev) {
                String tmp_st = currSt.trim();
                if (tmp_st.length() > 0) {
                    int i_eq_sign = tmp_st.indexOf("=");
                    if (i_eq_sign >= 1) {
                        String tmp_name = tmp_st.substring(0, i_eq_sign).trim();
                        String tmp_value = tmp_st.substring(i_eq_sign+1, tmp_st.length()).trim();
                        //SetValueFor(tmp_name, tmp_value);
                        if ("CommonRev".equals(tmp_name))     CommonRev = Integer.parseInt(tmp_value);
                        else if ("LocalRev".equals(tmp_name)) LocalRev = Integer.parseInt(tmp_value);
                    }
                } 
            }

            int tmp_last_n = -1;
            NameValueList tmp_curr_item = null;
            for (int tmp_i = 0; tmp_i < the_sect_body.size(); tmp_i++) {
                String tmp_st = the_sect_body.get(tmp_i).trim();
                if (tmp_st.length() > 0) {
                    int i_eq_sign = tmp_st.indexOf("=");
                    if (i_eq_sign >= 1) {
                        String tmp_name = tmp_st.substring(0, i_eq_sign);
                        String tmp_value = tmp_st.substring(i_eq_sign+1, tmp_st.length());
                        int k_dot = tmp_name.indexOf(".");
                        if (k_dot > 0) {
                            int tmp_n = Integer.parseInt(tmp_name.substring(0, k_dot));
                            if (tmp_n < 1) throw new Exception("Tum3SignalList: Content error: found invalid index " + tmp_n);
                            tmp_n--;
                            tmp_name = tmp_name.substring(k_dot+1, tmp_name.length());
                            if (tmp_n != tmp_last_n) {
                                tmp_last_n = tmp_n;
                                if (tmp_last_n < theList.size()) tmp_curr_item = theList.get(tmp_last_n);
                                else if (tmp_last_n == theList.size()) {
                                    tmp_curr_item = new NameValueList();
                                    theList.add(tmp_curr_item);
                                } else throw new Exception("Tum3SignalList: Content error: found index " + tmp_last_n + " but expected " + theList.size() + " or less.");
                            }
                            tmp_curr_item.AddNameVal(tmp_name, tmp_value);
                        } 
                    }
                } 
            }

        }

    }

    //private String DB_CONF_PATH;
    private final static String confPath = fetchConfPath();
    //private Tum3Db parent_db;

    private ListHolder theGroupList = new ListHolder("signal_groups");
    private ListHolder theRawSignalsList = new ListHolder("signal_ids");
    private ListHolder thePubFmlList = new ListHolder("pub_formulas");


    private final static String fetchConfPath() {

        return Tum3cfg.getGlbParValue(TUM3_CFG_db_conf_root);

    }

    public Tum3SignalList(boolean load_ini) {

        //parent_db = this_db;
        //System.out.println("[aq2j] DEBUG: Created Tum3SignalList");
        if (load_ini) {
            try {

                LoadFromFile(theGroupList, CONST_GRPS_CMN);
                //System.out.println("[aq2j] DEBUG: Loaded " + theGroupList.theList.size() + " groups;");

                //String line = theGroupList.theList.get(0).GetValueFor("Group", "");
                //System.out.println("[DEBUG]: Tum3SignalList: <" + line + "><" + Tum3Util.StrHexDump(line) + ">");

                LoadFromFile(theRawSignalsList, CONST_SGNL_CMN);
                theRawSignalsList.RebuildAccel();
                //System.out.println("[aq2j] DEBUG: Loaded " + theRawSignalsList.theList.size() + " signals;");
                LoadFromFile(thePubFmlList, CONST_FMLS_CMN);
                //System.out.println("[aq2j] DEBUG: Loaded " + thePubFmlList.theList.size() + " pub.formulas;");

            } catch (Exception e) {
                Tum3Logger.DoLogGlb(true, "Error reading Tum3SignalList: " + Tum3Util.getStackTrace(e));
            }
        }

    }

    public int FindIndex(int SignalId) {

        return theRawSignalsList.IndexById(SignalId);

    }

    public NameValueList GetSignalEntry(int the_index_inc) {

        return theRawSignalsList.theList.get(the_index_inc - 1);

    }

    public int SignalCount() {

        return theRawSignalsList.theList.size();

    }

    public Tum3SignalList CreatePhoto() {

        Tum3SignalList tmp_new = new Tum3SignalList(false);
        tmp_new.theGroupList.CopyFrom(theGroupList);
        tmp_new.theRawSignalsList.CopyFrom(theRawSignalsList);
        tmp_new.thePubFmlList.CopyFrom(thePubFmlList);
        return tmp_new;

    }

    private void LoadFromFile(ListHolder theHolder, String the_ini_name) throws Exception {

        String tmp_fname = confPath + the_ini_name + ".ini";
        //System.out.println("[DEBUG]: LoadFromFile: opening <" + tmp_fname + ">");
        SectionList tmpSections = null;
        try {
            tmpSections = new SectionList(StringList.readFromFile(tmp_fname));
        } catch (Exception e) {
            Tum3Logger.DoLogGlb(true, "WARNING: Tum3SignalList.LoadFromFile(): <" + tmp_fname + "> is invalid or inaccessible.");
            return;
        }

        StringList tmpSectBody = tmpSections.GetByName(theHolder.base_sect_name);
        StringList tmpSectRev = tmpSections.GetByName(theHolder.base_sect_name + "_rev");

        if ((tmpSectBody != null) && (tmpSectRev != null))
            theHolder.ParseFrom(tmpSectRev, tmpSectBody);
        else
            Tum3Logger.DoLogGlb(false, "Hint: LoadFromFile: file <" + tmp_fname + "> does not contain expected section(s).");

    }

    private void WriteIniFile(ListHolder theHolder, String the_ini_name) throws Exception {

        Tum3Util.WriteIniWithBak(confPath, the_ini_name, theHolder.GenerateTextConf(true));

    }

    public void SaveToIni() throws Exception {

        if (theGroupList.NeedSave) WriteIniFile(theGroupList, CONST_GRPS_CMN);
        if (theRawSignalsList.NeedSave) WriteIniFile(theRawSignalsList, CONST_SGNL_CMN);
        if (thePubFmlList.NeedSave) WriteIniFile(thePubFmlList, CONST_FMLS_CMN);

    }

    public void ZipFullConf(ZipOutputStream dst) throws Exception {

        ZipAddOne(dst, theGroupList, CONST_GRPS_CMN);
        ZipAddOne(dst, theRawSignalsList, CONST_SGNL_CMN);
        ZipAddOne(dst, thePubFmlList, CONST_FMLS_CMN);

    }

    private void ZipAddOne(ZipOutputStream dst, ListHolder theHolder, String the_ini_name) throws Exception {

        dst.putNextEntry(new ZipEntry(the_ini_name + ".ini"));
        dst.write(Tum3Util.StringToBytesRaw(theHolder.GenerateTextConf(true)));

    }

    private ListHolder LoadFromNet(ListHolder theHolder, String the_ini_name, NameValueList theList, StringBuffer ext_result) throws Exception {

        String tmp_raw_body = theList.GetValueFor(the_ini_name, "");
        if (tmp_raw_body.length() > 0) {
            SectionList tmpSections = new SectionList(new StringList(tmp_raw_body.split("\r\n")));

            StringList tmpSectBody = tmpSections.GetByName(theHolder.base_sect_name);
            StringList tmpSectRev = tmpSections.GetByName(theHolder.base_sect_name + "_rev");
            if ((tmpSectBody != null) && (tmpSectRev != null)) {
                ListHolder tmp_new_holder = new ListHolder(theHolder.base_sect_name);
                tmp_new_holder.ParseFrom(tmpSectRev, tmpSectBody);
                boolean tmp_changed = tmp_new_holder.UpdateRevision(theHolder);
                ext_result.append(tmp_new_holder.GenerateTextConf(false));
                if (tmp_changed) return tmp_new_holder;
                else return null;
            }
        }
        return null;

    }

    public void ParseFromNet(NameValueList theList, StringBuffer ext_result) throws Exception {

        ListHolder tmp_new = null;
        theGroupList.NeedSave = false;
        theRawSignalsList.NeedSave = false;
        thePubFmlList.NeedSave = false;
        tmp_new = LoadFromNet(theGroupList, CONST_GRPS_CMN, theList, ext_result);
        if (tmp_new != null) {
            tmp_new.NeedSave = true;
            theGroupList = tmp_new;
            //System.out.println("[aq2j] DEBUG: Loaded " + theGroupList.theList.size() + " groups;");
        }
        tmp_new = LoadFromNet(theRawSignalsList, CONST_SGNL_CMN, theList, ext_result);
        if (tmp_new != null) {
            tmp_new.NeedSave = true;
            theRawSignalsList = tmp_new;
            theRawSignalsList.RebuildAccel();
            //System.out.println("[aq2j] DEBUG: Loaded " + theRawSignalsList.theList.size() + " signals;");
        }
        tmp_new = LoadFromNet(thePubFmlList, CONST_FMLS_CMN, theList, ext_result);
        if (tmp_new != null) {
            tmp_new.NeedSave = true;
            thePubFmlList = tmp_new;
            //System.out.println("[aq2j] DEBUG: Loaded " + thePubFmlList.theList.size() + " pub.formulas;");
        }
    }

    public static String ConfigUploadResult(String the_error, StringBuffer ext_result) {

        return
                "[ConfigUploadResult]" + HandyMisc.crlf
                + "Error=" + Tum3Util.StrMultilineToOneLine(the_error) + HandyMisc.crlf
                + ext_result.toString() + HandyMisc.crlf;

    }

    public boolean NeedSaveSome() {

        return theGroupList.NeedSave || theRawSignalsList.NeedSave || thePubFmlList.NeedSave;

    }

    public void GetConfs(String the_names, NameValueList the_output) {

        String[] tmp_names = the_names.split(",");

        ListHolder tmpHolder = null;
        StringBuffer tmpBuff = new StringBuffer();

        for (int tmp_i = 0; tmp_i < tmp_names.length; tmp_i++) {
            tmpHolder = null;
            if (CONST_GRPS_CMN.equals(tmp_names[tmp_i]))
                tmpHolder = theGroupList;
            else if (CONST_SGNL_CMN.equals(tmp_names[tmp_i]))
                tmpHolder = theRawSignalsList;
            else if (CONST_FMLS_CMN.equals(tmp_names[tmp_i]))
                tmpHolder = thePubFmlList;
            if (tmpHolder != null)
                the_output.AddNameVal(tmp_names[tmp_i], tmpHolder.GenerateTextConf(true));
        }
    }

    public static Tum3SignalList GetSignalList() {

        synchronized(SignalListQuickLock) {

            if (SignalList == null) SignalList = new Tum3SignalList(true);
            return SignalList;

        }
    }

    public static String PutSignalList(int _db_idx, NameValueList theList, StringBuffer ext_result) {

        if (!Tum3cfg.isWriteable(_db_idx) || !Tum3cfg.isGlbWriteable()) return Tum3Db.CONST_MSG_READONLY_NOW;

        String tmp_txt = "Unknown server error";
        try {
            Tum3SignalList baseSignalList = GetSignalList();
            Tum3SignalList tmp_new_list = baseSignalList.CreatePhoto();
            tmp_new_list.ParseFromNet(theList, ext_result);

            synchronized(SignalListStoreLock) {
                synchronized(SignalListQuickLock) {
                    if (baseSignalList != SignalList) return "Update was intervened by some other conflicting operation, please try later.";
                    SignalList = tmp_new_list;
                }
                if (tmp_new_list.NeedSaveSome()) tmp_new_list.SaveToIni();
            }

            tmp_txt = "";
        } catch (Exception e) {
            tmp_txt = "Exception: " + Tum3Util.getStackTrace(e);
        }

        return tmp_txt;

    }

}
