import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'

import type { WorkspaceRole } from '@/features/auth/api'
import { useCurrentUser } from '@/features/auth/use-auth'

const STORAGE_KEY = 'cf_workspace'

interface WorkspaceContextValue {
  workspaceId: string
  workspaceName: string
  role: WorkspaceRole
  /** UX hints only — the backend checks every permission again. */
  canManageRecords: boolean
  canManageMembers: boolean
  workspaces: { id: string; name: string; role: WorkspaceRole }[]
  switchWorkspace: (id: string) => void
}

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null)

/** Remembers which workspace is selected. Must be rendered inside RequireAuth. */
export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { data: user } = useCurrentUser()
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState(() => localStorage.getItem(STORAGE_KEY))

  const value = useMemo<WorkspaceContextValue | null>(() => {
    const workspaces = user?.workspaces ?? []
    const current = workspaces.find((w) => w.id === selectedId) ?? workspaces[0]
    if (!current) return null
    return {
      workspaceId: current.id,
      workspaceName: current.name,
      role: current.role,
      canManageRecords: current.role === 'OWNER' || current.role === 'MANAGER',
      canManageMembers: current.role === 'OWNER',
      workspaces,
      switchWorkspace: (id: string) => {
        localStorage.setItem(STORAGE_KEY, id)
        setSelectedId(id)
        // Drop cached lists of the previous workspace
        queryClient.removeQueries({ predicate: (q) => q.queryKey[0] === 'ws' })
      },
    }
  }, [user, selectedId, queryClient])

  if (!value) return null
  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>
}

export function useWorkspace() {
  const context = useContext(WorkspaceContext)
  if (!context) throw new Error('useWorkspace must be used inside WorkspaceProvider')
  return context
}
