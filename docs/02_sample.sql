-- 오박사와 메타몽의 사라진 기억 : CASE 1~3 샘플 데이터
USE metamong_case;

-- 회원 (비밀번호는 BCrypt 해시 예시)
INSERT INTO users (user_id, login_id, password_hash, nickname, role) VALUES
(1, 'admin',       '$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234', '오박사',   'ADMIN'),
(2, 'detective01', '$2a$10$zyxwvutsrqponmlkjihgfeeZYXWVUTSRQPONMLKJIHGFEDCBA98765', '꼬마탐정', 'USER');

-- 포켓몬 캐시 (PokéAPI 원본 데이터로 확인한 값, 2026-11-24)
INSERT INTO pokemon (pokemon_id, name_en, name_ko, height_dm, weight_hg, type1, type2, color, shape, sprite_path, synced_at) VALUES
(54,  'psyduck',    '고라파덕',   8,  196, 'water',    NULL,     'yellow', 'upright',   '/sprites/54.png',  NOW()),
(64,  'kadabra',    '윤겔라',    13,  565, 'psychic',  NULL,     'brown',  'upright',   '/sprites/64.png',  NOW()),
(93,  'haunter',    '고우스트',  16,    1, 'ghost',    'poison', 'purple', 'arms',      '/sprites/93.png',  NOW()),
(200, 'misdreavus', '무우마',     7,   10, 'ghost',    NULL,     'gray',   'ball',      '/sprites/200.png', NOW()),
(4,   'charmander', '파이리',     6,   85, 'fire',     NULL,     'red',    'upright',   '/sprites/4.png',   NOW()),
(7,   'squirtle',   '꼬부기',     5,   90, 'water',    NULL,     'blue',   'upright',   '/sprites/7.png',   NOW()),
(81,  'magnemite',  '코일',       3,   60, 'electric', 'steel',  'gray',   'arms',      '/sprites/81.png',  NOW()),
(155, 'cyndaquil',  '브케인',     5,   79, 'fire',     NULL,     'yellow', 'humanoid',  '/sprites/155.png', NOW()),
(133, 'eevee',      '이브이',     3,   65, 'normal',   NULL,     'brown',  'quadruped', '/sprites/133.png', NOW()),
(29,  'nidoran-f',  '니드런♀',   4,   70, 'poison',   NULL,     'blue',   'quadruped', '/sprites/29.png',  NOW()),
(263, 'zigzagoon',  '지그제구리', 4,  175, 'normal',   NULL,     'brown',  'quadruped', '/sprites/263.png', NOW()),
(21,  'spearow',    '깨비참',     3,   20, 'normal',   'flying', 'brown',  'wings',     '/sprites/21.png',  NOW());

-- 포켓몬 특징
INSERT INTO pokemon_trait (trait_id, pokemon_id, trait_code, description, source_type, source_text, source_version, is_judgment) VALUES
(1, 4,   'CONSTANT_TAIL_FLAME', '꼬리 끝의 불꽃이 항상 타고 있다',          'POKEDEX', '태어날 때부터 꼬리의 불꽃이 타오르고 있다. 불꽃이 꺼지면 그 생명이 다하고 만다.', 'y', TRUE),
(2, 54,  'HEADACHE_SURGE',      '두통이 심해지면 신비한 힘을 쓴다',          'POKEDEX', '항상 두통에 시달리고 있다. 이 두통이 심해지면 이상한 힘을 쓰기 시작한다.', 'x', FALSE),
(3, 155, 'STARTLE_FLARE',       '놀라거나 화나면 등에서 불꽃이 솟는다',      'POKEDEX', '평소에는 몸을 웅크리고 있다. 화가 났을 때와 놀랐을 때 등에서 불꽃을 뿜어낸다.', 'scarlet', FALSE);

-- 사건 (validation_status는 넣지 않음: 기본값 PENDING, 서버 시작 시 검증기가 VALID/INVALID로 바꿈)
INSERT INTO case_file (case_id, case_code, title, subtitle, briefing, scene_image_path, answer_pokemon_id, truth_text, motive_text, twist_text, display_order, is_enabled) VALUES
(1, 'CASE01', '자정의 연구소', '초자연 현상의 정체를 밝혀라',
 '새벽 1시, 지하 기록보관실 경보가 울렸다. 아무도 없는데 물건들이 흩어져 있다. 그날 밤 현장에 있던 포켓몬은 변신한 메타몽뿐이다.',
 '/scenes/case01_scene.webp', 54,
 '경보음에 두통을 느낀 고라파덕 모습의 메타몽에게서 의도치 않은 힘이 터져 물건들이 튀어 나갔다.',
 '오박사가 아끼는 녹음기가 궁금해 몰래 꺼내 보다가, 붙어 있던 사진 속 고라파덕으로 변신했다.',
 '녹음기에는 젊은 오박사와 그의 고라파덕이 함께 지내던 시절의 소리가 담겨 있었다.', 1, TRUE),
