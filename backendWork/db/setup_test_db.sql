-- 통합 테스트용 DB와 전용 계정 만들기 (MySQL 관리자 계정으로 한 번만 실행)
-- 실행 전에 CHANGE_ME 를 본인이 정한 비밀번호로 바꾼다. 바꾼 파일은 저장하거나 커밋하지 않는다.

CREATE DATABASE IF NOT EXISTS metamong_case_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'metamong_test'@'localhost' IDENTIFIED BY 'CHANGE_ME';

-- GRANT의 DB 이름에서 _ 는 "아무 글자 하나"를 뜻하는 와일드카드이므로 반드시 \_ 로 적는다.
-- 이 계정에는 이 DB 밖의 권한을 주지 않는다. (코드의 안전장치가 실패해도 MySQL이 막게 한다)
GRANT ALL PRIVILEGES ON `metamong\_case\_test`.* TO 'metamong_test'@'localhost';

SHOW GRANTS FOR 'metamong_test'@'localhost';
