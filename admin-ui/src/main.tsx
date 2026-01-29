import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AppRoutes, QueryProvider } from '@/app'
import { ToastContainer } from '@/shared/ui'
import '@/app/styles/index.css'
import '@/app/styles/App.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryProvider>
      <BrowserRouter>
        <AppRoutes />
        <ToastContainer />
      </BrowserRouter>
    </QueryProvider>
  </StrictMode>,
)