(2, 'CASE02', '불타버린 실험실', '화재의 시작점을 추적하라',
 '새벽 2시, 생태 관찰실에서 작은 화재가 났다. 아크 흔적, 물, 탄 자국이 뒤섞여 있다. 그날 밤 현장에 있던 포켓몬은 변신한 메타몽뿐이다.',
 '/scenes/case02_scene.webp', 155,
 '보온함 전원이 꺼지자 메타몽은 브케인으로 변신해 알을 데웠다. 전원 복구 때 아크 소리에 놀라 등의 불꽃이 솟아 단열포에 옮겨 붙었다.',
 '알을 지키려고 불꽃이 늘 켜져 있는 파이리 대신, 평소 불꽃이 꺼져 있는 브케인을 골랐다.',
 '가장 안전한 선택이 놀람 하나로 사고가 되었다. 다행히 알은 무사했다.', 2, TRUE),
(3, 'CASE03', '들어간 발자국만 있는 창고', '남겨진 발자국의 모순을 풀어라',
 '새벽, 진흙 마당에 창고로 들어간 발자국만 남았다. 창고 안은 비어 있고 진화의 돌 상자만 열려 있다. 그날 밤 현장에 있던 포켓몬은 변신한 메타몽뿐이다.',
 '/scenes/case03_scene.webp', 133,
 '이브이 모습의 메타몽은 진화의 돌을 만져 본 뒤, 들어온 경로를 그대로 되짚어 나갔다.',
 '진화하지 않는 메타몽은 진짜로 달라질 수 있는지 알고 싶어 진화의 상징인 이브이가 되었다.',
 '상자 아래 연구 노트: 메타몽은 진화하지 않는다. 그러나 무엇이든 될 수 있다는 것 역시 이 아이만의 가능성이다.', 3, TRUE);

-- 사건별 용의자
INSERT INTO case_suspect (case_suspect_id, case_id, pokemon_id, suspicion_text, display_order) VALUES
(1,  1, 93,  '물체를 건드리지 않고 움직였을 것 같다', 1),
(2,  1, 64,  '염동력으로 물건을 움직였을 수 있다', 2),
(3,  1, 200, '한밤의 기묘한 현상과 어울린다', 3),
(4,  1, 54,  '배수로 주변에 물이 흥건하다', 4),
(5,  2, 7,   '물 때문에 합선이 났을 수 있다', 1),
(6,  2, 81,  '배전반 아크의 유력 후보', 2),
(7,  2, 4,   '불꽃으로 직접 불을 냈을 수 있다', 3),
(8,  2, 155, '불꽃을 낼 수 있는 또 하나의 후보', 4),
(9,  3, 133, '작은 네 발 포켓몬', 1),
(10, 3, 29,  '작은 네 발 포켓몬', 2),
(11, 3, 263, '깊은 발자국을 남길 만큼 무겁다', 3),
(12, 3, 21,  '나온 발자국이 없다면 날아서 나갔을 수 있다', 4);

-- 판정 규칙 (흔적 사전)
INSERT INTO evidence_rule (rule_id, condition_type, condition_value, basis_text, source_type, source_ref) VALUES
(1, 'NOT_TYPE',   'ghost',               '고스트 타입이 직접 간섭하면 감식 장비가 잔류 반응을 검출한다', 'CASE_RULE', 'CASE01 감식 장비'),
(2, 'MAX_HEIGHT', '10',                  '몸의 키가 통로 높이보다 크면 그 통로 안에 들어갈 수 없다',     'CASE_RULE', 'pokemon.height'),
(3, 'HAS_TYPE',   'fire',                '최초 발화가 포켓몬의 불꽃이라면 범인은 불꽃 타입이다',         'API',       'pokemon.types'),
(4, 'NOT_TRAIT',  'CONSTANT_TAIL_FLAME', '계속 노출된 불꽃이 좁은 자리에 25초 이상 머물면 연속 탄화 흔적이 남는다', 'CASE_RULE', 'pokemon_trait'),
(5, 'SHAPE_IS',   'quadruped',           '네 발 보행 흔적은 신체 형태가 네 발 보행형인 포켓몬이 남긴다', 'API',       'pokemon-species.shape'),
(6, 'MAX_WEIGHT', '100',                 '중첩 압흔을 보정한 감식 결과 체중 10kg 이하의 흔적이다',       'CASE_RULE', 'pokemon.weight'),
(7, 'COLOR_IS',   'brown',               '체표 잔류물의 색은 도감 대표 색 분류와 일치한다',             'API',       'pokemon-species.color');

