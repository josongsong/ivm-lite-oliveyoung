# Admin UI - AI Assistant Guide

## Quick Commands

```bash
pnpm dev               # 개발 서버 (localhost:3000)
pnpm lint              # ESLint 검사
pnpm lint:security     # Semgrep 보안 검사
pnpm check             # 전체 검사 (typecheck + lint + security)
pnpm build             # 프로덕션 빌드
```

---

## Architecture Rules (MUST FOLLOW)

### 1. FSD Layer Dependencies

```
app → widgets → features → shared
```

| From | Can Import |
|------|------------|
| `app/` | widgets, features, shared |
| `widgets/` | features, shared |
| `features/` | shared ONLY |
| `shared/` | external packages only |

**Violations are ESLint + Semgrep ERRORS.**

### 2. No Cross-Feature Imports

```typescript
// ❌ FORBIDDEN - features cannot import from other features
import { Something } from '@/features/contracts/components/X'
import { hook } from '@/features/explorer/hooks/useX'

// ✅ CORRECT - move shared code to shared layer
import { Something } from '@/shared/ui'
import { contractsApi } from '@/shared/api'
```

### 3. Shared UI Components (MANDATORY)

Native HTML elements are **BANNED** outside `shared/ui/`.

| Banned | Use Instead |
|--------|-------------|
| `<button>` | `<Button>`, `<IconButton>` |
| `<select>` | `<Select>` |

```typescript
// ❌ ESLint ERROR
<button onClick={handleClick}>Save</button>
<select value={val} onChange={e => setVal(e.target.value)}>

// ✅ CORRECT
import { Button, IconButton, Select } from '@/shared/ui'

<Button onClick={handleClick}>Save</Button>
<Button variant="secondary" size="sm">Cancel</Button>
<IconButton icon={<X size={16} />} onClick={onClose} aria-label="Close" />
<Select
  value={val}
  onChange={setVal}
  options={[
    { value: 'a', label: 'Option A' },
    { value: 'b', label: 'Option B' },
  ]}
/>
```

### Available Shared Components

```typescript
import {
  // Buttons
  Button,
  IconButton,

  // Form
  Select,
  Input,
  TextArea,
  Label,

  // Layout
  Tabs,
  Accordion,
  Modal,
  Table,
  Pagination,

  // Display
  Chip,
  Loading,
  ApiError,
  ErrorBoundary,
  PageHeader,
  YamlViewer,
} from '@/shared/ui'
```

---

## Security Rules (Semgrep)

### Banned Patterns

```typescript
// ❌ XSS Risk - BANNED
dangerouslySetInnerHTML={{ __html: content }}
element.innerHTML = userInput
document.write(anything)
location.href = 'javascript:...'

// ❌ Secrets - BANNED
const apiKey = 'sk-1234567890abcdef'
const password = 'hardcoded-secret'

// ⚠️ Warning - Avoid
const data = response as any  // Use proper types
fetch('http://api.example.com')  // Use HTTPS
```

### Safe Patterns

```typescript
// ✅ Use DOMPurify if HTML rendering is required
import DOMPurify from 'dompurify'
dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(content) }}

// ✅ Use environment variables
const apiKey = import.meta.env.VITE_API_KEY

// ✅ Proper typing
interface ApiResponse { data: User[] }
const data = response as ApiResponse
```

---

## Code Quality Rules

| Rule | Limit | Fix |
|------|-------|-----|
| Function complexity | ≤ 20 | Extract helper functions |
| Function lines | ≤ 150 | Split into components |
| Nesting depth | ≤ 5 | Early returns, extract |
| Parameters | ≤ 6 | Use options object |

---

## File Structure

```
src/
├── app/              # App initialization, routes, providers
├── features/         # Feature modules (self-contained)
│   └── [feature]/
│       ├── api/      # Feature-specific API (rarely used)
│       ├── components/
│       ├── hooks/
│       ├── ui/       # Page components
│       └── index.ts
├── shared/           # Shared across features
│   ├── api/          # API clients (contractsApi, explorerApi, etc.)
│   ├── types/        # TypeScript types
│   ├── ui/           # UI components
│   └── config/       # Constants, config
└── widgets/          # Layout components (Header, Sidebar, etc.)
```

---

## Checklist Before Commit

- [ ] `pnpm lint` passes with 0 errors
- [ ] `pnpm lint:security` passes with 0 blocking errors
- [ ] No native `<button>` or `<select>` outside shared/ui
- [ ] No cross-feature imports
- [ ] No `as any` (use proper types)
- [ ] No hardcoded secrets
- [ ] API calls use `@/shared/api`
