#!/bin/sh
# 昱通游戏服务器安装脚本（NeoForge 专用版）
# Server Files: /mnt/server

# ==================== 配置参数 ====================
# --- 必须配置项 ---
# 设置此项为你需要的 Minecraft 版本，例如 "1.20.1", "1.21.1"
# NEOFORGE_MC_VERSION="" # <<<--- 在此处设置你的 Minecraft 版本 ---
# 设置此项为 "release" 或 "beta"
# NEOFORGE_MC_VERSION="1.21.1"
# NEOFORGE_TYPE="release"
# 可选：自定义安装器文件名 (不含.jar)，留空则自动命名
NEOFORGE_INSTALLER_NAME=""
# 安装参数 (传递给 NeoForge 安装器)
NEOFORGE_INSTALL_ARGS="--installServer --mirror https://bmclapi2.bangbang93.com/maven/"
# 安装器补丁JAR下载地址（用于修复 --mirror 参数支持）
INSTALLER_PATCH_URL="https://redirect2.bbsmc.net/ytonidc/raw/neoforge-installer-patched.jar"
# -----------------

PRIMARY_MIRROR_URL="https://redirect2.bbsmc.net/ytonidc"
PRIMARY_MIRROR_NAME="昱通官方主节点"
# 统一配置：需要下载的额外JAR包列表（后续维护直接改这里）
EXTRA_JARS="authlib-injector-1.2.6.jar NetControl-1.0.jar ForceExit.jar"
# 额外JAR包下载根路径（raw目录）
EXTRA_JAR_URL="${PRIMARY_MIRROR_URL}/raw"
# 额外依赖包存放目录
AGENT_DIR="./agents"

# ==================================================

# ==================== 工具函数：URL 编码（无换行） ====================
url_encode() {
    local input="$1"
    printf '%s' "$input" | od -An -tx1 | tr ' ' % | tr '[:lower:]' '[:upper:]' | tr -d '\n'
}

# ==================== 工具函数：字节转MB（保留2位小数） ====================
bytes_to_mb() {
    local bytes="$1"
    echo "$bytes" | awk '{printf "%.2f", $1 / 1048576}'
}

