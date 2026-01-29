# Zustand Store 사용 가이드

## 기본 사용법

```tsx
import { useAppStore } from '@/shared/store'

function MyComponent() {
  // 전체 store 사용
  const { sidebarOpen, setSidebarOpen } = useAppStore()
  
  // 또는 selector 사용 (성능 최적화)
  const sidebarOpen = useAppStore((state) => state.sidebarOpen)
  const setSidebarOpen = useAppStore((state) => state.setSidebarOpen)
  
  return (
    <button onClick={() => setSidebarOpen(!sidebarOpen)}>
      {sidebarOpen ? 'Close' : 'Open'}
    </button>
  )
}
```

## Store 구조

- **UI State**: sidebarOpen, theme
- **Filters**: contractSearchTerm, selectedContractKind, highlightedSlice
- **View Preferences**: viewMode

## Persist 설정

일부 상태(theme, sidebarOpen, viewMode)는 localStorage에 자동 저장됩니다.
