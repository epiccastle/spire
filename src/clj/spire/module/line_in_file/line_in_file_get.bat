@echo off
setlocal enabledelayedexpansion

REM line_in_file_get.bat
REM cmd.exe version of the line-in-file get script.

if not exist "%FILE%" (
  echo File not found. 1>&2
  exit 1
)

REM Get line count
for /f "delims=" %%a in ('find /c /v "" ^< "%FILE%"') do set "FINDOUT=%%a"
for /f "tokens=2 delims=:" %%a in ("!FINDOUT!") do set "LINECOUNT=%%a"
set "LINECOUNT=!LINECOUNT: =!"

REM :get by linenum
if defined LINENUM (
  set "LINENUM_VAL=!LINENUM!"
  if "!LINENUM:~0,1!"=="-" (
    set /a "LINENUM_VAL=LINECOUNT + LINENUM + 1"
  )
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
    if !COUNT! equ !LINENUM_VAL! (
      set "RAW=%%a"
      set "CONTENT=!RAW:*]=!"
      echo !LINENUM_VAL!
      if "!CONTENT!"=="" (
        echo.
      ) else (
        echo !CONTENT!
      )
      exit 0
    )
  )
)

REM :get by regexp or string-match or line-match
set "MATCH_CMD="
if defined REGEX (
  set "MATCH_CMD=findstr /n /r /c:"!REGEX!" "%FILE%""
) else if defined STRING_MATCH (
  set "MATCH_CMD=findstr /n /c:"!STRING_MATCH!" "%FILE%""
) else if defined LINE_MATCH (
  set "MATCH_CMD=findstr /n /x /c:"!LINE_MATCH!" "%FILE%""
)

if defined MATCH_CMD (
  set "MATCHES_TMP=%TEMP%\spire_lif_g_%RANDOM%"
  !MATCH_CMD! > "%MATCHES_TMP%" 2>nul
  for /f %%a in ('type "%MATCHES_TMP%" ^| find /c /v ""') do set "MATCH_COUNT=%%a"

  if !MATCH_COUNT! equ 0 (
    del "%MATCHES_TMP%" >nul 2>&1
    <nul set /p =no match
    exit 0
  )

  if "!SELECTOR!"=="first" (
    set "DONE=0"
    for /f "tokens=1,* delims=:" %%a in ('type "%MATCHES_TMP%"') do (
      if !DONE! equ 0 (
        echo %%a
        if "%%b"=="" (
          echo.
        ) else (
          echo %%b
        )
        set "DONE=1"
      )
    )
  ) else if "!SELECTOR!"=="last" (
    set "LAST_NUM="
    set "LAST_CONTENT="
    for /f "tokens=1,* delims=:" %%a in ('type "%MATCHES_TMP%"') do (
      set "LAST_NUM=%%a"
      set "LAST_CONTENT=%%b"
    )
    echo !LAST_NUM!
    if "!LAST_CONTENT!"=="" (
      echo.
    ) else (
      echo !LAST_CONTENT!
    )
  ) else (
    REM all
    set "FIRST=1"
    for /f "tokens=1 delims=:" %%a in ('type "%MATCHES_TMP%"') do (
      if !FIRST! equ 1 (
        <nul set /p =%%a
        set "FIRST=0"
      ) else (
        <nul set /p = %%a
      )
    )
    echo.
    for /f "tokens=1,* delims=:" %%a in ('type "%MATCHES_TMP%"') do (
      if "%%b"=="" (
        echo.
      ) else (
        echo %%b
      )
    )
  )
  del "%MATCHES_TMP%" >nul 2>&1
  exit 0
)

echo script error 1>&2
exit 1
