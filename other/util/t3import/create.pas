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
const
  arr_size = 200;
var
  arr: packed array [1.. arr_size] of single;

procedure Work;
var
  i: integer;
  t: text;
  f: file;
begin
  for i := 1 to arr_size do
    arr[i] := 100.0 * sin(10.0*pi*i/arr_size);

  assign(t, '20062936_1.txt'); rewrite(t);
  for i := 1 to arr_size do
    writeln(t, arr[i]:7:1);
  close(t);
  
  assign(f, '20062936_2.bin'); rewrite(f, 1);
  BlockWrite(f, arr, SizeOf(arr));
  close(f);

end;

begin
  Work;
end.
