#!/bin/bash
set -e

# gradle.properties에서 버전 읽기
VERSION=$(grep "^version=" gradle.properties | cut -d'=' -f2)

if [ -z "$VERSION" ]; then
    echo "❌ Error: version not found in gradle.properties"
    exit 1
fi

TAG="v${VERSION}"

# 태그가 이미 존재하는지 확인
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "❌ Error: Tag $TAG already exists"
    echo ""
    echo "Existing tag info:"
    git show "$TAG" --no-patch --format="%H %s"
    exit 1
fi

# 변경사항 확인
echo "📝 Changes since last tag:"
PREVIOUS_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -z "$PREVIOUS_TAG" ]; then
    echo "  (첫 릴리스)"
    git log --oneline -10
else
    echo "  ($PREVIOUS_TAG → $TAG)"
    git log --oneline ${PREVIOUS_TAG}..HEAD
fi

echo ""
read -p "✅ Create tag $TAG? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Cancelled"
    exit 1
fi

# 태그 생성
git tag -a "$TAG" -m "Release version $VERSION"

# 태그 푸시
echo "📤 Pushing tag to GitHub..."
git push origin "$TAG"

echo ""
echo "✅ Tag $TAG created and pushed!"
echo ""
echo "GitHub Actions will automatically:"
echo "  - Build the package"
echo "  - Publish to GitHub Packages"
echo ""
echo "View release: https://github.com/oliveyoung/ivm-lite-oliveyoung-full/releases/tag/$TAG"
