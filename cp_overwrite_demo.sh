#!/bin/bash

# Linux cp 命令覆盖文件演示脚本

echo "=== Linux cp 命令覆盖文件演示 ==="
echo ""

# 创建测试目录
mkdir -p test_source test_dest
cd test_dest

# 创建源文件和目标文件
echo "创建测试文件..."
echo "这是源文件内容 - 版本 1" > ../test_source/source.txt
echo "这是目标文件内容 - 旧版本" > target.txt

echo "初始状态："
echo "源文件内容："
cat ../test_source/source.txt
echo ""
echo "目标文件内容："
cat target.txt
echo ""

# 1. 默认行为（会覆盖，但可能需要确认）
echo "=== 1. 默认 cp 命令（可能需要交互确认）==="
echo "注意：如果目标文件存在，可能需要确认"
cp ../test_source/source.txt target.txt 2>&1 || echo "覆盖被取消或需要确认"
echo ""

# 2. 强制覆盖（-f 选项）
echo "=== 2. 使用 -f 选项强制覆盖（不提示）==="
echo "这是目标文件内容 - 旧版本" > target.txt
cp -f ../test_source/source.txt target.txt
echo "目标文件内容（已覆盖）："
cat target.txt
echo ""

# 3. 交互式覆盖（-i 选项）
echo "=== 3. 使用 -i 选项交互式覆盖（会提示确认）==="
echo "这是目标文件内容 - 旧版本" > target.txt
echo "（演示：使用 -i 选项时，如果目标文件存在会提示）"
cp -i ../test_source/source.txt target.txt <<< "y" || echo "覆盖被取消"
echo ""

# 4. 备份覆盖（--backup 选项）
echo "=== 4. 使用 --backup 选项备份原文件==="
echo "这是目标文件内容 - 旧版本" > target.txt
cp --backup=numbered ../test_source/source.txt target.txt
echo "目标文件内容："
cat target.txt
echo ""
echo "备份文件列表："
ls -la target.txt* 2>/dev/null || echo "无备份文件"
echo ""

# 5. 更新模式（-u 选项，仅当源文件更新时覆盖）
echo "=== 5. 使用 -u 选项（仅当源文件更新时覆盖）==="
echo "这是目标文件内容 - 旧版本" > target.txt
sleep 1
echo "这是源文件内容 - 版本 2（更新）" > ../test_source/source.txt
cp -u ../test_source/source.txt target.txt
echo "目标文件内容："
cat target.txt
echo ""

# 6. 不覆盖已存在文件（-n 选项）
echo "=== 6. 使用 -n 选项（不覆盖已存在的文件）==="
echo "这是目标文件内容 - 保持不变" > target.txt
cp -n ../test_source/source.txt target.txt
echo "目标文件内容（应该保持不变）："
cat target.txt
echo ""

# 7. 详细模式（-v 选项，显示操作过程）
echo "=== 7. 使用 -v 选项（显示详细操作）==="
cp -v ../test_source/source.txt target.txt
echo ""

# 清理
cd ..
rm -rf test_source test_dest
echo "演示完成！"
