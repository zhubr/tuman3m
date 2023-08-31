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
import java.nio.*;
import java.nio.channels.FileChannel;

import aq2net.TumProtoConsts;


class SomeHeader {

    ByteBuffer buf = null;

    protected int Remaining() {
        return buf.limit() - buf.position();
    }

    protected int getWord() {
        int b0, b1;
        b0 = buf.get();
        b1 = buf.get();
        return (0xFF & (int)b0) + (0xFF00 & (((int)b1)<<8));
    }

    protected void putWord(int w) {
        byte b0 = (byte)w;
        byte b1 = (byte)(w >> 8);
        buf.put(b0);
        buf.put(b1);
    }

    protected String getStringPas(int len) {
        int tmp_len=buf.get();
        StringBuffer tmp_st = new StringBuffer();
        for (int tmp_i=0; tmp_i<len; tmp_i++)
            if (tmp_i < tmp_len) tmp_st.append((char)buf.get());
            else buf.get();
        return tmp_st.toString();
    }

    protected void putStringPas(String s, int len) {

        if (s.length() > len) s = s.substring(0, len);
        buf.put((byte)s.length());
        for (int tmp_i=0; tmp_i<len; tmp_i++)
            if (tmp_i < s.length()) buf.put((byte)s.charAt(tmp_i));
            else buf.put((byte)0);
    }
}

class SignalHeaderClass extends SomeHeader {
    int HSizeCorr; // YYY
    int HSize, HID;
    int HStatus;
    String hdrOriginalShotName;
    int ProgramSubversion;
    int wYear, wMonth, wDay, wHour, wMinute, wSecond;
    int SigUpdateCounter;

    public static int StaticSize() {
        return 76; // 71; // 63; // YYY
    }

    public void readAll() {
        if (Remaining() >= 4) HID = buf.getInt();
        else return;
        if (Remaining() >= 2) HStatus = getWord();
        else return;
        if (Remaining() >= 41) hdrOriginalShotName = getStringPas(40);
        else return;
        if (Remaining() >= 4) ProgramSubversion = buf.getInt();
        else return;
        if (Remaining() >= 2) wYear = getWord();
        else return;
        if (Remaining() >= 2) wMonth = getWord();
        else return;
        if (Remaining() >= 2) wDay = getWord();
        else return;
        if (Remaining() >= 2) wHour = getWord();
        else return;
        if (Remaining() >= 2) wMinute = getWord();
        else return;
        if (Remaining() >= 2) wSecond = getWord();
        else return;
        if (Remaining() >= 4) SigUpdateCounter = buf.getInt();
        else return;
    }

    public void writeAll() {
        buf.putInt(HSize);
        buf.putInt(HID);
        putWord(HStatus);
        putStringPas(hdrOriginalShotName, 40);
        buf.putInt(ProgramSubversion);
        putWord(wYear);
        putWord(wMonth);
        putWord(wDay);
        putWord(wHour);
        putWord(wMinute);
        putWord(wSecond);
        buf.putInt(SigUpdateCounter);
    }
}

class NewHeaderClass extends SomeHeader {
    int HSize, DirCount, DirLimit, ProgramSubversion;
    int wYear, wMonth, wDay, wHour, wMinute, wSecond;
    int dwUpdateCounter;
    String hdrOriginalShotName;
    byte hdrAlignFillerByte;
    int hdrPuffProgramOfs, hdrPuffProgramLen;

    public static int StaticSize() {
        return 80;
    }

    public void readAll() {
        if (Remaining() >= 4) DirCount = buf.getInt();
        else return;
        if (Remaining() >= 4) DirLimit = buf.getInt();
        else return;
        if (Remaining() >= 4) ProgramSubversion = buf.getInt();
        else return;
        if (Remaining() >= 2) wYear = getWord();
        else return;
        if (Remaining() >= 2) wMonth = getWord();
        else return;
        if (Remaining() >= 2) wDay = getWord();
        else return;
        if (Remaining() >= 2) wHour = getWord();
        else return;
        if (Remaining() >= 2) wMinute = getWord();
        else return;
        if (Remaining() >= 2) wSecond = getWord();
        else return;
        if (Remaining() >= 2) dwUpdateCounter = getWord();
        else return;
        if (Remaining() >= 41) hdrOriginalShotName = getStringPas(40);
        else return;
        if (Remaining() >= 1) hdrAlignFillerByte = buf.get();
        else return;
        if (Remaining() >= 4) hdrPuffProgramOfs = buf.getInt();
        else return;
        if (Remaining() >= 4) hdrPuffProgramLen = buf.getInt();
        else return;
    }

    public void writeAll() {
        buf.putInt(HSize);
        buf.putInt(DirCount);
        buf.putInt(DirLimit);
        buf.putInt(ProgramSubversion);
        putWord(wYear);
        putWord(wMonth);
        putWord(wDay);
        putWord(wHour);
        putWord(wMinute);
        putWord(wSecond);
        putWord(dwUpdateCounter);
        putStringPas(hdrOriginalShotName, 40);
        buf.put(hdrAlignFillerByte);
        buf.putInt(hdrPuffProgramOfs);
        buf.putInt(hdrPuffProgramLen);
    }

}

class HeaderWriter {

    protected ByteBuffer buf = null;

    public HeaderWriter(ByteBuffer thisBuf) {
        buf = thisBuf;
    }

    protected void putInt(int i) {
        buf.putInt(i);
    }
}

class HeaderWriterTraceSignPack extends HeaderWriter {

    public int HSize=0, HType=0, HCount=0;
    public float HTact=0, HDataStart=0, HCalibr=0, HZeroline=0;
    public int HDataSize=0;

    public static final int DTYPE_System01 = 53;
    public static final int DTYPE_System02 = 56;

    public HeaderWriterTraceSignPack(ByteBuffer thisBuf) {
        super(thisBuf);
    }

    public static int StaticSize() {
        return 4*4 + 4*4;
    }

    public void writeAll() {
        HSize = StaticSize();
        buf.putInt(HSize);
        buf.putInt(HType);
        buf.putInt(HCount);
        buf.putFloat(HTact);
        buf.putFloat(HDataStart);
        buf.putFloat(HCalibr);
        buf.putFloat(HZeroline);
        buf.putInt(HDataSize);
    }

}

abstract class BaseContinuator {

    protected long myLength; // YYY int --> long
    protected long writtenCount = 0; // YYY int --> long
    protected boolean use_trailing_status = false;

    public boolean withTrailingStatus() {

        return use_trailing_status;

    }

    public void EnsureOfs(long _seg_ofs) throws Exception {

        if (_seg_ofs != writtenCount) throw new Exception("Segment offset mismatch between OutBuffContinuator and OutgoingBuff");

    }

    public void ForceXByte() {

        use_trailing_status = true;

    }

    public long getPos() {

        return writtenCount;

    }

    public long getFullSizeX() {

        if (use_trailing_status) return myLength + 1;
        else return myLength;

    }
}

class TraceReaderContinuator extends BaseContinuator implements OutBuffContinuator {

    private volatile RandomAccessFile myFF;
    private Tum3Shot myShot;
    private String myFName;
    private boolean was_error = false;
    private byte WasEdited = 0;
    private int user_count = 1;
    public final boolean with_warning;


    public TraceReaderContinuator(Tum3Shot thisShot, String thisFName, RandomAccessFile thisFF, long thisLength, byte _edited, boolean _use_trailing_status, boolean _with_warning) {
        myFF = thisFF;
        myLength = thisLength;
        myShot = thisShot;
        myFName = thisFName;
        WasEdited = _edited;
        use_trailing_status = _use_trailing_status;
        with_warning = _with_warning;
    }

    public boolean WithWarning() {

        return with_warning;

    }

    public boolean PleaseWait() {

        return false;

    }

    private byte getTrailingByte() {

        return 0; // XXX TODO.

    }

    public byte getEditedByte() {

        return WasEdited;

    }

    public int ReadTo(byte[] buff, int ofs, int count) {
        //System.out.print(" ReadTo(" + was_error + "," + ofs + "," + count + "=" ); Tum3Util.SleepExactly(3000);
        if (!was_error) {
            try {
                long tmp_count_l = myLength - writtenCount;
                if (tmp_count_l > count) tmp_count_l = count;
                if (tmp_count_l < 0)     tmp_count_l = 0;
                int tmp_count = (int)tmp_count_l; // YYY
                if (null == myFF) {
                    Tum3Logger.DoLog(myShot.DbName(), true, "WARNING: <" + Thread.currentThread().getId() + "> TraceReaderContinuator.ReadTo(): myFF == null for '" + myFName);
                    was_error = true;
                }
                if (tmp_count > 0)
                    myFF.readFully(buff, ofs, tmp_count);
                writtenCount += tmp_count;
                if (use_trailing_status && (writtenCount == myLength) && (count > tmp_count)) {
                    buff[ofs+tmp_count] = getTrailingByte();
                    tmp_count++;
                    writtenCount++;
                }
                //System.out.print("[tmp_count=" + tmp_count + "]");
                //System.out.print(tmp_count + " ok)" );
                return tmp_count;
            } catch (Exception e) {
                //System.out.print(" err)" );
                was_error = true;
                Tum3Logger.DoLog(myShot.DbName(), true, "DEBUG: file read error in '" + myFName + "' with: " + Tum3Util.getStackTrace(e));
            }
        }

        long tmp_count_l = myLength - writtenCount;
        if (use_trailing_status) tmp_count_l++;
        if (tmp_count_l > count) tmp_count_l = count;
        int tmp_count = (int)tmp_count_l; // YYY
        Arrays.fill(buff, ofs, ofs+tmp_count, (byte)0);
        writtenCount += tmp_count;

        return tmp_count;
    }

