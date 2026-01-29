import { create } from 'zustand'
import { devtools, persist } from 'zustand/middleware'

interface AppState {
  // UI State
  sidebarOpen: boolean
  setSidebarOpen: (open: boolean) => void
  
  // Theme
  theme: 'dark' | 'light'
  setTheme: (theme: 'dark' | 'light') => void
  
  // Filters & Search
  contractSearchTerm: string
  setContractSearchTerm: (term: string) => void
  
  selectedContractKind: string
  setSelectedContractKind: (kind: string) => void
  
  // Pipeline filters
  highlightedSlice: string | null
  setHighlightedSlice: (slice: string | null) => void
  
  // Outbox filters
  activeOutboxTab: 'recent' | 'failed' | 'dlq' | 'stale'
  setActiveOutboxTab: (tab: 'recent' | 'failed' | 'dlq' | 'stale') => void
  
  // View preferences
  viewMode: 'grid' | 'list'
  setViewMode: (mode: 'grid' | 'list') => void
}

export const useAppStore = create<AppState>()(
  devtools(
    persist(
      (set) => ({
        // UI State
        sidebarOpen: true,
        setSidebarOpen: (open) => set({ sidebarOpen: open }),
        
        // Theme
        theme: 'dark',
        setTheme: (theme) => set({ theme }),
        
        // Filters & Search
        contractSearchTerm: '',
        setContractSearchTerm: (term) => set({ contractSearchTerm: term }),
        
        selectedContractKind: 'all',
        setSelectedContractKind: (kind) => set({ selectedContractKind: kind }),
        
        // Pipeline filters
        highlightedSlice: null,
        setHighlightedSlice: (slice) => set({ highlightedSlice: slice }),
        
        // Outbox filters
        activeOutboxTab: 'recent',
        setActiveOutboxTab: (tab) => set({ activeOutboxTab: tab }),
        
        // View preferences
        viewMode: 'grid',
        setViewMode: (mode) => set({ viewMode: mode }),
      }),
      {
        name: 'ivm-admin-storage',
        partialize: (state) => ({
          theme: state.theme,
          sidebarOpen: state.sidebarOpen,
          viewMode: state.viewMode,
        }),
      }
    ),
    { name: 'AppStore' }
  )
)
