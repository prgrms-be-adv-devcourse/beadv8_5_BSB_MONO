'use client'

import { QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import type { ReactNode } from 'react'

import { AuthSessionRestorer } from '@/features/auth'
import { getQueryClient } from '@/shared/api'
import { InAppHistoryTracker } from '@/shared/lib'
import { ToastProvider } from '@/shared/ui'

export function Providers({ children }: { children: ReactNode }) {
  const queryClient = getQueryClient()
  return (
    <QueryClientProvider client={queryClient}>
      <AuthSessionRestorer />
      <InAppHistoryTracker />
      <ToastProvider>{children}</ToastProvider>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  )
}
