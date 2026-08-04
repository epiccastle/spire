@echo off
setlocal enabledelayedexpansion

REM line_in_file_present.bat
REM cmd.exe version of the line-in-file present script.

if not exist "%FILE%" (
  echo File not found. 1>&2
  exit 1
)

REM Get line count
for /f "delims=" %%a in ('find /c /v "" ^< "%FILE%"') do set "FINDOUT=%%a"
for /f "tokens=2 delims=:" %%a in ("!FINDOUT!") do set "LINECOUNT=%%a"
set "LINECOUNT=!LINECOUNT: =!"

set "TMPFILE=%TEMP%\spire_lif_p_%RANDOM%"
set "EXIT=0"

REM :present by linenum
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
    set "RAW=%%a"
    set "CONTENT=!RAW:*]=!"
    if !COUNT! equ !LINENUM_VAL! (
      if "!CONTENT!"=="!LINE!" (
        set "OUTPUT=!CONTENT!"
      ) else (
        set "OUTPUT=!LINE!"
        set "EXIT=255"
      )
    ) else (
      set "OUTPUT=!CONTENT!"
    )
    if "!OUTPUT!"=="" (
      >> "%TMPFILE%" echo.
    ) else (
      >> "%TMPFILE%" echo !OUTPUT!
    )
  )
  move /y "%TMPFILE%" "%FILE%" >nul
  exit !EXIT!
)

REM :present by regexp or string-match or line-match
set "MATCH_CMD="
if defined REGEX (
  set "MATCH_CMD=findstr /n /r /c:"!REGEX!" "%FILE%""
) else if defined STRING_MATCH (
  set "MATCH_CMD=findstr / n /c:"!STRING_MATCH!" "%FILE%""
) else if defined LINE_MATCH (
  set "MATCH_CMD=findstr / n /x /c:"!LINE_MATCH!" "%FILE%""
)

