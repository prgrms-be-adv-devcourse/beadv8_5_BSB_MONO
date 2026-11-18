# 공통 컴포넌트 점검표 (CRU-52)

브라우저 기본 UI를 그대로 쓰거나, 공통 컴포넌트가 있는데 화면마다 따로 만든 곳을 찾은 결과입니다.
피그마 v3가 공통 컴포넌트 모양을 바꾸면 「지금 바꿈」 화면은 함께 따라옵니다. 「v3 뒤로 미룸」은 맞는 공통 부품이 아직 없는 곳입니다.

- 범위: `frontend/src` (테스트·스토리·`shared/ui` 안쪽 구현 제외)
- 줄 번호는 바꾸기 전 기준입니다.
- 점검한 것: `<select>`, `<input>`, `<textarea>`, `<dialog>`, `<details>`, `window.alert·confirm·prompt`, `title` 툴팁, 직접 그린 `<button>`, 직접 그린 칩·배지·알림 상자, 고정 위치 모달·시트, 탭·세그먼트
- 점검 결과 없던 것: 브라우저 `<select>`, 체크박스·라디오·날짜·숫자 `<input>`, `<dialog>`, `window.alert·confirm·prompt`, 직접 그린 모달·탭·토스트. 이미 공통 부품만 쓰고 있어서 확인 창을 바꾸는 일은 없었습니다.

## 요약

| 판정 | 개수 |
|---|---|
| 지금 바꿈 | 24 |
| v3 뒤로 미룸 | 22 |
| #619 뒤로 미룸 | 1 |
| 그대로 둠 | 15 |
| 남음 | 0 |

## 지금 바꿈 (24)

| 파일:줄 | 지금 | 바꾼 공통 컴포넌트 | 이유 |
|---|---|---|---|
| features/inquiry-ai-draft/ui/InquiryAiDraft.tsx:92 | 꾸미지 않은 `<textarea>` | `Textarea` | 같은 모양의 여러 줄 입력이 이미 있음. 값·글자 수 제한·접근성 이름 그대로 |
| views/auth/ui/SignupPages.tsx:526 | 펼침 아이콘 `<button>` | `IconButton` | 아이콘만 있는 버튼. `aria-expanded`·이름 그대로 |
| views/party/ui/PartyFailureScreen.tsx:45 | `buttonClassName`을 `<button>`에 입힘 | `Button` | 버튼 모양을 그대로 쓰므로 `Button`으로 충분 |
| views/race-queue/ui/RaceQueuePage.tsx:137 | 같은 이유 | `Button` | 위와 같음 |
| views/community/ui/CommunityPage.tsx:261 | 같은 이유 | `Button` | 위와 같음 |
| views/community-post/ui/CommentSection.tsx:124 | 알약형 로그인 버튼 | `Button` (보조) | 버튼 모양 직접 그림 |
| views/community-post/ui/CommentSection.tsx:155 | 글자 버튼(등록·저장) | `Button` (글자형) | 위와 같음. 입력이 비면 비활성 그대로 |
| views/community-post/ui/CommentSection.tsx:244 | 글자 버튼(답글 달기) | `Button` (글자형) | 위와 같음 |
| views/admin-stats/ui/TrendSection.tsx:59 | 알약형 토글 버튼 묶음 | `Chip` | 선택 상태를 `aria-pressed`로 알리는 칩과 같은 쓰임 |
| widgets/ax-briefing/ui/AxBriefing.tsx:92 | 빨간 `<span>` 배지 | `Badge` (위험) | 같은 배지가 이미 있음 |
| widgets/ax-briefing/ui/AxBriefing.tsx:150 | 글자 버튼(오늘 숨기기) | `Button` (글자형) | 버튼 모양 직접 그림 |
| views/my-page/ui/MyPage.tsx:132 | 글자 버튼(로그아웃) | `Button` (글자형) | 위와 같음 |
| views/race-list/ui/RecentSearches.tsx:32 | 글자 버튼(전체 삭제) | `Button` (글자형) | 위와 같음 |
| views/seller-races/ui/SellerRacesPage.tsx:99 | 글자 버튼(찾기) | `Button` (글자형) | 위와 같음 |
| views/admin-holds/ui/AdminHoldsPage.tsx:110 | 글자 버튼(찾기) | `Button` (글자형) | 위와 같음 |
| views/seller-kits/ui/SellerKitsPage.tsx:234 | 글자 버튼(찾기) | `Button` (글자형) | 위와 같음 |
| views/admin-races/ui/AdminRacesPage.tsx:91 | 글자 버튼(찾기) | `Button` (글자형) | 위와 같음 |
| views/seller-settlements/ui/SellerSettlementsPage.tsx:168 | 비활성 글자 버튼 | `Button` (글자형) | 위와 같음. `title` 툴팁은 아래 「Tooltip 없음」 참고 |
| views/seller-race-new/ui/SellerRaceNewPage.tsx:358 | 글자 버튼(종목 추가) | `Button` (글자형) | 위와 같음 |
| views/seller-race-new/ui/SellerRaceNewPage.tsx:378 | 글자 버튼(종목 삭제) | `Button` (글자형) | 위와 같음 |
| views/seller-race-new/ui/SellerRaceNewPage.tsx:425 | 글자 버튼(옵션 추가) | `Button` (글자형) | 위와 같음 |
| views/seller-race-new/ui/SellerRaceNewPage.tsx:485 | 글자 버튼(옵션 삭제) | `Button` (글자형) | 위와 같음 |
| views/admin-settlements/ui/AdminSettlementsPage.tsx:109 | 글자 버튼(비활성) | `Button` (글자형) | 위와 같음 |
| views/admin-settlements/ui/AdminSettlementsPage.tsx:154 | 글자 버튼(환급 완료로 바꾸기) | `Button` (글자형) | 위와 같음 |

