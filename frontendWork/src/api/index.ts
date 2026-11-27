import { httpApi } from './http'
import { mockApi } from './mock/server'
import type { GameApi } from './types'

/**
 * VITE_USE_MOCK=false 이면 실제 Spring 서버, 그 외에는 브라우저 안의 임시 서버를 쓴다.
 * 백엔드가 준비되면 frontendWork/.env.development 에 VITE_USE_MOCK=false 한 줄만 넣으면 된다.
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'
export const api: GameApi = USE_MOCK ? mockApi : httpApi

// crypto.randomUUID는 https·localhost에서만 동작하므로 http 배포 환경을 위한 대체 구현을 둔다.
export const newRequestId = (): string =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })
export * from './types'
