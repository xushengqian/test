@echo off
REM Java HTTP 文件上传示例 - Windows 构建脚本

echo ==========================================
echo   编译 Java 文件上传示例项目
echo ==========================================

REM 检查 Java 版本
java -version

echo.
echo 开始编译...
echo.

REM 编译核心示例文件
set SUCCESS=0
set FAILED=0

echo 编译 HttpFormFileUploadExample.java ...
javac HttpFormFileUploadExample.java 2>nul
if %errorlevel% equ 0 (
    echo ✓ 成功
    set /a SUCCESS+=1
) else (
    echo ✗ 失败
    set /a FAILED+=1
)

echo 编译 HttpClientFileUploadExample.java ...
javac HttpClientFileUploadExample.java 2>nul
if %errorlevel% equ 0 (
    echo ✓ 成功
    set /a SUCCESS+=1
) else (
    echo ✗ 失败 ^(需要 Java 11+^)
    set /a FAILED+=1
)

echo 编译 FileUploadUtils.java ...
javac FileUploadUtils.java 2>nul
if %errorlevel% equ 0 (
    echo ✓ 成功
    set /a SUCCESS+=1
) else (
    echo ✗ 失败
    set /a FAILED+=1
)

echo 编译 SimpleUploadServer.java ...
javac SimpleUploadServer.java 2>nul
if %errorlevel% equ 0 (
    echo ✓ 成功
    set /a SUCCESS+=1
) else (
    echo ✗ 失败
    set /a FAILED+=1
)

echo 编译 QuickTest.java ...
javac QuickTest.java 2>nul
if %errorlevel% equ 0 (
    echo ✓ 成功
    set /a SUCCESS+=1
) else (
    echo ✗ 失败
    set /a FAILED+=1
)

echo.
echo ==========================================
echo   编译完成
echo ==========================================
echo 成功: %SUCCESS% 个文件
echo 失败: %FAILED% 个文件
echo.

if %FAILED% equ 0 (
    echo ✓ 所有文件编译成功！
    echo.
    echo 快速使用:
    echo   1. 启动测试服务器: java SimpleUploadServer
    echo   2. 运行快速测试: java QuickTest
    echo.
) else (
    echo ⚠ 部分文件编译失败
    echo 注意: HttpClientFileUploadExample 需要 Java 11+
    echo.
)

echo ==========================================
pause
