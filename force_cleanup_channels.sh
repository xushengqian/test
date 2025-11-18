#!/bin/bash
# FreeSWITCH 强制通道清理脚本
# 用于处理 uuid_kill 返回 "No such channel" 的情况

FS_CLI="${FS_CLI:-fs_cli}"
DRY_RUN="${DRY_RUN:-false}"

echo "=========================================="
echo "FreeSWITCH 强制通道清理工具"
echo "（处理 uuid_kill 返回 'No such channel' 的情况）"
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

# 获取所有通道 UUID（从 show channels）
echo "正在获取通道列表..."
CHANNELS=$($FS_CLI -x "show channels" 2>/dev/null | grep "uuid:" | awk '{print $2}')

if [ -z "$CHANNELS" ]; then
    echo "没有发现任何通道"
    exit 0
fi

CHANNEL_COUNT=$(echo "$CHANNELS" | wc -l)
echo "发现 $CHANNEL_COUNT 个通道（来自 show channels）"
echo ""

CLEANED=0
NOT_FOUND=0
FAILED=0
SKIPPED=0

# 遍历每个通道
for UUID in $CHANNELS; do
    if [ -z "$UUID" ]; then
        continue
    fi
    
    echo "处理通道: $UUID"
    
    # 首先检查通道是否真的存在
    EXISTS=$($FS_CLI -x "uuid_exists $UUID" 2>/dev/null)
    
    if [ "$EXISTS" != "true" ]; then
        echo "  ⚠ 通道不存在（show channels 显示延迟）"
        NOT_FOUND=$((NOT_FOUND + 1))
        echo ""
        continue
    fi
    
    # 获取通道状态
    DUMP_RESULT=$($FS_CLI -x "uuid_dump $UUID" 2>&1)
    
    if echo "$DUMP_RESULT" | grep -q "No such channel"; then
        echo "  ⚠ uuid_dump 失败: 通道不存在"
        NOT_FOUND=$((NOT_FOUND + 1))
        echo ""
        continue
    fi
    
    STATE=$(echo "$DUMP_RESULT" | grep "state:" | awk '{print $2}')
    CALLSTATE=$(echo "$DUMP_RESULT" | grep "callstate:" | awk '{print $2}')
    HANGUP_CAUSE=$(echo "$DUMP_RESULT" | grep "hangup_cause:" | awk '{print $2}')
    
    echo "  状态: $STATE"
    echo "  呼叫状态: $CALLSTATE"
    echo "  挂断原因: $HANGUP_CAUSE"
    
    # 检查通道是否应该被清理
    SHOULD_CLEAN=false
    REASON=""
    
    # 如果已经挂断但通道仍存在
    if [ "$STATE" = "CS_HANGUP" ] || [ "$CALLSTATE" = "HANGUP" ]; then
        SHOULD_CLEAN=true
        REASON="通道已挂断但未清理"
    fi
    
    # 如果挂断原因存在但通道仍存在
    if [ -n "$HANGUP_CAUSE" ] && [ "$HANGUP_CAUSE" != "NONE_CALLED" ] && [ "$HANGUP_CAUSE" != "" ]; then
        SHOULD_CLEAN=true
        REASON="通道有挂断原因: $HANGUP_CAUSE"
    fi
    
    # 如果状态为空或异常，也尝试清理
    if [ -z "$STATE" ] || [ -z "$CALLSTATE" ]; then
        SHOULD_CLEAN=true
        REASON="通道状态异常（状态为空）"
    fi
    
    if [ "$SHOULD_CLEAN" = "true" ]; then
        echo "  -> $REASON"
        
        if [ "$DRY_RUN" = "true" ]; then
            echo "  [干运行] 将清理通道 $UUID"
            SKIPPED=$((SKIPPED + 1))
        else
            # 尝试多种清理方法
            SUCCESS=false
            
            # 方法1: uuid_kill
            echo "  尝试方法1: uuid_kill..."
            KILL_RESULT=$($FS_CLI -x "uuid_kill $UUID" 2>&1)
            if echo "$KILL_RESULT" | grep -q "No such channel"; then
                echo "    ⚠ 通道不存在（可能已清理）"
                SUCCESS=true
            elif echo "$KILL_RESULT" | grep -q "-ERR"; then
                echo "    ✗ 失败: $KILL_RESULT"
            else
                echo "    ✓ 成功"
                SUCCESS=true
            fi
            
            sleep 0.5
            
            # 检查是否还存在
            EXISTS=$($FS_CLI -x "uuid_exists $UUID" 2>/dev/null)
            if [ "$EXISTS" != "true" ]; then
                SUCCESS=true
            fi
            
            if [ "$SUCCESS" != "true" ]; then
                # 方法2: uuid_destroy
                echo "  尝试方法2: uuid_destroy..."
                DESTROY_RESULT=$($FS_CLI -x "uuid_destroy $UUID" 2>&1)
                if echo "$DESTROY_RESULT" | grep -q "No such channel"; then
                    echo "    ⚠ 通道不存在（可能已清理）"
                    SUCCESS=true
                elif echo "$DESTROY_RESULT" | grep -q "-ERR"; then
                    echo "    ✗ 失败: $DESTROY_RESULT"
                else
                    echo "    ✓ 成功"
                    SUCCESS=true
                fi
                
                sleep 0.5
                
                # 再次检查
                EXISTS=$($FS_CLI -x "uuid_exists $UUID" 2>/dev/null)
                if [ "$EXISTS" != "true" ]; then
                    SUCCESS=true
                fi
            fi
            
            if [ "$SUCCESS" = "true" ]; then
                echo "  ✓ 通道已清理"
                CLEANED=$((CLEANED + 1))
            else
                echo "  ✗ 警告: 所有方法都失败，通道可能处于异常状态"
                echo "  建议: 检查 FreeSWITCH 日志或考虑重启相关模块"
                FAILED=$((FAILED + 1))
            fi
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
    echo "不存在（显示延迟）: $NOT_FOUND 个通道"
    echo "清理失败: $FAILED 个通道"
    echo "已跳过: $SKIPPED 个通道"
fi

echo ""
echo "说明："
echo "- '不存在' 表示通道在 show channels 中显示但实际已不存在（显示延迟）"
echo "- '清理失败' 表示通道存在但无法通过常规方法清理，可能需要进一步调查"
echo ""
echo "如果仍有问题，可以尝试："
echo "1. 检查 FreeSWITCH 日志: tail -f /usr/local/freeswitch/log/freeswitch.log"
echo "2. 重启 FreeSWITCH: systemctl restart freeswitch"
echo "3. 使用 hupall 命令（危险！会挂断所有通道）: fs_cli -x 'hupall'"