if defined MATCH_CMD (
  REM Collect matching line numbers
  set "MATCHES_TMP=%TEMP%\spire_lif_m_%RANDOM%"
  !MATCH_CMD! > "%MATCHES_TMP%" 2>nul
  for /f %%a in ('type "%MATCHES_TMP%" ^| find / c / v ""') do set "MATCH_COUNT=%%a"

  if !MATCH_COUNT! gtr 0 (
    REM Apply selector and mark lines for replacement
    if "!SELECTOR!"=="first" (
      set "DONE=0"
      for /f "tokens=1 delims=:" %%a in ('type "%MATCHES_TMP%"') do (
        if !DONE! equ 0 (
          set "REPLACE_%%a=1"
          set "DONE=1"
        )
      )
    ) else if "!SELECTOR!"=="last" (
      set "LAST_MATCH=0"
      for /f "tokens=1 delims=:" %%a in ('type "%MATCHES_TMP%"') do set "LAST_MATCH=%%a"
      if !LAST_MATCH! gtr 0 set "REPLACE_!LAST_MATCH!=1"
    ) else (
      for /f "tokens=1 delims=:" %%a in ('type "%MATCHES_TMP%"') do set "REPLACE_%%a=1"
    )

    REM Process file, replacing marked lines
    set "COUNT=0"
    for /f "delims= eol=*" %%a in ('find / n /v "" ^< "%FILE%"') do (
      set /a COUNT+=1
      set "RAW=%%a"
      set "CONTENT=!RAW:*]=!"
      call set "DO_REPLACE=%%REPLACE_!COUNT!%%"
      if "!DO_REPLACE!"=="1" (
        if not "!CONTENT!"=="!LINE!" (
          set "OUTPUT=!LINE!"
          set "EXIT=255"
        ) else (
          set "OUTPUT=!CONTENT!"
        )
      ) else (
        set "OUTPUT=!CONTENT!"
      )
      if "!OUTPUT!"=="" (
        >> "%TMPFILE%" echo.
      ) else (
        >> "%TMPFILE%" echo !OUTPUT!
      )
    )
    del "%MATCHES_TMP%" >nul 2>&1
    move /y "%TMPFILE%" "%FILE%" >nul
    exit !EXIT!
  )

  del "%MATCHES_TMP%" >nul 2>&1

  REM No match found — check AFTER / BEFORE / INSERTAT
  if defined AFTER goto :handle_after
  if defined BEFORE goto :handle_before
  goto :handle_insertat

  :handle_after
  set "AFTER_TMP=%TEMP%\spire_lif_af_%RANDOM%"
  findstr / n / r /c:"!AFTER!" "%FILE%" > "%AFTER_TMP%" 2>nul
  for /f %%a in ('type "%AFTER_TMP%" ^| find / c / v ""') do set "AFTER_COUNT=%%a"
  if !AFTER_COUNT! gtr 0 (
    if "!SELECTOR!"=="first" (
      set "DONE=0"
      for /f "tokens=1 delims=:" %%a in ('type "%AFTER_TMP%"') do (
        if !DONE! equ 0 ( set "INSERT_AFTER_%%a=1" & set "DONE=1" )
      )
    ) else if "!SELECTOR!"=="last" (
      set "LAST_AF=0"
      for /f "tokens=1 delims=:" %%a in ('type "%AFTER_TMP%"') do set "LAST_AF=%%a"
      if !LAST_AF! gtr 0 set "INSERT_AFTER_!LAST_AF!=1"
    ) else (
      for /f "tokens=1 delims=:" %%a in ('type "%AFTER_TMP%"') do set "INSERT_AFTER_%%a=1"
    )
  )
  del "%AFTER_TMP%" >nul 2>&1
  set "COUNT=0"
  set "PREV_AFTER_MATCH=0"
  for /f "delims= eol=*" %%a in ('find / n /v "" ^< "%FILE%"') do (
    set /a COUNT+=1
    set "RAW=%%a"
    set "CONTENT=!RAW:*]=!"
    if !PREV_AFTER_MATCH! gtr 0 (
      if not "!CONTENT!"=="!LINE!" (
        set "OUTPUT=!LINE!"
        if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
        set "EXIT=255"
      )
      set "PREV_AFTER_MATCH=0"
    )
    set "OUTPUT=!CONTENT!"
    if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
    call set "DO_INSERT=%%INSERT_AFTER_!COUNT!%%"
    if "!DO_INSERT!"=="1" set "PREV_AFTER_MATCH=!COUNT!"
  )
  if !PREV_AFTER_MATCH! gtr 0 (
    set "OUTPUT=!LINE!"
    if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
    set "EXIT=255"
  )
  move /y "%TMPFILE%" "%FILE%" >nul
  exit !EXIT!

  :handle_before
  set "BEFORE_TMP=%TEMP%\spire_lif_bf_%RANDOM%"
  findstr / n / r /c:"!BEFORE!" "%FILE%" > "%BEFORE_TMP%" 2>nul
  for /f %%a in ('type "%BEFORE_TMP%" ^| find / c / v ""') do set "BEFORE_COUNT=%%a"
  if !BEFORE_COUNT! gtr 0 (
    if "!SELECTOR!"=="first" (
      set "DONE=0"
      for /f "tokens=1 delims=:" %%a in ('type "%BEFORE_TMP%"') do (
        if !DONE! equ 0 ( set "INSERT_BEFORE_%%a=1" & set "DONE=1" )
      )
    ) else if "!SELECTOR!"=="last" (
      set "LAST_BF=0"
      for /f "tokens=1 delims=:" %%a in ('type "%BEFORE_TMP%"') do set "LAST_BF=%%a"
      if !LAST_BF! gtr 0 set "INSERT_BEFORE_!LAST_BF!=1"
    ) else (
      for /f "tokens=1 delims=:" %%a in ('type "%BEFORE_TMP%"') do set "INSERT_BEFORE_%%a=1"
    )
  )
  del "%BEFORE_TMP%" >nul 2>&1
  set "COUNT=0"
  set "PREV_CONTENT="
  for /f "delims= eol=*" %%a in ('find / n /v "" ^< "%FILE%"') do (
    set /a COUNT+=1
    set "RAW=%%a"
    set "CONTENT=!RAW:*]=!"
    call set "DO_INSERT=%%INSERT_BEFORE_!COUNT!%%"
    if "!DO_INSERT!"=="1" (
      if not "!PREV_CONTENT!"=="!LINE!" (
        set "OUTPUT=!LINE!"
        if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
        set "EXIT=255"
      )
    )
    set "OUTPUT=!CONTENT!"
    if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
    set "PREV_CONTENT=!CONTENT!"
  )
  move /y "%TMPFILE%" "%FILE%" >nul
  exit !EXIT!

  :handle_insertat
  if "!INSERTAT!"=="bof" (
    set "OUTPUT=!LINE!"
    if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
    for /f "delims= eol=*" %%a in ('find / n /v "" ^< "%FILE%"') do (
      set "RAW=%%a"
      set "CONTENT=!RAW:*]=!"
      set "OUTPUT=!CONTENT!"
      if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
    )
    move /y "%TMPFILE%" "%FILE%" >nul
    exit 255
  ) else (
    for /f "delims= eol=*" %%a in ('find / n /v "" ^< "%FILE%"') do (
      set "RAW=%%a"
      set "CONTENT=!RAW:*]=!"
      set "OUTPUT=!CONTENT!"
      if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
    )
    set "OUTPUT=!LINE!"
    if "!OUTPUT!"=="" ( >> "%TMPFILE%" echo. ) else ( >> "%TMPFILE%" echo !OUTPUT! )
    move /y "%TMPFILE%" "%FILE%" >nul
    exit 255
  )
)

echo script error 1>&2
exit 1
