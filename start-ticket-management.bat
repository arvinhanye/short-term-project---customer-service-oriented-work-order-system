@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set "APP_NAME=工单管理系统"
set "APP_VERSION=加分项增强版"
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%"
set "JAR_PATH=%PROJECT_DIR%target\ticket-management.jar"
set "ALWAYS_BUILD=1"
set "MYSQL_HOST=127.0.0.1"
set "MYSQL_PORT=3306"
set "MONGO_HOST=127.0.0.1"
set "MONGO_PORT=27017"

echo ========================================
echo   %APP_NAME%启动器（%APP_VERSION%）
echo ========================================
echo   + 连接池监控面板
echo   + MySQL READ/WRITE 读写分离连接池
echo   + 数据库连接故障自动重连
echo   + 操作审计日志：MongoDB + logs/audit.log
echo.
echo 项目目录：%PROJECT_DIR%
echo 启动文件：%JAR_PATH%
echo 更新策略：每次启动自动打包最新代码
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo 启动失败：未找到 Java，请先安装 JDK 17+ 并配置 PATH。
    goto pause_fail
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo 启动失败：未找到 Maven，请先安装 Maven 3.8+ 并配置 PATH。
    goto pause_fail
)

if not exist "%PROJECT_DIR%pom.xml" (
    echo 启动失败：未找到 pom.xml，请确认脚本放在 ticket-management 项目根目录。
    goto pause_fail
)

echo 正在打包最新代码（已跳过测试）...
pushd "%PROJECT_DIR%"
call mvn -DskipTests package
if errorlevel 1 (
    popd
    echo 启动失败：Maven 打包失败，请检查上方错误信息。
    goto pause_fail
)
popd

if not exist "%JAR_PATH%" (
    echo 启动失败：打包后仍未找到 JAR：%JAR_PATH%
    goto pause_fail
)

if "%TICKET_MYSQL_USERNAME%"=="" (
    echo 启动失败：请先设置环境变量 TICKET_MYSQL_USERNAME 和 TICKET_MYSQL_PASSWORD。
    echo 数据库账号必须是最小权限的专用账号，不能使用 root。
    goto pause_fail
)
if /I "%TICKET_MYSQL_USERNAME%"=="root" (
    echo 启动失败：应用禁止使用 MySQL root，请配置专用数据库账号。
    goto pause_fail
)
if "%TICKET_MYSQL_PASSWORD%"=="" (
    echo 启动失败：TICKET_MYSQL_PASSWORD 不能为空。
    goto pause_fail
)

echo 检查 MySQL：%MYSQL_HOST%:%MYSQL_PORT%
powershell -NoProfile -Command "$c=New-Object Net.Sockets.TcpClient; try { $r=$c.BeginConnect('%MYSQL_HOST%',%MYSQL_PORT%,$null,$null); if (-not $r.AsyncWaitHandle.WaitOne(1500,$false)) { exit 1 }; $c.EndConnect($r); exit 0 } catch { exit 1 } finally { $c.Close() }" >nul 2>nul
if errorlevel 1 (
    echo 启动失败：MySQL 未在 %MYSQL_HOST%:%MYSQL_PORT% 监听。请先启动 MySQL，并确认 src\main\resources\db.properties 中的配置正确。
    goto pause_fail
)

echo 检查 MongoDB：%MONGO_HOST%:%MONGO_PORT%
powershell -NoProfile -Command "$c=New-Object Net.Sockets.TcpClient; try { $r=$c.BeginConnect('%MONGO_HOST%',%MONGO_PORT%,$null,$null); if (-not $r.AsyncWaitHandle.WaitOne(1500,$false)) { exit 1 }; $c.EndConnect($r); exit 0 } catch { exit 1 } finally { $c.Close() }" >nul 2>nul
if errorlevel 1 (
    echo 启动失败：MongoDB 未在 %MONGO_HOST%:%MONGO_PORT% 监听。请先启动 MongoDB，并确认 src\main\resources\db.properties 中的配置正确。
    goto pause_fail
)

echo.
echo 环境检查通过，正在启动 %APP_NAME%...
echo.
pushd "%PROJECT_DIR%"
java -jar "%JAR_PATH%"
set "APP_CODE=%ERRORLEVEL%"
popd

echo.
if "%APP_CODE%"=="0" (
    echo %APP_NAME%已退出。
) else (
    echo %APP_NAME%异常退出，退出码：%APP_CODE%
)
pause
exit /b %APP_CODE%

:pause_fail
echo.
pause
exit /b 1
