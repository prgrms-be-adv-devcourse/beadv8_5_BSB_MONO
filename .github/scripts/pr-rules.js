// PR 브랜치 이름 검사 (docs/LINEAR.md "작업 흐름")
// 형식: <type>/PRO-<번호>-<짧은-설명>. type은 커밋 타입과 같다.

const TYPES = ['feat', 'fix', 'refactor', 'perf', 'test', 'docs', 'style', 'build', 'ci', 'chore', 'revert']
const SKIP_LABEL = 'no-issue'
const BRANCH_RE = new RegExp(`^(?:${TYPES.join('|')})/PRO-\\d+-[a-z0-9]+(?:-[a-z0-9]+)*$`, 'i')

function isValidBranch(name) {
  return BRANCH_RE.test(name)
}

module.exports = async ({ context, core }) => {
  const pr = context.payload.pull_request
  if (pr.labels.some((l) => l.name === SKIP_LABEL)) {
    core.notice(`'${SKIP_LABEL}' 라벨이 있어 브랜치 이름 검사를 건너뜁니다.`)
    return
  }
  if (!isValidBranch(pr.head.ref)) {
    core.setFailed(
      `브랜치 이름 '${pr.head.ref}'이 규칙에 맞지 않습니다. ` +
        `<type>/PRO-<번호>-<짧은-설명> 형식으로 지어 주세요 (예: feat/PRO-12-crew-invite-accept). ` +
        `Linear 이슈가 없는 작업이면 PR에 '${SKIP_LABEL}' 라벨을 붙이세요.`,
    )
  }
}

module.exports.isValidBranch = isValidBranch
