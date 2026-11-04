@echo off
chcp 936 >nul
setlocal EnableDelayedExpansion
rem python stdout must follow the console code page (936)
set "PYTHONIOENCODING=gbk"
cd /d "%~dp0"

rem 附属版本号统一修改。四处必须一致：
rem   build.gradle 的 ext.addon_version / MakeNTMGreatAgain.VERSION /
rem   src/addon/resources/mcmod.info / src/main/resources/mcmod.info 第 2 条
rem 只改附带版本；本体版本用 tools\bump_version.py --hbm <版本> --build <号>

set "PY=python"
where python >nul 2>&1
if errorlevel 1 (
  where py >nul 2>&1
  if errorlevel 1 (
    echo [error] Python 3 not found in PATH.
    echo         Install Python 3 or run: py -3 tools\bump_version.py
    goto :END
  )
  set "PY=py -3"
)

set "NEWVER=%~1"

if "%NEWVER%"=="" (
  %PY% tools\bump_version.py
  echo.
  echo ------------------------------------------------------------
  set /p NEWVER=  New addon version ^(empty = just show, no change^):
)

if not "%NEWVER%"=="" (
  echo.
  echo ------------------------------------------------------------
  %PY% tools\bump_version.py "%NEWVER%"
  echo ------------------------------------------------------------
  echo Remember: rebuild so build\resources picks it up ^(run the pack script
  echo or: gradlew.bat build^).
)

:END
echo.
echo Press any key to close...
pause >nul
exit /b 0
