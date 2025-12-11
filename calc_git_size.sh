#!/bin/bash

# Check if a directory argument is provided
if [ -z "$1" ]; then
    ROOT_DIR="."
else
    ROOT_DIR="$1"
fi

echo "Calculating git sizes in $ROOT_DIR..."

# Use a pipeline and a subshell block to avoid process substitution < <(...)
# which causes syntax errors in some shells (like sh or dash).
# The variables modified inside the loop are local to the subshell block (inside {}),
# so we perform the final output inside the block as well.

find "$ROOT_DIR" -type d -name ".git" -prune 2>/dev/null | {
    total_kb=0
    count=0
    
    while read -r git_dir; do
        # Get size in KB
        size_kb=$(du -sk "$git_dir" | cut -f1)
        
        # Get human readable size
        size_human=$(du -sh "$git_dir" | cut -f1)
        
        repo_path=$(dirname "$git_dir")
        echo "Found git repo at: $repo_path ($size_human)"
        
        total_kb=$((total_kb + size_kb))
        count=$((count + 1))
    done
    
    if [ $count -eq 0 ]; then
        echo "No .git directories found."
    else
        # Convert total to human readable format roughly
        if [ $total_kb -gt 1048576 ]; then
            total_gb=$(awk "BEGIN {printf \"%.2f\", $total_kb/1048576}")
            total_human="$total_gb GB"
        elif [ $total_kb -gt 1024 ]; then
            total_mb=$(awk "BEGIN {printf \"%.2f\", $total_kb/1024}")
            total_human="$total_mb MB"
        else
            total_human="$total_kb KB"
        fi
        
        echo "----------------------------------------"
        echo "Total git repositories: $count"
        echo "Total size used by .git folders: $total_human"
    fi
}