# ==================== NeoForge 安装器下载与安装函数 (增强版) ====================
install_neoforge_server() {
    echo "[昱通游戏 GamePlan][NeoForge]: 开始准备安装 NeoForge 服务端..."

    # 1. 确定版本前缀 (处理常见格式，如 1.21.1 -> 21.1)
    # 注意：如果映射关系未来改变，可能需要调整此逻辑
    MAJOR_MINOR=$(echo "$NEOFORGE_MC_VERSION" | sed 's/^1\.//')
    NEOFORGE_VERSION_PREFIX=$(echo "$MAJOR_MINOR" | cut -d'.' -f1,2)
    echo "[昱通游戏 GamePlan][NeoForge]: 目标 Minecraft 版本: $NEOFORGE_MC_VERSION"
    echo "[昱通游戏 GamePlan][NeoForge]: 推断的 NeoForge 版本前缀: $NEOFORGE_VERSION_PREFIX"
    echo "[昱通游戏 GamePlan][NeoForge]: 下载类型: $NEOFORGE_TYPE"

    # 2. 下载并解析元数据 (带重试和备用镜像)
    echo "[昱通游戏 GamePlan][NeoForge]: 正在获取最新版本信息..."
    METADATA_URL_PRIMARY="https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
    # 如果有已知的备用镜像，可以在这里添加
    # METADATA_URL_BACKUP1="https://example-backup-mirror.com/path/to/maven-metadata.xml"
    TEMP_META_FILE="./neoforge_metadata_$$.xml" # 使用临时文件避免冲突

    # 定义一个下载函数，方便重用
    download_with_retries() {
        local url="$1"
        local output_file="$2"
        local max_retries=5
        local retry_delay=3
        local attempt=1

        while [ $attempt -le $max_retries ]; do
            echo "[昱通游戏 GamePlan][NeoForge]: 尝试 ($attempt/$max_retries) 从 $url 下载..."
            # 使用 curl 下载，增加最大时间和连接时间
            curl -sSL --insecure --retry 2 --retry-delay $retry_delay --connect-timeout 15 --max-time 600 \
                 --output "${output_file}" --show-error --fail --location "${url}"

            if [ $? -eq 0 ] && [ -f "$output_file" ] && [ -s "$output_file" ]; then
                echo "[昱通游戏 GamePlan][NeoForge]: 成功从 $url 下载。"
                return 0
            else
                echo "[昱通游戏 GamePlan][NeoForge]: 从 $url 下载失败 (第 $attempt 次尝试)。"
                if [ $attempt -lt $max_retries ]; then
                    echo "[昱通游戏 GamePlan][NeoForge]: 等待 ${retry_delay} 秒后进行下次尝试..."
                    sleep $retry_delay
                fi
            fi
            attempt=$((attempt + 1))
        done

        echo "[昱通游戏 GamePlan][NeoForge]: 错误：经过 $max_retries 次尝试后仍无法从 $url 下载。"
        return 1
    }

    # 首先尝试主镜像
    download_with_retries "$METADATA_URL_PRIMARY" "$TEMP_META_FILE"
    DOWNLOAD_RESULT=$?

    # 如果主镜像失败且有备用镜像，则尝试备用镜像
    # if [ $DOWNLOAD_RESULT -ne 0 ] && [ -n "$METADATA_URL_BACKUP1" ]; then
    #     echo "[昱通游戏 GamePlan][NeoForge]: 主镜像失败，正在尝试备用镜像1..."
    #     download_with_retries "$METADATA_URL_BACKUP1" "$TEMP_META_FILE"
    #     DOWNLOAD_RESULT=$?
    # fi

    # 最终检查
    if [ $DOWNLOAD_RESULT -ne 0 ] || [ ! -f "$TEMP_META_FILE" ] || [ ! -s "$TEMP_META_FILE" ]; then
        echo "[昱通游戏 GamePlan][NeoForge]: 错误：无法下载有效的元数据文件。"
        rm -f "$TEMP_META_FILE" 2>/dev/null
        return 1 # 返回错误码
    fi

    # 3. 解析最新版本 (区分 Release/Beta - 更严格匹配)
    LATEST_NEOFORGE_VERSION=""
    # 转义前缀中的特殊字符，以防干扰正则表达式
    ESCAPED_PREFIX=$(printf '%s\n' "$NEOFORGE_VERSION_PREFIX" | sed 's/[[\.*^$()+?{|]/\\&/g')
    if [ "$NEOFORGE_TYPE" = "release" ]; then
        echo "[昱通游戏 GamePlan][NeoForge]: 正在筛选最新 Release 版本 (严格匹配前缀: ${NEOFORGE_VERSION_PREFIX})..."
        # 匹配以指定前缀开头，后面紧跟 .数字 或直接结束的版本 (e.g., 21.1, 21.1.0, 21.1.10)
        # 避免匹配到 21.10.x, 21.11.x 等
        LATEST_NEOFORGE_VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$TEMP_META_FILE" | \
                                  grep -E "^${ESCAPED_PREFIX}(\.[0-9]+)*$" | \
                                  sort -V | \
                                  tail -n 1)
    elif [ "$NEOFORGE_TYPE" = "beta" ]; then
        echo "[昱通游戏 GamePlan][NeoForge]: 正在筛选最新 Beta 版本 (严格匹配前缀: ${NEOFORGE_VERSION_PREFIX})..."
        # 匹配以指定前缀开头，后面紧跟 .数字-beta 或 -beta 的版本 (e.g., 21.1-beta, 21.1.0-beta)
        LATEST_NEOFORGE_VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' "$TEMP_META_FILE" | \
                                  grep -E "^${ESCAPED_PREFIX}(\.[0-9]+)*-beta$" | \
                                  sort -V | \
                                  tail -n 1)
    else
        echo "[昱通游戏 GamePlan][NeoForge]: 错误：不支持的 NEOFORGE_TYPE '$NEOFORGE_TYPE'。请设置为 'release' 或 'beta'。"
        rm -f "$TEMP_META_FILE" 2>/dev/null
        return 1
    fi

    # 清理临时文件
    rm -f "$TEMP_META_FILE" 2>/dev/null

    if [ -z "$LATEST_NEOFORGE_VERSION" ]; then
        echo "[昱通游戏 GamePlan][NeoForge]: 错误：无法为 Minecraft $NEOFORGE_MC_VERSION 找到最新的 $NEOFORGE_TYPE 版本。"
        return 1
    fi
    echo "[昱通游戏 GamePlan][NeoForge]: 找到的最新 $NEOFORGE_TYPE 版本: $LATEST_NEOFORGE_VERSION"

    # 4. 构造下载链接和最终文件名
    NEOFORGE_JAR_URL="https://maven.neoforged.net/releases/net/neoforged/neoforge/${LATEST_NEOFORGE_VERSION}/neoforge-${LATEST_NEOFORGE_VERSION}-installer.jar"
    if [ -n "$NEOFORGE_INSTALLER_NAME" ]; then
        NEOFORGE_OUTPUT_FILE="${NEOFORGE_INSTALLER_NAME}.jar"
    else
        NEOFORGE_OUTPUT_FILE="neoforge-${LATEST_NEOFORGE_VERSION}-installer.jar"
    fi

    # 5. 下载安装器 JAR (同样使用增强的下载函数)
    echo "[昱通游戏 GamePlan][NeoForge]: 正在下载安装器..."
    # echo "[昱通游戏 GamePlan][NeoForge]: 下载链接: $NEOFORGE_JAR_URL"

    # 使用增强的下载函数下载安装器
    download_with_retries "$NEOFORGE_JAR_URL" "${NEOFORGE_OUTPUT_FILE}"
    INSTALLER_DOWNLOAD_RESULT=$?

    if [ $INSTALLER_DOWNLOAD_RESULT -ne 0 ]; then
        echo "[昱通游戏 GamePlan][NeoForge]: 错误：NeoForge 安装器下载失败！"
        rm -f "${NEOFORGE_OUTPUT_FILE}" 2>/dev/null
        return 1
    fi

    # 6. 校验文件大小并报告
    # 尝试 du -b，如果失败则尝试 stat (更通用)
    FILE_SIZE_BYTES=$(du -b "${NEOFORGE_OUTPUT_FILE}" 2>/dev/null | awk '{print $1}' || stat -c%s "${NEOFORGE_OUTPUT_FILE}" 2>/dev/null)
    if [ -z "$FILE_SIZE_BYTES" ] || [ "$FILE_SIZE_BYTES" -lt 1024 ]; then
        FILE_SIZE_MB=$(bytes_to_mb "$FILE_SIZE_BYTES")
        echo "[昱通游戏 GamePlan][NeoForge]: 错误：下载的安装器文件无效（大小：${FILE_SIZE_MB}MB）"
        rm -f "${NEOFORGE_OUTPUT_FILE}" 2>/dev/null
        return 1
    fi
    FILE_SIZE_MB=$(bytes_to_mb "$FILE_SIZE_BYTES")
    echo "[昱通游戏 GamePlan][NeoForge]: 下载成功！文件大小：${FILE_SIZE_MB}MB，保存为 ${NEOFORGE_OUTPUT_FILE}"

    # 6.5. 下载补丁并注入安装器（修复 --mirror 支持）
    PATCH_FILE="neoforge-installer-patched.jar"
    PATCH_TEMP_DIR="/tmp/patch_classes_$$"
    echo "[昱通游戏 GamePlan][NeoForge]: 正在下载安装器补丁..."
    curl -sSL --insecure --retry 3 --connect-timeout 15 --max-time 60 \
         --output "${PATCH_FILE}" --fail "${INSTALLER_PATCH_URL}"
    if [ $? -eq 0 ] && [ -f "$PATCH_FILE" ] && [ -s "$PATCH_FILE" ]; then
        echo "[昱通游戏 GamePlan][NeoForge]: 补丁下载成功，正在注入安装器..."
        PATCH_WORK_DIR="$(pwd)"
        mkdir -p "$PATCH_TEMP_DIR"
        cd "$PATCH_TEMP_DIR"
        jar xf "${PATCH_WORK_DIR}/${PATCH_FILE}"
        rm -rf META-INF
        jar uf "${PATCH_WORK_DIR}/${NEOFORGE_OUTPUT_FILE}" .
        cd "$PATCH_WORK_DIR"
        rm -rf "$PATCH_TEMP_DIR" "$PATCH_FILE"
        echo "[昱通游戏 GamePlan][NeoForge]: 安装器补丁注入完成（已启用镜像加速支持）"
    else
        echo "[昱通游戏 GamePlan][NeoForge]: 警告：补丁下载失败，将使用原版安装器继续（无镜像加速）"
        rm -f "$PATCH_FILE" 2>/dev/null
    fi

    # 7. 运行安装器
    echo "[昱通游戏 GamePlan][NeoForge]: 正在运行安装器以部署服务端文件..."

    # 执行安装命令
    java -jar "${NEOFORGE_OUTPUT_FILE}" ${NEOFORGE_INSTALL_ARGS}

    INSTALLER_RUN_RESULT=$?
    if [ $INSTALLER_RUN_RESULT -ne 0 ]; then
        echo "[昱通游戏 GamePlan][NeoForge]: 错误：NeoForge 安装器运行失败 (退出码: $INSTALLER_RUN_RESULT)！"
        echo "[昱通游戏 GamePlan][NeoForge]: 保留安装器文件和日志以供排查: ${NEOFORGE_OUTPUT_FILE}, ${NEOFORGE_OUTPUT_FILE}.log"
        return 1 # 返回错误码，终止脚本或标记失败
    else
        echo "[昱通游戏 GamePlan][NeoForge]: 安装器运行完成。服务端文件已部署。"
        # 8. 清理安装器文件和日志
        echo "[昱通游戏 GamePlan][NeoForge]: 正在清理安装器文件和日志..."
        rm -f "${NEOFORGE_OUTPUT_FILE}" "${NEOFORGE_OUTPUT_FILE}.log" 2>/dev/null
        if [ $? -eq 0 ]; then
            echo "[昱通游戏 GamePlan][NeoForge]: 安装器文件和日志已清理。"
        else
            echo "[昱通游戏 GamePlan][NeoForge]: 警告：部分安装器文件或日志清理失败。"
        fi
    fi

    # 注意：原来的第8步（可选清理）已经被整合并强化到这里了。

}
# =====================================================================


