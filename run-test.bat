@echo off
REM 一键测试脚本 - Windows

echo ==========================================
echo   Java HTTP 文件上传 - 一键测试
echo ==========================================
echo.

REM 检查文件是否已编译
if not exist SimpleUploadServer.class (
    echo 文件未编译，开始编译...
    call build.bat
    echo.
)

if not exist QuickTest.class (
    echo 文件未编译，开始编译...
    call build.bat
    echo.
)

REM 启动测试服务器（后台运行）
echo 正在启动测试服务器...
start /B java SimpleUploadServer > server.log 2>&1

REM 等待服务器启动
timeout /t 3 /nobreak >nul

echo ✓ 测试服务器已启动
echo.

REM 运行测试
echo 开始运行测试...
echo.
echo. | java QuickTest

REM 停止服务器
echo.
echo 正在停止测试服务器...
taskkill /F /IM java.exe /FI "WINDOWTITLE eq SimpleUploadServer*" >nul 2>&1

echo ✓ 测试完成
echo.
echo ==========================================
pause
