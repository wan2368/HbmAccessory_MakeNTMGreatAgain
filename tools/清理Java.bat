@echo off
chcp 936 >nul
setlocal

rem 清掉卡住的 Gradle daemon 和残留的 Minecraft 进程，不动 IDEA 本身。
rem 只杀命令行里带 GradleDaemon / launchwrapper / net.minecraft 的 java.exe，
rem 不会误杀其他 Java 程序。

cd /d "%~dp0"
if not exist "gradlew.bat" if exist "..\gradlew.bat" cd ..

echo.
echo 扫描 java 进程 ...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$t = @(Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { $_.CommandLine -match 'GradleDaemon|launchwrapper|fml.coreMods|gradle-launcher|net.minecraft' }); if ($t.Count -eq 0) { Write-Host '  没有发现 Gradle / Minecraft 进程' } else { foreach ($x in $t) { Write-Host ('  结束 PID ' + $x.ProcessId + '  ' + $x.Name); Stop-Process -Id $x.ProcessId -Force -ErrorAction SilentlyContinue } }; $left = @(Get-Process java,javaw -ErrorAction SilentlyContinue).Count; Write-Host ''; Write-Host ('剩余 java 进程: ' + $left)"

echo.
echo 完成。回 IDEA 重新点 Run Client 即可。
echo.
echo 按任意键关闭...
pause >nul
exit /b 0
