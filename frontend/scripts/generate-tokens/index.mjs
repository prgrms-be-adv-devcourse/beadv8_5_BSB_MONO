// 피그마 변수 JSON으로 웹 토큰 CSS를 만든다.
// 사용법 (frontend/ 폴더에서):
//   pnpm tokens                      # src/app/tokens.css를 다시 만든다
//   pnpm tokens --check              # 다시 만들었을 때 바뀌는 곳이 있으면 실패한다
//   pnpm tokens --check-hash <hash>  # JSON이 피그마에서 받은 값과 같은지만 확인한다 (design-tokens/figma-export.js의 hash).
//                                    # 파일은 쓰지 않는다. --check와 함께 주면 둘 다 확인한다
// PENDING(피그마와 아직 다른 토큰)을 다시 확인해야 하면 경고를 출력한다. 실패로 끝나지는 않는다.
import { readFileSync, writeFileSync } from 'node:fs'

import { hashOf, renderTokensCss, stalePending } from './render.mjs'

const SOURCE = 'design-tokens/figma-variables.json'
const OUTPUT = 'src/app/tokens.css'

const figma = JSON.parse(readFileSync(SOURCE, 'utf8'))
const args = process.argv.slice(2)

const hashIndex = args.indexOf('--check-hash')
if (hashIndex !== -1) {
  const expected = args[hashIndex + 1]
  const actual = hashOf(figma)
  if (actual !== expected) {
    console.error(`${SOURCE}가 피그마에서 받은 값과 다릅니다 (hash ${actual}, 기대 ${expected}).`)
    process.exit(1)
  }
  console.log(`ok   ${SOURCE} hash ${actual}`)
  if (!args.includes('--check')) process.exit(0)
}

for (const warning of stalePending(figma)) {
  console.warn(`warn PENDING ${warning}`)
}

const css = renderTokensCss(figma)
if (args.includes('--check')) {
  if (readFileSync(OUTPUT, 'utf8') !== css) {
    console.error(`${OUTPUT}가 ${SOURCE}와 다릅니다. pnpm tokens로 다시 만드세요.`)
    process.exit(1)
  }
  console.log(`ok   ${OUTPUT}는 ${SOURCE}와 같습니다`)
} else {
  writeFileSync(OUTPUT, css)
  console.log(`ok   ${SOURCE} → ${OUTPUT}`)
}
