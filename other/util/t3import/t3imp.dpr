(*
 * Copyright 2020-2021 Nikolai Zhubr <zhubr@mail.ru>
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
program t3imp;
{$APPTYPE CONSOLE }

uses
  SysUtils, Classes, NetTypes, genwrtr, ResWrite, ng2write;


procedure SplitCommas(src: string; dst: TStrings);
var
  i0, i1: integer;
  in_escape: boolean;
  st1: string;
begin
  dst.Clear;

  in_escape := false;
  i0 := 1;
  i1 := 1;
  while i1 <= length(src) do
  begin
    if src[i1] = '"' then
      in_escape := not in_escape
    else if not in_escape and (src[i1] = ',') then
      begin
        st1 := trim(copy(src, i0, i1-i0));
        if st1 <> '' then
          dst.Add(st1);
        i0 := i1 + 1;
      end;
    inc(i1);
  end;
  if in_escape then
    raise Exception.Create('Template error: unmatched " symbol');
  if i1 > i0 then
      begin
        st1 := trim(copy(src, i0, i1-i0));
        if st1 <> '' then
          dst.Add(st1);
      end;
end;


procedure SplitNameVal(src, sep: string; var the_name, the_val: string);
var
  j: integer;
begin
  j := pos(sep, src);
  if (j < 2) then
    raise Exception.Create('Template error: parameter ' + sep + ' symbol missing');
  the_name := LowerCase(trim(copy(src, 1, j-1)));
  the_val := LowerCase(trim(copy(src, j+1, length(src))));
end;


type
  TOneSignalInfo = record
    Rid: longint;
    RFilename: string[255];
    RFileIsBin: boolean;
    RHeader: TUnifiedTraceHeader;
  end;
  POneSignalInfo = ^TOneSignalInfo;


procedure DoWork(template_fname, shotnum: string; new_format: boolean);
var
  template: text;
  sig_list: TList;
  st: string;
  i, j: integer;
  temp_params: TStringList;
  temp_name, temp_val, temp_ftype, temp_fname, sig_fname: string;
  got_id, got_tact, got_type, got_data: boolean;
  p: POneSignalInfo;
  MyResWriter: PTumDbWriter; // TResWriter; // YYY
  fname, tmp_errors: string;
  sig_bin: file;
  sig_txt: text;
  byte_count: longint;
  temp_buff: packed array of single;
  temp_buff2: packed array of byte;
begin
  sig_list := TList.Create;
  temp_params := TStringList.Create;
  assign(template, template_fname); reset(template);
  while not EOF(template) do
  begin
    readln(template, st);
    st := trim(st);
    if length(st) > 0 then
      if st[1] <> ';' then
      begin
        SplitCommas(st, temp_params);
        got_id := false;
        got_tact := false;
        got_type := false;
        got_data := false;
        new(p);
        FillChar(p.RHeader, SizeOf(p.RHeader), 0);
        p.RHeader.HComment1 := 'Created by t3imp';
        for i := 0 to temp_params.Count - 1 do
        begin
          SplitNameVal(temp_params[i], '=', temp_name, temp_val);
          if temp_name = 'id' then
            begin
              got_id := true;
              p.Rid := StrToInt(temp_val);
            end
          else if temp_name = 'tact' then
            begin
              got_tact := true;
              p.RHeader.HTact := StrToFloat(temp_val);
            end
          else if temp_name = 'datastart' then
            begin
              p.RHeader.HDataStart := StrToFloat(temp_val);
              p.RHeader.HDataStartExternal := p.RHeader.HDataStart;
            end
          else if temp_name = 'calibr' then
            begin
              p.RHeader.HCalibr := StrToFloat(temp_val);
            end
          else if temp_name = 'zeroline' then
            begin
              p.RHeader.HZeroline := StrToFloat(temp_val);
            end
          else if temp_name = 'type' then
            begin
              got_type := true;

              //DTYPE_SmallInt = 50; { 16-bit, signed integer }
              //DTYPE_Single   = 51; { 32-bit, floating point }
              //DTYPE_LongInt  = 52; { 32-bit, signed integer }
              //DTYPE_Byte     = 55; { 8-bit, unsigned integer }

              if temp_val = 'single' then
                p.RHeader.HType := DTYPE_Single
              else if temp_val = 'smallint' then
                p.RHeader.HType := DTYPE_SmallInt
              else
                raise Exception.Create('Template error: unsupported type ' + temp_val);
            end
          else if temp_name = 'data' then
            begin
              got_data := true;
              // data=bin:"$_2.bin"
              SplitNameVal(temp_val, ':', temp_ftype, temp_fname);
              if temp_ftype = 'txt' then
                p.RFileIsBin := false
              else if temp_ftype = 'bin' then
                p.RFileIsBin := true
              else
                raise Exception.Create('Template error: unsupported format type ' + temp_ftype);
              temp_fname := trim(temp_fname);
              if temp_fname = '' then
                raise Exception.Create('Template error: file name is missing for a signal');
              if (temp_fname[1] <> '"') or (temp_fname[length(temp_fname)] <> '"') then
                raise Exception.Create('Template error: file name must be quoted');
              p.RFilename := copy(temp_fname, 2, length(temp_fname) - 2);
            end;
        end;
        if got_id and got_tact and got_type and got_data then
          sig_list.Add(p)
        else
          raise Exception.Create('Template error: required parameter(s) missing for a signal');
      end;
  end;
  close(template);

  fname := shotnum;
  if new_format then
    begin
      MyResWriter := new(PNg2Writer, Init); // YYY
      MyResWriter.AutoCreateMaster;
    end
  else
    begin
      MyResWriter := new(PResWriter, Init); // MyResWriter.Init; // YYY
      fname := fname + '.t3m';
    end;
  if not MyResWriter.ResOpen(fname) then
    raise Exception.Create('Could not open file "'+fname+'" for writing.');

  tmp_errors := '';
  for i := 0 to sig_list.Count - 1 do
    with POneSignalInfo(sig_list[i])^ do
  begin
    sig_fname := StringReplace(RFilename, '$', shotnum, [rfReplaceAll]);
    if RFileIsBin then
      begin
        assign(sig_bin, sig_fname); reset(sig_bin, 1);
        byte_count := FileSize(sig_bin);
        RHeader.HDataSize := byte_count; // YYY
        if RHeader.HType = DTYPE_Single then
          RHeader.HCount := byte_count shr 2
        else if RHeader.HType = DTYPE_SmallInt then
          RHeader.HCount := byte_count shr 1
        else
          raise Exception.Create('Internal error: unsupported data type ' + IntToStr(RHeader.HType));
        SetLength(temp_buff2, { RHeader.HCount } byte_count);
        BlockRead(sig_bin, temp_buff2[0], { RHeader.HCount * 4 } RHeader.HDataSize);
        close(sig_bin);
        IOResult;
      end
    else
      begin
        assign(sig_txt, sig_fname); reset(sig_txt);
        RHeader.HCount := 0;
        while not EOF(sig_txt) do
        begin
          readln(sig_txt, temp_val);
          temp_val := trim(temp_val);
          if temp_val <> '' then
            inc(RHeader.HCount);
        end;
        SetLength(temp_buff, RHeader.HCount);

        reset(sig_txt);
        for j := 1 to RHeader.HCount do
        begin
          readln(sig_txt, temp_val);
          temp_val := trim(temp_val);
          if temp_val <> '' then
          begin
            if RHeader.HType = DTYPE_Single then
              temp_buff[j-1] := StrToFloat(temp_val)
            else
              raise Exception.Create('Internal error: unsupported data type ' + IntToStr(RHeader.HType));
          end;
        end;
        close(sig_txt);
        IOResult;
      end;

    RHeader.HSize := SizeOf(RHeader);
    RHeader.HDataSize := DataPointSize(RHeader.HType) * RHeader.HCount;

    if not MyResWriter.ResBegin(Rid, @RHeader) then
      tmp_errors := tmp_errors + 'Error in ResBegin(a) for id ' + IntToStr(Rid) + #13#10;
    if RFileIsBin then
      MyResWriter.ResWriteData(temp_buff2[0], RHeader.HCount, 0)
    else
      MyResWriter.ResWriteData(temp_buff[0], RHeader.HCount, 0);
  end;

  MyResWriter.ResClose;
  if length(tmp_errors) > 0 then
    writeln('Save error(s): ', tmp_errors)
  else
    writeln('Wrote ' + fname + ' successfully.');

  temp_params.Free;
  sig_list.Free;
  dispose(MyResWriter); // YYY
end;


begin
{$IFDEF VER150 }
  DecimalSeparator := '.';
{$ELSE }
  FormatSettings.DecimalSeparator := '.';
{$ENDIF }
  if (ParamStr(2) = '') or ((ParamStr(3) <> '') and (ParamStr(3) <> 'legacy') ) then
  begin
    writeln('usage: t3imp <template.t3i> <shotnumber> [legacy]');
    exit;
  end;
  DoWork(ParamStr(1), ParamStr(2), LowerCase(trim(ParamStr(3))) <> 'legacy'); //readln;
end.
