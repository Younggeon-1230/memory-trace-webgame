// docs/api_spec.md 의 응답 구조를 그대로 옮긴 타입

export type User = {
  userId: number
  loginId: string
  nickname: string
  role: 'USER' | 'ADMIN'
}

export type SessionStatus = 'IN_PROGRESS' | 'SOLVED' | 'FAILED'
export type Grade = 'S' | 'A' | 'B'
export type SuspectStatus = 'ACTIVE' | 'EXCLUDED_BY_EVIDENCE' | 'ELIMINATED_BY_WRONG_ACCUSATION'
export type ActionType = 'INVESTIGATE' | 'INFER' | 'EXCLUDE' | 'ACCUSE'
export type ActionResult = 'SUCCESS' | 'FAIL' | 'REJECTED'

export type CaseSummary = {
  caseId: number
  caseCode: string
  title: string
  subtitle: string
  sceneImagePath: string
  myStatus: {
    inProgressSessionId: number | null
    playCount: number
    bestGrade: Grade | null
    bestScore: number | null
  }
}

export type SceneArea = { clueId: number; x: number; y: number; w: number; h: number; collected: boolean }

export type Clue = {
  clueId: number
  clueCode: string
  kind: 'SCENE' | 'INFERENCE'
  name: string
  summary: string
  detailText: string
  collectedAt: string
}

export type Suspect = {
  caseSuspectId: number
  pokemonId: number
  nameKo: string
  spritePath: string
  heightM: number
  weightKg: number
  types: string[]
  color: string
  shape: string
  traits: { traitCode: string; description: string }[]
  suspicionText: string
  status: SuspectStatus
  basisClueId: number | null
}

export type GameState = {
  sessionId: number
  status: SessionStatus
  score: number
  accusationsLeft: number
  wrongExclusionCount: number
  case: { caseId: number; caseCode: string; title: string; briefing: string; sceneImagePath: string }
  sceneAreas: SceneArea[]
  clues: Clue[]
  traceDictionary: { basisText: string }[]
  suspects: Suspect[]
}

export type ActionResponse = {
  result: ActionResult
  scoreDelta: number
  message: string
  rebuttalText?: string
  newClueIds?: number[]
  inferredClueIds?: number[]
  bonusAwarded?: boolean
  state: GameState
}

export type SessionResult = {
  sessionId: number
  caseId: number
  caseCode: string
  title: string
  status: Exclude<SessionStatus, 'IN_PROGRESS'>
  score: number
  grade: Grade | null
  bonusAwarded: boolean
  wrongExclusionCount: number
  accusationCount: number
  answer: { pokemonId: number; nameKo: string; spritePath: string }
  truthText: string
  motiveText: string
  twistText: string
  exclusions: { caseSuspectId: number; nameKo: string; status: SuspectStatus; basisClueName: string | null }[]
  startedAt: string
  endedAt: string
}

export type SessionRecord = {
  sessionId: number
  caseCode: string
  title: string
  status: SessionStatus
  score: number
  grade: Grade | null
  wrongExclusionCount: number
  collectedClueCount: number
  totalSceneClueCount: number
  startedAt: string
  endedAt: string | null
}

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number }

export type CaseRecordSummary = {
  caseId: number
  caseCode: string
  title: string
  playCount: number
  solvedCount: number
  bestGrade: Grade | null
  bestScore: number | null
  lastPlayedAt: string | null
}

export type ActionLog = {
  actionType: ActionType
  clueName: string | null
  suspectName: string | null
  result: ActionResult
  scoreDelta: number
  createdAt: string
}

export type ApiErrorCode =
  | 'VALIDATION_ERROR' | 'TARGET_NOT_IN_CASE' | 'UNAUTHORIZED' | 'FORBIDDEN' | 'NOT_SESSION_OWNER'
  | 'NOT_FOUND' | 'LOGIN_ID_TAKEN' | 'SESSION_ENDED' | 'SESSION_IN_PROGRESS' | 'CASE_UNAVAILABLE' | 'INTERNAL_ERROR' | 'NETWORK_ERROR' | 'RESPONSE_UNREADABLE'

export class ApiError extends Error {
  status: number
  code: ApiErrorCode
  constructor(status: number, code: ApiErrorCode, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

/** 화면이 사용하는 API 전체. 실제 서버(http.ts)와 임시 서버(mock/server.ts)가 같은 모양을 구현한다. */
export interface GameApi {
  signup(body: { loginId: string; password: string; nickname: string }): Promise<User>
  login(body: { loginId: string; password: string }): Promise<User>
  logout(): Promise<void>
  me(): Promise<User>
  getCases(): Promise<CaseSummary[]>
  startSession(caseId: number): Promise<GameState>
  getSession(sessionId: number): Promise<GameState>
  investigate(sessionId: number, body: { requestId: string; clueId: number }): Promise<ActionResponse>
  exclude(sessionId: number, body: { requestId: string; clueId: number; caseSuspectId: number }): Promise<ActionResponse>
  accuse(sessionId: number, body: { requestId: string; caseSuspectId: number }): Promise<ActionResponse>
  getResult(sessionId: number): Promise<SessionResult>
  getMySessions(params?: { caseId?: number; page?: number; size?: number }): Promise<Page<SessionRecord>>
  getMySummary(): Promise<CaseRecordSummary[]>
  getMyActions(sessionId: number): Promise<ActionLog[]>
}

/**
 * 서버가 요청을 처리했는지 알 수 없는 오류인지 (backend_design 6.11).
 * 연결 실패, 5xx(프록시 502·504 포함), 본문을 읽지 못한 2xx, ApiError가 아닌 예외.
 * 이 경우 같은 requestId로 다시 보내야 한다. 4xx는 서버가 거절을 확정한 것이므로 해당하지 않는다.
 */
export function isOutcomeUnknown(e: unknown): boolean {
  if (!(e instanceof ApiError)) return true
  return e.code === 'NETWORK_ERROR' || e.code === 'RESPONSE_UNREADABLE' || e.status === 0 || e.status >= 500
}
