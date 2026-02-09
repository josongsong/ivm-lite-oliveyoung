#!/bin/bash
# Install Git hooks for IVM-Lite project

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"
SOURCE_HOOKS_DIR="$SCRIPT_DIR/git-hooks"

echo "Installing Git hooks..."

# Create hooks directory if it doesn't exist
mkdir -p "$HOOKS_DIR"

# Copy hooks
for hook in pre-commit pre-push; do
    if [ -f "$SOURCE_HOOKS_DIR/$hook" ]; then
        cp "$SOURCE_HOOKS_DIR/$hook" "$HOOKS_DIR/$hook"
        chmod +x "$HOOKS_DIR/$hook"
        echo "  Installed $hook"
    else
        echo "  Warning: $hook not found in $SOURCE_HOOKS_DIR"
    fi
done

echo "Git hooks installed successfully!"
echo ""
echo "To skip hooks temporarily, use:"
echo "  git commit --no-verify"
echo "  git push --no-verify"
