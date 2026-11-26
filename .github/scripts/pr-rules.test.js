// 실행: node --test .github/scripts/pr-rules.test.js
const test = require('node:test')
const assert = require('node:assert')
const { isValidBranch } = require('./pr-rules.js')
const check = require('./pr-rules.js')

test('브랜치 이름: <종류>/<이슈 번호>-<설명>과 <종류>/auto-<설명>을 받는다', () => {
  assert.ok(isValidBranch('fix/812-queue-reentry'))
  assert.ok(isValidBranch('ci/827-matt-pocock-setup'))
  assert.ok(isValidBranch('refactor/auto-checkstyle-queue-service'))
  assert.ok(isValidBranch('chore/auto-retro-2026-11-30'))
})

test('브랜치 이름: 옛 CRU·BUK·PRO 키, claude/ 이름, 형식이 틀린 이름은 막는다', () => {
  for (const name of [
    'feat/CRU-12-crew-invite',
    'feat/BUK-12-a',
    'fix/PRO-3-a',
    'claude/ci-cru-workspace',
    'feat/812',
    'feat/812-',
    'feat/812-Queue',
    'feat/auto',
    'wip/812-a',
    'feat/a812-a',
  ]) {
    assert.ok(!isValidBranch(name), name)
  }
})

test('오류 문구: 새 형식 예시를 보여 준다', async () => {
  let message = ''
  await check({
    context: { payload: { pull_request: { labels: [], head: { ref: 'bad' }, base: { ref: 'dev' } } } },
    core: { setFailed: (m) => (message = m), notice() {} },
  })
  assert.match(message, /fix\/812-queue-reentry/)
  assert.match(message, /auto-/)
})

test('dev→main 동기화 PR은 검사하지 않는다', async () => {
  let failed = false
  await check({
    context: { payload: { pull_request: { labels: [], head: { ref: 'dev' }, base: { ref: 'main' } } } },
    core: { setFailed: () => (failed = true), notice() {} },
  })
  assert.ok(!failed)
})

test('no-issue 라벨이 있어도 브랜치 이름을 검사한다', async () => {
  let failed = false
  await check({
    context: { payload: { pull_request: { labels: [{ name: 'no-issue' }], head: { ref: 'bad' }, base: { ref: 'dev' } } } },
    core: { setFailed: () => (failed = true), notice() {} },
  })
  assert.ok(failed)
})