    public void close() {
        user_count--;
        //System.out.println("[debug] close() for '" + myFName + "': now " + user_count);
        if (user_count > 0) return;
        Tum3Shot tmpShot = null;
        try {
            //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> TraceReaderContinuator.close(): myFF := null for '" + myFName + "'");
            myFF.close();
            myFF = null;
        } catch (Exception e) {
            if (null != myFF)
                Tum3Logger.DoLog(myShot.DbName(), true, "IMPORTANT: close file error in '" + myFName + "' with: " + Tum3Util.getStackTrace(e));
        }
        //synchronized (this) { // Reminder: locking not necessary because continuators are only operated in one thread.
        tmpShot = myShot;
        myShot = null;
        //}
        if (null != tmpShot) {
            tmpShot.ShotRelease();
            //System.out.println("[debug] close() for '" + myFName + "': released shot.");
        }
    }

    public void AddUser() {
        user_count++;
        //System.out.println("[debug] AddUser() for '" + myFName + "': now " + user_count);
        /*
    Tum3Shot tmpShot = null;
    //synchronized (this) { // Reminder: locking not necessary because continuators are only operated in one thread.
      tmpShot = myShot;
    //}
    if (null != tmpShot) tmpShot.AddUser();
         */
    }

}

class ImmediateArrayContinuator extends BaseContinuator implements OutBuffContinuator {

    private byte[] myBuff;
    private boolean please_wait = false;


    public ImmediateArrayContinuator(byte[] thisBuff, int thisLength, boolean _please_wait) {
        myBuff = thisBuff;
        myLength = thisLength;
        please_wait = _please_wait;
    }

    public boolean WithWarning() {

        return false;

    }

    public boolean PleaseWait() {

        return please_wait;

    }

    public byte getEditedByte() {
        return 0;
    }

    private byte getTrailingByte() {

        return 0; // Note: this is unused, should be zero.

    }

    public int ReadTo(byte[] buff, int ofs, int count) {

        int tmp_count = (int)(myLength - writtenCount);
        if (tmp_count > count) tmp_count = count;
        if (tmp_count < 0)     tmp_count = 0;
        System.arraycopy(myBuff, (int)writtenCount, buff, ofs, tmp_count);
        writtenCount += tmp_count;

        if (use_trailing_status && (writtenCount == myLength) && (count > tmp_count)) {
            buff[ofs+tmp_count] = getTrailingByte();
            tmp_count++;
            writtenCount++;
        }
        return tmp_count;
    }

    public void AddUser() {
        // Note. This class does not need any reference management.
    }

    public void close() {
        // Note. This class does not need any explicite cleanup.
    }
}


public class Tum3Shot {

    private static final int const_tum3ng_sign_h = 0x484D5554;
    private static final int const_tum3ng_sign_s = 0x534D5554;

    private static final String FSUFF_NORMAL = ".000"; // YYY
    private static final String FSUFF_CFGZIP = ".010"; // YYY
    private static final String FSUFF_BUP_DENSITY = ".001"; // YYY
    private static final String FSUFF_BUP_GENERAL = ".002"; // YYY
    private static final String FSUFF_TMPFILE = ".900"; // ".003"; // YYY
    private static final String FSUFF_FLAGPREP = ".801"; // YYY
    private static final String FSUFF_FLAGDONE = ".800"; // YYY

    private static final int CONST_MIN_SIGN_PACK_LEN = 100; // XXX TODO!!! Make it configurable.
    private static final int CONST_DENSITY_FSIZE_LIMIT = 200000;

    private Tum3Db parent_db;
    public final boolean isWriteable; // YYY
    private String shotName, shotPathMain, shotPathVol, shotSubdir;
    private volatile HashMap<Integer, Byte> CacheIds = new HashMap<Integer, Byte>();
    private volatile boolean creation_complete = false;
    private volatile int UserCount = 0;
    private volatile long LastUsedAt = 0;
    private volatile boolean NotStored, Valid = false;
    private Object CreationLock;
    private UtilCreateFile1 FF;
    private int DirOffset = 0;
    private NewHeaderClass NewHeader;
    private volatile boolean is_new = false;
    private String new_puff_program;
    private ByteArrayOutputStream new_zip_configs;
    private volatile int[] expected_ids = null;

    private final static int min_hlen_ever = 32; // YYY
    private final static int min_hlen_Fmt64Ver = 336; // YYY
    private final static int ofs_HDataSize_old = 4*7; // YYY
    private final static int ofs_HDataSize_new = 328; // YYY
    private final static int ofs_HMetaDataSize_old = 304; // YYY
    private final static int ofs_HMetaDataSize_new = 392; // YYY


    static class TraceMetaData extends HashMap<String, String> {

        public TraceMetaData(byte[] src_bytes) {

            boolean tmp_in_val = false;
            String curr_name = "", curr_val = "";
            StringBuffer curr = new StringBuffer();
            for (int tmp_i = 0; tmp_i < src_bytes.length; tmp_i++) {
                //System.out.print((char)src_bytes[tmp_i]);
                byte tmp_b = src_bytes[tmp_i];
                if (tmp_b == 0) {
                    if (tmp_in_val) {
                        curr_val = curr.toString();
                        curr = new StringBuffer();
                        if (!curr_name.isEmpty()) {
                            put(curr_name, curr_val);
//System.out.print("[DEBUG] <" + curr_name + ">=<" + curr_val + ">");
                        }
                    } else {
                        curr_name = curr.toString();
                        curr = new StringBuffer();
                    }
                    tmp_in_val = !tmp_in_val;
                } else curr.append((char)tmp_b);
            }

        }

    }

    static class UtilCreateFile1 {

        public boolean NotStored;
        //public boolean ReadOnly;
        public RandomAccessFile raf = null;
        private String Fname;
        int tmpNewHeaderSizeInFile, tmpFiledHSize;
        long tmpBuffSize; // YYY
        boolean hdr_is_v1 = false; // YYY
        SignalHeaderClass tmpFirstHeader;
        byte WasEdited;
        private final String db_name;

        public UtilCreateFile1(String _DbName, String MainFName, boolean need_write) throws Exception {

            db_name = _DbName;
            Fname = MainFName;
            //ReadOnly = true;
            if (need_write) {
                raf = new RandomAccessFile(MainFName, "rw");
                //System.out.println("[aq2j] DEBUG: File '" + MainFName + "' opened(w).");
                //ReadOnly = false;
                NotStored = false;
            } else {
                NotStored = false;
                try {
                    raf = new RandomAccessFile(MainFName, "r");
                    //System.out.println("[aq2j] DEBUG: File '" + MainFName + "' opened(r).");
                } catch (Exception e) {
                    if (e instanceof FileNotFoundException) {
                        NotStored = true;
                    }
                }
            } 
        }

