(*
 * Copyright 2006-2021 Nikolai Zhubr <zhubr@mail.ru>
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
unit ResWrite;

interface

uses
  NetTypes, genwrtr;

const
  MaxEntries = 50;
//  ADC_ID = 30000;

type
  TNewHeader = packed record
    HSize,
    DirCount,
    DirLimit: longint;
    ProgramSubversion: longint;
    wYear, wMonth, wDay,
    wHour, wMinute, wSecond: word;
    dwUpdateCounter: word;
   { ProgVersion,
    ProgSubVersion,... }
  end;

  TDirEntry = record
    thisID, thisStatus: SmallInt;
    thisOffset: Longint;
  end;

  TResWriter = object(TTumDbWriter)
    constructor Init;
    function ResOpen(_FName: string): boolean; virtual;
    function  ResBegin(_Id: longint; var _Header: TUnifiedTraceHeader): boolean; virtual;
    procedure ResWriteData(var Buff; Cnt, Base: longint); virtual;
    procedure ResClose; virtual;
  private
    FileDat: file;
    DataStartPos: longint;
    FNewHeader: TNewHeader;
    FDirectory: array [1.. MaxEntries] of TDirEntry;
    FpEntryHeader: PUnifiedTraceHeader;
  end;
  PResWriter = ^TResWriter;


implementation

uses
  Common;


constructor TResWriter.Init;
begin
end;

{$I-}
function TResWriter.ResOpen(_FName: string): boolean;
var
  FHeader0: array [0.. 8] of word;
  FDirOffset: longint;
  j: longint;
begin
  ResOpen := false;

  assign(FileDat, _FName);
  IOResult;
  Rewrite(FileDat, 1);
  if IOResult <> 0 then
    exit;

  ResOpen := true;

  FHeader0 [0] := 1111;
  FHeader0 [1] := 0;
  BlockWrite(FileDat, FHeader0, 4);
  FNewHeader.HSize := SizeOf(FNewHeader);
  FNewHeader.ProgramSubversion := 2;
  FNewHeader.dwUpdateCounter := 0;
  with FNewHeader do
    CurrentDateTime(wYear, wMonth, wDay, wHour, wMinute, wSecond);
  FNewHeader.DirLimit := MaxEntries;
  FNewHeader.DirCount := 0;
  BlockWrite(FileDat, FNewHeader, FNewHeader.HSize);
  j := SizeOf(TDirEntry)*FNewHeader.DirLimit;
  FillChar(FDirectory, j, 0);
  FDirOffset := 4 + FNewHeader.HSize;
  FDirectory[1].thisOffset := FDirOffset + j;
  BlockWrite(FileDat, FDirectory, j);
  FpEntryHeader := nil;
end;


function TResWriter.ResBegin(_Id: longint; var _Header: TUnifiedTraceHeader): boolean;
var
  FDataSize: longint;
  NewPos: longint;
begin
  result := false;
  { Prepare Signal Header }

  if FpEntryHeader <> nil then
    FreeMem(FpEntryHeader, FpEntryHeader^.HSize);
  GetMem(FpEntryHeader, _Header.HSize);
  Move(_Header, FpEntryHeader^, _Header.HSize);

 { FEntryHeader.HZeroline := - FEntryHeader.HZeroline; } { XXX fixme !!! }
  FDataSize := FpEntryHeader^.HDataSize;

  { Prepare file header }

  NewPos := FNewHeader.DirCount + 1;
  if NewPos > FNewHeader.DirLimit then
    exit;

  FDirectory[NewPos].thisStatus := 0;
  FDirectory[NewPos].thisID := _Id;
  if NewPos < FNewHeader.DirLimit then
    FDirectory[NewPos+1].thisOffset := FDirectory[NewPos].thisOffset + FpEntryHeader^.HSize + FDataSize;
  FNewHeader.DirCount := NewPos;
  Seek(FileDat, 4);
  BlockWrite(FileDat, FNewHeader, FNewHeader.HSize);
  BlockWrite(FileDat, FDirectory, SizeOf(TDirEntry)*FNewHeader.DirLimit);

  { Write Signal Header }

  Seek(FileDat, FDirectory[NewPos].thisOffset);
  BlockWrite(FileDat, FpEntryHeader^, FpEntryHeader^.HSize);

  DataStartPos := FDirectory[NewPos].thisOffset + FpEntryHeader^.HSize;
  result := true;
end;


procedure TResWriter.ResWriteData(var Buff; Cnt, Base: longint);
begin
  Seek(FileDat, DataStartPos + Base * DataPointSize(FpEntryHeader^.HType));
  BlockWrite(FileDat, Buff, Cnt * DataPointSize(FpEntryHeader^.HType));
 { writeln(DataStartPos + Base * DataPointSize(FEntryHeader.HType) + Cnt * DataPointSize(FEntryHeader.HType)); }
end;


procedure TResWriter.ResClose;
begin
  if FpEntryHeader <> nil then
    FreeMem(FpEntryHeader, FpEntryHeader^.HSize);
  FpEntryHeader := nil;
  Close(FileDat);
end;


end.
