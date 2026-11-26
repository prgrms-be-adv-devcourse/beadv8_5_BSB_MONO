// PR 브랜치 이름 검사
// 형식: <종류>/<GitHub 이슈 번호>-<영어 설명>. 이슈 없이 자동으로 여는 PR은 <종류>/auto-<영어 설명>. 종류는 커밋 종류와 같다.

const TYPES = ['feat', 'fix', 'refactor', 'perf', 'test', 'docs', 'style', 'build', 'ci', 'chore', 'revert']
const BRANCH_RE = new RegExp(`^(?:${TYPES.join('|')})/(?:\\d+|auto)-[a-z0-9]+(?:-[a-z0-9]+)*$`)

function isValidBranch(name) {
  return BRANCH_RE.test(name)
}

// dev-main-sync 워크플로가 만드는 dev→main 동기화 PR은 사람이 쓰는 브랜치가 아니라 검사하지 않는다
function isSyncPr(pr) {
  return pr.head.ref === 'dev' && pr.base?.ref === 'main'
}

module.exports = async ({ context, core }) => {
  const pr = context.payload.pull_request
  if (isSyncPr(pr)) {
    core.notice('dev→main 동기화 PR이라 브랜치 이름 검사를 건너뜁니다.')
    return
  }
  if (!isValidBranch(pr.head.ref)) {
    core.setFailed(
      `브랜치 이름 '${pr.head.ref}'이 규칙에 맞지 않습니다. ` +
        `<종류>/<이슈 번호>-<영어 설명> 형식으로 지어 주세요 (예: fix/812-queue-reentry). ` +
        `이슈 없이 자동으로 여는 PR은 <종류>/auto-<영어 설명> 형식입니다.`,
    )
  }
}

module.exports.isValidBranch = isValidBranch
module.exports.isSyncPr = isSyncPr
