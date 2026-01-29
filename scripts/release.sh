#!/bin/bash
set -e

TYPE=${1:-patch}  # patch, minor, major

VERSION_FILE="src/main/kotlin/com/oliveyoung/ivmlite/sdk/VERSION"

# 현재 버전 읽기
CURRENT=$(cat "$VERSION_FILE" | tr -d '[:space:]')
IFS='.' read -ra V <<< "$CURRENT"

# 버전 증가
case $TYPE in
    major) V[0]=$((V[0]+1)); V[1]=0; V[2]=0 ;;
    minor) V[1]=$((V[1]+1)); V[2]=0 ;;
    patch) V[2]=$((V[2]+1)) ;;
    *) echo "Usage: $0 [patch|minor|major]"; exit 1 ;;
esac

NEW_VERSION="${V[0]}.${V[1]}.${V[2]}"
TAG="v${NEW_VERSION}"

# 버전 업데이트
echo "$NEW_VERSION" > "$VERSION_FILE"

# 커밋 및 태그
git add "$VERSION_FILE"
git commit -m "Bump version to $NEW_VERSION"
git tag -a "$TAG" -m "Release $NEW_VERSION"

# 푸시
git push origin main
git push origin "$TAG"

echo "✅ Released $NEW_VERSION"
echo "GitHub Actions will automatically publish"
