// Zustand 테스트 컴포넌트
import { useAppStore } from '@/shared/store'

export function ZustandTest() {
  const { sidebarOpen, setSidebarOpen, theme, setTheme } = useAppStore()

  return (
    <div className="p-4 space-y-4">
      <h2 className="text-xl font-bold">Zustand Store 테스트</h2>
      
      <div className="space-y-2">
        <div>
          <span>Sidebar Open: </span>
          <span className="font-mono">{sidebarOpen ? 'true' : 'false'}</span>
        </div>
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="bg-accent-purple px-4 py-2 rounded"
        >
          Toggle Sidebar
        </button>
      </div>

      <div className="space-y-2">
        <div>
          <span>Theme: </span>
          <span className="font-mono">{theme}</span>
        </div>
        <button
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          className="bg-accent-orange px-4 py-2 rounded"
        >
          Toggle Theme
        </button>
      </div>
    </div>
  )
}
