# 버전 태그 관리 (핵심만)

> **목적**: SDK 배포 시 버전 태그 관리

---

## 🚀 빠른 사용법

### 1. 릴리스 스크립트 사용 (권장)

**`scripts/release.sh` 생성**:
```bash
#!/bin/bash
set -e

TYPE=${1:-patch}  # patch, minor, major

# 버전 증가
CURRENT=$(grep "^version=" gradle.properties | cut -d'=' -f2)
IFS='.' read -ra V <<< "$CURRENT"
case $TYPE in
    major) V[0]=$((V[0]+1)); V[1]=0; V[2]=0 ;;
    minor) V[1]=$((V[1]+1)); V[2]=0 ;;
    patch) V[2]=$((V[2]+1)) ;;
esac
NEW_VERSION="${V[0]}.${V[1]}.${V[2]}"

# 업데이트
sed -i '' "s/^version=.*/version=$NEW_VERSION/" gradle.properties

# 커밋 및 태그
git add gradle.properties
git commit -m "Bump version to $NEW_VERSION"
git tag -a "v$NEW_VERSION" -m "Release $NEW_VERSION"
git push origin main
git push origin "v$NEW_VERSION"
```

**사용**:
```bash
chmod +x scripts/release.sh
./scripts/release.sh patch  # 1.0.0 → 1.0.1
```

### 2. 수동 방법

```bash
# 1. gradle.properties에서 버전 확인/수정
# version=1.0.0

# 2. 태그 생성 및 푸시
git tag v1.0.0
git push origin v1.0.0

# 3. GitHub Actions가 자동 배포
```

---

## 📋 체크리스트

- [ ] `gradle.properties`에서 버전 확인
- [ ] 태그 생성: `git tag v1.0.0`
- [ ] 태그 푸시: `git push origin v1.0.0`
- [ ] GitHub Actions 실행 확인

---

## 💡 권장 방법

**스크립트 사용**: `./scripts/release.sh patch`

**또는 수동**: `git tag v1.0.0 && git push origin v1.0.0`

끝!
