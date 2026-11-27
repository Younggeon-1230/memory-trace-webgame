import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, type User } from '../api'

type AuthValue = {
  user: User | null
  /** 첫 로그인 상태 확인이 끝났는지 */
  ready: boolean
  login: (loginId: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    api.me().then(setUser).catch(() => setUser(null)).finally(() => setReady(true))
  }, [])

  const login = useCallback(async (loginId: string, password: string) => {
    setUser(await api.login({ loginId, password }))
  }, [])

  const logout = useCallback(async () => {
    await api.logout().catch(() => undefined)
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, ready, login, logout }), [user, ready, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// oxlint-disable-next-line react/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('AuthProvider 안에서만 사용할 수 있습니다.')
  return ctx
}
