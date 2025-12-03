(*
 * Copyright 2006-2021 Nikolai Zhubr <zhubr@rambler.ru>
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
unit genwrtr;

interface

uses
  NetTypes;

type
  TTumDbWriter = object
    function ResOpen(_FName: string): boolean; virtual; abstract;
    function  ResBegin(_Id: longint; _pHeader: PUnifiedTraceHeader): boolean; virtual; abstract;
    procedure ResWriteData(var Buff; Cnt, Base: longint); virtual; abstract;
    procedure ResClose; virtual; abstract;
    procedure AutoCreateMaster; virtual;
  end;
  PTumDbWriter = ^TTumDbWriter;

implementation

procedure TTumDbWriter.AutoCreateMaster;
begin
end;

end.


