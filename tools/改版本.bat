@echo off
chcp 936 >nul
setlocal EnableDelayedExpansion
rem python stdout must follow the console code page (936)
set "PYTHONIOENCODING=gbk"

rem --- resolve project root (this script may sit in the root or in tools\) ---
cd /d "%~dp0"
if not exist "gradlew.bat" if exist "..\gradlew.bat" cd ..
if not exist "gradlew.bat" (
  echo [error] gradlew.bat not found in "%~dp0" or its parent.
  echo         Put this script in the project root or in tools\.
  pause >nul
  exit /b 1
)
set "ROOT=%CD%"

rem 附属版本号统一修改。四处必须一致：
rem   build.gradle 的 ext.addon_version / MakeNTMGreatAgain.VERSION /
rem   src/addon/resources/mcmod.info / src/main/resources/mcmod.info 第 2 条
rem 只改附带版本；本体版本用 tools\bump_version.py --hbm <版本> --build <号>

set "BV=%ROOT%\tools\bump_version.py"
if not exist "%BV%" set "BV=%~dp0bump_version.py"
if not exist "%BV%" (
  echo [error] bump_version.py not found
  echo         looked in: %ROOT%\tools
  echo                    %~dp0
  pause >nul
  exit /b 1
)

rem --- find a WORKING python ----------------------------------------------
rem The python.exe inside WindowsApps is a 0-byte Store stub: `where python`
rem finds it, but executing it prints "the system cannot find the file".
rem So every candidate must actually run, not just exist on PATH.
set "PY="
py -3 -c "import sys" >nul 2>&1
if not errorlevel 1 set "PY=py -3"
if not defined PY (
  python -c "import sys" >nul 2>&1
  if not errorlevel 1 set "PY=python"
)
if not defined PY (
  python3 -c "import sys" >nul 2>&1
  if not errorlevel 1 set "PY=python3"
)
if not defined PY (
  for %%P in (
    "%LOCALAPPDATA%\Programs\Python\Python313\python.exe"
    "%LOCALAPPDATA%\Programs\Python\Python312\python.exe"
    "%LOCALAPPDATA%\Programs\Python\Python311\python.exe"
    "%LOCALAPPDATA%\Programs\Python\Python310\python.exe"
    "%ProgramFiles%\Python313\python.exe"
    "%ProgramFiles%\Python312\python.exe"
    "C:\Python313\python.exe"
    "C:\Python312\python.exe"
  ) do (
    if not defined PY if exist %%P (
      %%P -c "import sys" >nul 2>&1
      if not errorlevel 1 set "PY=%%P"
    )
  )
)
if not defined PY (
  for /d %%D in ("%USERPROFILE%\.workbuddy\binaries\python\versions\*") do (
    if not defined PY if exist "%%D\python.exe" set "PY=%%D\python.exe"
  )
)
if not defined PY (
  echo [error] No working Python 3 found.
  echo         Note: the python.exe in WindowsApps is a Store stub and cannot run.
  echo         Install Python 3 from python.org and tick "Add python.exe to PATH",
  echo         or use a terminal where "py -3 --version" works.
  pause >nul
  exit /b 1
)

rem pass-through for python-style flags:/r/nrem   改版本.bat --hbm 1.0.28 --build 5900
set "A1=%~1"
if defined A1 if "!A1:~0,1!"=="-" (
  %PY% "%BV%" %*
  set "RC=!errorlevel!"
  echo.
  echo Press any key to close...
  pause >nul
  exit /b !RC!
)

set "NEWVER=%~1"

if "%NEWVER%"=="" (
  %PY% "%BV%"
  echo.
  echo ------------------------------------------------------------
  set /p NEWVER=  New addon version ^(empty = just show, no change^):
)

if not "%NEWVER%"=="" (
  echo.
  echo ------------------------------------------------------------
  %PY% "%BV%" "%NEWVER%"
  if errorlevel 1 (
    echo.
    echo [error] version update failed - nothing was guaranteed to change.
    pause >nul
    exit /b 1
  )
  echo ------------------------------------------------------------
  echo Remember: rebuild so build\resources picks it up ^(run the pack script
  echo or: gradlew.bat build^).
)

echo.
echo Press any key to close...
pause >nul
exit /b 0
