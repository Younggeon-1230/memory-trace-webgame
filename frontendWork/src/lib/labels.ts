// PokéAPI 영문 값을 화면용 한글로 바꾸는 표. 판정은 서버가 영문 값으로 한다.

export const TYPE_KO: Record<string, string> = {
  normal: '노말', fire: '불꽃', water: '물', grass: '풀', electric: '전기', ice: '얼음', fighting: '격투',
  poison: '독', ground: '땅', flying: '비행', psychic: '에스퍼', bug: '벌레', rock: '바위', ghost: '고스트',
  dragon: '드래곤', dark: '악', steel: '강철', fairy: '페어리',
}

export const COLOR_KO: Record<string, string> = {
  black: '검정', blue: '파랑', brown: '갈색', gray: '회색', green: '초록', pink: '분홍',
  purple: '보라', red: '빨강', white: '흰색', yellow: '노랑',
}

export const SHAPE_KO: Record<string, string> = {
  ball: '공 모양', squiggle: '구불구불형', fish: '물고기형', arms: '팔 달린 형', blob: '덩어리형',
  upright: '직립형', legs: '다리형', quadruped: '네 발 보행형', wings: '날개형', tentacles: '촉수형',
  heads: '여러 머리형', humanoid: '인간형', 'bug-wings': '곤충 날개형', armor: '갑옷형',
}

export const ko = (table: Record<string, string>, v: string) => table[v] ?? v

export const STATUS_KO = {
  ACTIVE: '용의선상',
  EXCLUDED_BY_EVIDENCE: '증거로 배제',
  ELIMINATED_BY_WRONG_ACCUSATION: '오지목 제외',
} as const

export const SESSION_KO = { IN_PROGRESS: '진행 중', SOLVED: '해결', FAILED: '실패' } as const

export const ACTION_KO = { INVESTIGATE: '현장 조사', INFER: '추론', EXCLUDE: '배제', ACCUSE: '지목' } as const
export const RESULT_KO = { SUCCESS: '성공', FAIL: '실패', REJECTED: '처리 안 됨' } as const

/** '2026-12-03T14:20:05' → '2026-12-03 14:20' */
export const fmtDate = (s: string | null) => (s ? s.replace('T', ' ').slice(0, 16) : '–')

/** 'CASE03' → 'CASE 03' */
export const caseLabel = (code: string) => code.replace(/^CASE(\d+)/, 'CASE $1')
