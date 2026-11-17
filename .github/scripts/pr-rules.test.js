// 실행: node --test .github/scripts/pr-rules.test.js
const test = require('node:test')
const assert = require('node:assert')
const { isValidBranch } = require('./pr-rules.js')

test('브랜치 이름: 새 팀 키 CRU를 받는다', () => {
  assert.ok(isValidBranch('feat/CRU-12-crew-invite-accept'))
  assert.ok(isValidBranch('ci/CRU-1-linear-two-workspaces'))
})

test('브랜치 이름: 열려 있는 BUK·PRO 브랜치도 계속 받는다', () => {
  assert.ok(isValidBranch('feat/BUK-12-crew-invite-accept'))
  assert.ok(isValidBranch('fix/PRO-3-a'))
})

test('브랜치 이름: 다른 키, claude/ 이름, 형식이 틀린 이름은 막는다', () => {
  for (const name of ['feat/ABC-1-a', 'claude/ci-cru-workspace', 'feat/CRU-a', 'feat/CRU-1', 'wip/CRU-1-a', 'feat/CRUX-1-a']) {
    assert.ok(!isValidBranch(name), name)
  }
})

test('안내 문구: 예시를 새 팀 키 CRU로 보여 준다', async () => {
  const check = require('./pr-rules.js')
  let message = ''
  await check({
    context: { payload: { pull_request: { labels: [], head: { ref: 'bad' } } } },
    core: { setFailed: (m) => (message = m), notice() {} },
  })
  assert.match(message, /feat\/CRU-12-crew-invite-accept/)
})
