// 실행마다 새 대회 2개(구매용·대기용)를 만들고 바로 판매를 연 뒤 ID를 ./race.json 에 저장한다. funnel.js 실행 전에 돌린다
// 구매자는 대회당 1번만 신청할 수 있어서(두 번째부터 409 중복 거절), 대회를 재사용하면 주문·결제가 한 건도 실행되지 않는다
// 실행(이 폴더에서): LOADTEST_PASSWORD='…' k6 run -e LABEL=s1 prepare-race.js
import http from 'k6/http';

const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
// 승인된 판매자인 부하 계정 번호. loadtest1000을 product_seller에 승인 상태로 넣어 둠(2026-11-19, id 900001)
// 대기자(1~990)·구매자(1~900)와 겹치지 않는 번호를 쓴다
const SELLER_NO = Number(__ENV.SELLER_NO || 1000);
const LABEL = __ENV.LABEL || 'run';
const QUOTA = Number(__ENV.QUOTA || 5000);
const PASSWORD = __ENV.LOADTEST_PASSWORD;
if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}

export const options = { vus: 1, iterations: 1 };

function pad(n) {
	return String(n).padStart(2, '0');
}

// 서버(KST) 기준 지금부터 days일 뒤의 날짜·일시 문자열
function kst(days, hour) {
	const d = new Date(Date.now() + 9 * 3600 * 1000 + days * 86400 * 1000);
	const date = `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
	return hour === undefined ? date : `${date}T${pad(hour)}:00:00`;
}

function createAndOpen(headers, role, stamp) {
	const body = {
		title: `부하테스트 ${LABEL} ${role} ${stamp}`,
		description: '부하 테스트용 대회입니다. 실행마다 새로 만듭니다.',
		location: '부하테스트 광장', regionSido: '서울특별시', regionSigungu: '종로구',
		raceDate: kst(120, 8),
		individualOpenAt: kst(3, 9),
		applicationCloseAt: kst(30, 18),
		refundDeadline: kst(100),
		refundPolicy: '환불 기한까지 취소하면 전액 환불됩니다.',
		crewMinCount: 4,
		courses: [{
			name: '10K', courseType: 'KM_10', surface: 'ROAD', distanceKm: 10, timeLimitMinutes: 90,
			price: 50000, kitIncluded: false, kitPrice: 0, minAge: 19, crewQuota: 0, individualQuota: QUOTA,
			images: [],
		}],
		// 대표 이미지 1장은 필수다. 파일 연결은 등록 뒤 따로 시도하고 실패해도 등록은 되므로 아무 파일 ID나 쓴다
		images: [{ fileId: 1, fileType: 'THUMBNAIL', sortNo: 0 }],
	};
	const created = http.post(`${BASE_URL}/api/v1/product/races`, JSON.stringify(body),
		{ headers: { ...headers, 'Idempotency-Key': `loadtest-${LABEL}-${role}-${stamp}` } });
	if (created.status !== 201) {
		throw new Error(`대회 등록 실패(${role}): ${created.status} ${created.body}`);
	}
	const raceId = created.json('data.raceId');
	const raceCourseId = created.json('data.courseIds')[0];
	const opened = http.post(`${BASE_URL}/api/v1/product/races/${raceId}/open`, null, { headers });
	if (opened.status !== 200) {
		throw new Error(`판매 바로 시작 실패(${role}, raceId=${raceId}): ${opened.status} ${opened.body}`);
	}
	return { raceId, raceCourseId, saleStatus: opened.json('data.saleStatus') };
}

export function setup() {
	// 판매자 계정만 새로 로그인한다. 구매자 토큰(.accounts.json)이 얼마나 오래됐는지와 상관없이 돌릴 수 있다
	const login = http.post(`${BASE_URL}/api/v1/auth/login`,
		JSON.stringify({ email: `loadtest${SELLER_NO}@crewrun.test`, password: PASSWORD }),
		{ headers: { 'Content-Type': 'application/json' } });
	if (login.status !== 200) {
		throw new Error(`판매자 계정 ${SELLER_NO}번 로그인 실패: ${login.status}`);
	}
	const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${login.json('data.accessToken')}` };
	const stamp = Date.now();
	return { label: LABEL, createdAt: new Date(stamp).toISOString(), buy: createAndOpen(headers, 'buy', stamp), wait: createAndOpen(headers, 'wait', stamp) };
}

export default function () {
}

export function handleSummary(data) {
	const race = data.setup_data;
	return {
		'./race.json': JSON.stringify(race),
		stdout: `RACE ${JSON.stringify(race)}\n`,
	};
}
