// 기록증 OCR worker가 쓰는 onnxruntime-web 실행 파일(WASM 4스레드판)을 public/ocr/ort/에 복사한다.
// 버전마다 바뀌는 15MB 파일이라 저장소에 넣지 않고 dev · build 전에 node_modules에서 가져온다(.gitignore).
import { copyFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const from = join(root, 'node_modules/onnxruntime-web/dist')
const to = join(root, 'public/ocr/ort')
mkdirSync(to, { recursive: true })
for (const file of ['ort-wasm-simd-threaded.mjs', 'ort-wasm-simd-threaded.wasm']) {
  copyFileSync(join(from, file), join(to, file))
}
