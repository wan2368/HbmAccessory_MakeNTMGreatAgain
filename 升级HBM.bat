@echo off
chcp 936 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"

rem ===========================================================================
rem  把上游 HBM 源码换成新版本，不动附属代码和打包配置。
rem
rem  用法：把新 HBM 源码文件夹拖到本文件上，或者
rem        升级HBM.bat D:\HBM-new-1.0.28
rem        （文件夹里必须有 src\main\java\com\hbm）
rem
rem  会被覆盖（属于上游）：
rem    src\main\java\com\hbm
rem    src\main\java\api
rem    src\main\java\cofh
rem    src\main\resources\assets\hbm
rem    src\main\resources\assets\minecraft
rem    src\main\resources\META-INF\HBM_at.cfg
rem  注意 assets\hbm 下还有附属自己的手册页（manual\makentmgreatagain），
rem  robocopy /E 只覆盖不删除，所以它们不会被冲掉。
rem
rem  绝对不会被碰：
rem    build.gradle / gradle.properties / src\addon
rem    src\main\java\com\makentmgreatagain
rem    src\main\resources\assets\makentmgreatagain
rem    src\main\resources\mcmod.info
rem
rem  前提：工作区必须是干净的（脚本会拒绝执行），这样旧状态能用
rem        git checkout <commit> -- <路径> 找回。
rem ===========================================================================

if "%~1"=="" goto :USAGE
set "NEW=%~1"
if not exist "%NEW%\" goto :BADSRC
if not exist "%NEW%\src\main\java\com\hbm\" goto :BADSRC

git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
  echo [error] 这里不是 git 仓库，请先在项目根目录执行 git init 并提交一次。
  goto :END
)

set "DIRTY="
for /f "delims=" %%i in ('git status --porcelain') do set "DIRTY=1"
if defined DIRTY (
  echo [error] 工作区不干净，先提交或暂存，否则升级不可回退：
  echo.
  git status --short
  goto :END
)

for /f %%d in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmm"') do set "TS=%%d"
set "BRANCH=upgrade-%TS%"

echo [git] 新建分支 %BRANCH% ...
git checkout -b "%BRANCH%" -q
if errorlevel 1 goto :GITFAIL

echo [copy] %NEW%  -^>  %CD%
call :SYNC "%NEW%\src\main\java\com\hbm"                "src\main\java\com\hbm"
call :SYNC "%NEW%\src\main\java\api"                    "src\main\java\api"
call :SYNC "%NEW%\src\main\java\cofh"                   "src\main\java\cofh"
call :SYNC "%NEW%\src\main\resources\assets\hbm"        "src\main\resources\assets\hbm"
call :SYNC "%NEW%\src\main\resources\assets\minecraft"  "src\main\resources\assets\minecraft"
if exist "%NEW%\src\main\resources\META-INF\HBM_at.cfg" (
  copy /y "%NEW%\src\main\resources\META-INF\HBM_at.cfg" "src\main\resources\META-INF\HBM_at.cfg" >nul
  echo    HBM_at.cfg 已更新
)

echo.
echo [git] 本次改动：
git status --short

echo.
echo [check] 编译检查 ...
call gradlew.bat compileJava --offline --console=plain
if errorlevel 1 (
  echo.
  echo *** 编译失败 ***  上游改 API 的坑基本都在这一步暴露。
  echo 修完再看上游到底改了什么：
  echo     git diff HEAD -- src/main/java/com/hbm
  echo 另外注意「上游已删除、本地还留着」的类：它照样能编过，但行为是旧的，
  echo 需要对着新源码树人工核对一遍。
  goto :END
)

echo.
echo *** 编译通过 ***
echo 进游戏验证：模组列表版本号、海水/铅锌/硫代硫酸盐三条线、4 个手册页。
echo 确认无误后提交：git add -A ^&^& git commit -m "sync upstream ..."
goto :END

:SYNC
robocopy "%~1" "%~2" /E /NFL /NDL /NP /NJH /NJS >nul
if errorlevel 8 (
  echo [error] robocopy 失败：%~2
  exit /b 1
)
echo    已同步 %~2
exit /b 0

:USAGE
echo 用法:  %~nx0  ^<新 HBM 源码根目录^>
echo   例如: %~nx0  D:\HBM-new-1.0.28
echo   （该目录下必须有 src\main\java\com\hbm）
goto :END

:BADSRC
echo [error] "%NEW%" 不是 HBM 源码根目录
echo         期望存在：%NEW%\src\main\java\com\hbm
goto :END

:GITFAIL
echo [error] git checkout 失败

:END
echo.
echo 按任意键关闭...
pause >nul
exit /b 0
