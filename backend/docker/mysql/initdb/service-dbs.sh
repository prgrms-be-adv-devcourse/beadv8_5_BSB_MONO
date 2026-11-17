# 서비스마다 DB를 하나씩 만들고, 앱 계정(MYSQL_USER)에 모든 서비스 DB 권한을 준다 (BUK-238)
#
# 두 곳에서 읽는다
#   1. MySQL 이미지가 볼륨을 처음 만들 때 /docker-entrypoint-initdb.d 의 이 파일을 source 한다
#      (운영 보조 EC2의 infra/data-node/compose.data.yml이 이 폴더를 붙인다. 로컬 compose.yml은 붙이지 않아 로컬은 bukang-db 하나를 쓴다.
#       이미 데이터가 있는 볼륨에서는 돌지 않는다)
#   2. 옛 bukang-db의 표를 옮기는 split-bukang-db.sh가 먼저 이 파일을 source 한다
# 두 번 돌아도 결과가 같다 (IF NOT EXISTS, GRANT는 같은 권한을 다시 줘도 그대로다)
#
# 계정 이름은 컨테이너 환경변수 MYSQL_USER를 쓴다 (보조 EC2는 infra/data-node/.env의 값)
# 표는 여기서 만들지 않는다. 각 서비스가 뜰 때 Flyway가 자기 DB에 만든다
#
# 서비스를 더하면 아래 목록과 그 서비스의 application-prod.yaml 기본 DB 이름(${MYSQL_DB:crewrun_<서비스>})을 함께 고친다
# gateway·ai-service는 DB를 쓰지 않는다

CREWRUN_SERVICE_DBS=(
	crewrun_member
	crewrun_cash
	crewrun_payout
	crewrun_file
	crewrun_order
	crewrun_product
	crewrun_queue
	crewrun_notification
	crewrun_inquiry
	crewrun_board
)

# MySQL 이미지의 시작 스크립트가 source 할 때는 그 안의 docker_process_sql(root 접속)을 쓴다
# split-bukang-db.sh에서 부를 때는 그 함수가 없으므로 root로 접속하는 같은 이름의 함수를 만든다
if ! declare -F docker_process_sql > /dev/null; then
	docker_process_sql() {
		MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot --comments "$@"
	}
fi

crewrun_create_service_dbs() {
	local db sql=""
	for db in "${CREWRUN_SERVICE_DBS[@]}"; do
		sql+="CREATE DATABASE IF NOT EXISTS \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
		sql+="GRANT ALL PRIVILEGES ON \`${db}\`.* TO '${MYSQL_USER}'@'%';"
	done
	sql+="FLUSH PRIVILEGES;"
	echo "${sql}" | docker_process_sql --database=mysql
	echo "[service-dbs] DB ${#CREWRUN_SERVICE_DBS[@]}개와 ${MYSQL_USER} 권한 준비 끝"
}

crewrun_create_service_dbs