        public void readHeaders(String _shotName, int _SignalId) throws Exception {

            byte[] buff0 = new byte[SignalHeaderClass.StaticSize()];
            ByteBuffer tmpBB0 = ByteBuffer.wrap(buff0);
            tmpBB0.order(ByteOrder.LITTLE_ENDIAN);
            raf.readFully(buff0, 0, 8);
            tmpBB0.limit(8);
            if (tmpBB0.getInt() != const_tum3ng_sign_s)
                throw new Exception("File '"+FileName()+"' is unknown format or corrupted (bad signature)");
            //System.out.println("[aq2j] DEBUG: signature OK in '" + Fname + "' ...");
            tmpNewHeaderSizeInFile = tmpBB0.getInt();
            tmpFirstHeader = new SignalHeaderClass();
            tmpFirstHeader.HSize = tmpNewHeaderSizeInFile;
            tmpFirstHeader.HSizeCorr = tmpNewHeaderSizeInFile; // YYY
            if (tmpFirstHeader.HSizeCorr > buff0.length) tmpFirstHeader.HSizeCorr = buff0.length; // YYY
            raf.readFully(buff0, 0, tmpFirstHeader.HSizeCorr-4); // YYY
            tmpBB0.clear();
            tmpBB0.limit(tmpFirstHeader.HSizeCorr-4); // YYY
            tmpFirstHeader.buf = tmpBB0;
            tmpFirstHeader.readAll();
            if ((tmpFirstHeader.HID != _SignalId) || (!tmpFirstHeader.hdrOriginalShotName.equals(_shotName)) || (tmpFirstHeader.HStatus != 0)) {
                //System.out.println("[aq2j] DEBUG: tmpFirstHeader.HID="+tmpFirstHeader.HID);
                //System.out.println("[aq2j] DEBUG: tmpFirstHeader.hdrOriginalShotName="+tmpFirstHeader.hdrOriginalShotName);
                //System.out.println("[aq2j] DEBUG: tmpFirstHeader.HStatus="+tmpFirstHeader.HStatus);
                throw new Exception("First header in '" + FileName() + "' is not acceptable to continue.");
            }
            //System.out.println("[aq2j] DEBUG: first header OK in '" + Fname + "' ...");

            // Now try to see if density editing happened already.
            int tmpSigUpdateCounter = 0;
            if ((_shotName.length() >= 8) && (_shotName.length() <= 10)) {
                try {
                    //System.out.println("[DEBUG] analysing SigUpdateCounter in " + _shotName);
                    int FYear = Integer.parseInt(_shotName.substring(0, 2));
                    if (FYear > 90) FYear = 1900 + FYear;
                    else FYear = 2000 + FYear;
                    int FMonth = Integer.parseInt(_shotName.substring(2, 4));
                    int FDay = Integer.parseInt(_shotName.substring(4, 6));
                    //System.out.println("[DEBUG] FYear=" + FYear + " FMonth=" + FMonth + " FDay=" + FDay);
                    //System.out.println("[DEBUG] tmpFirstHeader.SigUpdateCounter=" + tmpFirstHeader.SigUpdateCounter);

                    int tmp_date_int = (FYear * 20 + FMonth) * 40 + FDay;
                    if ((FYear == tmpFirstHeader.wYear) && (FMonth == tmpFirstHeader.wMonth) && (FDay == tmpFirstHeader.wDay) && (tmp_date_int >= ((2013 * 20 + 03) * 40 + 06)))
                        tmpSigUpdateCounter = tmpFirstHeader.SigUpdateCounter;
                    if (tmpFirstHeader.SigUpdateCounter >= 2)
                        tmpSigUpdateCounter = tmpFirstHeader.SigUpdateCounter;
                } catch (Exception ignored) {}
            }
            if (tmpSigUpdateCounter >= 2) // So: 0: unknown, 1: not modified, > 1: modified.
                WasEdited = 2;
            else if (tmpSigUpdateCounter < 0)
                WasEdited = 0;
            else
                WasEdited = (byte)tmpSigUpdateCounter;

            raf.seek(4 + tmpNewHeaderSizeInFile);
            raf.readFully(buff0, 0, 4);
            tmpBB0.clear(); tmpBB0.limit(4);
            tmpFiledHSize = tmpBB0.getInt();
            if (tmpFiledHSize < min_hlen_ever)
                throw new Exception("UnifiedTraceHeader size is too small (" + tmpFiledHSize + ") in '" + FileName() + "'");
            if (tmpFiledHSize >= min_hlen_Fmt64Ver) { // YYY
                raf.seek(4 + tmpNewHeaderSizeInFile + 12*4+256); // old HMetaDataSize
                raf.readFully(buff0, 0, 4);
                tmpBB0.clear(); tmpBB0.limit(4);
                if (0 == tmpBB0.getInt()) { // YYY
                    hdr_is_v1 = true; // YYY
                    raf.seek(4 + tmpNewHeaderSizeInFile + ofs_HDataSize_new); // new HDataSize
                    raf.readFully(buff0, 0, 8);
                    tmpBB0.clear(); tmpBB0.limit(8);
                    tmpBuffSize = tmpBB0.getLong(); // YYY
                }
            }
            if (!hdr_is_v1) { // YYY
                raf.seek(4 + tmpNewHeaderSizeInFile + ofs_HDataSize_old); // old HDataSize
                raf.readFully(buff0, 0, 4);
                tmpBB0.clear();
                tmpBB0.limit(4);
                tmpBuffSize = tmpBB0.getInt();
            }
            //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> UnifiedTraceHeader in '" + Fname + "': HSize=" + tmpFiledHSize + " HDataSize=" + tmpBuffSize);

        }

        public void close() {
            if (raf != null)
                try {
                    raf.close();
                    raf = null;
                    //System.out.println("[aq2j] DEBUG: closed file '" + Fname + "' successfully.");
                } catch (Exception e) {
                    Tum3Logger.DoLog(db_name, true, "WARNING: close() error in '" + Fname + "': " + Tum3Util.getStackTrace(e));
                }
        }

        public String FileName() {
            return Fname;
        }
    }

    public Tum3Shot(Tum3Db this_db, String this_path_main, String this_path_vol, String this_subdir, String this_shot_name, boolean as_new, String puff_program, int[] _expected_ids, ByteArrayOutputStream aq_zip_configs) {

        parent_db = this_db;
        isWriteable = parent_db.isWriteable; // Tum3cfg.isWriteable(parent_db.getIndex()); // YYY
        shotName = this_shot_name;
        shotPathMain = this_path_main;
        shotPathVol =  this_path_vol;
        shotSubdir = this_subdir;
        is_new = as_new;
        new_puff_program = puff_program;
        new_zip_configs = aq_zip_configs;
        expected_ids = _expected_ids;

        CreationLock = new Object();
        //System.out.println("[aq2j] DEBUG: Created Tum3Shot(" + shotPathMain + ", " + shotPathVol + ", " + shotName + ")");

    }

    public String DbName() {

        return parent_db.DbName();

    }

    public Tum3Db GetDb() {

        return parent_db;

    }


    private synchronized void ClearExpectedIds() {

        if (CacheIds == null)
            Tum3Logger.DoLog(DbName(), true, "Warning: ClearExpectedIds called after detach in <" + shotName + ">");
        else
            synchronized(CacheIds) { expected_ids = null; }

    }

    public void DoneSaving() {

        is_new = false;
        ClearExpectedIds();
        ShotRelease();

    }

    public void CompleteCreation() throws Exception {

        synchronized(CreationLock) {

            if (creation_complete) return;
            creation_complete = true;

            NewHeader = new NewHeaderClass();

            NotStored = true;

            if (is_new) {
                File tmp_monthdir = new File(shotPathMain + shotSubdir);
                //System.out.println("[aq2j] DEBUG: Checking month dir <" + shotPathMain + shotSubdir + ">");
                // [aq2j] DEBUG: Checking month dir <T:\_tmp\data\1809>
                if (!tmp_monthdir.exists()) tmp_monthdir.mkdir();
            }

            File tmp_shotdir = new File(shotPathMain + shotSubdir + File.separator + shotName);
            if (!tmp_shotdir.exists()) {
                if (is_new) {
                    CreateNew();
                } else {
                    Valid = true;
                }
                return;
            }
            DoOpenMainFile();

            DirOffset = -1;

            if (NotStored) Valid = true;
            else {
                try {
                    //System.out.println("[aq2j] DEBUG: reading '" + shotPathMain + shotSubdir + File.separator + shotName + "' ...");
                    byte[] buff0 = new byte[NewHeaderClass.StaticSize()];
                    ByteBuffer tmpBB0 = ByteBuffer.wrap(buff0);
                    tmpBB0.order(ByteOrder.LITTLE_ENDIAN);
                    FF.raf.readFully(buff0, 0, 8);
                    tmpBB0.limit(8);
                    if (tmpBB0.getInt() != const_tum3ng_sign_h)
                        throw new Exception("File '" + shotPathMain + shotSubdir + File.separator + shotName+"' is unknown format or corrupted (bad signature)");
                    //System.out.println("[aq2j] DEBUG: signature OK in '" + shotPathMain + shotSubdir + File.separator + shotName + "' ...");
                    int tmpNewHeaderSizeInFile = tmpBB0.getInt();
                    if (tmpNewHeaderSizeInFile < 16)
                        throw new Exception("File '" + shotPathMain + shotSubdir + File.separator + shotName+"' is unknown format or corrupted (hsize < 16)");
                    NewHeader.HSize = tmpNewHeaderSizeInFile;
                    if (NewHeader.HSize > NewHeaderClass.StaticSize()) NewHeader.HSize = NewHeaderClass.StaticSize();
                    tmpBB0.clear();
                    tmpBB0.putInt(NewHeader.HSize);

                    //FDirOffset := 4 + FPrevHeaderSize;
                    //if FHeader0[1] < SizeOf(TNewHeader) then
                    //FHeader0[1] := SizeOf(TNewHeader);
                    //GetMem(FpNewHeader, FHeader0[1]);
                    //FillChar(FpNewHeader^, FHeader0[1], 0);

                    FF.raf.readFully(buff0, 4, NewHeader.HSize - 4);
                    tmpBB0.clear();
                    tmpBB0.position(4);
                    tmpBB0.limit(NewHeader.HSize);
                    NewHeader.buf = tmpBB0;
                    NewHeader.readAll();
                    if (!NewHeader.hdrOriginalShotName.toUpperCase().equals(shotName.toUpperCase()))
                        throw new Exception("Shot number mismatch: '"+shotName+"' differs from '"+NewHeader.hdrOriginalShotName+"', open canceled");
                    //System.out.println("[aq2j] DEBUG: NewHeader.wYear=" + NewHeader.wYear);

                    if ((NewHeader.hdrPuffProgramOfs > 0) && (NewHeader.hdrPuffProgramLen > 0)) {
                        //System.out.println("[aq2j] DEBUG: puff prog ...");
                        FF.raf.seek(NewHeader.hdrPuffProgramOfs);

                        byte[] buff1 = new byte[NewHeader.HSize + NewHeader.hdrPuffProgramLen];
                        ByteBuffer tmpBB1 = ByteBuffer.wrap(buff1);
                        tmpBB1.order(ByteOrder.LITTLE_ENDIAN);
                        NewHeader.buf = tmpBB1;
                        NewHeader.hdrPuffProgramOfs = NewHeader.HSize; // Reminder! Within a file, hdrPuffProgramOfs means 
                        // absolute file offset. When sending a network message, it means absolute offset from 
                        // this message start. And because file also has a signature before, such offsets have
                        // to differ.
                        NewHeader.writeAll();
                        FF.raf.readFully(buff1, NewHeader.HSize, NewHeader.hdrPuffProgramLen);
                        tmpBB1.clear();
                        //System.out.println("[aq2j] DEBUG: header total = " + tmpBB1.limit());
                    }
                    Valid = true;
                    //System.out.println("[aq2j] DEBUG: Shot '" + shotPathMain + shotSubdir + File.separator + shotName + "' appears valid.");
                } catch (Exception e) {
                    Tum3Logger.DoLog(DbName(), true, "WARNING: " + Tum3Util.getStackTrace(e));
                    //FErrorDescription = e.toString();
                    FF.close();
                    FF = null;
                }
            }
            if (Valid && !NotStored) BuildCache();
        }
    }

