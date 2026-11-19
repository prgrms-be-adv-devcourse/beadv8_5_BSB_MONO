# 기록증 심사 E2E

기록증 제출부터 심사까지(BUK-274~277)를 실제 백엔드와 브라우저로 확인해요.
가짜 응답 모드가 아니라, 띄워 둔 서버(게이트웨이 · member · file · product · order · ai-service · 프론트)를 그대로 써요.

## 시나리오

| #   | 파일                 | 흐름                                                                               |
| --- | -------------------- | ---------------------------------------------------------------------------------- |
| 1   | `happy-path.spec.ts` | 화면 업로드 → 브라우저 OCR → 제출 → 서버 확인 → 자동 승인(심사 필요면 판매자 승인) |

## 실행

```bash
cd frontend
pnpm exec playwright install chromium   # 처음 한 번
E2E_BASE_URL=http://localhost:3177 pnpm test:record-cert
```

- 프론트는 `API_MOCKING=off`로 띄워 두고 주소를 `E2E_BASE_URL`로 넘겨요. 게이트웨이 주소는 `E2E_API_URL`(기본 `http://localhost:8000`)이에요.
- 로그인 · 구매 · DB 도우미는 점심 E2E의 `../lunch/support.ts`를 같이 써요. DB는 `bukang-db` 컨테이너에 직접 넣어요.
- 사진은 합성 기록증(`fixtures/synthetic-s-006.png`)만 써요. 실제 기록증은 넣지 않아요.

## 알아 둘 것

- 판매 상태 배치(5분 주기)를 기다려서 한 번에 5분쯤 걸려요.
- OCR 읽기 제한 10초는 「이 부분 읽기」를 누른 때부터 세요. 느린 PC는 모델 준비에 8~10초가 걸려서, 테스트가 준비를 기다린 뒤 눌러요.
- file-service의 dev 전용 CORS는 `localhost:8085`만 허용해요. 프론트가 넘긴 Origin이 403으로 막혀서, 파일 API 요청만 테스트가 대신 보내요. prod에는 이 설정이 없어요.
- 막 띄운 서버는 첫 요청이 느려 504가 날 수 있어요. 업로드 확인이 실패하면 테스트가 「제출하기」를 다시 눌러요(최대 3번).
