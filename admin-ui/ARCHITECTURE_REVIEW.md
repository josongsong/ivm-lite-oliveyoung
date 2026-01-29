# FSD 아키텍처 비판적 검토 결과

## ✅ 잘된 점

### 1. 레이어 분리
- ✅ `app`, `shared`, `features`, `widgets` 레이어가 명확히 분리됨
- ✅ 의존성 방향이 올바름 (features → shared, app → features/widgets/shared)
- ✅ Cross-layer import 위반 없음

### 2. Public API 패턴
- ✅ 모든 레이어/슬라이스에 `index.ts` 존재
- ✅ 외부에서는 index.ts를 통해서만 접근

### 3. 타입 안정성
- ✅ `import type` 사용으로 타입만 import
- ✅ TypeScript strict mode 활성화

---

## ⚠️ 개선 필요 사항

### 1. 같은 Feature 내부 상대 경로 Import
**현재:**
```tsx
// ContractDetail.tsx
import { ContractDescription } from '../components/ContractDescription'
```

**문제점:**
- FSD 규칙상 허용되지만, 일관성 부족
- `ContractDescription`이 `index.ts`에 export되지 않음

**개선안:**
```tsx
// features/contracts/index.ts에 추가
export { ContractDescription } from './components/ContractDescription'

// ContractDetail.tsx에서
import { ContractDescription } from '@/features/contracts'
```

---

### 2. 에러 처리 부족
**현재:**
- `fetchApi`에서 에러를 던지지만, 대부분의 컴포넌트에서 try-catch 없음
- Outbox에서만 `catch` 사용

**개선안:**
```tsx
// shared/api/client.ts에 에러 타입 추가
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

// 또는 React Query의 onError 사용
```

---

### 3. 공통 UI 컴포넌트 부족
**현재:**
- 각 feature에서 로딩 스피너를 직접 구현
- 에러 메시지 UI가 중복될 가능성

**개선안:**
```tsx
// shared/ui/LoadingSpinner.tsx
// shared/ui/ErrorMessage.tsx
// shared/ui/EmptyState.tsx
```

---

### 4. API 클라이언트 개선 여지
**현재:**
```ts
export async function fetchApi<T>(endpoint: string): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`)
  if (!response.ok) {
    throw new Error(`API Error: ${response.status} ${response.statusText}`)
  }
  return response.json()
}
```

**개선안:**
- 타임아웃 추가
- 재시도 로직
- 요청 인터셉터 (인증 토큰 등)
- 응답 인터셉터 (에러 처리)

---

### 5. 타입 정의 위치
**현재:**
- 모든 타입이 `shared/types`에 있음
- 일부 타입은 특정 feature에만 사용될 수 있음

**검토 필요:**
- Feature-specific 타입은 해당 feature 내부에 두는 것이 나을 수도 있음
- 하지만 현재는 API 응답 타입이므로 shared에 두는 것이 맞음 ✅

---

## 📊 통계

- **총 파일 수**: 24개
- **레이어 분포**: app(5), shared(7), features(10), widgets(2)
- **Import 패턴**: 모두 `@/` alias 사용 ✅
- **타입 안정성**: `import type` 사용 ✅
- **FSD 규칙 위반**: 0건 ✅

---

## 🎯 우선순위별 개선 사항

### High Priority
1. ✅ ContractDescription을 index.ts로 export (일관성)
2. ⚠️ 에러 처리 패턴 통일

### Medium Priority
3. 공통 UI 컴포넌트 추출 (Loading, Error, Empty)
4. API 클라이언트 개선 (타임아웃, 재시도)

### Low Priority
5. 코드 스플리팅 (lazy loading)
6. 테스트 추가

---

## 결론

**전반적으로 FSD 아키텍처 규칙을 잘 따르고 있습니다!** ✅

주요 문제점은 **일관성**과 **에러 처리** 부분이며, 구조적 문제는 없습니다.