    private void DoOpenMainFile() throws Exception {

        //System.out.println("[aq2j] DEBUG: '" + shotPathMain + shotSubdir + File.separator + shotName + "' exists.");
        FF = new UtilCreateFile1(DbName(), shotPathMain + shotSubdir + File.separator + shotName + File.separator + "0000" + FSUFF_NORMAL, false);
        NotStored = FF.NotStored;

    }

    public boolean NotStored() {

        return NotStored;

    }

    private void CreateNew() throws Exception {

        if (!isWriteable) throw new Exception(Tum3Db.CONST_MSG_READONLY_NOW); // YYY
        File tmp_shotdir = new File(shotPathMain + shotSubdir + File.separator + shotName);
        tmp_shotdir.mkdir();
        if (!tmp_shotdir.isDirectory()) return;

        //DoOpenMainFile(true);
        //if (FF == null) return;
        //if (FF.raf == null) return;
        String tmp_cmn_fname = shotPathMain + shotSubdir + File.separator + shotName + File.separator + "0000" + FSUFF_TMPFILE; // YYY
        String tmp_dest_name = shotPathMain + shotSubdir + File.separator + shotName + File.separator + "0000" + FSUFF_NORMAL; // YYY
        RandomAccessFile raf = new RandomAccessFile(tmp_cmn_fname, "rw"); // YYY

        try {

            byte[] buff0 = new byte[NewHeader.StaticSize() + new_puff_program.length()];
            ByteBuffer tmpBB0 = ByteBuffer.wrap(buff0);
            tmpBB0.order(ByteOrder.LITTLE_ENDIAN);

            NewHeader.HSize = NewHeader.StaticSize();
            NewHeader.DirCount = 0;
            NewHeader.DirLimit = 0;
            NewHeader.ProgramSubversion = a.CurrentVerNum;
            NewHeader.hdrOriginalShotName = shotName;
            NewHeader.dwUpdateCounter = 0;
            NewHeader.hdrAlignFillerByte = 1; // Platform type. 0 = legacy, 1 = java.

            Tum3Time t = new Tum3Time();
            NewHeader.wYear = t.year;
            NewHeader.wMonth = t.month;
            NewHeader.wDay = t.day;
            NewHeader.wHour = t.hour;
            NewHeader.wMinute = t.minute;
            NewHeader.wSecond = t.second;

            NewHeader.hdrPuffProgramOfs = 4 + NewHeader.HSize;
            NewHeader.hdrPuffProgramLen = new_puff_program.length();
            if (0 == NewHeader.hdrPuffProgramLen) NewHeader.hdrPuffProgramOfs = 0;

            NewHeader.buf = tmpBB0;
            NewHeader.writeAll();
            if (new_puff_program.length() > 0)
                for (int tmp_i=0; tmp_i < new_puff_program.length(); tmp_i++)
                    tmpBB0.put((byte)new_puff_program.charAt(tmp_i));

            byte[] buff00 = new byte[4];
            ByteBuffer tmpBB00 = ByteBuffer.wrap(buff00);
            tmpBB00.order(ByteOrder.LITTLE_ENDIAN);
            tmpBB00.putInt(const_tum3ng_sign_h);
            raf.write(buff00, 0, 4);

            raf.write(buff0, 0, buff0.length); // YYY

            if (NewHeader.hdrPuffProgramLen > 0) {
                NewHeader.hdrPuffProgramOfs -= 4; // Inplace adaptation for direct network sending.
                tmpBB0.position(0);
                NewHeader.writeAll();
            }

            try {
                raf.close(); // YYY
                raf = null;
            } catch (Exception e) {
                Tum3Logger.DoLog(DbName(), true, "WARNING: close() error in '" + tmp_cmn_fname + "': " + Tum3Util.getStackTrace(e));
            }

            File tmp_f = new File(tmp_cmn_fname);
            if (tmp_f.isFile()) {
                if (!tmp_f.renameTo(new File(tmp_dest_name)))
                    throw new Exception("Failed to rename config zip from '" + tmp_cmn_fname + "' to '" + tmp_dest_name + "'");
            } else {
                throw new Exception("Storing failed: config zip in '" + tmp_cmn_fname + "' failed to appear");
            }

            NotStored = false;
            Valid = true;

        } catch (Exception e) {

            if (raf != null) {
                try {
                    raf.close(); // YYY
                    raf = null;
                } catch (Exception e2) {
                    Tum3Logger.DoLog(DbName(), true, "WARNING: close() error in '" + tmp_cmn_fname + "': " + Tum3Util.getStackTrace(e2));
                }
            }
            throw e;
        }

        if (new_zip_configs != null) {

            FileOutputStream fos = null;
            String tmp_fname =      shotPathMain + shotSubdir + File.separator + shotName + File.separator + "0000" + FSUFF_TMPFILE;
            String tmp_dest_name2 = shotPathMain + shotSubdir + File.separator + shotName + File.separator + "0000" + FSUFF_CFGZIP;
            try {
                fos = new FileOutputStream(tmp_fname);
                new_zip_configs.writeTo(fos);
                new_zip_configs = null;
            } catch (Exception e) {
                Tum3Logger.DoLog(DbName(), true, "IMPORTANT: config zip write error in '" + tmp_fname + "' with: " + e);
            }
            if (fos != null) fos.close();
            File tmp_f = new File(tmp_fname);
            if (tmp_f.isFile()) {
                if (!tmp_f.renameTo(new File(tmp_dest_name2)))
                    Tum3Logger.DoLog(DbName(), true, "IMPORTANT: failed to rename config zip from '" + tmp_fname + "' to '" + tmp_dest_name2 + "'");
            } else {
                Tum3Logger.DoLog(DbName(), true, "IMPORTANT: config zip in '" + tmp_fname + "' failed to appear");
            }
        }

    }