-- 단서 (SCENE은 클릭 영역 필수, INFERENCE는 NULL)
INSERT INTO clue (clue_id, case_id, clue_code, clue_kind, name, summary, detail_text, rebuttal_text, rule_id, is_core, area_x, area_y, area_w, area_h, display_order) VALUES
(1,  1, 'E1', 'SCENE', '날아간 금속 트레이', '트레이가 3m 떨어진 벽 아래에 있다', '표면에 손으로 밀거나 잡은 흔적은 없다.', NULL, NULL, FALSE, 0.6000, 0.5500, 0.1200, 0.1000, 1),
(2,  1, 'E2', 'SCENE', '영체 반응 없음', '고스트 잔류 반응이 검출되지 않았다', '움직인 책, 트레이, 서랍 모두에서 영체 반응이 없다.', NULL, 1, FALSE, 0.3000, 0.4000, 0.1500, 0.1200, 2),
(3,  1, 'E3', 'SCENE', '경보음 기록', '경보 직후마다 진동이 감지됐다', '01:17:41 경보 최대 출력, 01:17:42 기록실 진동 감지가 반복된다.', '경보 기록만으로는 누구인지 알 수 없네.', NULL, FALSE, 0.0500, 0.1000, 0.1000, 0.1500, 3),
(4,  1, 'E4', 'SCENE', 'CCTV 물체 이동 기록', '물건이 점검로 입구를 중심으로 튀었다', '여러 물건이 점검로 입구를 중심으로 바깥쪽으로 동시에 튀어 나갔다.', '움직인 방식만으로는 판단할 수 없네. 언제, 어디서였는지 함께 보게.', NULL, FALSE, 0.8000, 0.0500, 0.1500, 0.1200, 4),
(5,  1, 'E5', 'SCENE', '점검로 입구', '점검로 입구 높이는 1.0m', '물이 고인 낮은 배수 점검로의 입구 높이는 1.0m다.', NULL, NULL, FALSE, 0.4500, 0.7000, 0.1800, 0.2000, 5),
(6,  1, 'E6', 'SCENE', '점검로 압력 기록', '01:17:41 점검로 내부 압력 감지', '유지보수용 바닥 압력 센서가 01:17:41에 점검로 내부 압력을 감지했다.', NULL, NULL, FALSE, 0.4000, 0.9000, 0.1000, 0.0800, 6),
(7,  1, 'E7', 'SCENE', '젖은 자국', '점검로 안쪽에 젖은 자국이 있다', '누군가 점검로 안쪽까지 들어갔다 나온 흔적이다.', NULL, NULL, FALSE, 0.5500, 0.8000, 0.1200, 0.1000, 7),
(8,  1, 'E8', 'SCENE', '젖은 녹음기와 사진', '출구 근처의 녹음기', '오박사의 오래된 녹음기에 젊은 오박사와 고라파덕의 사진이 붙어 있다.', NULL, NULL, FALSE, 0.7000, 0.7500, 0.1000, 0.1000, 8),
(9,  1, 'I1', 'INFERENCE', '점검로 안의 변신체', '사건 순간 변신체는 점검로 안에 있었다', '경보 직후 점검로 내부에서 압력이 감지되고, 같은 순간 점검로 입구를 중심으로 물건이 튀었다. 변신체는 높이 1.0m의 점검로 안에 있었다.', NULL, 2, TRUE, NULL, NULL, NULL, NULL, 9),
(10, 2, 'E1', 'SCENE', '바닥의 물', '바닥에 물이 흥건하다', '실험실 바닥 전체가 젖어 있다.', '물이 언제 생겼는지 먼저 확인해 보게.', NULL, FALSE, 0.2000, 0.8000, 0.2500, 0.1500, 1),
(11, 2, 'E2', 'SCENE', '자동 소화 장치 기록', '02:13:35 작동', '소화 장치는 화염 감지 이후인 02:13:35에 작동했다.', NULL, NULL, FALSE, 0.8500, 0.0500, 0.1000, 0.1000, 2),
(12, 2, 'E3', 'SCENE', '배전반 아크 흔적', '배전반 안에 강한 아크 흔적', '배전반 내부에서 강한 전기 아크 흔적이 발견됐다.', NULL, NULL, FALSE, 0.0500, 0.3000, 0.1200, 0.2000, 3),
(13, 2, 'E4', 'SCENE', '시설 점검 로그', '릴레이 오류, 재투입 실패', '02:11:34 릴레이 응답 오류, 02:13:05 자동 재투입 실패. 보온함 전원은 계속 꺼져 있었다.', NULL, NULL, FALSE, 0.0500, 0.0500, 0.1000, 0.1000, 4),
(14, 2, 'E5', 'SCENE', '최초 발화 지점', '최초 발화는 단열포', '최초 발화 지점은 전기 설비가 없는 단열포다. 화학물질, 발열 장치, 기계적 발화 등 다른 점화원은 없다.', NULL, NULL, FALSE, 0.5500, 0.5000, 0.1200, 0.1200, 5),
(15, 2, 'E6', 'SCENE', '단열포 착화 흔적', '서서히 그을린 흔적 없이 순간 착화', '단열포는 서서히 그을린 흔적 없이 순간적인 고열로 한 번에 불이 붙었다.', NULL, NULL, FALSE, 0.6000, 0.6200, 0.1000, 0.1000, 6),
(16, 2, 'E7', 'SCENE', '외벽 온도 센서 기록', '약 25초 낮은 열, 이후 순간 고온', '단열포 쪽 외벽 센서: 02:12:40부터 약 25초 동안 낮은 열 지속, 02:13:08 순간 고온 감지.', NULL, NULL, FALSE, 0.6800, 0.4500, 0.0800, 0.1000, 7),
(17, 2, 'I0', 'INFERENCE', '화재 이후에 생긴 물', '물은 소화 장치에서 나왔다', '현장의 물은 화재가 난 뒤 소화 장치가 뿌린 것이다.', NULL, NULL, FALSE, NULL, NULL, NULL, NULL, 8),
(18, 2, 'I1', 'INFERENCE', '포켓몬 불꽃에서 시작된 화재', '불은 포켓몬의 불꽃에서 시작됐다', '아크는 발화 지점과 떨어진 설비 내부 고장이고, 보온함은 꺼져 있었으며, 다른 점화원도 없다. 불은 포켓몬의 불꽃에서 시작됐다.', NULL, 3, TRUE, NULL, NULL, NULL, NULL, 9),
(19, 2, 'I2', 'INFERENCE', '계속 타는 불꽃은 없었다', '단열포 옆 범인에게 지속 불꽃은 없었다', '범인은 약 25초 동안 단열포 옆에서 열을 공급했지만 서서히 그을린 흔적이 없다. 계속 타는 불꽃은 없었다.', NULL, 4, TRUE, NULL, NULL, NULL, NULL, 10),
(20, 3, 'E1', 'SCENE', '네 발 보행 흔적', '네 발로 걸은 발자국', '진흙 위 발자국은 네 발로 걸은 흔적이다.', NULL, 5, FALSE, 0.5140, 0.6380, 0.1500, 0.1330, 1),
(21, 3, 'E2', 'SCENE', '출구 흔적 없음', '지붕창·환기구 먼지가 그대로', '지붕창과 환기구에 먼지가 그대로다. 창고 안에 숨을 곳도 없다.', '나간 방법을 알아야 하네.', NULL, FALSE, 0.3380, 0.0000, 0.2320, 0.1600, 2),
(22, 3, 'E3', 'SCENE', '깊은 발자국', '크기에 비해 깊은 발자국', '발자국이 크기에 비해 깊게 패어 있다.', '압흔이 겹쳤는지 확인하지 않은 깊이로는 판단할 수 없네.', NULL, FALSE, 0.1670, 0.8870, 0.0630, 0.0850, 3),
(23, 3, 'E4', 'SCENE', '겹친 압흔', '압흔이 겹쳐 있다', '자세히 보니 발자국 대부분에서 압흔이 겹쳐 있다.', NULL, NULL, FALSE, 0.4340, 0.7760, 0.0550, 0.0640, 4),
(24, 3, 'E5', 'SCENE', '갈색 체표 잔류물', '상자 경첩의 갈색 잔류물', '열린 진화의 돌 상자 경첩에 갈색 체표 잔류물이 끼어 있다.', NULL, 7, FALSE, 0.7690, 0.3830, 0.1070, 0.1570, 5),
(25, 3, 'I1', 'INFERENCE', '되짚어 나간 경로', '들어온 경로를 되짚어 나갔다', '범인은 들어온 경로를 그대로 되짚어 나갔다. 중첩 압흔을 보정한 감식 결과 체중 10kg 이하의 흔적이다.', NULL, 6, TRUE, NULL, NULL, NULL, NULL, 6);

