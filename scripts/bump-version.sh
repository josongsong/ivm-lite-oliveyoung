#!/bin/bash
set -e

TYPE=$1  # major, minor, patch

if [ -z "$TYPE" ]; then
    echo "Usage: ./scripts/bump-version.sh [major|minor|patch]"
    exit 1
fi

if [[ ! "$TYPE" =~ ^(major|minor|patch)$ ]]; then
    echo "❌ Error: Invalid type. Use major, minor, or patch"
    exit 1
fi

# 현재 버전 읽기
CURRENT_VERSION=$(grep "^version=" gradle.properties | cut -d'=' -f2)
IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
MAJOR=${VERSION_PARTS[0]}
MINOR=${VERSION_PARTS[1]}
PATCH=${VERSION_PARTS[2]}

# 버전 증가
case $TYPE in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
esac

NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"

# gradle.properties 업데이트
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/^version=.*/version=$NEW_VERSION/" gradle.properties
else
    # Linux
    sed -i "s/^version=.*/version=$NEW_VERSION/" gradle.properties
fi

echo "✅ Version bumped: $CURRENT_VERSION → $NEW_VERSION"
echo ""
echo "Next steps:"
echo "  1. Review changes: git diff gradle.properties"
echo "  2. Commit: git add gradle.properties && git commit -m \"Bump version to $NEW_VERSION\""
echo "  3. Create tag: ./scripts/create-release-tag.sh"
