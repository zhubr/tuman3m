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


import java.nio.*;
import java.io.*;

import aq2db.*;
import aq2net.*;


public class OutgoingBuff implements TumProtoConsts, OutBuffData {

    private byte[] real_buff;
    private ByteBuffer byte_buff;
    private int curr_size_total = 0;
    private int curr_sent_count = 0;
    private volatile int curr_trace_size, curr_trace_number;
    private volatile OutBuffContinuator curr_continuator = null;
    //private Object ContinuatorLock = new Object();
    private boolean in_continuator;
    private int curr_continuator_len;
    private long seg_ofs = 0;
    private int seg_size = 0;
    private boolean is_last_seg = true;    
    private int const_min_out_buff; //private int db_index; // YYY


    public OutgoingBuff(int _min_out_buff) { // YYY

        //db_index = _db_idx;
        const_min_out_buff = _min_out_buff;

    }

    public void InitSrvReply(byte rep_code, int realSize, int trailingSize) {
        InitSrvReply(rep_code, realSize, trailingSize, null);
    }

    public void InitSrvReply(byte rep_code, int realSize, int trailingSize, OutBuffContinuator thisContinuator) {

        curr_size_total = trailingSize;
        curr_sent_count = 0;
        in_continuator = false;
        { // synchronized(ContinuatorLock)
            curr_continuator = thisContinuator;
            if (null == curr_continuator) {
                curr_continuator_len = 0;
                curr_trace_size = 0;
                curr_trace_number = 0;
            } else {
                curr_continuator_len = seg_size;
                //System.out.print("[" + hashCode() /* Thread.currentThread().getId() */ + ":len1=" + curr_continuator_len + "]");
                curr_trace_size = curr_continuator_len;
                curr_trace_number = 1;
            }
        }
        if (real_buff != null) if (real_buff.length < (8+realSize)) real_buff = null;
        if (real_buff == null) {
            int tmp_size = 8+realSize;
            if (tmp_size < const_min_out_buff) tmp_size = const_min_out_buff;
            real_buff = new byte[tmp_size];
            byte_buff = ByteBuffer.wrap(real_buff);
            byte_buff.order(ByteOrder.LITTLE_ENDIAN);
        } else {
            byte_buff.clear();
        }
        byte_buff.put(rep_code);
        byte_buff.put(REQUEST_SIGN3);
        byte_buff.put(REQUEST_SIGN2);
        byte_buff.put(REQUEST_SIGN1);
        int tmp_total_trailing = trailingSize + curr_continuator_len;
        byte_buff.putInt(tmp_total_trailing);
    }

    public void SetSegment(long _seg_ofs, int _seg_size, boolean _is_last_seg) {

        seg_ofs = _seg_ofs;
        seg_size = _seg_size;
        is_last_seg = _is_last_seg;

    }

    public long getSegOfs() {

        return seg_ofs;

    }

    public void CheckBuffFill() throws Exception {
        if ((curr_size_total+8) != byte_buff.position()) 
            throw new Exception("Internal error: in CheckBuffFill() " + curr_size_total + " != " + byte_buff.position());
    }

    public OutBuffContinuator GetContinuator() {
        { // synchronized(ContinuatorLock)
            return curr_continuator;
        }
    }

    public int GetTraceSize() {
        return curr_trace_size;
    }

    public int GetTraceNumber() {
        return curr_trace_number;
    }

    public int SentCount() {
        return curr_sent_count;
    }

    public void putInt(int i) {
        byte_buff.putInt(i);
    }

    public void putLong(long i) {
        byte_buff.putLong(i);
    }

    public void putByte(byte b) {
        byte_buff.put(b);
    }

    public void putString(String s) {
        for (int tmp_i = 0; tmp_i < s.length(); tmp_i++)
            byte_buff.put((byte)s.charAt(tmp_i));
    }

    public void putPasString(String s) throws Exception {
        if (s.length() > 254) throw new Exception("Attempt to store >254 length pascal string");
        byte_buff.put((byte)s.length());
        for (int tmp_i = 0; tmp_i < s.length(); tmp_i++)
            byte_buff.put((byte)s.charAt(tmp_i));
    }

    public void putStream(ByteArrayOutputStreamX buf) {
        //byte_buff.put(buf.toByteArray());
        byte_buff.put(buf.AsByteBuffer());
    }

    public void putBytes(byte[] buf) {
        byte_buff.put(buf);
    }

    public void putBytes(byte[] buf, int offset, int length) {
        byte_buff.put(buf, offset, length);
    }

    public int SendToByteArray(byte[] buff) throws Exception {
        int tmp_count = 0;
        if (!in_continuator) {
            tmp_count = byte_buff.position() - curr_sent_count;
            if (tmp_count > buff.length) tmp_count = buff.length;
            //System.out.print("[A=" + tmp_count + "]");
            System.arraycopy(real_buff, curr_sent_count, buff, 0, tmp_count);
            curr_sent_count += tmp_count;
            if ((GetContinuator() != null) && (curr_sent_count == byte_buff.position()))
                in_continuator = true;
            //return tmp_count;
        }
        // 
        if (in_continuator) {
            int tmp_count2 = buff.length - tmp_count;
            if (tmp_count2 > (byte_buff.position() + curr_continuator_len - curr_sent_count))
                tmp_count2 = byte_buff.position() + curr_continuator_len - curr_sent_count;
            //System.out.print("[B=" + tmp_count2 + "]");
            if (tmp_count2 > 0) {
                curr_continuator.EnsureOfs(seg_ofs + curr_sent_count - byte_buff.position());
                tmp_count2 = curr_continuator.ReadTo(buff, tmp_count, tmp_count2);
                //System.out.print("[C=" + tmp_count2 + "]");
                //System.out.println("[aq2j] DEBUG: Entering sleep...");
                //Tum3Util.SleepExactly(5000);
                //System.out.println("[aq2j] DEBUG: Left sleep.");
                curr_sent_count += tmp_count2;
                tmp_count += tmp_count2;
            }
        }
        //System.out.print("[sent=" + tmp_count + "]");
        return tmp_count;
    }

    public boolean SentAll() {
        //System.out.print("[rem=" + (byte_buff.position() + curr_continuator_len - curr_sent_count) + "/" + byte_buff.position() + "/" + curr_continuator_len + "/" + curr_sent_count + "]");
        //System.out.print("[" + hashCode() /* Thread.currentThread().getId() */ + ":len2=" + curr_continuator_len + "]");
        return curr_sent_count >= (byte_buff.position() + curr_continuator_len);
    }

    public void CancelData() {
        OutBuffContinuator tmp_continuator = null;
        { // synchronized(ContinuatorLock)
            if (null != curr_continuator) {
                tmp_continuator = curr_continuator;
                curr_continuator = null;
            }
        }
        if (null != tmp_continuator) {
            tmp_continuator.close();
        }
    }

    //public void ReleaseContinuator() {
    //  OutBuffContinuator tmp_continuator = GetContinuator();
    //  if (tmp_continuator != null) tmp_continuator.close();
    //}

}