## v3 뒤로 미룸 (22)

| 파일:줄 | 지금 | 필요한 부품 또는 옵션 | 이유 |
|---|---|---|---|
| features/record-cert/ui/RecordCertFlow.tsx:113 | 맨 `<input type="file">` | 파일 고르기 버튼 | `ImageUpload`는 창을 띄워 자르는 흐름이라 쓰임이 달라짐 |
| widgets/ai-chat/ui/AiChat.tsx:89 | 카드 안에 박힌 맨 `<textarea>` | 테두리 없는 `Textarea` 모양 | `Textarea`는 자체 테두리와 라벨 틀이 있어 카드 안에서 겹침 |
| views/auth/ui/parts.tsx:108 | 밑줄형 `<input>` | `TextField` 밑줄 모양 | 가입·로그인 화면 전용 모양 |
| views/community-post/ui/CommentSection.tsx:142 | 알약형 댓글 `<input>` | `TextField` 알약 모양 | 위와 같음 |
| views/race-list/ui/RaceListPage.tsx:167 | 검색 `<input>` | 검색 입력 부품(아이콘·지우기) | 공통 부품 없음 |
| views/community/ui/CommunityPage.tsx:91 | 검색 `<input type="search">` | 검색 입력 부품 | 위와 같음 |
| views/race-detail/ui/RaceDetailPage.tsx:389 | `<details>/<summary>` 아코디언 | `Accordion` | 공통 부품 없음 |
| views/deposit/ui/DepositPage.tsx:84 | 펼침 버튼 | `Accordion` | 위와 같음 |
| views/deposit/ui/DepositRefundPages.tsx:270 | 펼침 버튼 | `Accordion` | 위와 같음 |
| views/seller-settlements/ui/SellerSettlementsPage.tsx:168 | `title` 기본 툴팁 | `Tooltip` | 공통 부품 없음 |
| widgets/console-sidebar/ui/ConsoleSidebar.tsx:110 | `title` 기본 툴팁 | `Tooltip` | 위와 같음 |
| views/crew/ui/CrewInvitesPage.tsx:327 | 직접 만든 툴팁 | `Tooltip` | 위와 같음 |
| views/seller-race-change/ui/SellerRaceChangePage.tsx:306 | 카드형 `role="radio"` 버튼 | 카드형 `Radio` | `Radio`는 점 모양 선택만 지원 |
| views/inquiry/ui/InquiryComposePage.tsx:59,105 | 카드형 선택 버튼 | 카드형 선택 | 위와 같음 |
| views/party/ui/PartyNewPage.tsx:198 | 카드형 선택 버튼 | 카드형 선택 | 위와 같음 |
| views/race-detail/ui/RaceDetailPage.tsx:327 | 카드형 선택 버튼(품절 표시) | 카드형 선택 | 위와 같음 |
| views/community-post/ui/MenuSheet.tsx:21,50 | `Drawer` 직접 사용, 행동 버튼 | `BottomSheet`의 제목 줄 없는 모양 | `BottomSheet`는 제목 줄이 꼭 있음 |
| views/community-post/ui/CommunityPostPage.tsx:185 | 좋아요 토글 버튼 | 눌림이 검정 반전이 아닌 `Chip` | `Chip` 선택 모양은 검정 바탕이라 하트 색과 어울리지 않음 |
| views/race-list/ui/RecentSearches.tsx:46,53 | 지울 수 있는 칩 | 지우기 버튼이 붙은 `Chip` | 공통 부품 없음 |
| views/community/ui/CommunityPage.tsx:188 | 정렬 글자 버튼 + `BottomSheet` | `SortSelect` | 시트에서 드롭다운으로 쓰임이 달라짐. 시안 결정 필요 |
| views/community-write/ui/CommunityWritePage.tsx:251,288 | 사진 추가·삭제 버튼 | 여러 장 사진 올리기 부품 | `ImageUpload`는 한 장·창 흐름 |
| views/party/ui/PartyDetailPage.tsx:3 | `@base-ui` `Menu` 직접 사용 | `Menu` | 공통 부품 없음 |

