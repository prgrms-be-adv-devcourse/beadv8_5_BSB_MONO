# 운영 스모크

배포 뒤 운영이 크게 깨지지 않았는지 1~2분 안에 확인해요.
화면을 열기만 해요. 신청 · 주문 · 결제 · 충전 · 글쓰기 · 설정 변경은 하지 않아요.

## 언제 돌리나요

- 운영 배포가 끝난 직후에 한 번 돌려요.
- 실패하면 배포를 되돌릴지 먼저 판단해요.

## 실행

```bash
cd frontend
pnpm exec playwright install chromium   # 처음 한 번
pnpm test:smoke
```

- 기본 주소는 `https://crewrun.duckdns.org`예요. 다른 서버는 `SMOKE_BASE_URL`로 바꿔요.
- 계정 값이 없으면 그 역할의 시나리오는 건너뛰어요. 공개 화면만 확인해요.

| 환경 변수                                      | 확인하는 화면        |
| ---------------------------------------------- | -------------------- |
| `SMOKE_BUYER_EMAIL` · `SMOKE_BUYER_PASSWORD`   | 내 참가권, 예치금    |
| `SMOKE_SELLER_EMAIL` · `SMOKE_SELLER_PASSWORD` | 판매자 센터 첫 화면  |
| `SMOKE_ADMIN_EMAIL` · `SMOKE_ADMIN_PASSWORD`   | 관리자 홈, AI 브리핑 |

계정 값은 git 밖 파일에 두고 셸에서 불러와요(예: `set -a; source ~/.crewrun-smoke.env; set +a`).

## 무엇을 실패로 보나요

- 화면의 오류 안내(「불러오지 못했어요」, 「찾을 수 없는 페이지예요」, 「로그인이 필요합니다」 등)
- 브라우저 콘솔 에러와 페이지 예외(hydration 오류 포함)
- 5xx 응답, 그리고 앱 시작 때 보내는 재발급(`/api/v1/auth/reissue`) 말고 다른 401 응답

로그인은 nginx가 IP당 초당 1건으로 막아서 계정마다 한 번만 하고, 한 번에 하나씩 돌아요.
GA4 요청은 막아서 운영 통계에 섞이지 않아요.
