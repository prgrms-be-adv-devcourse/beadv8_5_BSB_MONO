// 모노레포 전체 커밋에 적용된다. 규칙은 ../.claude/rules/git-commit-convention.md와 같다.
// 영역(scope)은 경고(1)로 시작한다. 열린 PR이 한꺼번에 막히지 않도록 일주일 뒤 실패(2)로 올린다.
const config = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'scope-enum': [
      1,
      'always',
      [
        'member',
        'product',
        'order',
        'queue',
        'cash',
        'payout',
        'board',
        'inquiry',
        'notification',
        'file',
        'ai',
        'gateway',
        'common',
        'design',
        'infra',
        'ci',
        'e2e',
        'loadtest',
      ],
    ],
  },
}

export default config
