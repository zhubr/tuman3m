%dcc32path% -$C create.pas
.\create.exe
%dcc32path% -U..\reswrite -U..\..\common t3imp.dpr
.\t3imp.exe example.t3i 20062936
