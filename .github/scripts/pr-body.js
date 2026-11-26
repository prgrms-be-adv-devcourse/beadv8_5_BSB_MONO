// PR 본문 양식 검사. 칸 규칙은 .github/pull_request_template.md 안내 주석과 같다.
// 본문의 `<!-- -->` 주석은 검사에서 뺀다. 라벨(중점 리뷰·논의 필요)도 여기서 붙이고 뗀다.

const TEMPLATE_PATH = '.github/pull_request_template.md'
const SECTIONS = ['요약', '결정 필요', '테스트 항목', '리뷰 위치', '롤백', '검증', '상세']
const LABEL_REVIEW = '중점 리뷰'
const LABEL_DISCUSS = '논의 필요'
const MAX_REVIEW_SPOTS = 3

const FORBIDDEN = [
  ['짚다', /짚[다는어었으고며지]/],
  ['끌어내다', /끌어\s?[내냅냈낸낼]/],
  ['해당', /해당/],
  ['진행하다', /진행(?:하|한|했|할|합)/],
  ['수행하다', /수행(?:하|한|했|할|합)/],
  ['~를 통해', /[를을]\s*통해/],
  ['~에 대해', /에\s*대해/],
  ['기반으로', /기반으로/],
]

const stripComments = (body) => (body ?? '').replace(/<!--[\s\S]*?-->/g, '')

// 코드 블록 안의 줄은 제목·목록·금지어 검사에서 뺀다.
function outsideFences(text) {
  let inFence = false
  return text.split(/\r?\n/).filter((line) => {
    if (/^\s*```/.test(line)) {
      inFence = !inFence
      return false
    }
    return !inFence
  })
}

// '## 제목' 줄 기준으로 칸 이름 → 내용(코드 블록 포함)으로 나눈다.
function parseSections(body) {
  const sections = new Map()
  let current = null
  let inFence = false
  for (const line of stripComments(body).split(/\r?\n/)) {
    if (/^\s*```/.test(line)) {
      inFence = !inFence
    }
    const m = !inFence && line.match(/^##\s+(.+?)\s*$/)
    if (m) {
      current = m[1]
      sections.set(current, [])
    } else if (current !== null) {
      sections.get(current).push(line)
    }
  }
  return new Map([...sections].map(([k, v]) => [k, v.join('\n').trim()]))
}

const isFilled = (text) => text !== undefined && text !== '' && !/^없음[.\s]*$/.test(text)

function checkBody(body, { frontendChanged = false } = {}) {
  const errors = []
  const sections = parseSections(body)

  const missing = SECTIONS.filter((name) => !sections.has(name))
  if (missing.length > 0) {
    errors.push(`빠진 칸이 있습니다: ${missing.map((s) => `'## ${s}'`).join(', ')}. ${TEMPLATE_PATH} 양식대로 칸을 모두 넣어 주세요.`)
  }

  const summary = sections.get('요약')
  if (summary !== undefined) {
    const sentences = summary.split(/(?<=[.!?])\s+/).filter(Boolean)
    if (sentences.length !== 2) {
      errors.push(`'## 요약'은 두 문장이어야 합니다(지금 ${sentences.length}문장). 무엇이 바뀌는지, 그래서 무엇이 달라지는지 씁니다.`)
    }
  }

  const spots = outsideFences(sections.get('리뷰 위치') ?? '').filter((l) => /^(?:[-*]|\d+\.)\s+\S/.test(l)).length
  if (spots > MAX_REVIEW_SPOTS) {
    errors.push(`'## 리뷰 위치'는 최대 ${MAX_REVIEW_SPOTS}곳입니다(지금 ${spots}곳). 가장 중요한 곳만 남겨 주세요.`)
  }

  if (frontendChanged && !isFilled(sections.get('스크린샷'))) {
    errors.push(`프론트 파일을 바꿨으니 '## 스크린샷'에 변경 전·후 화면을 넣어 주세요.`)
  }

  const prose = outsideFences(stripComments(body)).join('\n').replace(/`[^`\n]*`/g, '')
  const found = FORBIDDEN.filter(([, re]) => re.test(prose)).map(([word]) => word)
  if (found.length > 0) {
    errors.push(`쓰지 않는 말이 있습니다: ${found.join(', ')}. 더 쉬운 말로 바꿔 주세요.`)
  }

  return {
    errors,
    labels: {
      [LABEL_REVIEW]: isFilled(sections.get('리뷰 위치')),
      [LABEL_DISCUSS]: isFilled(sections.get('결정 필요')),
    },
  }
}

// 봇 PR, dev-main-sync 워크플로가 만드는 dev→main 동기화 PR은 검사하지 않는다
function skipReason(pr) {
  if (pr.user?.type === 'Bot') {
    return `봇(${pr.user.login})이 만든 PR`
  }
  if (pr.head.ref === 'dev' && pr.base.ref === 'main') {
    return 'dev→main 동기화 PR'
  }
  return null
}

async function syncLabels({ github, context, core }, pr, labels) {
  const current = new Set(pr.labels.map((l) => l.name))
  const issue = { ...context.repo, issue_number: pr.number }
  try {
    for (const [name, want] of Object.entries(labels)) {
      if (want && !current.has(name)) {
        await github.rest.issues.addLabels({ ...issue, labels: [name] })
      } else if (!want && current.has(name)) {
        await github.rest.issues.removeLabel({ ...issue, name })
      }
    }
  } catch (e) {
    // 포크 PR은 토큰이 읽기 전용이라 라벨을 못 붙인다. 본문 검사 결과는 그대로 낸다.
    core.warning(`라벨을 바꾸지 못했습니다: ${e.message}`)
  }
}

module.exports = async ({ github, context, core }) => {
  // 본문은 사용자 입력이라 스크립트에 끼워 넣지 않고 이벤트 payload에서 읽는다
  const pr = context.payload.pull_request
  const reason = skipReason(pr)
  if (reason) {
    core.notice(`${reason}이라 본문 검사를 건너뜁니다.`)
    return
  }
  if (!pr.body || pr.body.trim() === '') {
    core.setFailed(`PR 본문이 비어 있습니다. ${TEMPLATE_PATH} 양식으로 채워 주세요.`)
    return
  }
  const files = await github.paginate(github.rest.pulls.listFiles, { ...context.repo, pull_number: pr.number, per_page: 100 })
  const { errors, labels } = checkBody(pr.body, { frontendChanged: files.some((f) => f.filename.startsWith('frontend/')) })
  await syncLabels({ github, context, core }, pr, labels)
  if (errors.length > 0) {
    core.setFailed(errors.join('\n'))
  }
}

module.exports.SECTIONS = SECTIONS
module.exports.checkBody = checkBody
module.exports.skipReason = skipReason
