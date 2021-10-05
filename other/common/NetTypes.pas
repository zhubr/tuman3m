(*
 * Copyright 2003-2021 Nikolai Zhubr <zhubr@mail.ru>
 *
 * This file is provided under the terms of the GNU General Public
 * License version 2. Please see LICENSE file at the uppermost 
 * level of the repository.
 * 
 * Unless required by applicable law or agreed to in writing, this
 * software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OF ANY KIND.
 *
 *)
unit NetTypes;

interface

const
  REQUEST_SIGN1  = $F0;
  REQUEST_SIGN2  = $98;
  REQUEST_SIGN3  = $67;
  { REQUEST_SIGN3a = $68; }

  { REQUEST_TYPE_NAMEING   = $60; }
  { REQUEST_TYPE_UPLOAD    = $61; }
  REQUEST_TYPE_TRACECALL  = $62;
  REQUEST_TYPE_TRACECOME  = $63;
  REQUEST_TYPE_TRACECOME_X  = $9A; { YYY }
  REQUEST_TYPE_TRACENOTAVAIL   = $64;
  REQUEST_TYPE_TRACEINVALIDATE = $65; { Obsolete }
  REQUEST_TYPE_DIRECTORYCALL = $66;
  REQUEST_TYPE_DIRECTORYCOME = $67;
  REQUEST_TYPE_ANYBODYTHERE = $68;
  REQUEST_TYPE_KEEPCONNECTED = $69;
  REQUEST_TYPE_GETINFO = $70;
  REQUEST_TYPE_TAKEINFO = $71;
  { REQUEST_TYPE_FORCETRIG = $72; } { Obsolete, not supported. }
  REQUEST_TYPE_AQ2EXTENDED = $73;
  REQUEST_TYPE_TRACEINVALIDATEONE = $74;
  { REQUEST_TYPE_TRACEUPLOADONE = $75; } { YYY Obsolete, use REQUEST_TYPE_UPLOAD_ONE instead. } 
  REQUEST_TYPE_TRACEUPLOADACK = $76;
  REQUEST_TYPE_AQ2CANCEL = $77;
  REQUEST_TYPE_FEATURESELECT = $78;
  REQUEST_TYPE_USERLOGIN = $79;
  REQUEST_TYPE_USERLOGINREPLY = $7A;
  REQUEST_TYPE_REPORTAVAILVERSION = $7B;
  REQUEST_TYPE_AVAILVERSION = $7C;
  REQUEST_TYPE_SENDMEPROGRAMFILE = $7D;
  REQUEST_TYPE_PROGRAMFILECOMING = $7E;
  REQUEST_TYPE_FAILED = $7F;
  REQUEST_TYPE_DEBUGON = $80;
  REQUEST_TYPE_TALKMSG = $81;
  REQUEST_TYPE_TALKMSG_IN = $82;
  REQUEST_TYPE_REPORTAVAILVERSION_FULL = $83;
  REQUEST_TYPE_EXTERNAL_REQUEST_TO_DEVICE = $84;  { Obsolete, should not be used anymore. }
  REQUEST_TYPE_UPLOAD_ONE = $85;
  REQUEST_TYPE_UPLOAD_END_HINT = $86;
  REQUEST_TYPE_EXTERNAL_REQUEST_TO_DEVICE_2 = $87;
  REQUEST_TYPE_EXTERNAL_REQUEST_TO_DEVICE_RESULT = $87;
  REQUEST_TYPE_NETCAMAC_REQUEST = $88;
  REQUEST_TYPE_NETCAMAC_REPLY = $89;

  REQUEST_TYPE_EXTERNAL_REQUEST_TO_DEVICE_3 = $8A;
  REQUEST_TYPE_EXTERNAL_REQUEST_TO_DEVICE_3_RESULT = $8A;

  REQUEST_TYPE_INIT_CONT_METER = $8B;
  REQUEST_TYPE_CONT_METER_DATA = $8C;

  REQUEST_TYPE_AGENT_INFO = $8D;
  REQUEST_TYPE_DOWNLOAD_PAUSED = $8E;
  REQUEST_TYPE_DOWNLOAD_RESUME = $8F;
  REQUEST_TYPE_REFUSE = $90;

  REQUEST_TYPE_AQ3_REQ = $91;
  REQUEST_TYPE_AQ3_REP = $92;
  REQUEST_TYPE_AQ3_EVT = $93;

  REQUEST_TYPE_CONFIGSCALL = $94;
  REQUEST_TYPE_CONFIGSFETCH = $95;
  REQUEST_TYPE_CONFIGSSAVE = $96;
  REQUEST_TYPE_CONFIGSUPLRES = $97;

  REQUEST_TYPE_DENSITY_UPD = $98;
  REQUEST_TYPE_INFORMATION_TEXT = $99;

  REQUEST_TYPE_UPLOAD_ONE_VAR = $9B;

  REQUEST_TYPE_SPEEDTEST_REQ = $9C;
  REQUEST_TYPE_SPEEDTEST_REP = $9D;

  REQUEST_TYPE_REPORTAVAILVERSION_64 = $9E;
  REQUEST_TYPE_DELETE_ONE_VAR = $9F;
  REQUEST_TYPE_TRACEREMOVED = $A0;

  REQUEST_TYPE_GET_MISC = $A1;
  REQUEST_TYPE_MISC_FETCH = $A2;
  REQUEST_TYPE_TALKMSGX = $A3;

  REQUEST_TYPE_TRACECOME_S = $A4;
  REQUEST_TYPE_USERLOGINX = $A5;
  REQUEST_TYPE_FLEX_TXT   = $A6;
  REQUEST_TYPE_FLEX_REPL  = $A7;

  REQUEST_TYPE_REPORTAVAILVERSION_FULL_64 = $A8;
  REQUEST_TYPE_TRIG_ALLOW = $A9; { YYY }

  DTYPE_SmallInt = 50; { 16-bit, signed integer }
  DTYPE_Single   = 51; { 32-bit, floating point }
  DTYPE_LongInt  = 52; { 32-bit, signed integer }
  DTYPE_System01 = 53; { Reserved for internal use }
  DTYPE_Splines  = 54; { Spline profile evolution }
  DTYPE_Byte     = 55; { 8-bit, unsigned integer }
  DTYPE_System02 = 56; { Reserved for internal use }

  RESULT_TRACE_BEGIN = 3;
  RESULT_TRACE_END = 4;
  RESULT_SHOT_END = 2;
  RESULT_SHOT_BEGIN = 1;

  bcmNewShot     = $43129801;
  bcmRefreshShot = $43129802;
  bcmTalkMsg     = $43129803;
  bcmServiceMsg  = $43129804;

  dcRmtReply_22 = 22; { YYY }

  dcRmtReq_20 = 20;
  dcRmtReq_24 = 24;
  dcRmtReq_26 = 26;
  dcRmtReq_28 = 28;
  dcRmtReq_30 = 30;
  dcRmtReq_32 = 32; { YYY } { Note: like 24, but intended for volatile storage. }
  dcRmtReq_34 = 34; { YYY } { Note: For internal use. Performs signal deletion. }

  { dcCancelThis = 0; }
  { dcInitialize = 1; }
  { dcPrepare = 2; }
  { dcTest = 5; }
  { dcNone = 7; }
  dcStart = 3;
  dcStartLight = 6;
  dcStop = 4;
  dcStartExtAccess = 8; { Obsolete. Do not use. }

  aq3_req_attach = 1; { YYY }
  aq3_req_action = 2; { YYY }

  aq3_action_apply = 'Apply'; { YYY }
  aq3_action_profile_get = 'GetProfile'; { YYY }
  aq3_action_start = 'Start'; { YYY }
  aq3_action_stop = 'Stop'; { YYY }
  aq3_action_reset = 'Reset'; { YYY }
  aq3_action_puff_profile_get = 'GetPuffProfile'; { YYY }
  aq3_action_puff_apply = 'PuffApply'; { YYY }
  aq3_action_quartz_corr_put = 'QuartzPut'; { YYY }
  aq3_action_puff_zero_put = 'PuffZeroPut'; { YYY }
  aq3_action_saving_stop = 'SavingStop'; { YYY }
  aq3_action_starting_stop = 'StartingStop'; { YYY }

  aq3_async_push_event = 129; { YYY }

  { Miscellaneous collateral info type codes. }
  tum3misc_file_overwrite = 1; { YYY }
  tum3misc_file_no_overwrite = 2; { YYY }
  tum3misc_userlist = 4; { YYY }

  FLEXCMD_hotstart = 'hotstart'; { YYY }


