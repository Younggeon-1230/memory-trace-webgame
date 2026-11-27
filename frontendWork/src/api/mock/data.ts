// docs/02_sample.sql 과 같은 데이터. 백엔드가 생기면 이 파일은 쓰지 않는다.

export type Pokemon = {
  id: number; nameKo: string; heightDm: number; weightHg: number
  type1: string; type2: string | null; color: string; shape: string
}
export type Trait = { pokemonId: number; traitCode: string; description: string; isJudgment: boolean }
export type CaseRow = {
  caseId: number; caseCode: string; title: string; subtitle: string; briefing: string
  sceneImagePath: string; answerPokemonId: number; truthText: string; motiveText: string; twistText: string
}
export type CaseSuspectRow = { caseSuspectId: number; caseId: number; pokemonId: number; suspicionText: string; order: number }
export type ConditionType = 'HAS_TYPE' | 'NOT_TYPE' | 'MAX_HEIGHT' | 'MAX_WEIGHT' | 'SHAPE_IS' | 'COLOR_IS' | 'NOT_TRAIT'
export type RuleRow = { ruleId: number; conditionType: ConditionType; conditionValue: string; basisText: string }
export type ClueRow = {
  clueId: number; caseId: number; clueCode: string; kind: 'SCENE' | 'INFERENCE'; name: string; summary: string
  detailText: string; rebuttalText: string | null; ruleId: number | null; isCore: boolean
  area: [number, number, number, number] | null; order: number
}

export const pokemon: Pokemon[] = [
  { id: 54, nameKo: '고라파덕', heightDm: 8, weightHg: 196, type1: 'water', type2: null, color: 'yellow', shape: 'upright' },
  { id: 64, nameKo: '윤겔라', heightDm: 13, weightHg: 565, type1: 'psychic', type2: null, color: 'brown', shape: 'upright' },
  { id: 93, nameKo: '고우스트', heightDm: 16, weightHg: 1, type1: 'ghost', type2: 'poison', color: 'purple', shape: 'arms' },
  { id: 200, nameKo: '무우마', heightDm: 7, weightHg: 10, type1: 'ghost', type2: null, color: 'gray', shape: 'ball' },
  { id: 4, nameKo: '파이리', heightDm: 6, weightHg: 85, type1: 'fire', type2: null, color: 'red', shape: 'upright' },
  { id: 7, nameKo: '꼬부기', heightDm: 5, weightHg: 90, type1: 'water', type2: null, color: 'blue', shape: 'upright' },
  { id: 81, nameKo: '코일', heightDm: 3, weightHg: 60, type1: 'electric', type2: 'steel', color: 'gray', shape: 'arms' },
  { id: 155, nameKo: '브케인', heightDm: 5, weightHg: 79, type1: 'fire', type2: null, color: 'yellow', shape: 'humanoid' },
  { id: 133, nameKo: '이브이', heightDm: 3, weightHg: 65, type1: 'normal', type2: null, color: 'brown', shape: 'quadruped' },
  { id: 29, nameKo: '니드런♀', heightDm: 4, weightHg: 70, type1: 'poison', type2: null, color: 'blue', shape: 'quadruped' },
  { id: 263, nameKo: '지그제구리', heightDm: 4, weightHg: 175, type1: 'normal', type2: null, color: 'brown', shape: 'quadruped' },
  { id: 21, nameKo: '깨비참', heightDm: 3, weightHg: 20, type1: 'normal', type2: 'flying', color: 'brown', shape: 'wings' },
]

export const traits: Trait[] = [
  { pokemonId: 4, traitCode: 'CONSTANT_TAIL_FLAME', description: '꼬리 끝의 불꽃이 항상 타고 있다', isJudgment: true },
  { pokemonId: 54, traitCode: 'HEADACHE_SURGE', description: '두통이 심해지면 신비한 힘을 쓴다', isJudgment: false },
  { pokemonId: 155, traitCode: 'STARTLE_FLARE', description: '놀라거나 화나면 등에서 불꽃이 솟는다', isJudgment: false },
]

