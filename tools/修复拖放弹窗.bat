@echo off
chcp 936 >nul 2>&1
title 修复拖放弹窗 - HBM-Source

set "TARGET=F:\HBM-Source"

echo ============================================================
echo   修复「拖放文件时提示 Internet 安全设置可能有害」
echo ============================================================
echo.

if not exist "%TARGET%\" (
    echo [错误] 找不到目录：%TARGET%
    echo.
    pause
    exit /b 1
)

echo [1/3] 当前完整性级别：
echo ------------------------------------------------------------
icacls "%TARGET%" | findstr /i "Mandatory"
if errorlevel 1 echo     未检测到显式标签（等同 Medium，正常）
echo ------------------------------------------------------------
echo.

echo [2/3] 正在复位为 中度完整性 Medium ...
icacls "%TARGET%" /setintegritylevel "(OI)(CI)Medium" >nul 2>&1
if errorlevel 1 (
    echo [失败] 复位未成功。请确认该目录未被其他程序独占。
    echo        如仍失败，请右键以管理员身份重新运行。
    echo.
    pause
    exit /b 1
)
echo     完成。
echo.

echo [3/3] 复核结果：
echo ------------------------------------------------------------
icacls "%TARGET%" | findstr /i "Mandatory"
echo ------------------------------------------------------------
echo.

echo 提示：
echo   - 上面显示 Medium 即表示已修复。
echo   - 打开资源管理器，从 %TARGET% 拖一个文件到桌面验证。
echo   - 若仍弹窗，重启一次「Windows 资源管理器」再试。
echo.
pause
