/**
 * 백엔드가 생기기 전까지 쓰는 브라우저 안의 임시 서버.
 * docs/api_spec.md 의 처리 순서·판정·점수 규칙을 그대로 따르므로, Spring 서비스 구현 때 참고 코드로도 쓸 수 있다.
 * 데이터는 localStorage에 저장된다. (비밀번호도 평문으로 저장되므로 실제 서비스에는 절대 쓰지 않는다)
 */
import {
  ApiError, type ActionLog, type ActionResponse, type ActionResult, type ActionType, type CaseRecordSummary,
  type CaseSummary, type GameApi, type GameState, type Grade, type SessionRecord, type SessionResult,
  type SessionStatus, type SuspectStatus, type User,
} from '../types'
import { caseSuspects, cases, clues, pokemon, prerequisites, rules, traits, type ClueRow, type Pokemon, type RuleRow } from './data'

type UserRow = User & { password: string }
type SessionRow = {
  sessionId: number; userId: number; caseId: number; status: SessionStatus; score: number
  wrongExclusionCount: number; accusationCount: number; bonusAwarded: boolean; grade: Grade | null
  startedAt: string; endedAt: string | null
}
type ActionRow = {
  actionId: number; sessionId: number; actionType: ActionType; clueId: number | null; caseSuspectId: number | null
  result: ActionResult; scoreDelta: number; dedupKey: string | null; requestId: string | null; createdAt: string
}
type Db = {
  users: UserRow[]
  currentUserId: number | null
  sessions: SessionRow[]
  sessionClues: { sessionId: number; clueId: number; collectedAt: string }[]
  sessionSuspects: { sessionId: number; caseSuspectId: number; status: SuspectStatus; basisClueId: number | null }[]
  actions: ActionRow[]
  /** requestId → 처리 결과 (같은 요청 재전송 시 그대로 돌려주기 위함) */
  responses: Record<string, Omit<ActionResponse, 'state'>>
}

const KEY = 'memory-trace-mock-db-v1'
const seed = (): Db => ({
  users: [{ userId: 1, loginId: 'detective01', password: 'pass1234!', nickname: '꼬마탐정', role: 'USER' }],
  currentUserId: null, sessions: [], sessionClues: [], sessionSuspects: [], actions: [], responses: {},
})

function load(): Db {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return JSON.parse(raw) as Db
  } catch { /* 저장소를 못 쓰면 새로 시작 */ }
  return seed()
}
function save(db: Db) {
  try { localStorage.setItem(KEY, JSON.stringify(db)) } catch { /* 무시 */ }
}