-- 추론 선행 조건
INSERT INTO clue_prerequisite (clue_id, required_clue_id) VALUES
(9, 3), (9, 4), (9, 5), (9, 6),
(17, 10), (17, 11),
(18, 12), (18, 13), (18, 14),
(19, 18), (19, 15), (19, 16),
(25, 21), (25, 23);

-- 플레이 기록 예시: detective01의 CASE 3 플레이 (A 등급)
INSERT INTO play_session (session_id, user_id, case_id, status, score, wrong_exclusion_count, accusation_count, bonus_awarded, grade, active_flag, started_at, ended_at) VALUES
(1, 2, 3, 'SOLVED', 100, 1, 1, TRUE, 'A', NULL, '2026-12-01 20:00:00', '2026-12-01 20:12:30');

INSERT INTO session_clue (session_id, clue_id, collected_at) VALUES
(1, 20, '2026-12-01 20:01:00'), (1, 21, '2026-12-01 20:02:00'), (1, 22, '2026-12-01 20:03:00'),
(1, 23, '2026-12-01 20:04:00'), (1, 25, '2026-12-01 20:04:00'), (1, 24, '2026-12-01 20:05:00');

INSERT INTO session_suspect (session_id, case_suspect_id, status, basis_clue_id, updated_at) VALUES
(1, 9,  'ACTIVE',               NULL, '2026-12-01 20:00:00'),
(1, 10, 'EXCLUDED_BY_EVIDENCE', 24,   '2026-12-01 20:10:00'),
(1, 11, 'EXCLUDED_BY_EVIDENCE', 25,   '2026-12-01 20:09:00'),
(1, 12, 'EXCLUDED_BY_EVIDENCE', 20,   '2026-12-01 20:08:00');