export const cases: CaseRow[] = [
  {
    caseId: 1, caseCode: 'CASE01', title: '자정의 연구소', subtitle: '아무도 없는 방에서 물건이 움직였다',
    briefing: '새벽 1시, 지하 기록보관실 경보가 울렸다. 아무도 없는데 물건들이 흩어져 있다. 그날 밤 현장에 있던 포켓몬은 변신한 메타몽뿐이다.',
    sceneImagePath: '/scenes/case01_scene.webp', answerPokemonId: 54,
    truthText: '경보음에 두통을 느낀 고라파덕 모습의 메타몽에게서 의도치 않은 힘이 터져 물건들이 튀어 나갔다.',
    motiveText: '오박사가 아끼는 녹음기가 궁금해 몰래 꺼내 보다가, 붙어 있던 사진 속 고라파덕으로 변신했다.',
    twistText: '녹음기에는 젊은 오박사와 그의 고라파덕이 함께 지내던 시절의 소리가 담겨 있었다.',
  },
  {
    caseId: 2, caseCode: 'CASE02', title: '불타버린 실험실', subtitle: '불이 먼저였나, 정전이 먼저였나',
    briefing: '새벽 2시, 생태 관찰실에서 작은 화재가 났다. 아크 흔적, 물, 탄 자국이 뒤섞여 있다. 그날 밤 현장에 있던 포켓몬은 변신한 메타몽뿐이다.',
    sceneImagePath: '/scenes/case02_scene.webp', answerPokemonId: 155,
    truthText: '보온함 전원이 꺼지자 메타몽은 브케인으로 변신해 알을 데웠다. 전원 복구 때 아크 소리에 놀라 등의 불꽃이 솟아 단열포에 옮겨 붙었다.',
    motiveText: '알을 지키려고 불꽃이 늘 켜져 있는 파이리 대신, 평소 불꽃이 꺼져 있는 브케인을 골랐다.',
    twistText: '가장 안전한 선택이 놀람 하나로 사고가 되었다. 다행히 알은 무사했다.',
  },
  {
    caseId: 3, caseCode: 'CASE03', title: '들어간 발자국만 있는 창고', subtitle: '나온 발자국은 어디로 갔을까',
    briefing: '새벽, 진흙 마당에 창고로 들어간 발자국만 남았다. 창고 안은 비어 있고 진화의 돌 상자만 열려 있다. 그날 밤 현장에 있던 포켓몬은 변신한 메타몽뿐이다.',
    sceneImagePath: '/scenes/case03_scene.webp', answerPokemonId: 133,
    truthText: '이브이 모습의 메타몽은 진화의 돌을 만져 본 뒤, 들어온 경로를 그대로 되짚어 나갔다.',
    motiveText: '진화하지 않는 메타몽은 진짜로 달라질 수 있는지 알고 싶어 진화의 상징인 이브이가 되었다.',
    twistText: '상자 아래 연구 노트: 메타몽은 진화하지 않는다. 그러나 무엇이든 될 수 있다는 것 역시 이 아이만의 가능성이다.',
  },
]

export const caseSuspects: CaseSuspectRow[] = [
  { caseSuspectId: 1, caseId: 1, pokemonId: 93, suspicionText: '물체를 건드리지 않고 움직였을 것 같다', order: 1 },
  { caseSuspectId: 2, caseId: 1, pokemonId: 64, suspicionText: '염동력으로 물건을 움직였을 수 있다', order: 2 },
  { caseSuspectId: 3, caseId: 1, pokemonId: 200, suspicionText: '한밤의 기묘한 현상과 어울린다', order: 3 },
  { caseSuspectId: 4, caseId: 1, pokemonId: 54, suspicionText: '배수로 주변에 물이 흥건하다', order: 4 },
  { caseSuspectId: 5, caseId: 2, pokemonId: 7, suspicionText: '물 때문에 합선이 났을 수 있다', order: 1 },
  { caseSuspectId: 6, caseId: 2, pokemonId: 81, suspicionText: '배전반 아크의 유력 후보', order: 2 },
  { caseSuspectId: 7, caseId: 2, pokemonId: 4, suspicionText: '불꽃으로 직접 불을 냈을 수 있다', order: 3 },
  { caseSuspectId: 8, caseId: 2, pokemonId: 155, suspicionText: '불꽃을 낼 수 있는 또 하나의 후보', order: 4 },
  { caseSuspectId: 9, caseId: 3, pokemonId: 133, suspicionText: '작은 네 발 포켓몬', order: 1 },
  { caseSuspectId: 10, caseId: 3, pokemonId: 29, suspicionText: '작은 네 발 포켓몬', order: 2 },
  { caseSuspectId: 11, caseId: 3, pokemonId: 263, suspicionText: '깊은 발자국을 남길 만큼 무겁다', order: 3 },
  { caseSuspectId: 12, caseId: 3, pokemonId: 21, suspicionText: '나온 발자국이 없다면 날아서 나갔을 수 있다', order: 4 },
]

