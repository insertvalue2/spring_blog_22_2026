-- board_tb 테이블의 content 컬럼을 LONGTEXT로 변경하는 SQL
-- Base64 이미지가 포함된 대용량 HTML 콘텐츠를 저장하기 위함

-- MySQL/MariaDB
ALTER TABLE board_tb MODIFY COLUMN content LONGTEXT;

-- 실행 방법:
-- 1. MySQL 클라이언트에서 직접 실행
-- 2. 또는 애플리케이션 재시작 후 ddl-auto: update가 자동으로 처리할 수도 있음
--    (하지만 기존 데이터가 있으면 실패할 수 있으므로 수동 실행 권장)