INSERT INTO session_action (session_id, action_type, clue_id, case_suspect_id, result, score_delta, dedup_key, request_id, created_at) VALUES
(1, 'INVESTIGATE', 20, NULL, 'SUCCESS',   0,  'INV:20',    '0b8f2c1e-0001-4a00-9000-000000000001', '2026-12-01 20:01:00'),
(1, 'INVESTIGATE', 21, NULL, 'SUCCESS',   0,  'INV:21',    '0b8f2c1e-0002-4a00-9000-000000000002', '2026-12-01 20:02:00'),
(1, 'INVESTIGATE', 22, NULL, 'SUCCESS',   0,  'INV:22',    '0b8f2c1e-0003-4a00-9000-000000000003', '2026-12-01 20:03:00'),
(1, 'INVESTIGATE', 23, NULL, 'SUCCESS',   0,  'INV:23',    '0b8f2c1e-0004-4a00-9000-000000000004', '2026-12-01 20:04:00'),
(1, 'INFER',       25, NULL, 'SUCCESS',   0,  'INF:25',    NULL,                                   '2026-12-01 20:04:00'),
(1, 'INVESTIGATE', 24, NULL, 'SUCCESS',   0,  'INV:24',    '0b8f2c1e-0005-4a00-9000-000000000005', '2026-12-01 20:05:00'),
(1, 'EXCLUDE',     22, 9,    'FAIL',     -10, 'EXC:22:9',  '0b8f2c1e-0006-4a00-9000-000000000006', '2026-12-01 20:06:00'),
(1, 'EXCLUDE',     22, 9,    'REJECTED',  0,  NULL,        '0b8f2c1e-0007-4a00-9000-000000000007', '2026-12-01 20:06:30'),
(1, 'EXCLUDE',     20, 12,   'SUCCESS',   0,  'EXC:20:12', '0b8f2c1e-0008-4a00-9000-000000000008', '2026-12-01 20:08:00'),
(1, 'EXCLUDE',     25, 11,   'SUCCESS',   0,  'EXC:25:11', '0b8f2c1e-0009-4a00-9000-000000000009', '2026-12-01 20:09:00'),
(1, 'EXCLUDE',     24, 10,   'SUCCESS',   0,  'EXC:24:10', '0b8f2c1e-0010-4a00-9000-000000000010', '2026-12-01 20:10:00'),
(1, 'ACCUSE',      NULL, 9,  'SUCCESS',  10,  'ACC:1',     '0b8f2c1e-0011-4a00-9000-000000000011', '2026-12-01 20:12:30');
