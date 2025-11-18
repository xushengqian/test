#!/bin/bash
# FreeSWITCH 通道清理脚本
# 用于强制挂断残留的通道

FS_CLI="${FS_CLI:-fs_cli}"
DRY_RUN="${DRY_RUN:-false}"
MAX_AGE="${MAX_AGE:-3600}"  # 默认清理超过1小时的通道（秒）

echo "=========================================="
echo "FreeSWITCH 通道清理工具"
echo "=========================================="
echo ""

# 检查 fs_cli 是否可用
if ! command -v $FS_CLI &> /dev/null; then
    echo "错误: 找不到 fs_cli 命令"
    echo "请设置 FS_CLI 环境变量指向 fs_cli 的路径"
    exit 1
fi

if [ "$DRY_RUN" = "true" ]; then
    echo "*** 干运行模式 - 不会实际删除通道 ***"
    echo ""
fi

# 获取所有通道 UUID
echo "正在获取通道列表..."
CHANNELS=$($FS_CLI -x "show channels" 2>/dev/null | grep "uuid:" | awk '{print $2}')

if [ -z "$CHANNELS" ]; then
    echo "没有发现任何通道"
    exit 0
fi

CHANNEL_COUNT=$(echo "$CHANNELS" | wc -l)
echo "发现 $CHANNEL_COUNT 个通道"
echo ""

CLEANED=0
SKIPPED=0

# 遍历每个通道
for UUID in $CHANNELS; do
    if [ -z "$UUID" ]; then
        continue
    fi
    
    echo "检查通道: $UUID"
    
    # 获取通道状态
    STATE=$($FS_CLI -x "uuid_dump $UUID" 2>/dev/null | grep "state:" | awk '{print $2}')
    CALLSTATE=$($FS_CLI -x "uuid_dump $UUID" 2>/dev/null | grep "callstate:" | awk '{print $2}')
    HANGUP_CAUSE=$($FS_CLI -x "uuid_dump $UUID" 2>/dev/null | grep "hangup_cause:" | awk '{print $2}')
    
    echo "  状态: $STATE"
    echo "  呼叫状态: $CALLSTATE"
    echo "  挂断原因: $HANGUP_CAUSE"
    
    # 检查通道是否应该被清理
    SHOULD_CLEAN=false
    
    # 如果已经挂断但通道仍存在
    if [ "$STATE" = "CS_HANGUP" ] || [ "$CALLSTATE" = "HANGUP" ]; then
        SHOULD_CLEAN=true
        echo "  -> 通道已挂断但未清理"
    fi
    
    # 如果挂断原因存在但通道仍存在
    if [ -n "$HANGUP_CAUSE" ] && [ "$HANGUP_CAUSE" != "NONE_CALLED" ]; then
        SHOULD_CLEAN=true
        echo "  -> 通道有挂断原因但未清理"
    fi
    
    # 检查通道年龄（如果可能）
    # 注意: 这需要解析 created 时间，简化处理
    
    if [ "$SHOULD_CLEAN" = "true" ]; then
        if [ "$DRY_RUN" = "true" ]; then
            echo "  [干运行] 将清理通道 $UUID"
            SKIPPED=$((SKIPPED + 1))
        else
            echo "  正在清理通道 $UUID..."
            # 尝试正常挂断
            $FS_CLI -x "uuid_kill $UUID" 2>/dev/null
            sleep 0.5
            
            # 检查是否还存在
            EXISTS=$($FS_CLI -x "uuid_exists $UUID" 2>/dev/null)
            if [ "$EXISTS" = "true" ]; then
                echo "  警告: 通道仍然存在，尝试强制销毁..."
                $FS_CLI -x "uuid_destroy $UUID" 2>/dev/null
            fi
            
            CLEANED=$((CLEANED + 1))
            echo "  ✓ 已清理"
        fi
    else
        echo "  -> 通道状态正常，跳过"
        SKIPPED=$((SKIPPED + 1))
    fi
    
    echo ""
done

echo "=========================================="
echo "清理完成"
echo "=========================================="
if [ "$DRY_RUN" = "true" ]; then
    echo "干运行模式: 将清理 $SKIPPED 个通道"
else
    echo "已清理: $CLEANED 个通道"
    echo "已跳过: $SKIPPED 个通道"
fi

echo ""
echo "使用方法："
echo "  干运行: DRY_RUN=true ./cleanup_channels.sh"
echo "  实际清理: ./cleanup_channels.sh"
echo "  自定义 fs_cli 路径: FS_CLI=/path/to/fs_cli ./cleanup_channels.sh"