# ==================== 第一步：清理目录 ====================
if [ "$SAVE_DATA" = "0" ]; then
    echo "[昱通游戏 GamePlan]: 检测到 SAVE_DATA=0，开始清理 /mnt/server 所有文件（含隐藏文件）"
    mkdir -p /mnt/server
    find /mnt/server -mindepth 1 -delete
    echo "[昱通游戏 GamePlan]: 目录清理完成"
fi

# ==================== 第二步：输出字符画 ====================
cat << 'EOF'
__     ___               _____                      
 \ \   / / |             / ____|                     
  \ \_/ /| |_ ___  _ __ | |  __  __ _ __ ___   ___ 
   \   / | __/ _ \| '_ \| | |_ |_` | '_ ` _ \ / _ \
    | |  | || (_) | | | | |__| | (_| | | | | | |  __/
    |_|   \__\___/|_| |_|\_____|\__,_|_| |_| |_|\___|
                                                     
                                                     
EOF

echo "[昱通游戏 GamePlan]: 正在更新国内下载源..."
V=$(cat /etc/alpine-release | cut -d '.' -f 1-2)
cp -n /etc/apk/repositories /etc/apk/repositories.bak
echo -e "https://mirrors.aliyun.com/alpine/v$V/main\nhttps://mirrors.aliyun.com/alpine/v$V/community" > /etc/apk/repositories
apk add --no-cache ca-certificates openjdk17-jdk > /dev/null 2>&1

if apk update > /dev/null 2>&1; then
    echo "[昱通游戏 GamePlan]: 国内源更新成功！"
else
    echo "[昱通游戏 GamePlan]: 国内源更新失败，将继续使用默认源（可能影响下载速度）"
fi

# ==================== 第三步：准备工作目录 ====================
if [ ! -d /mnt/server ]; then
    mkdir -p /mnt/server
fi
cd /mnt/server || {
    echo "[昱通游戏 GamePlan]: 无法进入 /mnt/server 目录，安装终止"
    exit 1
}


# ==================== 第四步：检查配置并安装 NeoForge 服务端 ====================
# 检查是否配置了必要的 NeoForge 版本
# 修正逻辑：仅当变量未设置或为空时才报错
if [ -z "$NEOFORGE_MC_VERSION" ]; then
    echo "[昱通游戏 GamePlan][NeoForge]: 错误：必须在配置中设置有效的 NEOFORGE_MC_VERSION (例如 '1.20.1', '1.21.1')"
    echo "[昱通游戏 GamePlan][NeoForge]: 请编辑脚本，在 NEOFORGE_MC_VERSION='' 后面填入你的版本号。"
    exit 1
fi

echo "------------------------------------------------------------"
install_neoforge_server
INSTALL_RESULT=$?
if [ $INSTALL_RESULT -ne 0 ]; then
    echo "[昱通游戏 GamePlan][NeoForge]: NeoForge 安装过程遇到致命错误 (退出码: $INSTALL_RESULT)，脚本终止。"
    exit 1
fi
echo "------------------------------------------------------------"


# ==================== 第五步：自动创建并同意 EULA 协议 ====================
EULA_FILE="/mnt/server/eula.txt"
if [ ! -f "$EULA_FILE" ]; then
    echo "[昱通游戏 GamePlan]: 未检测到 eula.txt，自动创建并同意用户协议"
    echo "eula=true" > "$EULA_FILE"
    echo "[昱通游戏 GamePlan]: 已自动设置 eula=true，无需手动操作"
else
    echo "[昱通游戏 GamePlan]: 已存在 eula.txt，保持原有配置"
fi

# ==================== 第六步：循环下载所有额外JAR包（保存到agents目录） ====================
# 先创建agents目录（不存在则新建，存在则跳过）
mkdir -p "${AGENT_DIR}"
echo "[昱通游戏 GamePlan]: 开始下载额外依赖包列表：${EXTRA_JARS}（保存到 ${AGENT_DIR} 目录）"
echo "------------------------------------------------------------"

for JAR in $EXTRA_JARS; do
    echo "[昱通游戏 GamePlan]: 正在下载：$JAR"
    # echo "[昱通游戏 GamePlan]: 下载链接：${EXTRA_JAR_URL}/${JAR}"
    # 拼接当前JAR包的下载链接
    JAR_URL="${EXTRA_JAR_URL}/${JAR}"
    # 拼接保存路径（agents目录下）
    JAR_SAVE_PATH="${AGENT_DIR}/${JAR}"

    # 使用增强的下载函数下载额外JAR包
    download_with_retries "$JAR_URL" "${JAR_SAVE_PATH}"
    JAR_DOWNLOAD_RESULT=$?

    if [ $JAR_DOWNLOAD_RESULT -ne 0 ]; then
        echo "[昱通游戏 GamePlan]: 错误：$JAR 下载失败！"
        echo "[昱通游戏 GamePlan]: 排查建议：手动访问链接 ${JAR_URL} 验证是否可下载"
        rm -f "${JAR_SAVE_PATH}"  # 清理无效文件
        # 可选：此处可以选择退出脚本
        # exit 1
    fi

    # 统一文件大小校验
    JAR_SIZE=$(du -b "${JAR_SAVE_PATH}" | awk '{print $1}')
    if [ "$JAR_SIZE" -lt 1024 ]; then
        JAR_SIZE_MB=$(bytes_to_mb "$JAR_SIZE")
        echo "[昱通游戏 GamePlan]: 错误：$JAR 下载无效（大小：${JAR_SIZE_MB}MB）"
        rm -f "${JAR_SAVE_PATH}"  # 清理无效文件
        # 可选：此处可以选择退出脚本
        # exit 1
    fi

    # 下载成功提示（显示保存路径）
    JAR_SIZE_MB=$(bytes_to_mb "$JAR_SIZE")
    echo "[昱通游戏 GamePlan]: $JAR 下载完成（大小：${JAR_SIZE_MB}MB，保存路径：${JAR_SAVE_PATH}）"
    echo "------------------------------------------------------------"
done

echo "[昱通游戏 GamePlan]: 所有额外依赖包下载完成！"

# ==================== 新增：智能复制 unix_args.txt（按优先级+版本排序） ====================
echo -e "\n[昱通游戏 GamePlan]: 开始处理 unix_args.txt 复制..."
TARGET_FILE="unix_args.txt"
# 1. 先检查根目录是否已存在 unix_args.txt
if [ -f "$TARGET_FILE" ]; then
    echo "[昱通游戏 GamePlan]: ✅ 根目录已存在 $TARGET_FILE，跳过复制"
else
    # 定义两个查找路径（优先级：Forge > NeoForge）
    FORGE_BASE_PATH="./libraries/net/minecraftforge/forge"
    NEOFORGE_BASE_PATH="./libraries/net/neoforged/neoforge"
    COPY_SUCCESS=false
    
    # 2. 处理 Forge 目录：查找版本目录，取最高版本
    if [ -d "$FORGE_BASE_PATH" ]; then
        # 找到所有一级子目录（版本目录），按版本号自然排序，取最后一个（最高版本）
        # 使用 -mindepth 1 -maxdepth 1 避免正则表达式兼容性问题
        FORGE_VER_DIRS=$(find "$FORGE_BASE_PATH" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)
        if [ -n "$FORGE_VER_DIRS" ]; then
            FORGE_SOURCE_FILE="${FORGE_VER_DIRS}/${TARGET_FILE}"
            
            if [ -f "$FORGE_SOURCE_FILE" ]; then
                # 复制到根目录（-n 避免覆盖，虽然已检查根目录不存在，但留保险）
                cp -n "$FORGE_SOURCE_FILE" "$TARGET_FILE"
                echo "[昱通游戏 GamePlan]: ✅ 从 Forge 最高版本目录复制 $TARGET_FILE"
                echo "  来源路径：$FORGE_SOURCE_FILE"
                echo "  目标路径：./$TARGET_FILE"
                COPY_SUCCESS=true
            else
                echo "[昱通游戏 GamePlan]: ⚠️ Forge 版本目录 $(basename "$FORGE_VER_DIRS") 中未找到 $TARGET_FILE"
            fi
        else
            echo "[昱通游戏 GamePlan]: ⚠️ Forge 基础目录存在，但未找到有效版本目录"
        fi
    fi
    
    # 3. 若 Forge 未复制成功，处理 NeoForge 目录
    if [ "$COPY_SUCCESS" = false ] && [ -d "$NEOFORGE_BASE_PATH" ]; then
        # 同样按版本号自然排序，取最高版本
        # 使用 -mindepth 1 -maxdepth 1 避免正则表达式兼容性问题
        NEOFORGE_VER_DIRS=$(find "$NEOFORGE_BASE_PATH" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)
        if [ -n "$NEOFORGE_VER_DIRS" ]; then
            NEOFORGE_SOURCE_FILE="${NEOFORGE_VER_DIRS}/${TARGET_FILE}"
            
            if [ -f "$NEOFORGE_SOURCE_FILE" ]; then
                cp -n "$NEOFORGE_SOURCE_FILE" "$TARGET_FILE"
                echo "[昱通游戏 GamePlan]: ✅ 从 NeoForge 最高版本目录复制 $TARGET_FILE"
                echo "  来源路径：$NEOFORGE_SOURCE_FILE"
                echo "  目标路径：./$TARGET_FILE"
                COPY_SUCCESS=true
            else
                echo "[昱通游戏 GamePlan]: ⚠️ NeoForge 版本目录 $(basename "$NEOFORGE_VER_DIRS") 中未找到 $TARGET_FILE"
            fi
        else
            echo "[昱通游戏 GamePlan]: ⚠️ NeoForge 基础目录存在，但未找到有效版本目录"
        fi
    fi
    
    # 4. 若两个目录都未复制成功，输出跳过提示
    if [ "$COPY_SUCCESS" = false ]; then
        echo "[昱通游戏 GamePlan]: ❌ 未找到可复制的 $TARGET_FILE（Forge/NeoForge 目录不存在或无对应文件），跳过复制"
    fi
fi

# ==================== 第七步：安装完成提示 ====================
echo -e "\n[昱通游戏 GamePlan]: NeoForge 服务端安装及初始化已完成！"
echo "[昱通游戏 GamePlan]: 现在您可以启动服务器了。"
echo "[昱通游戏 GamePlan]: 启动命令通常类似于: java @user_jvm_args.txt @libraries/net/neoforged/neoforge/<版本>/unix_args.txt nogui"
echo "[昱通游戏 GamePlan]: 请根据实际生成的文件调整启动命令。"