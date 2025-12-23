# Linux cp 命令覆盖文件行为演示

本项目演示了 Linux `cp` 命令的各种覆盖文件行为。

## 文件说明

- `cp_overwrite_demo.sh`: 交互式演示脚本，展示不同的 cp 覆盖选项
- `cp_overwrite_guide.md`: 详细的 cp 命令覆盖选项指南

## 快速开始

运行演示脚本：

```bash
chmod +x cp_overwrite_demo.sh
./cp_overwrite_demo.sh
```

## 主要覆盖选项

- `-f`: 强制覆盖（不提示）
- `-i`: 交互式覆盖（需要确认）
- `-n`: 不覆盖已存在的文件
- `-u`: 仅当源文件更新时覆盖
- `--backup`: 覆盖前创建备份

详细说明请查看 `cp_overwrite_guide.md`。