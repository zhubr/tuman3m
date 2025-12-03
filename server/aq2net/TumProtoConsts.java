/*
 * Copyright 2011-2024 Nikolai Zhubr <zhubr@rambler.ru>
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
package aq2net;


public interface TumProtoConsts
{
    public final static byte REQUEST_SIGN1 = (byte)0xF0;
    public final static byte REQUEST_SIGN2 = (byte)0x98;
    public final static byte REQUEST_SIGN3 = (byte)0x67;

    public final static byte REQUEST_TYPE_AQ2EXTENDED = (byte)0x73;
    public final static byte REQUEST_TYPE_AQ2CANCEL = (byte)0x77;

    // Request codes.
    public final static byte REQUEST_TYPE_FEATURESELECT = (byte)0x78;
    public final static byte REQUEST_TYPE_REPORTAVAILVERSION = (byte)0x7B;
    public final static byte REQUEST_TYPE_REPORTAVAILVERSION_FULL = (byte)0x83;
    public final static byte REQUEST_TYPE_REPORTAVAILVERSION_FULL_64 = (byte)0xA8;

    public final static byte REQUEST_TYPE_REPORTAVAILVERSION_64 = (byte)0x9E;
    public final static byte REQUEST_TYPE_SENDMEPROGRAMFILE = (byte)0x7D;
    public final static byte REQUEST_TYPE_PROGRAMFILECOMING = 0x7E;
    //public final static byte REQUEST_TYPE_USERLOGIN = (byte)0x79; // YYY Obsolete. Do not use.
    public final static byte REQUEST_TYPE_USERLOGINX = (byte)0xA5;
    public final static byte REQUEST_TYPE_FLEX_TXT   = (byte)0xA6;
    public final static byte REQUEST_TYPE_FLEX_REPL  = (byte)0xA7;

    public final static byte REQUEST_TYPE_DIRECTORYCALL = (byte)0x66;
    public final static byte REQUEST_TYPE_ANYBODYTHERE = (byte)0x68;
    public final static byte REQUEST_TYPE_TRACECALL = (byte)0x62;
    public final static byte REQUEST_TYPE_REFUSE = (byte)0x90;
    public final static byte REQUEST_TYPE_UPLOAD_ONE = (byte)0x85;
    public final static byte REQUEST_TYPE_UPLOAD_ONE_VAR = (byte)0x9B;
    public final static byte REQUEST_TYPE_DELETE_ONE_VAR = (byte)0x9F;
    public final static byte REQUEST_TYPE_UPLOAD_END_HINT = (byte)0x86;
    public final static byte REQUEST_TYPE_TALKMSG = (byte)0x81;
    public final static byte REQUEST_TYPE_TRACEINVALIDATEONE = (byte)0x74;
    public final static byte REQUEST_TYPE_INVALIDATEMANY = (byte)0xB4; // YYY
    public final static byte REQUEST_TYPE_TRACEREMOVED = (byte)0xA0;
    public final static byte REQUEST_TYPE_DOWNLOAD_PAUSED = (byte)0x8E;
    public final static byte REQUEST_TYPE_AQ3_REQ = (byte)0x91;
    public final static byte REQUEST_TYPE_GET_MISC = (byte)0xA1;
    public final static byte REQUEST_TYPE_TALKMSGX = (byte)0xA3;
    public final static byte REQUEST_TYPE_AGENT_INFO = (byte)0x8D;
    public final static byte REQUEST_TYPE_UGC_REQ = (byte)0xAB;
    public final static byte REQUEST_TYPE_REQUEST_FILES = (byte)0xAA;
    public final static byte REQUEST_TYPE_PUBLISH_SHOTS = (byte)0xB2; // YYY

    // Reply codes.
    public final static byte REQUEST_TYPE_AVAILVERSION = (byte)0x7C;
    public final static byte REQUEST_TYPE_USERLOGINREPLY = (byte)0x7A;
    public final static byte REQUEST_TYPE_DIRECTORYCOME = (byte)0x67;
    public final static byte REQUEST_TYPE_KEEPCONNECTED = (byte)0x69;
    public final static byte REQUEST_TYPE_TRACENOTAVAIL = (byte)0x64;
    public final static byte REQUEST_TYPE_TRACECOME     = (byte)0x63;
    public final static byte REQUEST_TYPE_TRACECOME_X   = (byte)0x9A;
    public final static byte REQUEST_TYPE_TRACECOME_S   = (byte)0xA4;
    public final static byte REQUEST_TYPE_TRACEUPLOADACK = (byte)0x76;
    public final static byte REQUEST_TYPE_TALKMSG_IN = (byte)0x82;
    public final static byte REQUEST_TYPE_DOWNLOAD_RESUME = (byte)0x8F;
    public final static byte REQUEST_TYPE_AQ3_REP = (byte)0x92;
    public final static byte REQUEST_TYPE_AQ3_EVT = (byte)0x93;
    public final static byte REQUEST_TYPE_CONFIGSCALL = (byte)0x94;
    public final static byte REQUEST_TYPE_CONFIGSFETCH = (byte)0x95;
    public final static byte REQUEST_TYPE_CONFIGSSAVE = (byte)0x96;
    public final static byte REQUEST_TYPE_CONFIGSUPLRES = (byte)0x97;
    public final static byte REQUEST_TYPE_DENSITY_UPD = (byte)0x98;
    public final static byte REQUEST_TYPE_INFORMATION_TEXT = (byte)0x99; // Pushed actually.
    public final static byte REQUEST_TYPE_MISC_FETCH = (byte)0xA2;
    public final static byte REQUEST_TYPE_TRIG_ALLOW = (byte)0xA9;
    public final static byte REQUEST_TYPE_UGC_REP = (byte)0xAC;
    public final static byte REQUEST_TYPE_SERVERINFO = (byte)0xAF; // Pushed actually.

    public final static byte REQUEST_TYPE_JSON = (byte)0xAD; // This is for both request and reply.
    public final static byte REQUEST_TYPE_JSON_WBIN = (byte)0xAE; // This is for both request and reply.

    public final static byte REQUEST_TYPE_FPART_TRNSF = (byte)0xB0; // YYY
    public final static byte REQUEST_TYPE_FPART_CNFRM = (byte)0xB1; // YYY

    public final static byte REQUEST_TYPE_PUBLISH_RSLT = (byte)0xB3; // YYY

    // Special IDs
    public final static int CONST_ID_LIST_ALL = -1;
    public final static int CONST_ID_SHOT_HDR = -2;

    // Aq-2 codes.
    public final static byte dcRmtReply_1  =  1; // Standard cmd final reply.
    //public final static byte dcRmtReply_2  =  2; // Incremental refusal. Obsolete, do not use.
    //public final static byte dcRmtReply_3  =  3; // Incremental cmd success. Obsolete, do not use.
    public final static byte dcRmtReply_10 = 10; // Notify of read errors.
    public final static byte dcRmtReply_11 = 11; // Pass error message.
    public final static byte dcRmtReply_22 = 22; // Keepalive (dumb) reply.

    public final static byte dcServReply_21 = 21;
    public final static byte dcServReply_25 = 25;

    public final static byte dcRmtReq_20 = 20;
    public final static byte dcRmtReq_24 = 24;
    public final static byte dcRmtReq_26 = 26;
    public final static byte dcRmtReq_28 = 28;
    public final static byte dcRmtReq_30 = 30;
    public final static byte dcRmtReq_32 = 32; // Note: like 24, but intended for volatile storage.

    // Aq-3 codes.
    public final static int aq3_req_attach = 1;
    public final static int aq3_req_action = 2;

    public final static String aq3_action_apply = "Apply";
    public final static String aq3_action_profile_get = "GetProfile";
    public final static String aq3_action_start = "Start";
    public final static String aq3_action_stop = "Stop";
    public final static String aq3_action_reset = "Reset";
    public final static String aq3_action_puff_profile_get = "GetPuffProfile";
    public final static String aq3_action_puff_apply = "PuffApply";
    public final static String aq3_action_quartz_corr_put = "QuartzPut";
    public final static String aq3_action_puff_zero_put = "PuffZeroPut";
    public final static String aq3_action_saving_stop = "SavingStop";
    public final static String aq3_action_starting_stop = "StartingStop";

    public final static int aq3_async_push_event = 129;

    // Trace Metadata magic key
    public final static String tag_editlocked = "EditLocked";

    // Miscellaneous collateral info type codes.
    public final static byte tum3misc_file_overwrite = 1;
    public final static byte tum3misc_file_no_overwrite = 2;
    public final static byte tum3misc_userlist = 4;
    public final static byte tum3misc_file_per_request = 5;

    public final static String TUM3_KEYWORD_users = "users";
    public final static String TUM3_KEYWORD_files = "files";

    public final static String FLEXCMD_hotstart = "hotstart";

}