export const rules: RuleRow[] = [
  { ruleId: 1, conditionType: 'NOT_TYPE', conditionValue: 'ghost', basisText: '고스트 타입이 직접 간섭하면 감식 장비가 잔류 반응을 검출한다' },
  { ruleId: 2, conditionType: 'MAX_HEIGHT', conditionValue: '10', basisText: '몸의 키가 통로 높이보다 크면 그 통로 안에 들어갈 수 없다' },
  { ruleId: 3, conditionType: 'HAS_TYPE', conditionValue: 'fire', basisText: '최초 발화가 포켓몬의 불꽃이라면 범인은 불꽃 타입이다' },
  { ruleId: 4, conditionType: 'NOT_TRAIT', conditionValue: 'CONSTANT_TAIL_FLAME', basisText: '계속 노출된 불꽃이 좁은 자리에 25초 이상 머물면 연속 탄화 흔적이 남는다' },
  { ruleId: 5, conditionType: 'SHAPE_IS', conditionValue: 'quadruped', basisText: '네 발 보행 흔적은 신체 형태가 네 발 보행형인 포켓몬이 남긴다' },
  { ruleId: 6, conditionType: 'MAX_WEIGHT', conditionValue: '100', basisText: '중첩 압흔을 보정한 감식 결과 체중 10kg 이하의 흔적이다' },
  { ruleId: 7, conditionType: 'COLOR_IS', conditionValue: 'brown', basisText: '체표 잔류물의 색은 도감 대표 색 분류와 일치한다' },
]

const c = (
  clueId: number, caseId: number, clueCode: string, kind: 'SCENE' | 'INFERENCE', name: string, summary: string,
  detailText: string, rebuttalText: string | null, ruleId: number | null, isCore: boolean,
  area: [number, number, number, number] | null, order: number,
): ClueRow => ({ clueId, caseId, clueCode, kind, name, summary, detailText, rebuttalText, ruleId, isCore, area, order })