const now = () => {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
const delay = () => new Promise((r) => setTimeout(r, 120))
const nextId = (rows: { [k: string]: unknown }[], key: string) =>
  rows.reduce((m, r) => Math.max(m, r[key] as number), 0) + 1

const poke = (id: number) => pokemon.find((p) => p.id === id) as Pokemon
const clueById = (id: number) => clues.find((c) => c.clueId === id)
const ruleById = (id: number) => rules.find((r) => r.ruleId === id) as RuleRow
const suspectsOf = (caseId: number) => caseSuspects.filter((s) => s.caseId === caseId).sort((a, b) => a.order - b.order)
const publicUser = ({ password: _pw, ...u }: UserRow): User => u

/** 용의자가 규칙 조건을 만족하지 못하면 true (= 배제 성공). project_context.md 판정 계산표 그대로 */
function excludes(rule: RuleRow, p: Pokemon): boolean {
  const v = rule.conditionValue
  const types = [p.type1, p.type2].filter(Boolean)
  switch (rule.conditionType) {
    case 'HAS_TYPE': return !types.includes(v)
    case 'NOT_TYPE': return types.includes(v)
    case 'MAX_HEIGHT': return p.heightDm > Number(v)
    case 'MAX_WEIGHT': return p.weightHg > Number(v)
    case 'SHAPE_IS': return p.shape !== v
    case 'COLOR_IS': return p.color !== v
    case 'NOT_TRAIT': return traits.some((t) => t.pokemonId === p.id && t.traitCode === v)
  }
}

function gradeOf(s: SessionRow): Grade | null {
  if (s.status !== 'SOLVED') return null
  const wrongAccusations = s.accusationCount - 1
  if (s.wrongExclusionCount === 0 && wrongAccusations === 0 && s.bonusAwarded) return 'S'
  return s.score >= 80 ? 'A' : 'B'
}

function currentUser(db: Db): UserRow {
  const u = db.users.find((x) => x.userId === db.currentUserId)
  if (!u) throw new ApiError(401, 'UNAUTHORIZED', '로그인이 필요합니다.')
  return u
}

function ownedSession(db: Db, sessionId: number): SessionRow {
  const user = currentUser(db)
  const s = db.sessions.find((x) => x.sessionId === sessionId)
  if (!s) throw new ApiError(404, 'NOT_FOUND', '수사 기록을 찾을 수 없습니다.')
  if (s.userId !== user.userId) throw new ApiError(403, 'NOT_SESSION_OWNER', '다른 탐정의 수사 기록입니다.')
  return s
}

function buildState(db: Db, s: SessionRow): GameState {
  const cf = cases.find((c) => c.caseId === s.caseId)!
  const collected = db.sessionClues.filter((x) => x.sessionId === s.sessionId)
  const isCollected = (id: number) => collected.some((x) => x.clueId === id)
  const caseClues = clues.filter((c) => c.caseId === s.caseId)
  const ruleIds = [...new Set(caseClues.map((c) => c.ruleId).filter((x): x is number => x !== null))]
  return {
    sessionId: s.sessionId,
    status: s.status,
    score: s.score,
    accusationsLeft: 2 - s.accusationCount,
    wrongExclusionCount: s.wrongExclusionCount,
    case: { caseId: cf.caseId, caseCode: cf.caseCode, title: cf.title, briefing: cf.briefing, sceneImagePath: cf.sceneImagePath },
    sceneAreas: caseClues.filter((c) => c.kind === 'SCENE' && c.area).map((c) => {
      const [x, y, w, h] = c.area!
      return { clueId: c.clueId, x, y, w, h, collected: isCollected(c.clueId) }
    }),
    clues: collected
      .map((x) => ({ row: clueById(x.clueId)!, at: x.collectedAt }))
      .sort((a, b) => a.row.order - b.row.order)
      .map(({ row, at }) => ({
        clueId: row.clueId, clueCode: row.clueCode, kind: row.kind, name: row.name,
        summary: row.summary, detailText: row.detailText, collectedAt: at,
      })),
    // 어떤 단서에 어떤 규칙이 붙었는지는 보내지 않는다 (함정 단서 보호)
    traceDictionary: ruleIds.sort((a, b) => a - b).map((id) => ({ basisText: ruleById(id).basisText })),
    suspects: suspectsOf(s.caseId).map((cs) => {
      const p = poke(cs.pokemonId)
      const st = db.sessionSuspects.find((x) => x.sessionId === s.sessionId && x.caseSuspectId === cs.caseSuspectId)!
      return {
        caseSuspectId: cs.caseSuspectId, pokemonId: p.id, nameKo: p.nameKo, spritePath: `/sprites/${p.id}.png`,
        heightM: p.heightDm / 10, weightKg: p.weightHg / 10,
        types: [p.type1, p.type2].filter((t): t is string => !!t), color: p.color, shape: p.shape,
        traits: traits.filter((t) => t.pokemonId === p.id && t.isJudgment).map((t) => ({ traitCode: t.traitCode, description: t.description })),
        suspicionText: cs.suspicionText, status: st.status, basisClueId: st.basisClueId,
      }
    }),
  }
}

function log(db: Db, a: Omit<ActionRow, 'actionId' | 'createdAt'>) {
  db.actions.push({ ...a, actionId: nextId(db.actions, 'actionId'), createdAt: now() })
}

/** 행동 API 공통: requestId 확인 → 세션 확인 → 처리 → 저장 */
async function act(
  sessionId: number, requestId: string | undefined,
  handle: (db: Db, s: SessionRow) => Omit<ActionResponse, 'state'>,
): Promise<ActionResponse> {
  await delay()
  const db = load()
  if (!requestId) throw new ApiError(400, 'VALIDATION_ERROR', 'requestId가 필요합니다.')
  const done = db.responses[requestId]
  const s = ownedSession(db, sessionId)
  if (done) return { ...done, state: buildState(db, s) }
  if (s.status !== 'IN_PROGRESS') throw new ApiError(409, 'SESSION_ENDED', '이미 종료된 수사입니다.')
  const res = handle(db, s)
  db.responses[requestId] = res
  save(db)
  return { ...res, state: buildState(db, s) }
}

function inCase(clueId: number, caseId: number): ClueRow {
  const c = clueById(clueId)
  if (!c || c.caseId !== caseId) throw new ApiError(400, 'TARGET_NOT_IN_CASE', '이 사건의 단서가 아닙니다.')
  return c
}
function suspectInCase(caseSuspectId: number, caseId: number) {
  const cs = caseSuspects.find((x) => x.caseSuspectId === caseSuspectId)
  if (!cs || cs.caseId !== caseId) throw new ApiError(400, 'TARGET_NOT_IN_CASE', '이 사건의 용의자가 아닙니다.')
  return cs
}
const rejected = (message: string) => ({ result: 'REJECTED' as const, scoreDelta: 0, message })

function finish(s: SessionRow, status: 'SOLVED' | 'FAILED') {
  s.status = status
  s.endedAt = now()
  if (status === 'FAILED') s.score = 0
  s.grade = gradeOf(s)
}

export const mockApi: GameApi = {
  async signup({ loginId, password, nickname }) {
    await delay()
    const db = load()
    if (!/^[a-z0-9_]{4,30}$/.test(loginId)) throw new ApiError(400, 'VALIDATION_ERROR', '아이디는 영문 소문자·숫자·_ 4~30자입니다.')
    if (password.length < 8 || password.length > 64) throw new ApiError(400, 'VALIDATION_ERROR', '비밀번호는 8~64자입니다.')
    if (nickname.trim().length < 2 || nickname.trim().length > 20) throw new ApiError(400, 'VALIDATION_ERROR', '닉네임은 2~20자입니다.')
    if (db.users.some((u) => u.loginId === loginId)) throw new ApiError(409, 'LOGIN_ID_TAKEN', '이미 사용 중인 아이디입니다.')
    const user: UserRow = { userId: nextId(db.users, 'userId'), loginId, password, nickname: nickname.trim(), role: 'USER' }
    db.users.push(user)
    save(db)
    return publicUser(user)
  },

  async login({ loginId, password }) {
    await delay()
    const db = load()
    const u = db.users.find((x) => x.loginId === loginId && x.password === password)
    if (!u) throw new ApiError(401, 'UNAUTHORIZED', '아이디 또는 비밀번호가 맞지 않습니다.')
    db.currentUserId = u.userId
    save(db)
    return publicUser(u)
  },

  async logout() {
    await delay()
    const db = load()
    db.currentUserId = null
    save(db)
  },

  async me() {
    await delay()
    return publicUser(currentUser(load()))
  },

  async getCases() {
    await delay()
    const db = load()
    const user = currentUser(db)
    return cases.map((cf): CaseSummary => {
      const mine = db.sessions.filter((s) => s.userId === user.userId && s.caseId === cf.caseId)
      const done = mine.filter((s) => s.status === 'SOLVED')
      const best = done.sort((a, b) => b.score - a.score)[0]
      return {
        caseId: cf.caseId, caseCode: cf.caseCode, title: cf.title, subtitle: cf.subtitle, sceneImagePath: cf.sceneImagePath,
        myStatus: {
          inProgressSessionId: mine.find((s) => s.status === 'IN_PROGRESS')?.sessionId ?? null,
          playCount: mine.filter((s) => s.status !== 'IN_PROGRESS').length,
          bestGrade: best?.grade ?? null,
          bestScore: best?.score ?? null,
        },
      }
    })
  },

  async startSession(caseId) {
    await delay()
    const db = load()
    const user = currentUser(db)
    if (!cases.some((c) => c.caseId === caseId)) throw new ApiError(404, 'NOT_FOUND', '사건을 찾을 수 없습니다.')
    const open = db.sessions.find((s) => s.userId === user.userId && s.caseId === caseId && s.status === 'IN_PROGRESS')
    if (open) return buildState(db, open)
    const s: SessionRow = {
      sessionId: nextId(db.sessions, 'sessionId'), userId: user.userId, caseId, status: 'IN_PROGRESS', score: 100,
      wrongExclusionCount: 0, accusationCount: 0, bonusAwarded: false, grade: null, startedAt: now(), endedAt: null,
    }
    db.sessions.push(s)
    suspectsOf(caseId).forEach((cs) =>
      db.sessionSuspects.push({ sessionId: s.sessionId, caseSuspectId: cs.caseSuspectId, status: 'ACTIVE', basisClueId: null }))
    save(db)
    return buildState(db, s)
  },

  async getSession(sessionId) {
    await delay()
    const db = load()
    return buildState(db, ownedSession(db, sessionId))
  },

  investigate(sessionId, { requestId, clueId }) {
    return act(sessionId, requestId, (db, s) => {
      const clue = inCase(clueId, s.caseId)
      if (clue.kind !== 'SCENE') throw new ApiError(400, 'TARGET_NOT_IN_CASE', '현장에서 조사할 수 있는 곳이 아닙니다.')
      const has = (id: number) => db.sessionClues.some((x) => x.sessionId === s.sessionId && x.clueId === id)
      if (has(clueId)) {
        log(db, { sessionId: s.sessionId, actionType: 'INVESTIGATE', clueId, caseSuspectId: null, result: 'REJECTED', scoreDelta: 0, dedupKey: null, requestId })
        return rejected('이미 조사한 곳입니다.')
      }
      db.sessionClues.push({ sessionId: s.sessionId, clueId, collectedAt: now() })
      log(db, { sessionId: s.sessionId, actionType: 'INVESTIGATE', clueId, caseSuspectId: null, result: 'SUCCESS', scoreDelta: 0, dedupKey: `INV:${clueId}`, requestId })

      // 선행 조건이 모두 갖춰진 추론 단서를 더 생기지 않을 때까지 자동 수집
      const inferred: number[] = []
      for (let changed = true; changed;) {
        changed = false
        for (const inf of clues.filter((c) => c.caseId === s.caseId && c.kind === 'INFERENCE' && !has(c.clueId))) {
          const need = prerequisites.filter(([target]) => target === inf.clueId).map(([, req]) => req)
          if (need.length > 0 && need.every(has)) {
            db.sessionClues.push({ sessionId: s.sessionId, clueId: inf.clueId, collectedAt: now() })
            log(db, { sessionId: s.sessionId, actionType: 'INFER', clueId: inf.clueId, caseSuspectId: null, result: 'SUCCESS', scoreDelta: 0, dedupKey: `INF:${inf.clueId}`, requestId: null })
            inferred.push(inf.clueId)
            changed = true
          }
        }
      }
      return {
        result: 'SUCCESS', scoreDelta: 0,
        message: inferred.length ? '단서를 기록했습니다. 모은 단서에서 새로운 추론이 떠올랐습니다!' : '새 단서를 수첩에 기록했습니다.',
        newClueIds: [clueId], inferredClueIds: inferred,
      }
    })
  },

  exclude(sessionId, { requestId, clueId, caseSuspectId }) {
    return act(sessionId, requestId, (db, s) => {
      const clue = inCase(clueId, s.caseId)
      const cs = suspectInCase(caseSuspectId, s.caseId)
      const base = { sessionId: s.sessionId, actionType: 'EXCLUDE' as const, clueId, caseSuspectId, requestId }
      const reject = (msg: string) => {
        log(db, { ...base, result: 'REJECTED', scoreDelta: 0, dedupKey: null })
        return rejected(msg)
      }
      if (!db.sessionClues.some((x) => x.sessionId === s.sessionId && x.clueId === clueId)) return reject('아직 수집하지 않은 단서입니다.')
      const st = db.sessionSuspects.find((x) => x.sessionId === s.sessionId && x.caseSuspectId === caseSuspectId)!
      if (st.status !== 'ACTIVE') return reject('이미 용의선상에서 빠진 용의자입니다.')
      const dedupKey = `EXC:${clueId}:${caseSuspectId}`
      if (db.actions.some((a) => a.sessionId === s.sessionId && a.dedupKey === dedupKey)) return reject('이미 제출한 추리입니다.')

      const p = poke(cs.pokemonId)
      const ok = clue.ruleId !== null && excludes(ruleById(clue.ruleId), p)
      if (ok) {
        st.status = 'EXCLUDED_BY_EVIDENCE'
        st.basisClueId = clueId
        log(db, { ...base, result: 'SUCCESS', scoreDelta: 0, dedupKey })
        return { result: 'SUCCESS', scoreDelta: 0, message: `${p.nameKo}은(는) 이 증거와 모순됩니다. 용의선상에서 배제했습니다.` }
      }
      const delta = Math.max(-10, -s.score)
      s.score += delta
      s.wrongExclusionCount += 1
      log(db, { ...base, result: 'FAIL', scoreDelta: delta, dedupKey })
      return {
        result: 'FAIL', scoreDelta: delta,
        message: `이 증거로는 ${p.nameKo}을(를) 배제할 수 없습니다.`,
        rebuttalText: clue.rebuttalText ?? undefined,
      }
    })
  },

  accuse(sessionId, { requestId, caseSuspectId }) {
    return act(sessionId, requestId, (db, s) => {
      const cs = suspectInCase(caseSuspectId, s.caseId)
      const st = db.sessionSuspects.find((x) => x.sessionId === s.sessionId && x.caseSuspectId === caseSuspectId)!
      const base = { sessionId: s.sessionId, actionType: 'ACCUSE' as const, clueId: null, caseSuspectId, requestId }
      if (st.status !== 'ACTIVE') {
        log(db, { ...base, result: 'REJECTED', scoreDelta: 0, dedupKey: null })
        return rejected('용의선상에 남아 있는 용의자만 지목할 수 있습니다.')
      }
      const cf = cases.find((c) => c.caseId === s.caseId)!
      const p = poke(cs.pokemonId)
      s.accusationCount += 1
      const dedupKey = `ACC:${s.accusationCount}`

      if (cs.pokemonId === cf.answerPokemonId) {
        const wrong = db.sessionSuspects.filter((x) => x.sessionId === s.sessionId && x.caseSuspectId !== caseSuspectId)
        const bonus = wrong.every((x) => x.status === 'EXCLUDED_BY_EVIDENCE')
        const delta = bonus ? 10 : 0
        s.bonusAwarded = bonus
        s.score += delta
        finish(s, 'SOLVED')
        log(db, { ...base, result: 'SUCCESS', scoreDelta: delta, dedupKey })
        return { result: 'SUCCESS', scoreDelta: delta, bonusAwarded: bonus, message: `메타몽이 변신했던 모습은 ${p.nameKo}였습니다!` }
      }
      const delta = Math.max(-30, -s.score)
      s.score += delta
      st.status = 'ELIMINATED_BY_WRONG_ACCUSATION'
      if (s.accusationCount >= 2) finish(s, 'FAILED')
      log(db, { ...base, result: 'FAIL', scoreDelta: delta, dedupKey })
      return {
        result: 'FAIL', scoreDelta: delta,
        message: s.status === 'FAILED' ? '두 번째 지목도 빗나갔습니다. 수사는 실패로 끝났습니다.' : `${p.nameKo}은(는) 메타몽이 아니었습니다.`,
      }
    })
  },

  async getResult(sessionId) {
    await delay()
    const db = load()
    const s = ownedSession(db, sessionId)
    if (s.status === 'IN_PROGRESS') throw new ApiError(409, 'SESSION_IN_PROGRESS', '아직 진행 중인 수사입니다.')
    const cf = cases.find((c) => c.caseId === s.caseId)!
    const ans = poke(cf.answerPokemonId)
    return {
      sessionId: s.sessionId, caseId: cf.caseId, caseCode: cf.caseCode, title: cf.title,
      status: s.status, score: s.score, grade: s.grade, bonusAwarded: s.bonusAwarded,
      wrongExclusionCount: s.wrongExclusionCount, accusationCount: s.accusationCount,
      answer: { pokemonId: ans.id, nameKo: ans.nameKo, spritePath: `/sprites/${ans.id}.png` },
      truthText: cf.truthText, motiveText: cf.motiveText, twistText: cf.twistText,
      exclusions: suspectsOf(s.caseId).filter((cs) => cs.pokemonId !== cf.answerPokemonId).map((cs) => {
        const st = db.sessionSuspects.find((x) => x.sessionId === s.sessionId && x.caseSuspectId === cs.caseSuspectId)!
        return {
          caseSuspectId: cs.caseSuspectId, nameKo: poke(cs.pokemonId).nameKo, status: st.status,
          basisClueName: st.basisClueId ? clueById(st.basisClueId)!.name : null,
        }
      }),
      startedAt: s.startedAt, endedAt: s.endedAt!,
    } satisfies SessionResult
  },

  async getMySessions({ caseId, page = 0, size = 10 } = {}) {
    await delay()
    const db = load()
    const user = currentUser(db)
    const rows = db.sessions
      .filter((s) => s.userId === user.userId && (caseId === undefined || s.caseId === caseId))
      .sort((a, b) => b.sessionId - a.sessionId)
    const content = rows.slice(page * size, page * size + size).map((s): SessionRecord => {
      const cf = cases.find((c) => c.caseId === s.caseId)!
      const sceneIds = clues.filter((c) => c.caseId === s.caseId && c.kind === 'SCENE').map((c) => c.clueId)
      return {
        sessionId: s.sessionId, caseCode: cf.caseCode, title: cf.title, status: s.status, score: s.score, grade: s.grade,
        wrongExclusionCount: s.wrongExclusionCount,
        collectedClueCount: db.sessionClues.filter((x) => x.sessionId === s.sessionId && sceneIds.includes(x.clueId)).length,
        totalSceneClueCount: sceneIds.length,
        startedAt: s.startedAt, endedAt: s.endedAt,
      }
    })
    return { content, page, size, totalElements: rows.length }
  },

  async getMySummary() {
    await delay()
    const db = load()
    const user = currentUser(db)
    return cases.map((cf): CaseRecordSummary => {
      const mine = db.sessions.filter((s) => s.userId === user.userId && s.caseId === cf.caseId && s.status !== 'IN_PROGRESS')
      const solved = mine.filter((s) => s.status === 'SOLVED').sort((a, b) => b.score - a.score)
      return {
        caseId: cf.caseId, caseCode: cf.caseCode, title: cf.title, playCount: mine.length, solvedCount: solved.length,
        bestGrade: solved[0]?.grade ?? null, bestScore: solved[0]?.score ?? null,
        lastPlayedAt: mine.map((s) => s.endedAt!).sort().at(-1) ?? null,
      }
    })
  },

  async getMyActions(sessionId) {
    await delay()
    const db = load()
    const s = ownedSession(db, sessionId)
    return db.actions.filter((a) => a.sessionId === s.sessionId).map((a): ActionLog => ({
      actionType: a.actionType,
      clueName: a.clueId ? clueById(a.clueId)!.name : null,
      suspectName: a.caseSuspectId ? poke(caseSuspects.find((x) => x.caseSuspectId === a.caseSuspectId)!.pokemonId).nameKo : null,
      result: a.result, scoreDelta: a.scoreDelta, createdAt: a.createdAt,
    }))
  },
}
