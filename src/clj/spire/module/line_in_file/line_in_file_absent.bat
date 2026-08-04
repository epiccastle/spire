@echo off
setlocal enabledelayedexpansion

REM line_in_file_absent.bat
REM cmd.exe version of the line-in-file absent script.

if not exist "%FILE%" (
  echo File not found. 1>&2
  exit 1
)

REM Get line count
for /f "delims=" %%a in ('find /c /v "" ^< "%FILE%"') do set "FINDOUT=%%a"
for /f "tokens=2 delims=:" %%a in ("!FINDOUT!") do set "LINECOUNT=%%a"
set "LINECOUNT=!LINECOUNT: =!"

set "TMPFILE=%TEMP%\spire_lif_a_%RANDOM%"

REM :absent by linenum
if defined LINENUM (
  set "LINENUM_VAL=!LINENUM!"
  if "!LINENUM:~0,1!"=="-" set /a "LINENUM_VAL=LINECOUNT + LINENUM + 1"
  if !LINENUM_VAL! lss 1 (
    echo No line number !LINENUM! in file. 1>&2
    exit 2
  )
  if !LINENUM_VAL! gtr !LINECOUNT! (
    echo No line number !LINENUM! in file. 1>&2
    exit 2
  )
  set "COUNT=0"
  for /f "delims= eol=*" %%a in ('find /n /v "" ^< "%FILE%"') do (
    set /a COUNT+=1
    if !COUNT! neq !LINENUM_VAL! (
      set "RAW=%%a"
      set "CONTENT=!RAW:*]=!"
      if "!CONTENT!"=="" (
        >> "%TMPFILE%" echo.
      ) else (
        >> "%TMPFILE%" echo !CONTENT!
      )
    )
  )
  move /y "%TMPFILE%" "%FILE%" >nul
  exit 255
)

REM :absent by regexp or string-match or line-match
set "MATCH_CMD="
if defined REGEX (
  set "MATCH_CMD=findstr /n /r /c:"!REGEX!" "%FILE%""
) else if defined STRING_MATCH (
  set "MATCH_CMD=findstr /n /c:"!STRING_MATCH!" "%FILE%""
) else if defined LINE_MATCH (
  set "MATCH_CMD=findstr /n /x /c:"!LINE_MATCH!" "%FILE%""
)

if defined MATCH_CMD (
  REM Find the first matching line number
  set "FIRST_MATCH=0"
  set "DONE=0"
  for /f "tokens=1 delims=:" %%a in ('!MATCH_CMD! 2^>nul') do (
    if !DONE! equ 0 (
      set "FIRST_MATCH=%%a"
      set "DONE=1"
    )
  )

  if !FIRST_MATCH! equ 0 exit 0

  set "COUNT=0"
  for /f "delims= eol=*" %%a in ('find /n /v "" ^< "%FILE%"') do (
    set /a COUNT+=1
    if !COUNT! neq !FIRST_MATCH! (
      set "RAW=%%a"
      set "CONTENT=!RAW:*]=!"
      if "!CONTENT!"=="" (
        >> "%TMPFILE%" echo.
      ) else (
        >> "%TMPFILE%" echo !CONTENT!
      )
    )
  )
  move /y "%TMPFILE%" "%FILE%" >nul
  exit 255
)
