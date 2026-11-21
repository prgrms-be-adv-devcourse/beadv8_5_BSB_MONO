// 시나리오 B 공통: 주소 검사, 헤더, 번호대
import exec from 'k6/execution';

// 운영 서버로는 절대 보내지 않는다. 로컬 주소나 측정 컨테이너(container/compose.load.yml의 cash)가 아니면
// VU가 시작하기 전(초기화 단계)에 멈춘다
export const CASH_URL = __ENV.CASH_URL || 'http://localhost:8081';
if (!/^http:\/\/(localhost|127\.0\.0\.1|cash)(:\d+)?$/.test(CASH_URL)) {
	throw new Error(`로컬 주소가 아니라 멈춥니다: ${CASH_URL}`);
}
export const INTERNAL = `${CASH_URL}/internal/v1/cash`;

// 같은 데이터로 다시 돌릴 때 RUN을 바꿔 주문·정산 번호가 겹치지 않게 한다 (verify.sh에도 같은 RUN을 넣는다)
export const RUN = Number(__ENV.RUN || 1);
if (!(RUN >= 1 && RUN <= 99)) {
	throw new Error('RUN은 1 ~ 99');
}
const REF_BASE = 900000000 + RUN * 1000000;

// 반복 번호는 시나리오마다 0부터 다시 세므로, 시나리오 수만큼 칸을 똑같이 나눠 겹치지 않게 한다
// (시나리오 2개면 칸마다 약 50만 개). 999,999는 같은 주문·같은 정산 동시 요청용
export function refId() {
	const names = Object.keys(exec.test.options.scenarios);
	const width = Math.floor(999999 / names.length);
	const slot = names.indexOf(exec.scenario.name);
	const iteration = exec.scenario.iterationInTest;
	if (iteration >= width) {
		throw new Error(`번호 칸을 넘었어요: 시나리오 ${slot}, 반복 ${iteration}`);
	}
	return REF_BASE + slot * width + iteration;
}
export const SAME_REF_ID = REF_BASE + 999999;

// 결제 회원 900001 ~ 901000, 판매자 990001 ~ 990050 (sql/01-seed.sql)
export function payerId() {
	return 900001 + Math.floor(Math.random() * 1000);
}
export function sellerId() {
	return 990001 + Math.floor(Math.random() * 50);
}

// cash 내부 API 키 검사(#514)가 들어오면 .env의 INTERNAL_API_KEY를 -e INTERNAL_API_KEY=...로 넘긴다
const internalKey = __ENV.INTERNAL_API_KEY;
export const JSON_HEADERS = {
	headers: Object.assign({ 'Content-Type': 'application/json' }, internalKey ? { 'X-Internal-Key': internalKey } : {}),
};

export const PAYMENT_AMOUNT = 10000;
