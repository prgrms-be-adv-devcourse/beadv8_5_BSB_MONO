// 실행: node --test .github/scripts/project-sync.test.js
const test = require('node:test')
const assert = require('node:assert')
const { decideStatus, parseSection, linkedIssueNumbers } = require('./project-sync.js')
const { weekOf, weekAfter } = require('./week-milestone.js')

const open = (o = {}) => ({ state: 'open', labels: [], assignees: [], ...o })

test('칸: 아무것도 없으면 백로그', () => {
  assert.deepStrictEqual(decideStatus(open(), []), { status: '백로그' })
  assert.deepStrictEqual(decideStatus(open({ labels: ['검토 대기', '정보 필요'] }), []), { status: '백로그' })
})

test('칸: 팩토리 구현·구현 대기 라벨은 준비', () => {
  assert.deepStrictEqual(decideStatus(open({ labels: ['팩토리 구현'] }), []), { status: '준비' })
  assert.deepStrictEqual(decideStatus(open({ labels: ['구현 대기'] }), []), { status: '준비' })
})

test('칸: 담당자가 있거나 초안 PR만 있으면 진행', () => {
  assert.deepStrictEqual(decideStatus(open({ assignees: ['a'] }), []), { status: '진행' })
  assert.deepStrictEqual(decideStatus(open(), [{ draft: true }]), { status: '진행' })
  assert.deepStrictEqual(decideStatus(open({ labels: ['구현 대기'], assignees: ['a'] }), []), { status: '진행' })
})

test('칸: 초안이 아닌 PR이 열려 있으면 리뷰 (담당자·초안 PR보다 우선)', () => {
  assert.deepStrictEqual(decideStatus(open({ assignees: ['a'] }), [{ draft: false }]), { status: '리뷰' })
  assert.deepStrictEqual(decideStatus(open(), [{ draft: true }, { draft: false }]), { status: '리뷰' })
})

test('칸: 닫히면 완료, 모든 규칙보다 우선', () => {
  assert.deepStrictEqual(decideStatus(open({ state: 'closed', assignees: ['a'] }), [{ draft: false }]), { status: '완료' })
})

test('칸: 취소 라벨로 닫히면 완료가 아니라 보관', () => {
  assert.deepStrictEqual(decideStatus(open({ state: 'closed', labels: ['취소'] }), []), { archive: true })
  assert.deepStrictEqual(decideStatus(open({ labels: ['취소'] }), []), { status: '백로그' })
})

test('본문 파싱: 담당 서비스·우선순위 값', () => {
  const body = '### 현재 문제\n\n불편\n\n### 담당 서비스\n\nqueue\n\n### 관련 정책\n\n_No response_\n\n### 우선순위\n\n높음'
  assert.strictEqual(parseSection(body, '담당 서비스'), 'queue')
  assert.strictEqual(parseSection(body, '우선순위'), '높음')
  assert.strictEqual(parseSection(body, '관련 정책'), '')
  assert.strictEqual(parseSection(body, '없는 칸'), '')
  assert.strictEqual(parseSection(null, '우선순위'), '')
  assert.strictEqual(parseSection('### 담당 서비스\r\n\r\ncash\r\n', '담당 서비스'), 'cash')
  assert.strictEqual(parseSection('### 담당 서비스\n### 우선순위\n보통', '담당 서비스'), '')
})

test('연결 이슈: 본문 닫는 키워드와 브랜치 번호', () => {
  const pr = { body: 'Closes #12, fixes #13\nRefs #99\nCloses other/repo#7', head: { ref: 'feat/833-project-sync' } }
  assert.deepStrictEqual(linkedIssueNumbers(pr).sort(), [12, 13, 833])
  assert.deepStrictEqual(linkedIssueNumbers({ body: null, head: { ref: 'feat/CRU-12-x' } }), [])
  assert.deepStrictEqual(linkedIssueNumbers({ body: 'Resolves: #5', head: { ref: 'main' } }), [5])
})

test('주: 월요일 시작, 제목은 월요일 날짜, 마감은 일요일 12:00Z', () => {
  // 2026-11-27(금) 한국 시간
  assert.deepStrictEqual(weekOf(new Date('2026-11-27T03:00:00Z')), {
    title: '11/23 주', start: '2026-11-23', dueOn: '2026-11-29T12:00:00Z',
  })
  assert.strictEqual(weekAfter(new Date('2026-11-27T03:00:00Z'), 1).title, '11/30 주')
})

test('주: 한국 시간 기준 경계(일요일 23:59 KST는 그 주, 월요일 00:00 KST는 다음 주)', () => {
  assert.strictEqual(weekOf(new Date('2026-11-29T14:59:00Z')).title, '11/23 주')
  assert.strictEqual(weekOf(new Date('2026-11-29T15:00:00Z')).title, '11/30 주')
})

test('주: 달이 바뀌는 주', () => {
  assert.deepStrictEqual(weekOf(new Date('2026-12-02T00:00:00Z')).dueOn, '2026-12-06T12:00:00Z')
  assert.strictEqual(weekOf(new Date('2026-12-30T00:00:00Z')).title, '12/28 주')
  assert.strictEqual(weekOf(new Date('2026-12-30T00:00:00Z')).dueOn, '2027-01-03T12:00:00Z')
})
