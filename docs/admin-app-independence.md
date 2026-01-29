# Admin 앱 독립성

## 런타임 독립성 ✅

**Admin 앱은 runtimeapi와 완전히 독립적으로 실행 가능합니다.**

| 항목 | Runtime API | Admin App |
|------|------------|-----------|
| **포트** | 8080 | 8081 |
| **프로세스** | 별도 프로세스 | 별도 프로세스 |
| **실행** | `./gradlew run` | `./gradlew runAdmin` |
| **의존성** | 없음 | 없음 (독립 실행 가능) |

---

## 코드 공유 ⚠️

**Wiring 모듈은 공유하지만, 런타임은 독립적입니다.**

### 현재 구조

```
apps/
├── runtimeapi/
│   └── wiring/
│       ├── TracingModule.kt      ← 공유
│       ├── InfraModule.kt        ← 공유
│       ├── AdapterModule.kt      ← 공유
│       ├── WorkflowModule.kt     ← 공유
│       └── WorkerModule.kt       ← 공유
└── admin/
    └── wiring/
        └── AdminModule.kt        ← Admin 전용
            └── adminAllModules    ← 위 모듈들 참조
```

### 공유 이유

1. **코드 중복 방지**: 동일한 인프라 설정을 재사용
2. **일관성 유지**: 같은 데이터베이스, 같은 Worker 사용
3. **유지보수 용이**: 인프라 변경 시 한 곳만 수정

### 독립성 보장

- ✅ **별도 포트**: 8080 vs 8081
- ✅ **별도 프로세스**: 각각 독립적으로 실행/종료 가능
- ✅ **Worker 제어**: Admin에서는 Worker를 시작하지 않음 (모니터링만)
- ✅ **데이터베이스**: 같은 DB를 읽지만, 쓰기는 각각 독립적

---

## 실행 시나리오

### 시나리오 1: Admin만 실행 (모니터링 전용)

```bash
# Admin 앱만 실행
./gradlew runAdmin

# 접속: http://localhost:8081/
# 기능: Outbox 통계, Worker 상태 조회 (Worker는 실행 안 됨)
```

**용도**: 
- 시스템 모니터링
- 문제 진단
- 통계 확인

---

### 시나리오 2: Runtime API만 실행 (비즈니스 로직 전용)

```bash
# Runtime API만 실행
./gradlew run

# 접속: http://localhost:8080/
# 기능: Ingest, Query, Worker 실행
```

**용도**:
- 비즈니스 API 제공
- 데이터 처리
- Worker 실행

---

### 시나리오 3: 둘 다 실행 (Production)

```bash
# 터미널 1: Runtime API
./gradlew run

# 터미널 2: Admin
./gradlew runAdmin
```

**용도**:
- Runtime API: 비즈니스 로직 처리
- Admin: 실시간 모니터링

---

## 완전 독립화 (선택사항)

만약 완전히 독립적인 구조를 원한다면:

### 옵션 1: 공통 Wiring 모듈 추출

```
apps/shared-wiring/
├── TracingModule.kt
├── InfraModule.kt
├── AdapterModule.kt
└── ...

apps/runtimeapi/wiring/
└── RuntimeApiModule.kt  ← shared-wiring 참조

apps/admin/wiring/
└── AdminModule.kt       ← shared-wiring 참조
```

### 옵션 2: Admin 전용 Wiring 생성

```
apps/admin/wiring/
├── AdminTracingModule.kt
├── AdminInfraModule.kt
├── AdminAdapterModule.kt
└── AdminModule.kt
```

**단점**: 코드 중복 발생

---

## 결론

**현재 구조는 적절합니다:**

1. ✅ **런타임 독립성**: 완전히 독립적으로 실행 가능
2. ✅ **코드 공유**: 중복 없이 인프라 모듈 재사용
3. ✅ **유지보수성**: 인프라 변경 시 한 곳만 수정

**패키지 의존성은 있지만, 런타임 의존성은 없습니다.**

- 패키지 의존성: 코드 공유 (코드 레벨)
- 런타임 의존성: 없음 (프로세스 레벨)

---

## 권장 사항

**현재 구조 유지 권장:**

- 코드 중복 없이 인프라 모듈 재사용
- 런타임은 완전히 독립적
- 유지보수 용이

**완전 독립화가 필요한 경우:**

- Admin 앱이 다른 기술 스택 사용 시
- Admin 앱을 별도 레포지토리로 분리 시
- Admin 앱이 다른 데이터베이스 사용 시
