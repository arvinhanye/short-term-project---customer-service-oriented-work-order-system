#!/bin/zsh

set -u

APP_NAME="工单管理系统"
APP_VERSION="加分项增强版"
SCRIPT_DIR="${0:A:h}"
if [[ -f "${SCRIPT_DIR}/pom.xml" && -d "${SCRIPT_DIR}/src" ]]; then
    PROJECT_DIR="${SCRIPT_DIR}"
else
    PROJECT_DIR="${SCRIPT_DIR}/ticket-management"
fi
JAR_PATH="${PROJECT_DIR}/target/ticket-management.jar"
ALWAYS_BUILD="${ALWAYS_BUILD:-1}"
MYSQL_HOST="127.0.0.1"
MYSQL_PORT="3306"
MONGO_HOST="127.0.0.1"
MONGO_PORT="27017"

print_title() {
    echo "========================================"
    echo "  ${APP_NAME}启动器（${APP_VERSION}）"
    echo "========================================"
    echo "  + 连接池监控面板"
    echo "  + MySQL READ/WRITE 读写分离连接池"
    echo "  + 数据库连接故障自动重连"
    echo "  + 操作审计日志：MongoDB + logs/audit.log"
    echo
}

pause_and_exit() {
    local code="$1"
    echo
    read -r "?按回车键关闭窗口..."
    exit "${code}"
}

fail() {
    echo "启动失败：$1"
    pause_and_exit 1
}

check_command() {
    local command_name="$1"
    local display_name="$2"
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        fail "未找到 ${display_name}，请先安装或配置 PATH。"
    fi
}

check_port() {
    local host="$1"
    local port="$2"

    if command -v nc >/dev/null 2>&1; then
        nc -z "${host}" "${port}" >/dev/null 2>&1
        return "$?"
    fi

    if command -v lsof >/dev/null 2>&1; then
        lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
        return "$?"
    fi

    return 2
}

build_if_needed() {
    if [[ "${ALWAYS_BUILD}" == "1" ]]; then
        echo "正在打包最新代码（已跳过测试）..."
        run_maven_package
        return 0
    fi

    if ! jar_needs_build; then
        echo "JAR 已是最新：${JAR_PATH}"
        return 0
    fi

    if [[ -f "${JAR_PATH}" ]]; then
        echo "检测到源码或配置比 JAR 更新，正在重新打包..."
    else
        echo "未找到 ${JAR_PATH}"
        echo "正在尝试使用 Maven 打包..."
    fi
    run_maven_package
}

jar_needs_build() {
    if [[ ! -f "${JAR_PATH}" ]]; then
        return 0
    fi
    if [[ "${PROJECT_DIR}/pom.xml" -nt "${JAR_PATH}" ]]; then
        return 0
    fi
    local newer_file
    newer_file=$(find "${PROJECT_DIR}/src" -type f -newer "${JAR_PATH}" -print -quit 2>/dev/null)
    if [[ -n "${newer_file}" ]]; then
        return 0
    fi
    [[ "${PROJECT_DIR}/README.md" -nt "${JAR_PATH}" ]]
}

run_maven_package() {
    check_command "mvn" "Maven"

    cd "${PROJECT_DIR}" || fail "无法进入项目目录：${PROJECT_DIR}"
    mvn -DskipTests package
    local build_code="$?"
    if [[ "${build_code}" -ne 0 ]]; then
        fail "Maven 打包失败，请检查上方错误信息。"
    fi

    [[ -f "${JAR_PATH}" ]] || fail "打包后仍未找到 JAR：${JAR_PATH}"
    echo "打包完成：${JAR_PATH}"
}

check_already_running() {
    if command -v pgrep >/dev/null 2>&1 && pgrep -f "java .*ticket-management.jar" >/dev/null 2>&1; then
        fail "检测到 ${APP_NAME} 可能已经在运行。请先关闭现有程序窗口后再启动。"
    fi
}

print_title

echo "项目目录：${PROJECT_DIR}"
echo "启动文件：${JAR_PATH}"
echo "更新策略：每次启动自动打包最新代码"
echo

[[ -d "${PROJECT_DIR}" ]] || fail "未找到项目目录，请确认启动器和 ticket-management 文件夹在同一目录。"
check_command "java" "Java"
build_if_needed
check_already_running

echo "检查 MySQL：${MYSQL_HOST}:${MYSQL_PORT}"
if ! check_port "${MYSQL_HOST}" "${MYSQL_PORT}"; then
    fail "MySQL 未在 ${MYSQL_HOST}:${MYSQL_PORT} 监听。请先启动 MySQL，并确认 src/main/resources/db.properties 中的配置正确。"
fi

echo "检查 MongoDB：${MONGO_HOST}:${MONGO_PORT}"
if ! check_port "${MONGO_HOST}" "${MONGO_PORT}"; then
    fail "MongoDB 未在 ${MONGO_HOST}:${MONGO_PORT} 监听。请先启动 MongoDB，并确认 src/main/resources/db.properties 中的配置正确。"
fi

echo
echo "环境检查通过，正在启动 ${APP_NAME}..."
echo

cd "${PROJECT_DIR}" || fail "无法进入项目目录：${PROJECT_DIR}"
java -jar "${JAR_PATH}"
app_code="$?"

echo
if [[ "${app_code}" -eq 0 ]]; then
    echo "${APP_NAME}已退出。"
else
    echo "${APP_NAME}异常退出，退出码：${app_code}"
fi

pause_and_exit "${app_code}"
