@echo off
chcp 936 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "T0=%TIME%"
set "LOG=%~dp0build\pack.log"
if not exist "%~dp0build" mkdir "%~dp0build"

rem ---------------------------------------------------------------------------
rem Default = fastest mode:
rem   --offline          skip remote repos (avoid jitpack/cursemaven timeout waits)
rem   -x jar             skip re-packing the 54MB main HBM jar
rem   --console=plain    no progress bar refresh, less IO
rem
rem WHY THE GUARDS BELOW EXIST:
rem   `reobf` re-obfuscates BOTH jars in place (buildAddonJar + the main jar),
rem   and it always needs the main HBM jar as an *input* -- even when -x jar
rem   tells Gradle not to rebuild it. If build\libs holds no main jar
rem   (fresh clone, or after `gradlew clean`), reobf dies with
rem   "FileNotFoundException: ...\HBM-NTM-xxx.jar" AFTER buildAddonJar already
rem   wrote an un-obfuscated (MCP-named) addon jar -- that jar is unplayable.
rem   Fix: build the main jar instead of excluding it.
rem
rem   The retry keeps --offline on purpose: rebuilding the main jar only needs
rem   already-cached dependencies, and dropping --offline makes ForgeGradle
rem   audit every repo (jitpack / cursemaven / ntmr / gregtech) which stalls
rem   for many minutes when one of them is unreachable.
rem ---------------------------------------------------------------------------

set "TASKS=buildAddonJar reobf"

rem Exact main jar name, derived from gradle.properties (the same name the
rem `jar` task uses: HBM-NTM-[<mod_version>_X<build>.jar]).
rem NOTE: "for %%f in (wildcard)" runs once with the literal pattern when
rem nothing matches, so it must not be used as an existence test.
set "MV="
set "MB="
for /f "tokens=2 delims==" %%a in ('findstr /b /c:"mod_version=" gradle.properties') do set "MV=%%a"
for /f "tokens=2 delims==" %%a in ('findstr /b /c:"mod_build_number=" gradle.properties') do set "MB=%%a"
if defined MB (set "MAINJAR=build\libs\HBM-NTM-[%MV%_X%MB%].jar") else (set "MAINJAR=build\libs\HBM-NTM-%MV%.jar")

set "HAVE_MAIN="
if exist "%MAINJAR%" set "HAVE_MAIN=1"

set "SKIP_JAR=-x jar"
set "OFFLINE=--offline"
if not defined HAVE_MAIN (
  echo [warn] %MAINJAR% is missing -^> it will be rebuilt ^(slower^)
  echo        ^(on a fresh clone with an empty cache run: "%~nx0" net first^)
  set "SKIP_JAR="
)

if /i "%1"=="full" (
  echo [mode] FULL: addon jar + main HBM jar
  set "GRADLE_ARGS=--offline --console=plain %2 %3 %4"
) else if /i "%1"=="net" (
  echo [mode] ONLINE: use when adding a new dependency or first build
  set "GRADLE_ARGS=-x jar --console=plain %2 %3 %4"
) else (
  echo [mode] FASTEST: addon jar only, skip main jar and remote checks
  set "GRADLE_ARGS=%SKIP_JAR% %OFFLINE% --console=plain %2 %3 %4"
)

echo.
echo tasks: %TASKS%  args: %GRADLE_ARGS%
echo.

echo ===== pack start %DATE% %TIME% =====>"%LOG%"
call gradlew.bat %TASKS% %GRADLE_ARGS% >>"%LOG%" 2>&1

rem Safety net for a stale build\libs (an old main jar with a different version
rem name still sitting there): reobf fails, so build the main jar and try again.
if errorlevel 1 (
  findstr /c:"FileNotFoundException" "%LOG%" >nul
  if not errorlevel 1 (
    echo.
    echo [retry] reobf needs the main jar - rebuilding it...
    echo ===== retry with main jar %DATE% %TIME% =====>>"%LOG%"
    call gradlew.bat %TASKS% --offline --console=plain %2 %3 %4 >>"%LOG%" 2>&1
  )
)
echo ===== pack end   %DATE% %TIME% =====>>"%LOG%"

if errorlevel 1 goto :FAILED

echo.
echo start %T0%    end %TIME%
if exist "build\libs\MakeNTMGreatAgain-*.jar" (
  for %%f in ("build\libs\MakeNTMGreatAgain-*.jar") do echo output: %%f   [%%~zf bytes]
) else (
  echo output: NOT FOUND - check build\pack.log
)
echo main:   %MAINJAR%
echo log:    build\pack.log
echo (need the main HBM jar too? run: "%~nx0" full)
goto :END

:FAILED
echo.
echo *** BUILD FAILED ***
echo.
echo If the failure is inside :reobf, the addon jar left in build\libs probably
echo still holds MCP names (no func_xxx renaming) and WILL crash in a real
echo modpack. Do not ship it - fix the error and re-run this script.
echo.
echo ---- last 40 lines of build\pack.log ----
powershell -NoProfile -Command "Get-Content -LiteralPath '%LOG%' -Tail 40"
echo.
echo full log: build\pack.log

:END
echo.
echo Press any key to close...
pause >nul
exit /b 0
