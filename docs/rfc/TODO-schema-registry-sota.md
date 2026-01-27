# TODO: Schema Registry SOTA 개선 계획

> 작성일: 2026-01-26  
> 상태: **BACKLOG** (나중에 진행)

## 현재 수준

- 프로덕션 코드: ~1,700 LoC
- 테스트 코드: ~4,500 LoC
- SOTA 대비 달성률: **~40%**

### 있는 것 ✅
- SemVer 버전 관리
- Status 라이프사이클 (DRAFT → ACTIVE → DEPRECATED → ARCHIVED)
- Checksum 무결성 검증
- L1 캐싱
- GSI 기반 목록 조회
- ACTIVE 상태 강제 (fail-closed)

---

## SOTA 달성 필요 기능

### Phase 1: 필수 (~3,150 LoC, 1-1.5주)

#### 1. 스키마 호환성 검사 (~1,500 LoC)
```
pkg/contracts/
├── compatibility/
│   ├── CompatibilityChecker.kt      # 호환성 검사 엔진
│   ├── BreakingChangeDetector.kt    # Breaking change 감지
│   ├── CompatibilityPolicy.kt       # BACKWARD, FORWARD, FULL, NONE
│   └── CompatibilityReport.kt       # 검사 결과 리포트
```

**검사 항목:**
- 필드 추가/삭제 시 호환성
- 타입 변경 감지
- Required → Optional 변경
- Default 값 변경

#### 2. Audit Trail (~900 LoC)
```
pkg/contracts/
├── audit/
│   ├── AuditLog.kt                  # 감사 로그 도메인
│   ├── AuditLogRepository.kt        # DynamoDB 저장소
│   └── AuditLogService.kt           # 로깅 서비스
```

**기록 항목:**
- WHO: 변경한 사용자/시스템
- WHAT: 변경된 계약 ID, 버전
- WHEN: 타임스탬프
- WHY: 변경 사유 (optional)
- DIFF: 이전 버전과의 차이

#### 3. Rollback 기능 (~750 LoC)
```
pkg/contracts/
├── rollback/
│   ├── RollbackService.kt           # 롤백 실행
│   ├── VersionHistory.kt            # 버전 히스토리 관리
│   └── RollbackPolicy.kt            # 롤백 정책
```

---

### Phase 2: 권장 (~3,900 LoC, 1.5주)

#### 4. 스키마 Diff/비교 (~1,050 LoC)
```
pkg/contracts/
├── diff/
│   ├── SchemaDiffEngine.kt          # Diff 계산 엔진
│   ├── DiffReport.kt                # Diff 결과 모델
│   └── DiffFormatter.kt             # 출력 포맷터 (JSON, Text)
```

#### 5. Dry-run 배포 (~1,050 LoC)
```
pkg/contracts/
├── dryrun/
│   ├── ImpactAnalyzer.kt            # 영향 범위 분석
│   ├── DryRunReport.kt              # 분석 결과
│   └── AffectedEntitiesCalculator.kt
```

**분석 항목:**
- 영향받는 뷰 목록
- 영향받는 엔티티 수 추정
- Breaking change 경고

#### 6. 승인 워크플로우 (~1,800 LoC)
```
pkg/contracts/
├── approval/
│   ├── ApprovalRequest.kt           # 승인 요청 도메인
│   ├── ApprovalWorkflow.kt          # 워크플로우 상태머신
│   ├── ApprovalPolicy.kt            # 승인 정책 (auto/manual)
│   ├── ApprovalRepository.kt        # 저장소
│   └── NotificationService.kt       # 알림 (Slack, Email)
```

**워크플로우:**
```
DRAFT → PENDING_APPROVAL → APPROVED → ACTIVE
                        ↘ REJECTED
```

---

### Phase 3: 고급 (~2,950 LoC, 1주)

#### 7. Canary 배포 (~1,350 LoC)
```
pkg/contracts/
├── canary/
│   ├── CanaryRouter.kt              # 트래픽 라우팅
│   ├── TrafficSplitter.kt           # 비율 기반 분배
│   ├── CanaryPolicy.kt              # 배포 정책
│   └── MetricsCollector.kt          # 성공률 수집
```

**배포 전략:**
- 1% → 10% → 50% → 100% 점진적 롤아웃
- 에러율 기반 자동 롤백

#### 8. Admin API (~1,600 LoC)
```
apps/adminapi/
├── routes/
│   ├── ContractRoutes.kt            # CRUD API
│   ├── AuditRoutes.kt               # 감사 로그 조회
│   ├── ApprovalRoutes.kt            # 승인 API
│   └── DiffRoutes.kt                # Diff API
├── dto/
│   ├── ContractDto.kt
│   └── ApprovalDto.kt
└── auth/
    └── RbacFilter.kt                # 권한 검사
```

---

## 총 예상 LoC

| 항목 | 프로덕션 | 테스트 | 합계 |
|------|----------|--------|------|
| 현재 | 1,700 | 4,500 | 6,200 |
| Phase 1 | 1,050 | 2,100 | 3,150 |
| Phase 2 | 1,300 | 2,600 | 3,900 |
| Phase 3 | 1,150 | 1,800 | 2,950 |
| **SOTA 총계** | **5,200** | **11,000** | **16,200** |

---

## 참고: SOTA 스키마 레지스트리

- [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/)
- [AWS Glue Schema Registry](https://docs.aws.amazon.com/glue/latest/dg/schema-registry.html)
- [Apicurio Registry](https://www.apicur.io/registry/)

---

## 우선순위 판단 기준

1. **운영 사고 방지**: 호환성 검사 > 승인 워크플로우
2. **디버깅 용이성**: Audit Trail > Diff
3. **배포 안정성**: Rollback > Dry-run > Canary
4. **사용성**: Admin API (UI 백엔드)

---

*이 문서는 TODO 참조용입니다. 실제 구현 시 RFC 문서로 전환하세요.*
