// 실행: node --test .github/scripts/pr-body.test.js
const test = require('node:test')
const assert = require('node:assert')
const { checkBody, skipReason } = require('./pr-body.js')

const body = (over = {}) => {
  const s = {
    요약: '대기열 재입장 제한을 풀었습니다. 결제를 취소한 회원이 같은 종목에 다시 들어갈 수 있습니다.',
    '결정 필요': '없음',
    '테스트 항목': '- 결제를 취소한 회원은 같은 종목 대기열에 다시 들어갈 수 있다',
    '리뷰 위치': '없음',
    롤백: '쉬움, 코드만 바뀝니다.',
    검증: './gradlew :queue-service:test 통과',
    스크린샷: null,
    상세: '<details><summary>배경</summary>\n\n배경 설명\n\n</details>',
    ...over,
  }
  return (
    Object.entries(s)
      .filter(([, v]) => v !== null)
      .map(([k, v]) => `## ${k}\n\n${v}`)
      .join('\n\n') + '\n\nCloses #1'
  )
}

test('모든 칸이 있고 규칙을 지키면 통과, 라벨은 둘 다 없다', () => {
  const r = checkBody(body())
  assert.deepStrictEqual(r.errors, [])
  assert.deepStrictEqual(r.labels, { '중점 리뷰': false, '논의 필요': false })
})

test('빠진 칸 이름을 알려 준다', () => {
  const r = checkBody(body({ 롤백: null, 검증: null }))
  assert.match(r.errors.join(), /## 롤백.*## 검증/)
})

test('요약은 두 문장이어야 한다', () => {
  assert.ok(checkBody(body({ 요약: '한 문장만 씁니다.' })).errors.length > 0)
  assert.ok(checkBody(body({ 요약: '하나입니다. 둘입니다. 셋입니다.' })).errors.length > 0)
  assert.deepStrictEqual(checkBody(body({ 요약: '하나입니다.\n둘입니다.' })).errors, [])
})

test('리뷰 위치는 최대 3곳, 코드 조각 안의 목록은 세지 않는다', () => {
  const spot = (n) => `- \`A.java:${n}\` 돈이 바뀝니다`
  const code = '```\n- 코드 안의 줄\n- 또 한 줄\n```'
  const ok = checkBody(body({ '리뷰 위치': [spot(1), code, spot(2), spot(3)].join('\n') }))
  assert.deepStrictEqual(ok.errors, [])
  const bad = checkBody(body({ '리뷰 위치': [spot(1), spot(2), spot(3), spot(4)].join('\n') }))
  assert.match(bad.errors.join(), /최대 3곳/)
})

test('금지어가 있으면 실패하고, 주석·코드 안의 말은 보지 않는다', () => {
  for (const w of ['문제를 짚었습니다.', '값을 끌어냅니다.', '해당 파일입니다.', '작업을 진행합니다.', '검증을 수행했습니다.', '설정을 통해 바꿉니다.', '정책에 대해 씁니다.', '이를 기반으로 만듭니다.']) {
    const r = checkBody(body({ 롤백: w }))
    assert.match(r.errors.join(), /쓰지 않는 말/, w)
  }
  assert.deepStrictEqual(checkBody(body({ 롤백: '<!-- 해당 -->쉬움 `해당`\n```\n해당\n```' })).errors, [])
})

test('주석 안의 글자는 칸 내용으로 치지 않는다', () => {
  const r = checkBody(body({ '리뷰 위치': '<!-- 최대 3곳 -->', '결정 필요': '<!-- 정해 줄 것 -->' }))
  assert.deepStrictEqual(r.labels, { '중점 리뷰': false, '논의 필요': false })
})

test('리뷰 위치·결정 필요에 내용이 있으면 라벨을 붙인다', () => {
  const r = checkBody(body({ '리뷰 위치': '- `A.java:3` 잔액을 바꿉니다', '결정 필요': '취소 수수료를 정해 주세요.' }))
  assert.deepStrictEqual(r.labels, { '중점 리뷰': true, '논의 필요': true })
})

test('프론트 파일을 바꿨는데 스크린샷이 비면 실패한다', () => {
  assert.match(checkBody(body(), { frontendChanged: true }).errors.join(), /스크린샷/)
  assert.match(checkBody(body({ 스크린샷: '없음' }), { frontendChanged: true }).errors.join(), /스크린샷/)
  assert.deepStrictEqual(checkBody(body({ 스크린샷: '| 전 | 후 |' }), { frontendChanged: true }).errors, [])
  assert.deepStrictEqual(checkBody(body()).errors, [])
})

test('봇 PR과 dev→main 동기화 PR은 건너뛴다', () => {
  assert.ok(skipReason({ user: { type: 'Bot', login: 'x' }, head: { ref: 'a' }, base: { ref: 'dev' } }))
  assert.ok(skipReason({ user: { type: 'User' }, head: { ref: 'dev' }, base: { ref: 'main' } }))
  assert.strictEqual(skipReason({ user: { type: 'User' }, head: { ref: 'a' }, base: { ref: 'dev' } }), null)
})