## #619 뒤로 미룸 (1)

| 파일:줄 | 지금 | 이유 |
|---|---|---|
| views/deposit/ui/DepositChargePage.tsx:62 | 금액 `<input>` | `features/deposit-charge`, `DepositChargePage`, `DepositChargeReturnPage`는 PR #619가 고치는 중 |

## 그대로 둠 (15)

| 파일:줄 | 지금 | 이유 |
|---|---|---|
| app/global-error.tsx:36 | 인라인 스타일 `<button>` | 루트 화면이 깨졌을 때 쓰는 화면이라 공통 CSS·부품 없이 그려야 함 |
| features/auth/ui/SocialLoginButtons.tsx:61 | 카카오·구글 원형 버튼 | 각 서비스 가이드 색을 따르는 버튼 |
| features/inquiry-manage/ui/InquiryBoard.tsx:156 | 목록 행 선택 `<button>` | 목록 행이라 맞는 공통 부품이 없고 바꿀 이유도 없음 |
| views/community-write/ui/CommunityWritePage.tsx:265 | `hidden` 파일 `<input>` | 사진 추가 버튼이 여는 숨은 입력 |
| entities/member/ui/BirthDateField.tsx:50 | `type="hidden"` `<input>` | 폼 값을 넘기는 숨은 입력 |
| views/ticket-detail/ui/TicketDetailPage.tsx:146 | 카드 아래 전체 폭 취소 행 | 카드의 푸터 행이라 `Button` 모양이 맞지 않음 |
| views/party/ui/PartyDetailPage.tsx:289,298,302 | 세 칸 셀 안 글자 버튼 | 부모의 `*:` 규칙이 칸 크기를 정함 |
| views/race-list/ui/CategoryScreen.tsx:91,107,117,157 | 지역·종목 글자 목록 행 | 목록 행이라 공통 부품 없이 유지 |
| views/community/ui/CommunityPage.tsx:100 | 검색창 안 지우기 아이콘 | 검색 입력 부품(위 미룸)과 함께 바뀜 |
| views/community/ui/CommunityPage.tsx:121 | 글 검색 아이콘 버튼 | 옆 알림 링크와 같은 모양을 유지해야 함 |
| views/my-page/ui/MenuRow.tsx:57 | 메뉴 행 버튼 | 링크 행과 같은 모양을 공유 |
| widgets/console-sidebar/ui/ConsoleSidebar.tsx:110 | 사이드바 항목 | 같은 목록의 링크 항목과 같은 모양 |
| widgets/race-share-sheet/ui/RaceShareSheet.tsx:53 | 아이콘 + 글자 타일 | 카카오 가이드 색 타일 |
| 여러 파일 `<p role="alert">` | 칸 아래 오류 문구 | 필드 보조 문구라 상자형 `Alert`가 아님 |
| 여러 파일 `role="status"` 스켈레톤 | 불러오는 중 영역 | 이미 `Skeleton`을 안에 씀 |
