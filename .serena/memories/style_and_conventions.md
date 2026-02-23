# IVM-Lite 코딩 스타일 및 컨벤션

## Kotlin
- 들여쓰기: 4 spaces
- 명명: camelCase(함수/변수), PascalCase(클래스), UPPER_SNAKE_CASE(상수)
- max_line_length: 140
- Detekt, ktlint 사용

## 에러 처리 (중요)
- **try-catch 금지** → Arrow의 `Either<DomainError, T>` 사용
- `either { }` 빌더, `raise()`, `.bind()` 패턴
- import: arrow.core.Either, arrow.core.raise.*

## TypeScript (admin-ui)
- 들여쓰기: 2 spaces
- ESLint + Prettier
- FSD (Feature-Sliced Design)

## 주석
- 한국어로 작성