function DataPointSize(_Dtype: integer): integer;

type
  PGeneralRequest =  ^TGeneralRequest;
  TGeneralRequest = packed record
    RequestType, Sign3, Sign2, Sign1: byte;
    TrailingSize: longint;
  end;

  TAq2ExtArea = packed record
    aq2_command: longint;
    aq2_cmd_flags {Incremental_rsrv}: longint; { YYY }
    LastChecksum, ThisChecksum: longint;
    aq2_rsrv1 { aq2_nameback }: packed array[0.. 21] of byte { string[21] }; { Obsolete. }
    aq2_relevent_shot: string[15];
    aq2_rsrv2 { aq2_host_id}: packed array[0.. 21] of byte { string[21] };  { Obsolete }
  end;

  PGeneralExtendedRequest =  ^TGeneralExtendedRequest;
  TGeneralExtendedRequest = packed record
    RequestType, Sign3, Sign2, Sign1: byte;
    TrailingSize: longint;
    aq2_area: TAq2ExtArea;
  end;

  PUploadHeader = ^TUploadHeader;
  TUploadHeader =  packed record
    ShotName: string[15];
    SignalId: longint;
    Reserved: array[1.. 12] of byte; 
  end;

  TNameRequest = packed record         { Original:                        }
    hour1, min1, sec1, rsrv1: byte;    {  Get name  00ssmmhh, 00ssmmhh    }
    hour2, min2, sec2, rsrv2: byte;    {  Get time  00000000, 00000000    }
  end;                                 {  Reg name  01000000, 00000000    }
  TNameRequestX = packed record        {  Put data  00000001, 000000xx    }
    Base: TNameRequest;                {                                  }
    Extd: String[255];                 {                                  }
  end;                                 {                                  }

  TDataRequest = packed record
    rsrv1: longint;
    Count: longint;
    ShotName: String[255];
  end;

  TTraceRequest = packed record
    rsrv2: longint;
    PointCount: longint;
    Comment: String[255];
    SignalID: longint;
    ADC_Tact_mks: longint;
    ADC_Calibr,
    ADC_Zeroline: Single;
    ADC_Tact_float,
    ADC_Start_time: Single;
  end;

  TRequestResult = packed record
    ResultType: longint;
    ErrorCode: longint;
    Comment: String[255];
  end;

  PUnifiedTraceHeader = ^TUnifiedTraceHeader;
  TUnifiedTraceHeader = packed record
    HSize: longint; { Header size }
    HType,          { Data type  }
    HCount: longint;  { Point count (used for oscillograms) }
    HTact, HDataStart, HCalibr, HZeroline: Single;
    HDataSize: longint;  { Total data size in bytes }
    HCalibr2Milivolts: Single;
    HComment1: string[255];
    HDataStartExternal: double;
    HAqVersion: longint;
    HMetaDataSize: longint;
   { HMetaData: packed array[1..0] of byte; }
    HReserved1: longint;
  end;

  { The following section is very experimental and may not be reliable yet. }

  PCamExtHeader = ^TCamExtHeader;
  TCamExtHeader = packed record
    RReqNumber: longint;
    RReqCode: longint;
  end;

  PCamReqHeader = ^TCamReqHeader;
  TCamReqHeader = packed record
    RBuffLimit: longint;
    RGeneralHeader: TGeneralRequest;
    RExtHeader: TCamExtHeader;
  end;

  PCamRetBlock = ^TCamRetBlock;
  TCamRetBlock = packed record
    RBuffFill: longint;
    RResultCode, RResultMsgLen: word;
    RData: array[0.. 0] of byte;
  end;

  PCamRepBlock = ^TCamRepBlock;
  TCamRepBlock = packed record
    RExtHeader: TCamExtHeader;
    RResultCode, RResultMsgLen: word;
    RData: array[0.. 0] of byte;
  end;

  PAgentInfo = ^TAgentInfo;
  TAgentInfo = packed record { YYY }
    RCompatVersion: longint;
    RLinkKilobits: longint;
    RCompatFlags: longint; { YYY }
  end;

implementation


function DataPointSize(_Dtype: integer): integer;
begin
  DataPointSize := 0;
  case _Dtype of
    DTYPE_Byte: DataPointSize := 1; { YYY }
    DTYPE_SmallInt: DataPointSize := 2;
    DTYPE_Single  : DataPointSize := 4;
    DTYPE_LongInt : DataPointSize := 4;
    DTYPE_Splines : DataPointSize := 1; { YYY }
  end;
end;


end.
