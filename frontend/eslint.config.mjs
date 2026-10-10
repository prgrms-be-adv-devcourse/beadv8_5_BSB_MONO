import nextVitals from 'eslint-config-next/core-web-vitals'
import nextTs from 'eslint-config-next/typescript'
import prettier from 'eslint-config-prettier/flat'
import boundaries from 'eslint-plugin-boundaries'
import { defineConfig, globalIgnores } from 'eslint/config'

// FSD 층. 위에서 아래로만 import할 수 있다.
const LAYERS = ['app', 'views', 'widgets', 'features', 'entities', 'shared']
const SLICE_LAYERS = ['views', 'widgets', 'features', 'entities']

/** 다른 슬라이스는 index.ts(public API)를 통해서만 가져온다. */
const publicApi = (types) => ({
  to: { element: { types: { anyOf: types }, fileInternalPath: 'index.{ts,tsx}' } },
})

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries },
    settings: {
      'import/resolver': { typescript: { alwaysTryTypes: true } },
      'boundaries/elements': [
        // Next 라우팅 폴더(src/app)가 FSD의 app 층을 겸한다
        { type: 'app', pattern: 'src/app' },
        ...SLICE_LAYERS.map((type) => ({
          type,
          pattern: `src/${type}/*`,
          capture: ['slice'],
        })),
        { type: 'shared', pattern: 'src/shared/*', capture: ['segment'] },
      ],
    },
    rules: {
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          message:
            'FSD 규칙 위반: {{from.element.type}}에서 {{to.element.type}}의 이 파일을 import할 수 없습니다. ' +
            '아래 층만, 다른 슬라이스는 index.ts로만 가져오세요.',
          policies: [
            // 같은 슬라이스 안, 외부 패키지, Node 내장 모듈은 자유
            { allow: { dependency: { relationship: { to: 'internal' } } } },
            { allow: { to: { module: { origin: ['external', 'core'] } } } },
            // 층 순서
            ...LAYERS.slice(0, -1).map((layer, i) => ({
              from: { element: { type: layer } },
              allow: publicApi(LAYERS.slice(i + 1).filter((l) => l !== 'shared')),
            })),
            // shared는 슬라이스가 없는 층이라 어느 층에서든 세그먼트 index를 쓰고,
            // shared 안에서는 세그먼트끼리 자유롭게 쓴다.
            { from: { element: { types: { anyOf: LAYERS } } }, allow: publicApi(['shared']) },
            {
              from: { element: { type: 'shared' } },
              allow: { to: { element: { type: 'shared' } } },
            },
          ],
        },
      ],
    },
  },
  prettier,
  globalIgnores([
    '.next/**',
    'storybook-static/**',
    'out/**',
    'build/**',
    'next-env.d.ts',
    'src/shared/api/generated/**',
  ]),
])

export default eslintConfig
