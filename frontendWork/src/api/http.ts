import { ApiError, type ApiErrorCode, type GameApi } from './types'

// 실제 Spring Boot 서버 호출. 개발 중에는 vite.config.ts의 proxy가 /api 를 8080으로 넘긴다.
async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response
  try {
    res = await fetch(`/api${path}`, {
      method,
      credentials: 'include',
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.')
  }
  if (res.status === 204) return undefined as T
  const data = await res.json().catch(() => null)
  // 2xx인데 본문을 읽지 못하면 서버가 처리했는지 알 수 없다. 성공(null)으로 넘기지 않는다 (backend_design 6.11)
  if (res.ok && data === null) throw new ApiError(res.status, 'RESPONSE_UNREADABLE', '서버 응답을 읽지 못했습니다.')
  if (!res.ok) {
    const fallback: ApiErrorCode = res.status === 401 ? 'UNAUTHORIZED' : res.status >= 500 ? 'INTERNAL_ERROR' : 'VALIDATION_ERROR'
    const code = (data?.code ?? fallback) as ApiErrorCode
    throw new ApiError(res.status, code, data?.message ?? '요청을 처리하지 못했습니다.')
  }
  return data as T
}

const query = (params: Record<string, number | undefined>) => {
  const q = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined) q.set(k, String(v)) })
  const s = q.toString()
  return s ? `?${s}` : ''
}

export const httpApi: GameApi = {
  signup: (body) => request('POST', '/auth/signup', body),
  login: (body) => request('POST', '/auth/login', body),
  logout: () => request('POST', '/auth/logout'),
  me: () => request('GET', '/auth/me'),
  getCases: () => request('GET', '/cases'),
  startSession: (caseId) => request('POST', `/cases/${caseId}/sessions`),
  getSession: (id) => request('GET', `/sessions/${id}`),
  investigate: (id, body) => request('POST', `/sessions/${id}/investigations`, body),
  exclude: (id, body) => request('POST', `/sessions/${id}/exclusions`, body),
  accuse: (id, body) => request('POST', `/sessions/${id}/accusations`, body),
  getResult: (id) => request('GET', `/sessions/${id}/result`),
  getMySessions: (p = {}) => request('GET', `/me/sessions${query(p)}`),
  getMySummary: () => request('GET', '/me/summary'),
  getMyActions: (id) => request('GET', `/me/sessions/${id}/actions`),
}
