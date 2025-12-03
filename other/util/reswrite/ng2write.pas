(*
 * Copyright 2011-2021 Nikolai Zhubr <zhubr@rambler.ru>
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
unit ng2write;

interface

uses
  NetTypes, genwrtr;

type
  TNewHeader = packed record
    HSize,
    DirCount,
    DirLimit: longint;
    ProgramSubversion: longint;
    wYear, wMonth, wDay,
    wHour, wMinute, wSecond: word;
    dwUpdateCounter: word;
    hdrOriginalShotName: string[40];
  end;

  TNewSignalHeader = packed record
    HSize,
    HID: longint;
    HStatus: SmallInt;
    hdrOriginalShotName: string[40];
    ProgramSubversion: longint;
    wYear, wMonth, wDay,
    wHour, wMinute, wSecond: word;
    SigUpdateCounter: longint;  // YYY
  end;

  TNg2Writer = object(TTumDbWriter)
    constructor Init;
    function ResOpen(_FName: string): boolean; virtual;
    function  ResBegin(_Id: longint; _pHeader: PUnifiedTraceHeader): boolean; virtual;
    procedure ResWriteData(var Buff; Cnt, Base: longint); virtual;
    procedure ResClose; virtual;
    procedure AutoCreateMaster; virtual;
  private
    FAutoCreateMaster: boolean;
    FPureName, FBasePath: string[254];
    FHeader0: array [0.. 1] of longint;
    FMainFile,
    FileDat: file;
    FDataSize: longint;
    DataStartPos: longint;
    tmpFirstHeader: TNewSignalHeader;
    FStartedWriting: boolean;
    FNewHeader: TNewHeader;
    FpEntryHeader: PUnifiedTraceHeader;
  end;
  PNg2Writer = ^TNg2Writer;


implementation

uses
  Common, SysUtils;

const
  const_tum3ng_sign_h = $484D5554;
  const_tum3ng_sign_s = $534D5554;
  const_ng2_writer_version = 291;

constructor TNg2Writer.Init;
begin
  FAutoCreateMaster := false;
end;


procedure TNg2Writer.AutoCreateMaster;
begin
  FAutoCreateMaster := true;
end;

{$I-}
function TNg2Writer.ResOpen(_FName: string): boolean;
label
  mf_err_exit;
var
  i, jjj: integer;
  j: longint;
  tmp_main_file_ok: boolean;
begin
  ResOpen := false;

  FBasePath := _FName;
  FPureName := ExtractFileName(FBasePath);
  if length(FPureName) > 40 then
    exit;

  if not DirectoryExists(FBasePath) then
    begin
      tmp_main_file_ok := false;
      if not CreateDir(FBasePath) then
        exit;

      if FAutoCreateMaster then
        begin
          assign(FMainFile, FBasePath + '\0000.000');
          Rewrite(FMainFile, 1);
          if IOResult <> 0 then
            goto mf_err_exit;

          FHeader0 [0] := const_tum3ng_sign_h;
          BlockWrite(FMainFile, FHeader0, 4, i);
          if IOResult <> 0 then
            goto mf_err_exit;
          if i <> 4 then
            goto mf_err_exit;

          FillChar(FNewHeader, SizeOf(TNewHeader), 0);
          FNewHeader.HSize := SizeOf(TNewHeader);
          FNewHeader.ProgramSubversion := const_ng2_writer_version;
          FNewHeader.dwUpdateCounter := 0;
          FNewHeader.hdrOriginalShotName := FPureName;
          with FNewHeader do
            CurrentDateTime(wYear, wMonth, wDay, wHour, wMinute, wSecond);
          FNewHeader.DirCount := 0;
          FNewHeader.DirLimit := 0;
          jjj := FNewHeader.HSize;
          BlockWrite(FMainFile, FNewHeader, jjj, i);
          if IOResult <> 0 then
            goto mf_err_exit;
          if i <> jjj then
            goto mf_err_exit;
          tmp_main_file_ok := true;
        mf_err_exit:
          Close(FMainFile);
        end
      else
        tmp_main_file_ok := true;
    end
  else
    tmp_main_file_ok := true;

  if not tmp_main_file_ok then
    exit;

  ResOpen := tmp_main_file_ok;
end;


function TNg2Writer.ResBegin(_Id: longint; _pHeader: PUnifiedTraceHeader): boolean;
label
  sf_err_exit;
var
  tmpSt, tmp_curr_fname: string;
  Success: boolean;
  tmpSignature,
  tmpHeaderFill: longint;
  i: integer;
begin
  result := false;

  FStartedWriting := false;

  Str(_id, tmpSt);
  while length(tmpSt) < 4 do
    tmpSt := '0' + tmpSt;
  tmp_curr_fname := FBasePath + '\' + tmpSt + '.000';

  assign(FileDat, tmp_curr_fname);
  Rewrite(FileDat, 1);
  if IOResult <> 0 then
    goto sf_err_exit;

  { Prepare Signal Header }

  if FpEntryHeader <> nil then
    FreeMem(FpEntryHeader, FpEntryHeader^.HSize);
  GetMem(FpEntryHeader, _pHeader.HSize);
  Move(_pHeader^, FpEntryHeader^, _pHeader.HSize);

 { FEntryHeader.HZeroline := - FEntryHeader.HZeroline; } { XXX fixme !!! }
  FDataSize := FpEntryHeader^.HDataSize;

  { Prepare file header }

  tmpSignature := const_tum3ng_sign_s;
  FillChar(tmpFirstHeader, SizeOf(tmpFirstHeader), 0);
  tmpFirstHeader.HSize := SizeOf(tmpFirstHeader);
  tmpFirstHeader.HID := _ID;
  tmpFirstHeader.HStatus := 1;  // == not ready
  tmpFirstHeader.hdrOriginalShotName := FPureName;
  tmpFirstHeader.ProgramSubversion := const_ng2_writer_version;
  with tmpFirstHeader do
    CurrentDateTime(wYear, wMonth, wDay, wHour, wMinute, wSecond);
  Success := true;
  BlockWrite(FileDat, tmpSignature, 4, i);
  Success := Success and (i = 4);
  if Success then
    BlockWrite(FileDat, tmpFirstHeader, tmpFirstHeader.HSize, i);
  Success := Success and (i = tmpFirstHeader.HSize);
  tmpHeaderFill := _pHeader.HSize;
  if Success then
    BlockWrite(FileDat, _pHeader^, tmpHeaderFill, i);
  Success := Success and (i = tmpHeaderFill);
  FDataSize := _pHeader.HDataSize;
  DataStartPos := 4 + tmpFirstHeader.HSize + tmpHeaderFill;
  FStartedWriting := true;
  result := true;
  exit;

sf_err_exit:
  Close(FileDat);
end;


procedure TNg2Writer.ResWriteData(var Buff; Cnt, Base: longint);
begin
  if not FStartedWriting then
    exit;
  Seek(FileDat, DataStartPos + Base * DataPointSize(FpEntryHeader^.HType));
  BlockWrite(FileDat, Buff, Cnt * DataPointSize(FpEntryHeader^.HType));
 { writeln(DataStartPos + Base * DataPointSize(FEntryHeader.HType) + Cnt * DataPointSize(FEntryHeader.HType)); }
end;


procedure TNg2Writer.ResClose;
var
  i: integer;
begin
  if FStartedWriting then
  begin
    inc(tmpFirstHeader.SigUpdateCounter); // YYY
    tmpFirstHeader.HStatus := 0;  // == ready
    Seek(FileDat, 4);
    if IOResult = 0 then
      BlockWrite(FileDat, tmpFirstHeader, tmpFirstHeader.HSize, i);
  end;

  if FpEntryHeader <> nil then
    FreeMem(FpEntryHeader, FpEntryHeader^.HSize);
  FpEntryHeader := nil;
  Close(FileDat);
end;


end.