    private void BuildCache_Internal(String thePath, byte theKind) {

        File dir = new File(thePath + shotSubdir + File.separator + shotName);
        if (dir == null) return;
        File[] tmpFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if ((8 <= name.length()) && name.endsWith(FSUFF_NORMAL))
                    if (Tum3Util.StrNumeric(name.substring(0, name.length()-4))) 
                        return true;
                return false;
            }
        });
        if (tmpFiles == null) return;
        for (File file: tmpFiles) {
            String tmp_name = file.getName();
            tmp_name = tmp_name.substring(0, tmp_name.length()-4);
            int tmp_id = Integer.parseInt(tmp_name);
            //System.out.print("[aq2j] DEBUG: considering ID= '" + tmp_id + "'");
            if (1 == theKind) CacheIds.put(tmp_id, theKind);
            else {
                if (CacheIds.containsKey(tmp_id)) {
                    CacheIds.put(tmp_id, (byte)(CacheIds.get(tmp_id) | theKind));
                } else CacheIds.put(tmp_id, theKind);
            }
        }

    }

    private void BuildCache() {

        synchronized(CacheIds) {
            if (shotPathMain.length() > 0) BuildCache_Internal(shotPathMain, (byte)1);
            if (shotPathVol.length() > 0)  BuildCache_Internal(shotPathVol, (byte)2);
        }

    }

    public synchronized void ShotAddUser() {

        UserCount++;
        //System.out.println("[aq2j] DEBUG: ShotAddUser(): new UserCount=" + UserCount + " in '" + DbName() + "." + shotName + "'");

    }

    public synchronized boolean notUsed(long max_millis, boolean force) {
        //System.out.println("[aq2j] DEBUG: notUsed(): UserCount=" + UserCount + " in '" + DbName() + "." + shotName + "'");
        if (0 != UserCount) return false;
        else return ((LastUsedAt < max_millis) || force);
    }

    public synchronized void ShotRelease() {

        UserCount--;
        //System.out.println("[aq2j] DEBUG: ShotRelease(): new UserCount=" + UserCount + " in '" + DbName() + "." + shotName + "'");
        if (UserCount == 0) LastUsedAt = System.currentTimeMillis();
        if (UserCount < 0)
            Tum3Logger.DoLog(DbName(), true, "Warning: in ShotRelease() UserCount=" + UserCount + " in '" + shotName + "'");

    }

    private String SignalFName(int thisSignalId) {
        StringBuffer tmp_st = new StringBuffer();
        tmp_st.append("" + thisSignalId);
        while (tmp_st.length() < 4) tmp_st.insert(0, '0');
        return tmp_st.toString();
    }

    private boolean RemoveFromExpected(int _id) {

        if (null == expected_ids) return false;
        for (int tmp_i = 0; tmp_i < expected_ids.length; tmp_i++)
            if (expected_ids[tmp_i] == _id) {
                expected_ids[tmp_i] = 0;
                return true;
            }
        return false;

    }

    private OutBuffContinuator GetByPositionNew(int thisSignalId, boolean _use_trailing_status, boolean _as_volatile, boolean _with_warning) {

        //System.out.println("[aq2j] DEBUG: GetByPositionNew(): '" + shotName + "' id=" + thisSignalId);

        String tmpCommonName = shotName + File.separator + SignalFName(thisSignalId) + FSUFF_NORMAL;
        String tmpActualPath = shotPathMain;

        if (_as_volatile) {
            //System.out.println("[aq2j] DEBUG: GetByPositionNew(): exists in volatile: '" + shotName + "' id=" + thisSignalId);
            tmpActualPath = shotPathVol;
        }

        UtilCreateFile1 tmpFF = null;
        try {
            tmpFF = new UtilCreateFile1(DbName(), tmpActualPath + shotSubdir + File.separator + tmpCommonName, false);
        } catch (Exception e) {
            return null;
        }
        if (tmpFF.NotStored) return null;

        try {
            tmpFF.readHeaders(shotName, thisSignalId);

            tmpFF.raf.seek(4 + tmpFF.tmpNewHeaderSizeInFile);
            ShotAddUser(); // Reminder. There is no race here because dbLink.getShot has yet another AddUser().
            //System.out.println("[aq2j] DEBUG: <" + Thread.currentThread().getId() + "> new TraceReaderContinuator for '" + tmpFF.FileName() + "'");
            return new TraceReaderContinuator(this, tmpFF.FileName(), tmpFF.raf, tmpFF.tmpFiledHSize + tmpFF.tmpBuffSize, tmpFF.WasEdited, _use_trailing_status, _with_warning);

        } catch (Exception e) {

            Tum3Logger.DoLog(DbName(), true, "WARNING: GetByPositionNew() exception: " + Tum3Util.getStackTrace(e));
            return null;
        }
    }

    private OutBuffContinuator PackAvailableSignalsList() {

        /*
  new(PEntryHeader);
  FillChar(PEntryHeader^, SizeOf(TUnifiedTraceHeader), 0);
  PEntryHeader.HSize := SizeOf(TUnifiedTraceHeader);
  PEntryHeader.HType := DTYPE_System01;
  PEntryHeader.HCount := SignCacheCount+1;
  PEntryHeader.HDataSize := 4*PEntryHeader.HCount;
  tmpBuffSize := PEntryHeader.HDataSize;
  GetMem(tmpDataBuff, tmpBuffSize);

  plongint(tmpDataBuff)^ := SignCacheCount;
  for i := 1 to SignCacheCount do
    plongint(longint(tmpDataBuff)+i*4)^ := SignCacheId(i);
         */
        int tmp_entry_count = 0, tmp_filled_count = 0, tmp_real_filled_count = 0;
        int tmp_buff_size;
        byte[] tmp_buff;

        ByteBuffer tmpBB0 = null;
        synchronized(CacheIds) {
            tmp_entry_count = CacheIds.size()+1;
            if (tmp_entry_count < CONST_MIN_SIGN_PACK_LEN) tmp_entry_count = CONST_MIN_SIGN_PACK_LEN;
            tmp_entry_count = tmp_entry_count*2;
            tmp_buff_size = HeaderWriterTraceSignPack.StaticSize() + (tmp_entry_count)*4;
            tmp_buff = new byte[tmp_buff_size];
            tmpBB0 = ByteBuffer.wrap(tmp_buff);
            tmpBB0.order(ByteOrder.LITTLE_ENDIAN);
            tmpBB0.position(HeaderWriterTraceSignPack.StaticSize());
            tmp_filled_count = CacheIds.size()+1;
            if (tmp_filled_count > tmp_entry_count) tmp_filled_count = tmp_entry_count;
            tmpBB0.putInt(0);
            tmp_real_filled_count = 1;
            for (Map.Entry<Integer, Byte> entry : CacheIds.entrySet())
                if (((entry.getValue() & 0x0C) == 0) && (tmp_real_filled_count < tmp_filled_count)) {
                    tmpBB0.putInt(entry.getKey());
                    tmp_real_filled_count++;
                }
            tmpBB0.position(HeaderWriterTraceSignPack.StaticSize());
            tmpBB0.putInt(tmp_real_filled_count - 1);
        }
        tmpBB0.clear();
        tmpBB0.order(ByteOrder.LITTLE_ENDIAN);
        HeaderWriterTraceSignPack SignPack = new HeaderWriterTraceSignPack(tmpBB0);
        SignPack.HType = HeaderWriterTraceSignPack.DTYPE_System01;
        SignPack.HCount = tmp_real_filled_count;
        SignPack.HDataSize = 4*tmp_real_filled_count;
        SignPack.writeAll();
        return new ImmediateArrayContinuator(tmp_buff, HeaderWriterTraceSignPack.StaticSize()+tmp_real_filled_count*4, false);
    }

    private OutBuffContinuator PackShotHeader() {
        /*
  new(PEntryHeader);
  FillChar(PEntryHeader^, SizeOf(TUnifiedTraceHeader), 0);
  PEntryHeader.HSize := SizeOf(TUnifiedTraceHeader);
  PEntryHeader.HType := DTYPE_System02;
  PEntryHeader.HCount := 1;
  if FpNewHeader = nil then
    PEntryHeader.HDataSize := 0
  else
    PEntryHeader.HDataSize := FpNewHeader.HSize;

  tmpBuffSize := PEntryHeader.HDataSize;
  tmpDataBuff := nil;
  if tmpBuffSize > 0 then
  begin
    Z_GetMem(tmpDataBuff, tmpBuffSize);
    move(FpNewHeader^, tmpDataBuff^, tmpBuffSize);
  end;
         */
        int tmp_buff_size;
        byte[] tmp_buff;

        if (null == NewHeader.buf)
            tmp_buff_size = 0;
        else
            tmp_buff_size = NewHeader.buf.limit();
        //System.out.println("[aq2j] DEBUG: PackShotHeader() in " + shotName + " tmp_buff_size=" + tmp_buff_size + " ProgramSubversion=" + NewHeader.ProgramSubversion);

        tmp_buff = new byte[HeaderWriterTraceSignPack.StaticSize() + tmp_buff_size];
        ByteBuffer tmpBB0 = ByteBuffer.wrap(tmp_buff);
        tmpBB0.order(ByteOrder.LITTLE_ENDIAN);
        if (tmp_buff_size > 0) {
            tmpBB0.position(HeaderWriterTraceSignPack.StaticSize());
            tmpBB0.put(NewHeader.buf.array(), 0, tmp_buff_size);
        }
        tmpBB0.clear();
        tmpBB0.order(ByteOrder.LITTLE_ENDIAN);
        HeaderWriterTraceSignPack SignPack = new HeaderWriterTraceSignPack(tmpBB0);
        SignPack.HType = HeaderWriterTraceSignPack.DTYPE_System02;
        SignPack.HCount = 1;
        SignPack.HDataSize = tmp_buff_size;
        SignPack.writeAll();
        return new ImmediateArrayContinuator(tmp_buff, HeaderWriterTraceSignPack.StaticSize()+tmp_buff_size, false);
    }

    public static void insertHostName(ByteBuffer _header, String _host_name) throws Exception {

        if (_header.remaining() < (9*4+256)) throw new Exception("UnifiedTraceHeader butebuffer is suspicuously small");

        _header.position(9*4);
        int HComment1_len = 0xFF & _header.get();
        StringBuilder HComment1_buf = new StringBuilder();
        for (int tmp_i = 0; tmp_i < HComment1_len; tmp_i++) HComment1_buf.append((char)_header.get());
        String HComment1_upd = _host_name + ": " + HComment1_buf.toString();
        if (HComment1_upd.length() > 254) HComment1_upd = HComment1_upd.substring(0, 254);
        HComment1_len = HComment1_upd.length();
        //System.out.println("[DEBUG] insertHostName(): (" + HComment1_buf.toString() + ") -->> (" + HComment1_upd + ")");
        _header.position(9*4);
        _header.put((byte)HComment1_len);
        for (int tmp_i = 0; tmp_i < HComment1_len; tmp_i++) _header.put((byte)HComment1_upd.charAt(tmp_i));
        _header.position(0);

    }

    private void SyncStatusVolOpBegin(int _ID) throws Exception {
    // Reminder! If it cannot do the thing then it MUST throw!
    // Reminder2! Concurrent calls for the same (shot+id) are strictly 
    //   prevented with external locking, however need to consider that some 
    //   previous operation could be unexpectedly interrupted at any stage.

        if (!parent_db.withSyncState) return;

        File tmp_monthdir    = new File(parent_db.SYNC_STATE_PATH + shotSubdir);
        File tmp_shotdir     = new File(parent_db.SYNC_STATE_PATH + shotSubdir + File.separator + shotName);
        String tmp_new_fname = parent_db.SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ID) + FSUFF_FLAGPREP;
        File tmp_new_file = new File(tmp_new_fname);

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                tmp_monthdir.mkdir();
                tmp_shotdir.mkdir();
                tmp_new_file.createNewFile();
            } catch (Exception ignored) {}
            if (tmp_new_file.exists()) break;
        }
        if (!tmp_new_file.isFile()) throw new Exception("Failed to create sync flag file <" + tmp_new_fname + ">");
    }

    private void SyncStatusVolOpEnd(int _ID, boolean _BeginOk) {
    // Reminder! If it cannot do the thing then it MUST log, but never throw.
    // Reminder2! Concurrent calls for the same (shot+id) are strictly 
    //   prevented with external locking, however need to consider that some 
    //   previous operation could be unexpectedly interrupted at any stage.

        if (!parent_db.withSyncState) return;

        String tmp_new_fname1 = parent_db.SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ID) + FSUFF_FLAGPREP;
        String tmp_new_fname2 = parent_db.SYNC_STATE_PATH + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ID) + FSUFF_FLAGDONE;
        File tmp_new_file1 = new File(tmp_new_fname1);
        File tmp_new_file2 = new File(tmp_new_fname2);

        boolean tmp_ok = false;
        try {
            tmp_new_file2.delete();
            tmp_ok = tmp_new_file1.renameTo(tmp_new_file2) || tmp_new_file2.isFile();
        } catch (Exception ignored) {}
        if (!tmp_ok && _BeginOk) {
            Tum3Logger.DoLog(DbName(), true, "Sync error: <" + tmp_new_fname2 + "> could not be renamed from <" + tmp_new_fname1 + "> and apparently does not exist");
        }
    }

    public void putTrace(int _ThisID, ByteBuffer _header, ByteBuffer _body, ShotChangeMonitor chgMonitor, boolean DataIsVolatile) throws Exception {

        if (_ThisID <= 0) throw new Exception("illegal signal id specified"); // YYY
        if (!Valid) throw new Exception("data directory seems invalid");
        if (!isWriteable) throw new Exception(Tum3Db.CONST_MSG_READONLY_NOW); // YYY

        String tmpActualPath = shotPathMain;
        boolean tmp_as_volatile = DataIsVolatile && (shotPathVol.length() > 0);
        byte tmp_ok_bit = 1, tmp_in_progress = 4;
        if (tmp_as_volatile) { tmp_ok_bit = 2; tmp_in_progress = 8; }
        boolean tmp_warning_msg = false;
        boolean tmp_with_rename = true; // YYY
        boolean tmp_already_stored = false; // YYY
        boolean tmp_store_ok = false;
        String tmp_target_fname = "";

        synchronized(CacheIds) {
            byte tmp_cache_val = 0;
            if (CacheIds.containsKey(_ThisID)) tmp_cache_val = CacheIds.get(_ThisID);
            if ((tmp_cache_val & tmp_in_progress) != 0) throw new Exception("Data update can not be performed until another update completes, please try later.");
            if ((tmp_cache_val & tmp_ok_bit) != 0) { // Changed to use tmp_cache_val instead of real file probe.
                if (tmp_as_volatile) { // Allow override for volatile signals.

                    // XXX TODO! Try to kickoff possible readers now or somehow prevent rename/delete blocking on windows?
                    // Reminder: on unix, renaming and deletion of files works 
                    //  fine regardless of any other readers/writers. On windows most
                    //  usually renaming/deletion will not be possible if concurrenly
                    //  in use (at least through RandomAccessFile).
                    // See https://stackoverflow.com/questions/39293193/random-access-file-filelock-java-io-vs-java-nio
                    // On the other hand, renaming and subsequent deletion is necessary
                    //  to be atomic, compared to overwriting/truncating the same file.
                    // This might need to more thinking and optimization to work better on windows.

                    //tmp_with_rename = true; // YYY Now made always true.
                    tmp_already_stored = true; // YYY
                } else
                    throw new Exception("The signal is already on file");
            }
            tmp_warning_msg = (tmp_as_volatile && ((tmp_cache_val & 5) != 0)) || (!tmp_as_volatile && ((tmp_cache_val & 10) != 0));
            tmp_cache_val |= tmp_in_progress;
            CacheIds.put(_ThisID, tmp_cache_val);
        }

        if (tmp_warning_msg)
            Tum3Logger.DoLog(DbName(), true, "WARNING: Both raw and variable data written for '" + shotName + "', id=" + _ThisID + ", likely erroneously.");

        File tmp_monthdir = null; // YYY
        File tmp_shotdir = null; // YYY
        if (tmp_as_volatile) {
            tmpActualPath = shotPathVol;
            //System.out.println("[aq2j] DEBUG: putTrace(): '" + shotName + "' id=" + thisSignalId);
            tmp_monthdir = new File(shotPathVol + shotSubdir);
            tmp_shotdir = new File(shotPathVol + shotSubdir + File.separator + shotName);
            if (!tmp_monthdir.exists()) try { tmp_monthdir.mkdir(); } catch (Exception ignored) {} else tmp_monthdir = null; // YYY
            if (!tmp_shotdir.exists()) try { tmp_shotdir.mkdir(); } catch (Exception ignored) {} else tmp_shotdir = null; // YYY
        }
        String tmp_new_fname = tmpActualPath + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_NORMAL;
        if (tmp_with_rename) {
            tmp_target_fname = tmp_new_fname;
            tmp_new_fname = tmpActualPath + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_TMPFILE;
        }

        //UtilCreateFile1 tmp_FF = null;
        RandomAccessFile tmp_raf = null; // YYY
        FileChannel tmp_fc = null;
        boolean tmp_data_likely_lost = false;
        boolean SyncStatusVolOpBegin_ok = false; // YYY

        try {
            if (tmp_as_volatile) {
                SyncStatusVolOpBegin(_ThisID); // YYY
                SyncStatusVolOpBegin_ok = true; // YYY
            }

            if (tmp_with_rename) {
                String tmp_backup_fname = shotPathVol + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_BUP_GENERAL;
                File tmp_backup_file = new File(tmp_backup_fname);
                if (tmp_backup_file.exists()) tmp_backup_file.delete();
                if (tmp_backup_file.exists()) {
                    String tmp_err_msg = "Backup file <" + tmp_backup_fname + "> could not be deleted";
                    Tum3Logger.DoLog(DbName(), true, tmp_err_msg);
                    throw new Exception(tmp_err_msg);
                }
            }

            //tmp_FF = new UtilCreateFile1(DbName(), tmp_new_fname, true);
            //if (tmp_FF.raf == null) return;
            tmp_raf = new RandomAccessFile(tmp_new_fname, "rw"); // YYY

            int tmp_datasize_hdr = 0;
            boolean tmp_size_done = false; // YYY
            if (_header.remaining() >= min_hlen_Fmt64Ver) { // YYY
                _header.position(12*4+256); // old HMetaDataSize
                if (0 == _header.getInt()) {
                    _header.position(ofs_HDataSize_new);
                    tmp_datasize_hdr = _header.getInt(); // YYY
                    tmp_size_done = true; // YYY
                }
                _header.position(0);
            }
            if (!tmp_size_done) if (_header.remaining() >= (ofs_HDataSize_old+4)) { // YYY
                _header.position(ofs_HDataSize_old);
                tmp_datasize_hdr = _header.getInt();
                _header.position(0);
            }
            int tmp_datasize_actual = 0;
            if (_body != null) tmp_datasize_actual = _body.remaining();
            //System.out.println("[DEBUG] tmp_datasize_hdr=" + tmp_datasize_hdr + ", tmp_datasize_actual=" + tmp_datasize_actual);
            if (tmp_datasize_actual != tmp_datasize_hdr) throw new Exception("Signal body size mismatch (HDataSize=" + tmp_datasize_hdr + ", actual=" + tmp_datasize_actual + ")");

            SignalHeaderClass tmpHeader = new SignalHeaderClass();
            byte[] buff0 = new byte[4 + tmpHeader.StaticSize()];
            ByteBuffer tmpBB0 = ByteBuffer.wrap(buff0);
            tmpBB0.order(ByteOrder.LITTLE_ENDIAN);

            tmpBB0.putInt(const_tum3ng_sign_s);

            tmpHeader.HSize = tmpHeader.StaticSize();
            tmpHeader.HID = _ThisID;
            tmpHeader.HStatus = 1; // == not ready
            tmpHeader.SigUpdateCounter = 0;
            tmpHeader.ProgramSubversion = a.CurrentVerNum;
            tmpHeader.hdrOriginalShotName = shotName;

            Tum3Time t = new Tum3Time();
            tmpHeader.wYear = t.year;
            tmpHeader.wMonth = t.month;
            tmpHeader.wDay = t.day;
            tmpHeader.wHour = t.hour;
            tmpHeader.wMinute = t.minute;
            tmpHeader.wSecond = t.second;

            tmpHeader.buf = tmpBB0;
            tmpHeader.writeAll();
            tmp_raf.write(buff0, 0, buff0.length);

            tmp_fc = tmp_raf.getChannel();

            while (_header.hasRemaining()) tmp_fc.write(_header);
            if (tmp_datasize_actual > 0) 
                while (_body.hasRemaining()) tmp_fc.write(_body);

            tmpHeader.HStatus = 0; // == ready
            tmpHeader.SigUpdateCounter++;
            tmpBB0.clear();
            tmpBB0.putInt(const_tum3ng_sign_s);
            tmpHeader.writeAll();
            tmp_raf.seek(0);
            tmp_raf.write(buff0, 0, buff0.length);

            tmp_fc.close(); tmp_fc = null;
            //tmp_FF.close(); tmp_FF = null;
            try {
                tmp_raf.close(); // YYY
                tmp_raf = null;
            } catch (Exception e) {
                Tum3Logger.DoLog(DbName(), true, "WARNING: close() error in '" + tmp_new_fname + "': " + Tum3Util.getStackTrace(e));
            }

            if (tmp_with_rename) {
                File tmp_orig_file = new File(tmp_target_fname);
                String tmp_bup_fname = shotPathVol + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_BUP_GENERAL;
                File tmp_bup_file = new File(tmp_bup_fname);
                boolean tmp_prev_file_exists = tmp_orig_file.exists(); // YYY
                if (tmp_prev_file_exists) if (!tmp_orig_file.renameTo(tmp_bup_file)) throw new Exception("Previous file <" + tmp_target_fname + "> could not be renamed into <" + tmp_bup_fname + ">"); // YYY
                File tmp_temp_file = new File(tmp_new_fname);
                File tmp_final_file = new File(tmp_target_fname);
                if (!tmp_temp_file.renameTo(tmp_final_file)) {
                    String tmp_err_msg = "Temporary new file <" + tmp_new_fname + "> could not be renamed into <" + tmp_target_fname + ">";
                    Tum3Logger.DoLog(DbName(), true, tmp_err_msg);
                    if (tmp_prev_file_exists) { // YYY
                        File tmp_orig_file2 = new File(tmp_target_fname);
                        if (!tmp_bup_file.renameTo(tmp_orig_file2)) 
                            if (!tmp_orig_file2.exists()) tmp_data_likely_lost = true;
                    }
                    throw new Exception(tmp_err_msg);
                }
            }

            tmp_store_ok = true;

        } finally {

            if (tmp_fc != null) tmp_fc.close();
            tmp_fc = null;
            //if (tmp_FF != null) tmp_FF.close();
            //tmp_FF = null;
            try {
                if (null != tmp_raf) tmp_raf.close(); // YYY
                tmp_raf = null;
            } catch (Exception e) {
                Tum3Logger.DoLog(DbName(), true, "WARNING: close() error in '" + tmp_new_fname + "': " + Tum3Util.getStackTrace(e));
            }

            if (tmp_as_volatile) SyncStatusVolOpEnd(_ThisID, SyncStatusVolOpBegin_ok); // YYY

            boolean tmp_was_waiting = false;
            synchronized(CacheIds) {
                byte tmp_cache_val = 0;
                if (CacheIds.containsKey(_ThisID)) tmp_cache_val = CacheIds.get(_ThisID);
                if (tmp_store_ok) tmp_cache_val |= tmp_ok_bit;
                tmp_cache_val &= ~tmp_in_progress;
                if (tmp_data_likely_lost) tmp_cache_val &= ~tmp_ok_bit;
                if (0 == tmp_cache_val) CacheIds.remove(_ThisID);
                else CacheIds.put(_ThisID, tmp_cache_val);
                tmp_was_waiting = RemoveFromExpected(_ThisID);
            }
            chgMonitor.AddUpdatedId(_ThisID, false, tmp_was_waiting, false);

            if (!tmp_store_ok) {
                if (null != tmp_shotdir) if (tmp_shotdir.isDirectory()) tmp_shotdir.delete(); // YYY
                if (null != tmp_monthdir) if (tmp_monthdir.isDirectory()) tmp_monthdir.delete(); // YYY
            }
        }

    }

    public void deleteTrace(int _ThisID, ShotChangeMonitor chgMonitor) throws Exception {
    // Note. This function is only intended for volatile storage path.

        if (_ThisID <= 0) throw new Exception("illegal signal id specified"); // YYY
        if (!isWriteable) throw new Exception(Tum3Db.CONST_MSG_READONLY_NOW); // YYY

        byte tmp_ok_bit = 2, tmp_in_progress = 8;
        boolean tmp_delete_ok = false;
        boolean SyncStatusVolOpBegin_ok = false; // YYY

        synchronized(CacheIds) {
            byte tmp_cache_val = 0;
            if (CacheIds.containsKey(_ThisID)) tmp_cache_val = CacheIds.get(_ThisID);
            if ((tmp_cache_val & tmp_in_progress) != 0) throw new Exception("Data update can not be performed until another update completes, please try later.");
            if ((tmp_cache_val & tmp_ok_bit) == 0) return;
            tmp_cache_val |= tmp_in_progress;
            CacheIds.put(_ThisID, tmp_cache_val);
        }

        try {
            SyncStatusVolOpBegin(_ThisID); // YYY
            SyncStatusVolOpBegin_ok = true; // YYY

            String tmp_target_fname = shotPathVol + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_NORMAL;
            String tmp_bup_fname    = shotPathVol + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_BUP_GENERAL;
            File tmp_backup_file = new File(tmp_bup_fname);
            if (tmp_backup_file.exists()) tmp_backup_file.delete();
            if (tmp_backup_file.exists()) {
                String tmp_err_msg = "Backup file <" + tmp_bup_fname + "> could not be deleted";
                Tum3Logger.DoLog(DbName(), true, tmp_err_msg);
                throw new Exception(tmp_err_msg);
            }

            File tmp_orig_file = new File(tmp_target_fname);
            File tmp_bup_file = new File(tmp_bup_fname);
            if (!tmp_orig_file.renameTo(tmp_bup_file)) throw new Exception("Previous file <" + tmp_target_fname + "> could not be renamed into <" + tmp_bup_fname + ">");

            tmp_delete_ok = true;

        } finally {

            SyncStatusVolOpEnd(_ThisID, SyncStatusVolOpBegin_ok); // YYY

            synchronized(CacheIds) {
                byte tmp_cache_val = 0;
                if (CacheIds.containsKey(_ThisID)) tmp_cache_val = CacheIds.get(_ThisID);
                if (tmp_delete_ok) tmp_cache_val &= ~tmp_ok_bit;
                tmp_cache_val &= ~tmp_in_progress;
                if (0 == tmp_cache_val) CacheIds.remove(_ThisID);
                else CacheIds.put(_ThisID, tmp_cache_val);
            }
            chgMonitor.AddUpdatedId(_ThisID, false, false, true);
        }

    }

    public String UpdateDensityData(int _ThisID, byte[] _upd_arr, ShotChangeMonitor chgMonitor) {

        if (_ThisID == 0) return "illegal signal id specified";
        if (!Valid) return "data directory seems invalid";
        if (!isWriteable) return Tum3Db.CONST_MSG_READONLY_NOW; // YYY

        if (_upd_arr.length > CONST_DENSITY_FSIZE_LIMIT) return "data size seems way too big"; // YYY

        boolean tmp_as_volatile = true;
        byte tmp_in_progress = 8;
        synchronized(CacheIds) {
            //if (2 == 2) return "Artifical error in density update.";
            if (!CacheIds.containsKey(_ThisID))
                return "Cache record not found for the required density update.";
            byte tmp_cache_val = CacheIds.get(_ThisID);
            if ((tmp_cache_val & 3) == 0)
                return "Cache record is empty for the required density update.";
            if ((tmp_cache_val & 1) != 0) tmp_as_volatile = false;
            if ((tmp_cache_val & 2) != 0) tmp_as_volatile = true;

            if (!tmp_as_volatile && (shotPathVol.length() > 0)) return "Updating of non-volatile data is not possible."; // YYY

            if (tmp_as_volatile) tmp_in_progress = 8;
            else  tmp_in_progress = 4;
            if ((tmp_cache_val & tmp_in_progress) != 0)
                return "Storage file for the required density update is in use, please try later.";
            tmp_cache_val |= tmp_in_progress;
            CacheIds.put(_ThisID, tmp_cache_val);
        }
        String tmp_result = "Unknown error";
        UtilCreateFile1 tmpFF = null;
        RandomAccessFile tmp_f_src = null, tmp_f_dst = null;
        boolean tmp_writing_started = false;
        boolean SyncStatusVolOpBegin_ok = false; // YYY

        String tmpActualPath = shotPathMain;

        if (tmp_as_volatile) {
            tmpActualPath = shotPathVol;
            //System.out.println("[aq2j] DEBUG: UpdateDensityData(): using volatile for '" + shotName + "' id=" + _ThisID);
        }

        boolean tmp_need_sync_end = false; // YYY
        try {
            String tmp_bup_fname = tmpActualPath + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_BUP_DENSITY;
            String tmp_std_fname = tmpActualPath + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_NORMAL;
            String tmp_tmp_fname = tmpActualPath + shotSubdir + File.separator + shotName + File.separator + SignalFName(_ThisID) + FSUFF_TMPFILE; // YYY
            boolean tmp_bup_ok = false;

            // Check for editlocked tag.
            UtilCreateFile1 tmpCheckLocked = null;
            TraceMetaData tmpMeta = null;
            try {
                tmpCheckLocked = new UtilCreateFile1(DbName(), tmp_std_fname, false);
                tmpCheckLocked.readHeaders(shotName, _ThisID);
                if ((!tmpCheckLocked.hdr_is_v1 && (tmpCheckLocked.tmpFiledHSize > (ofs_HMetaDataSize_old+4)))
                 ||  (tmpCheckLocked.hdr_is_v1 && (tmpCheckLocked.tmpFiledHSize > (ofs_HMetaDataSize_new+8)))
                ) {
                    int tmp_metasz_ofs = ofs_HMetaDataSize_old; // old .HMetaDataSize
                    if (tmpCheckLocked.hdr_is_v1) tmp_metasz_ofs = ofs_HMetaDataSize_new; // new .HMetaDataSize
                    tmpCheckLocked.raf.seek(4 + tmpCheckLocked.tmpNewHeaderSizeInFile + tmp_metasz_ofs); // YYY
                    int tmpHMetaDataSize = tmpCheckLocked.raf.readInt();
                    if (tmpCheckLocked.hdr_is_v1) tmpCheckLocked.raf.readInt(); // YYY
                    int tmpMetaLimit;
                    if (!tmpCheckLocked.hdr_is_v1)
                        tmpMetaLimit = tmpCheckLocked.tmpFiledHSize - ofs_HMetaDataSize_old - 4;
                    else
                        tmpMetaLimit = tmpCheckLocked.tmpFiledHSize - ofs_HMetaDataSize_new - 8; // YYY
                    if (tmpHMetaDataSize > tmpMetaLimit) tmpHMetaDataSize = tmpMetaLimit;
                    byte[] tmpMetaAsBytes = new byte[tmpHMetaDataSize];
                    tmpCheckLocked.raf.readFully(tmpMetaAsBytes); // YYY
                    tmpMeta = new TraceMetaData(tmpMetaAsBytes);
                }
            } finally {
                if (tmpCheckLocked != null) {
                    tmpCheckLocked.close();
                }
            }

            if (tmpMeta != null) if (tmpMeta.containsKey(TumProtoConsts.tag_editlocked)) {
                String tmp_editlocked = tmpMeta.get(TumProtoConsts.tag_editlocked);
                //System.out.println("[DEBUG] editlocked=" + tmp_editlocked);
                if (!tmp_editlocked.isEmpty()) throw new Exception("Editing was locked (" + tmp_editlocked + ")");
            }

            if (tmp_as_volatile) {
                tmp_need_sync_end = true; // YYY
                SyncStatusVolOpBegin(_ThisID); // YYY
                SyncStatusVolOpBegin_ok = true; // YYY
            }

            if (!new File(tmp_bup_fname).exists()) {

                tmp_f_src = new RandomAccessFile(tmp_std_fname, "r");
                long tmp_fsize_l = tmp_f_src.length();
                if (tmp_fsize_l > CONST_DENSITY_FSIZE_LIMIT) throw new Exception("Original file size is too big for backup.");
                int tmp_fsize = (int)tmp_fsize_l;
                byte[] tmp_copy_buff = new byte[tmp_fsize];
                tmp_f_src.readFully(tmp_copy_buff);
                tmp_f_src.close();
                tmp_f_src = null;
                tmp_f_dst = new RandomAccessFile(tmp_tmp_fname /* tmp_bup_fname */, "rw"); // YYY
                tmp_f_dst.write(tmp_copy_buff);
                tmp_f_dst.setLength(tmp_fsize);
                tmp_f_dst.close();
                tmp_f_dst = null;

                if (!new File(tmp_tmp_fname).renameTo(new File(tmp_bup_fname))) { // YYY
                    throw new Exception("Temporary new file <" + tmp_tmp_fname + "> could not be renamed into <" + tmp_bup_fname + ">"); // YYY
                    //Tum3Logger.DoLog(DbName(), true, tmp_err_msg);
                }
            }

            if (new File(tmp_bup_fname).exists()) {

                tmpFF = new UtilCreateFile1(DbName(), tmp_bup_fname, false);
                tmpFF.readHeaders(shotName, _ThisID);
                if ((tmpFF.tmpFiledHSize > 0) && (tmpFF.tmpBuffSize > 0) && (tmpFF.tmpBuffSize < CONST_DENSITY_FSIZE_LIMIT)) {
                    tmpFF.raf.seek(4 + tmpFF.tmpNewHeaderSizeInFile);
                    int tmp_full_size = tmpFF.tmpFiledHSize + (int)tmpFF.tmpBuffSize;
                    tmp_bup_ok = (tmpFF.raf.skipBytes(tmp_full_size) == tmp_full_size);
                }
                tmpFF.close();
                tmpFF = null;

            }

            if (tmp_bup_ok) {

                //System.out.println("[DEBUG] Backup density found OK in '" + shotName + "' id=" + _ThisID);

                tmpFF = new UtilCreateFile1(DbName(), tmp_std_fname, true);
                tmpFF.readHeaders(shotName, _ThisID);
                if (tmpFF.tmpBuffSize != _upd_arr.length) {
                    tmp_result = "New data size is inappropriate for applying update";
                }
                else if ((tmpFF.tmpFiledHSize > 0) && (tmpFF.tmpBuffSize > 0) && (tmpFF.tmpBuffSize < CONST_DENSITY_FSIZE_LIMIT)) {

                    tmpFF.tmpFirstHeader.HStatus = 1;
                    tmpFF.tmpFirstHeader.buf.clear();
                    tmpFF.tmpFirstHeader.writeAll();
                    tmpFF.raf.seek(4);
                    tmp_writing_started = true;
                    tmpFF.raf.write(tmpFF.tmpFirstHeader.buf.array(), 0, tmpFF.tmpFirstHeader.HSizeCorr);
                    tmpFF.raf.seek(4 + tmpFF.tmpNewHeaderSizeInFile + tmpFF.tmpFiledHSize);
                    tmpFF.raf.write(_upd_arr);
                    //System.out.println("[DEBUG] Updated density written in '" + shotName + "' id=" + _ThisID);
                    tmpFF.tmpFirstHeader.HStatus = 0;
                    tmpFF.tmpFirstHeader.SigUpdateCounter++;
                    tmpFF.tmpFirstHeader.buf.clear();
                    tmpFF.tmpFirstHeader.writeAll();
                    tmpFF.raf.seek(4);
                    tmpFF.raf.write(tmpFF.tmpFirstHeader.buf.array(), 0, tmpFF.tmpFirstHeader.HSizeCorr);

                    tmp_result = "";

                } else {
                    tmp_result = "Current data file looks inappropriate for applying update"; // XXX TODO. Make the message more specific.
                }
                tmpFF.close();
                tmpFF = null;

            } else {

                tmp_result = "There is some problem with backup file"; // XXX TODO. Make the message more specific.

            }

        } catch (Exception e) {
            tmp_result = "Internal exception " + Tum3Util.getStackTrace(e);
        }
        if (tmpFF != null) tmpFF.close();
        if (tmp_f_dst != null) try {
            tmp_f_dst.close();
        } catch (Exception e) {
            if (tmp_result.length() == 0) tmp_result = Tum3Util.getStackTrace(e);
        }
        if (tmp_f_src != null) try {
            tmp_f_src.close();
        } catch (Exception e) {
            if (tmp_result.length() == 0) tmp_result = Tum3Util.getStackTrace(e);
        }

        if (tmp_as_volatile && tmp_need_sync_end) SyncStatusVolOpEnd(_ThisID, SyncStatusVolOpBegin_ok); // YYY

        synchronized(CacheIds) {
            byte tmp_cache_val = 0;
            if (CacheIds.containsKey(_ThisID)) tmp_cache_val = CacheIds.get(_ThisID);
            tmp_cache_val &= ~tmp_in_progress;
            if (0 == tmp_cache_val) CacheIds.remove(_ThisID);
            else CacheIds.put(_ThisID, tmp_cache_val);
            CacheIds.put(_ThisID, tmp_cache_val);
        }

        if (tmp_writing_started) try {
            chgMonitor.AddUpdatedId(_ThisID, true, false, false);
        } catch (Exception e) {
            if (tmp_result.isEmpty()) tmp_result = "Internal error: " + Tum3Util.getStackTrace(e);
        }

        return tmp_result;

    }

    public OutBuffContinuator getTraceReader(int thisSignalId, boolean _use_trailing_status) {

        //System.out.println("[aq2j] DEBUG: searching signal in '" + shotName + "' id=" + thisSignalId + " Valid=" + Valid + " NotStored=" + NotStored + " is_new=" + is_new);
        if (!Valid || NotStored || (0 == thisSignalId)) return null;

        if (TumProtoConsts.CONST_ID_LIST_ALL /* -1 */ == thisSignalId) return PackAvailableSignalsList();
        if (TumProtoConsts.CONST_ID_SHOT_HDR /* -2 */ == thisSignalId) return PackShotHeader();

        boolean tmp_is_saving = false, tmp_as_volatile = false, tmp_with_warning = false;
        synchronized(CacheIds) {
            if (!CacheIds.containsKey(thisSignalId)) {
                //System.out.println("[aq2j] DEBUG: signal not found in '" + shotName + "' id=" + thisSignalId);
                if (!is_new) return null;
            } else {
                byte tmp_cache_val = CacheIds.get(thisSignalId);
                tmp_as_volatile = (tmp_cache_val & 2) != 0;
                if (tmp_as_volatile) tmp_is_saving = (tmp_cache_val & 0x08) != 0;
                else                 tmp_is_saving = (tmp_cache_val & 0x04) != 0;
                tmp_with_warning = ((tmp_cache_val & 5) != 0) && ((tmp_cache_val & 10) != 0);
            }
            if (is_new && !tmp_is_saving) {
                if (expected_ids != null) {
                    for (int tmp_i = 0; tmp_i < expected_ids.length; tmp_i++)
                        if (expected_ids[tmp_i] == thisSignalId) {
                            tmp_is_saving = true;
                            break;
                        }
                }
            }
        }
        //System.out.println("[aq2j] DEBUG: signal in '" + shotName + "' id=" + thisSignalId + " tmp_is_saving=" + tmp_is_saving);
        if (tmp_is_saving) {
            //System.out.println("[DEBUG] id=" + thisSignalId + " suspended delivery.");
            return new ImmediateArrayContinuator(null, 0, true);
        }

        return GetByPositionNew(thisSignalId, _use_trailing_status, tmp_as_volatile, tmp_with_warning);
    }

    private synchronized int Detach_helper() {

        parent_db = null;
        CacheIds = null;
        return UserCount;

    }

    public void Detach() {

        Tum3Db tmp_parent_db = parent_db;
        int tmp_user_count = Detach_helper();
        if (0 != tmp_user_count) {
            if (null != tmp_parent_db)
                Tum3Logger.DoLog(tmp_parent_db.DbName(), true, "Warning: <" + shotName + "> was detached with UserCount=" + tmp_user_count);
            else
                Tum3Logger.DoLogGlb(true, "Warning: <" + shotName + "> was detached with UserCount=" + tmp_user_count);
        }

        CreationLock = null;
        NewHeader = null;
        if (null != FF) {
            FF.close();
            FF = null;
        }
    }

    public String getName() {

        return shotName;

    }

}
