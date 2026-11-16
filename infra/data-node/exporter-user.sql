-- mysqld-exporter 전용 MySQL 계정 (BUK-257)
-- MySQL 볼륨이 이미 있어 처음 실행 SQL이 돌지 않으므로, 보조 EC2에서 사람이 한 번 실행한다
-- 비밀번호는 이 파일에 적지 않는다. 실행할 때 .env의 MYSQL_EXPORTER_PASSWORD를 @pw 변수로 앞에 붙여 넘긴다
-- (값은 openssl rand -hex 24로 만든 영문·숫자라 따옴표 문제가 없다)
--
-- 실행 (보조 EC2, 저장소 루트 /opt/crewrun 에서)
--   PW=$(sudo grep '^MYSQL_EXPORTER_PASSWORD=' infra/data-node/.env | cut -d= -f2-)
--   { echo "SET @pw = '$PW';"; cat infra/data-node/exporter-user.sql; } \
--     | sudo docker exec -i bukang-db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
--   unset PW
--
-- 여러 번 실행해도 된다 (계정이 있으면 비밀번호만 다시 맞춘다)

-- 접속은 같은 Docker 네트워크의 exporter 컨테이너에서만 온다. 주소가 바뀔 수 있어 %로 둔다 (3306은 보안 그룹이 메인 EC2만 허용)
-- 동시 접속은 3개로 묶어, exporter가 멈춰도 접속을 많이 잡아먹지 않게 한다
SET @create = CONCAT('CREATE USER IF NOT EXISTS ''exporter''@''%'' IDENTIFIED BY ''', @pw, ''' WITH MAX_USER_CONNECTIONS 3');
PREPARE stmt FROM @create;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @alter = CONCAT('ALTER USER ''exporter''@''%'' IDENTIFIED BY ''', @pw, '''');
PREPARE stmt FROM @alter;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- PROCESS: 실행 중인 쿼리·InnoDB 상태 보기
-- REPLICATION CLIENT: 복제 상태 보기 (복제를 쓰지 않아도 exporter가 묻는다)
-- performance_schema SELECT: 표별 읽기·쓰기, 쿼리 통계
-- 앱 데이터(표 안의 행)는 읽지 못한다
GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'exporter'@'%';
GRANT SELECT ON performance_schema.* TO 'exporter'@'%';