export const clues: ClueRow[] = [
  c(1, 1, 'E1', 'SCENE', '날아간 금속 트레이', '트레이가 3m 떨어진 벽 아래에 있다', '표면에 손으로 밀거나 잡은 흔적은 없다.', null, null, false, [0.6, 0.55, 0.12, 0.1], 1),
  c(2, 1, 'E2', 'SCENE', '영체 반응 없음', '고스트 잔류 반응이 검출되지 않았다', '움직인 책, 트레이, 서랍 모두에서 영체 반응이 없다.', null, 1, false, [0.3, 0.4, 0.15, 0.12], 2),
  c(3, 1, 'E3', 'SCENE', '경보음 기록', '경보 직후마다 진동이 감지됐다', '01:17:41 경보 최대 출력, 01:17:42 기록실 진동 감지가 반복된다.', '경보 기록만으로는 누구인지 알 수 없네.', null, false, [0.05, 0.1, 0.1, 0.15], 3),
  c(4, 1, 'E4', 'SCENE', 'CCTV 물체 이동 기록', '물건이 점검로 입구를 중심으로 튀었다', '여러 물건이 점검로 입구를 중심으로 바깥쪽으로 동시에 튀어 나갔다.', '움직인 방식만으로는 판단할 수 없네. 언제, 어디서였는지 함께 보게.', null, false, [0.8, 0.05, 0.15, 0.12], 4),
  c(5, 1, 'E5', 'SCENE', '점검로 입구', '점검로 입구 높이는 1.0m', '물이 고인 낮은 배수 점검로의 입구 높이는 1.0m다.', null, null, false, [0.45, 0.7, 0.18, 0.2], 5),
  c(6, 1, 'E6', 'SCENE', '점검로 압력 기록', '01:17:41 점검로 내부 압력 감지', '유지보수용 바닥 압력 센서가 01:17:41에 점검로 내부 압력을 감지했다.', null, null, false, [0.4, 0.9, 0.1, 0.08], 6),
  c(7, 1, 'E7', 'SCENE', '젖은 자국', '점검로 안쪽에 젖은 자국이 있다', '누군가 점검로 안쪽까지 들어갔다 나온 흔적이다.', null, null, false, [0.55, 0.8, 0.12, 0.1], 7),
  c(8, 1, 'E8', 'SCENE', '젖은 녹음기와 사진', '출구 근처의 녹음기', '오박사의 오래된 녹음기에 젊은 오박사와 고라파덕의 사진이 붙어 있다.', null, null, false, [0.7, 0.75, 0.1, 0.1], 8),
  c(9, 1, 'I1', 'INFERENCE', '점검로 안의 변신체', '사건 순간 변신체는 점검로 안에 있었다', '경보 직후 점검로 내부에서 압력이 감지되고, 같은 순간 점검로 입구를 중심으로 물건이 튀었다. 변신체는 높이 1.0m의 점검로 안에 있었다.', null, 2, true, null, 9),
  c(10, 2, 'E1', 'SCENE', '바닥의 물', '바닥에 물이 흥건하다', '실험실 바닥 전체가 젖어 있다.', '물이 언제 생겼는지 먼저 확인해 보게.', null, false, [0.2, 0.8, 0.25, 0.15], 1),
  c(11, 2, 'E2', 'SCENE', '자동 소화 장치 기록', '02:13:35 작동', '소화 장치는 화염 감지 이후인 02:13:35에 작동했다.', null, null, false, [0.85, 0.05, 0.1, 0.1], 2),
  c(12, 2, 'E3', 'SCENE', '배전반 아크 흔적', '배전반 안에 강한 아크 흔적', '배전반 내부에서 강한 전기 아크 흔적이 발견됐다.', null, null, false, [0.05, 0.3, 0.12, 0.2], 3),
  c(13, 2, 'E4', 'SCENE', '시설 점검 로그', '릴레이 오류, 재투입 실패', '02:11:34 릴레이 응답 오류, 02:13:05 자동 재투입 실패. 보온함 전원은 계속 꺼져 있었다.', null, null, false, [0.05, 0.05, 0.1, 0.1], 4),
  c(14, 2, 'E5', 'SCENE', '최초 발화 지점', '최초 발화는 단열포', '최초 발화 지점은 전기 설비가 없는 단열포다. 화학물질, 발열 장치, 기계적 발화 등 다른 점화원은 없다.', null, null, false, [0.55, 0.5, 0.12, 0.12], 5),
  c(15, 2, 'E6', 'SCENE', '단열포 착화 흔적', '서서히 그을린 흔적 없이 순간 착화', '단열포는 서서히 그을린 흔적 없이 순간적인 고열로 한 번에 불이 붙었다.', null, null, false, [0.6, 0.62, 0.1, 0.1], 6),
  c(16, 2, 'E7', 'SCENE', '외벽 온도 센서 기록', '약 25초 낮은 열, 이후 순간 고온', '단열포 쪽 외벽 센서: 02:12:40부터 약 25초 동안 낮은 열 지속, 02:13:08 순간 고온 감지.', null, null, false, [0.68, 0.45, 0.08, 0.1], 7),
  c(17, 2, 'I0', 'INFERENCE', '화재 이후에 생긴 물', '물은 소화 장치에서 나왔다', '현장의 물은 화재가 난 뒤 소화 장치가 뿌린 것이다.', null, null, false, null, 8),
  c(18, 2, 'I1', 'INFERENCE', '포켓몬 불꽃에서 시작된 화재', '불은 포켓몬의 불꽃에서 시작됐다', '아크는 발화 지점과 떨어진 설비 내부 고장이고, 보온함은 꺼져 있었으며, 다른 점화원도 없다. 불은 포켓몬의 불꽃에서 시작됐다.', null, 3, true, null, 9),
  c(19, 2, 'I2', 'INFERENCE', '계속 타는 불꽃은 없었다', '단열포 옆 범인에게 지속 불꽃은 없었다', '범인은 약 25초 동안 단열포 옆에서 열을 공급했지만 서서히 그을린 흔적이 없다. 계속 타는 불꽃은 없었다.', null, 4, true, null, 10),
  c(20, 3, 'E1', 'SCENE', '네 발 보행 흔적', '네 발로 걸은 발자국', '진흙 위 발자국은 네 발로 걸은 흔적이다.', null, 5, false, [0.514, 0.638, 0.15, 0.133], 1),
  c(21, 3, 'E2', 'SCENE', '출구 흔적 없음', '지붕창·환기구 먼지가 그대로', '지붕창과 환기구에 먼지가 그대로다. 창고 안에 숨을 곳도 없다.', '나간 방법을 알아야 하네.', null, false, [0.338, 0, 0.232, 0.16], 2),
  c(22, 3, 'E3', 'SCENE', '깊은 발자국', '크기에 비해 깊은 발자국', '발자국이 크기에 비해 깊게 패어 있다.', '압흔이 겹쳤는지 확인하지 않은 깊이로는 판단할 수 없네.', null, false, [0.167, 0.887, 0.063, 0.085], 3),
  c(23, 3, 'E4', 'SCENE', '겹친 압흔', '압흔이 겹쳐 있다', '자세히 보니 발자국 대부분에서 압흔이 겹쳐 있다.', null, null, false, [0.434, 0.776, 0.055, 0.064], 4),
  c(24, 3, 'E5', 'SCENE', '갈색 체표 잔류물', '상자 경첩의 갈색 잔류물', '열린 진화의 돌 상자 경첩에 갈색 체표 잔류물이 끼어 있다.', null, 7, false, [0.769, 0.383, 0.107, 0.157], 5),
  c(25, 3, 'I1', 'INFERENCE', '되짚어 나간 경로', '들어온 경로를 되짚어 나갔다', '범인은 들어온 경로를 그대로 되짚어 나갔다. 중첩 압흔을 보정한 감식 결과 체중 10kg 이하의 흔적이다.', null, 6, true, null, 6),
]

/** [추론 단서, 먼저 모아야 하는 단서] */
export const prerequisites: [number, number][] = [
  [9, 3], [9, 4], [9, 5], [9, 6],
  [17, 10], [17, 11],
  [18, 12], [18, 13], [18, 14],
  [19, 18], [19, 15], [19, 16],
  [25, 21], [25, 23],
]
