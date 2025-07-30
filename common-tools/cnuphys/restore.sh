#!/bin/bash

# List all .classpath and .project files from HEAD (i.e., pre-merge state)
FILES=$(git ls-tree -r --name-only HEAD | grep -E '(\.classpath|\.project)$')

if [ -z "$FILES" ]; then
  echo "No .classpath or .project files found in HEAD."
  exit 0
fi

# Restore each file from HEAD (both to working tree and index)
for file in $FILES; do
  echo "Restoring $file from HEAD"
  git restore --source=HEAD --staged --worktree "$file"
done

echo "All .classpath and .project files from HEAD have been restored."
