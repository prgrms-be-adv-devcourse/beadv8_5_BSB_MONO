// 기록증 OCR 모델 · 사전의 sha256이 명세(ocr-spec.json) 값과 같은지 본다. 프론트 public/과 ai-service 리소스 두 벌이 어긋나지 않게 CI에서 돌린다.
// 사용: node scripts/check-ocr-models.mjs
import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const spec = JSON.parse(
  readFileSync(join(root, 'src/features/record-cert/model/ocr-spec.json'), 'utf8'),
)
const { det, rec, dict } = spec.models

// 두 곳 모두 필수다. 파일이 없으면 실패한다
const places = [
  { dir: join(root, 'public/ocr'), files: [det, rec, dict] },
  // ai-service는 인식만 하므로 검출 모델은 없다(BUK-272)
  { dir: join(root, '../backend/ai-service/src/main/resources/recordcert'), files: [rec, dict] },
]

let failed = false
for (const { dir, files } of places) {
  for (const { file, sha256 } of files) {
    const path = join(dir, file)
    const got = existsSync(path)
      ? createHash('sha256').update(readFileSync(path)).digest('hex')
      : '(파일 없음)'
    const ok = got === sha256
    failed ||= !ok
    console.log(`${ok ? '같음' : '다름'}: ${path}${ok ? '' : `\n  명세 ${sha256}\n  파일 ${got}`}`)
  }
}
if (failed) {
  console.error(
    '모델 · 사전이 ocr-spec.json과 다릅니다. 파일을 바꿨으면 specVersion을 올리고 골든 세트를 다시 만드세요.',
  )
  process.exit(1)
}
