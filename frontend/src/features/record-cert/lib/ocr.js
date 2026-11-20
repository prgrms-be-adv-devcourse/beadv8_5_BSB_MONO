// 기록증 OCR 파이프라인(검출 + 인식). 시제품 proto-web/ocr.js를 그대로 옮겼다(기준 구현 onnx_ocr.py를 한 줄씩 옮긴 것, 골든 테스트로 확인).
// 브라우저(ocr.worker.ts)와 Node(golden.test.ts)가 같이 쓴다: createOcr({ ort, cv, ClipperLib, detModel, recModel, dictText }).
// float32 계산은 Math.fround로 numpy(float32)와 맞춘다. 반올림은 numpy · Python과 같은 짝수 반올림.
// 앞처리 값은 model/ocr-spec.json과 같아야 한다. 바꾸면 specVersion을 올리고 골든 세트를 다시 만든다.

const DET_MEAN = [0.485, 0.456, 0.406].map(Math.fround)
const DET_STD = [0.229, 0.224, 0.225].map(Math.fround)
const THRESH = 0.3,
  BOX_THRESH = 0.6,
  UNCLIP = 1.5,
  MAX_CAND = 1000,
  MIN_SIZE = 3
const f = Math.fround

export function roundHalfEven(x) {
  const r = Math.round(x)
  return Math.abs(x % 1) === 0.5 && r % 2 !== 0 ? r - 1 : r
}

export function createOcr({
  ort,
  cv,
  ClipperLib,
  detModel,
  recModel,
  dictText,
  limitSide = 960,
  batch = 6,
  sessionOptions = {},
}) {
  const chars = [
    'blank',
    ...dictText.split(/\r?\n/).filter((_, i, a) => i < a.length - 1 || a[i] !== ''),
    ' ',
  ]
  let det,
    rec,
    recRunMs = 0

  async function load() {
    const opt = { executionProviders: ['wasm'], ...sessionOptions }
    ;[det, rec] = await Promise.all([
      ort.InferenceSession.create(detModel, opt),
      ort.InferenceSession.create(recModel, opt),
    ])
  }

  // cv.boxPoints는 opencv.js에서 비어 나온다. RotatedRect.points는 cv2.boxPoints와 무작위 2000개에서 비트까지 같았다
  const boxPoints = (r) => cv.RotatedRect.points(r).map((p) => [f(p.x), f(p.y)])

  function orderPts(p) {
    p = [...p].sort((u, v) => u[0] - v[0])
    const [i1, i4] = p[1][1] > p[0][1] ? [0, 1] : [1, 0]
    const [i2, i3] = p[3][1] > p[2][1] ? [2, 3] : [3, 2]
    return [p[i1], p[i2], p[i3], p[i4]]
  }

  function minAreaRect(flatInts) {
    const m = cv.matFromArray(flatInts.length / 2, 1, cv.CV_32SC2, flatInts)
    const r = cv.minAreaRect(m)
    m.delete()
    return r
  }

  function miniBox(flatInts) {
    const r = minAreaRect(flatInts)
    return [orderPts(boxPoints(r)), Math.min(f(r.size.width), f(r.size.height))]
  }

  function boxScoreFast(pred, H, W, box) {
    const xs = box.map((p) => p[0]),
      ys = box.map((p) => p[1])
    const clip = (v, hi) => Math.min(Math.max(v, 0), hi)
    const xmin = clip(Math.floor(Math.min(...xs)), W - 1),
      xmax = clip(Math.ceil(Math.max(...xs)), W - 1)
    const ymin = clip(Math.floor(Math.min(...ys)), H - 1),
      ymax = clip(Math.ceil(Math.max(...ys)), H - 1)
    const mh = ymax - ymin + 1,
      mw = xmax - xmin + 1
    const mask = cv.Mat.zeros(mh, mw, cv.CV_8UC1)
    const poly = cv.matFromArray(
      4,
      1,
      cv.CV_32SC2,
      box.flatMap((p) => [Math.trunc(f(p[0] - xmin)), Math.trunc(f(p[1] - ymin))]),
    )
    const mv = new cv.MatVector()
    mv.push_back(poly)
    cv.fillPoly(mask, mv, new cv.Scalar(1))
    let s = 0,
      n = 0
    for (let y = 0; y < mh; y++) {
      for (let x = 0; x < mw; x++) {
        if (mask.data[y * mw + x]) {
          s += pred[(y + ymin) * W + x + xmin]
          n++
        }
      }
    }
    mask.delete()
    poly.delete()
    mv.delete()
    return n ? s / n : 0
  }

  function unclip(box) {
    const m = cv.matFromArray(4, 1, cv.CV_32FC2, box.flat())
    const area = cv.contourArea(m),
      length = cv.arcLength(m, true)
    m.delete()
    const off = new ClipperLib.ClipperOffset(2, 0.25)
    off.AddPath(
      box.map((p) => ({ X: Math.trunc(p[0]), Y: Math.trunc(p[1]) })),
      ClipperLib.JoinType.jtRound,
      ClipperLib.EndType.etClosedPolygon,
    )
    const sol = new ClipperLib.Paths()
    off.Execute(sol, (area * UNCLIP) / length)
    return sol
  }

  function dbPost(pred, H, W, srcH, srcW) {
    const seg = new cv.Mat(H, W, cv.CV_8UC1)
    for (let i = 0; i < H * W; i++) seg.data[i] = pred[i] > THRESH ? 255 : 0
    const contours = new cv.MatVector(),
      hier = new cv.Mat()
    cv.findContours(seg, contours, hier, cv.RETR_LIST, cv.CHAIN_APPROX_SIMPLE)
    const boxes = []
    for (let k = 0; k < Math.min(contours.size(), MAX_CAND); k++) {
      const c = contours.get(k)
      const flat = Array.from(c.data32S)
      c.delete()
      let [pts, sside] = miniBox(flat)
      if (sside < MIN_SIZE) continue
      if (BOX_THRESH > boxScoreFast(pred, H, W, pts)) continue
      const ex = unclip(pts)
      if (ex.length !== 1) continue // paddlex: 확장 결과가 여러 개면 버린다
      let box
      ;[box, sside] = miniBox(ex[0].flatMap((p) => [p.X, p.Y]))
      if (sside < MIN_SIZE + 2) continue
      boxes.push(
        box.map(([x, y]) => [
          Math.min(Math.max(roundHalfEven(f(f(x / W) * srcW)), 0), srcW),
          Math.min(Math.max(roundHalfEven(f(f(y / H) * srcH)), 0), srcH),
        ]),
      )
    }
    seg.delete()
    contours.delete()
    hier.delete()
    return boxes
  }

  function orderClockwise(pts) {
    const s = pts.map((p) => f(p[0] + p[1]))
    const iMin = s.indexOf(Math.min(...s)),
      iMax = s.indexOf(Math.max(...s))
    const tmp = pts.filter((_, i) => i !== iMin && i !== iMax)
    const d = tmp.map((p) => f(p[1] - p[0]))
    return [pts[iMin], tmp[d.indexOf(Math.min(...d))], pts[iMax], tmp[d.indexOf(Math.max(...d))]]
  }

  const norm = (a, b) => f(Math.sqrt(f(f(f(a[0] - b[0]) ** 2) + f(f(a[1] - b[1]) ** 2))))

  function filterBoxes(boxes, h, w) {
    const out = []
    for (const b0 of boxes) {
      const b = orderClockwise(b0).map(([x, y]) => [
        Math.min(Math.max(x, 0), w - 1),
        Math.min(Math.max(y, 0), h - 1),
      ])
      if (Math.trunc(norm(b[0], b[1])) <= 3 || Math.trunc(norm(b[0], b[3])) <= 3) continue
      out.push(b)
    }
    return out
  }

  function sortBoxes(boxes) {
    const b = [...boxes].sort((u, v) => u[0][1] - v[0][1] || u[0][0] - v[0][0])
    for (let i = 0; i < b.length - 1; i++) {
      for (let j = i; j >= 0; j--) {
        if (Math.abs(b[j + 1][0][1] - b[j][0][1]) < 10 && b[j + 1][0][0] < b[j][0][0])
          [b[j], b[j + 1]] = [b[j + 1], b[j]]
        else break
      }
    }
    return b
  }

  function crop(img, box) {
    const pts = orderPts(
      boxPoints(minAreaRect(box.flatMap((p) => [Math.trunc(p[0]), Math.trunc(p[1])]))),
    )
    const cw = Math.trunc(Math.max(norm(pts[0], pts[1]), norm(pts[2], pts[3])))
    const ch = Math.trunc(Math.max(norm(pts[0], pts[3]), norm(pts[1], pts[2])))
    const src = cv.matFromArray(4, 1, cv.CV_32FC2, pts.flat())
    const dst = cv.matFromArray(4, 1, cv.CV_32FC2, [0, 0, cw, 0, cw, ch, 0, ch])
    const M = cv.getPerspectiveTransform(src, dst)
    const out = new cv.Mat()
    cv.warpPerspective(
      img,
      out,
      M,
      new cv.Size(cw, ch),
      cv.INTER_CUBIC,
      cv.BORDER_REPLICATE,
      new cv.Scalar(),
    )
    src.delete()
    dst.delete()
    M.delete()
    if (out.rows / out.cols >= 1.5) {
      const r = new cv.Mat()
      cv.rotate(out, r, cv.ROTATE_90_COUNTERCLOCKWISE) // np.rot90
      out.delete()
      return r
    }
    return out
  }

  // onnx_ocr.rec_input: 높이 48, 채움 너비 imgW, 그림 너비 rw. 결과는 [3, 48, imgW] float32
  function recInput(img) {
    const h = img.rows,
      w = img.cols
    let imgW = Math.trunc(48 * Math.max(320 / 48, w / h)),
      rw
    if (imgW > 3200) [rw, imgW] = [3200, 3200]
    else rw = Math.ceil((48 * w) / h) > imgW ? imgW : Math.ceil((48 * w) / h)
    const r = new cv.Mat()
    cv.resize(img, r, new cv.Size(rw, 48), 0, 0, cv.INTER_LINEAR)
    const x = new Float32Array(3 * 48 * imgW),
      d = r.data
    for (let y = 0; y < 48; y++) {
      for (let xx = 0; xx < rw; xx++) {
        for (let c = 0; c < 3; c++)
          x[c * 48 * imgW + y * imgW + xx] = f(f(d[(y * rw + xx) * 3 + c] / 255) - 0.5) / 0.5
      }
    }
    r.delete()
    return { x, imgW }
  }

  function ctcDecode(probs, off, T, C) {
    let text = '',
      s = 0,
      n = 0,
      prev = -1
    for (let t = 0; t < T; t++) {
      let best = 0,
        bv = -Infinity
      for (let c = 0; c < C; c++) {
        const v = probs[off + t * C + c]
        if (v > bv) [bv, best] = [v, c]
      }
      if (best !== prev && best !== 0) {
        text += chars[best]
        s += bv
        n++
      }
      prev = best
    }
    return [text, n ? s / n : 0]
  }

  async function recBatch(inputs) {
    const mw = Math.max(...inputs.map((t) => t.imgW))
    const x = new Float32Array(inputs.length * 3 * 48 * mw)
    inputs.forEach((t, b) => {
      for (let row = 0; row < 3 * 48; row++)
        x.set(t.x.subarray(row * t.imgW, (row + 1) * t.imgW), (b * 3 * 48 + row) * mw)
    })
    const rs = performance.now()
    const out = await rec.run({
      [rec.inputNames[0]]: new ort.Tensor('float32', x, [inputs.length, 3, 48, mw]),
    })
    recRunMs += performance.now() - rs
    const p = out[rec.outputNames[0]]
    const [, T, C] = p.dims
    if (C !== chars.length) throw new Error(`사전 크기 ${chars.length} != 모델 출력 ${C}`)
    return inputs.map((_, b) => ctcDecode(p.data, b * T * C, T, C))
  }

  // RGBA(브라우저 ImageData) 또는 RGB(sharp) → BGR Mat
  function toBgr({ data, width, height, channels = 4 }) {
    const m = new cv.Mat(height, width, channels === 4 ? cv.CV_8UC4 : cv.CV_8UC3)
    m.data.set(data)
    const bgr = new cv.Mat()
    cv.cvtColor(m, bgr, channels === 4 ? cv.COLOR_RGBA2BGR : cv.COLOR_RGB2BGR)
    m.delete()
    return bgr
  }

  async function ocr(image) {
    const t = { start: performance.now() }
    const img = toBgr(image)
    const h = img.rows,
      w = img.cols
    const ratio = Math.max(h, w) > limitSide ? limitSide / Math.max(h, w) : 1
    let rh = Math.trunc(h * ratio),
      rw = Math.trunc(w * ratio)
    if (Math.max(rh, rw) > 4000) {
      const r = 4000 / Math.max(rh, rw)
      ;[rh, rw] = [Math.trunc(rh * r), Math.trunc(rw * r)]
    }
    rh = Math.max(roundHalfEven(rh / 32) * 32, 32)
    rw = Math.max(roundHalfEven(rw / 32) * 32, 32)
    const small = new cv.Mat()
    cv.resize(img, small, new cv.Size(rw, rh), 0, 0, cv.INTER_LINEAR)
    const x = new Float32Array(3 * rh * rw),
      d = small.data,
      plane = rh * rw
    for (let i = 0; i < plane; i++) {
      for (let c = 0; c < 3; c++)
        x[c * plane + i] = f(f(f(d[i * 3 + c] / 255) - DET_MEAN[c]) / DET_STD[c])
    }
    small.delete()
    t.pre = performance.now()
    const out = await det.run({ [det.inputNames[0]]: new ort.Tensor('float32', x, [1, 3, rh, rw]) })
    const pred = out[det.outputNames[0]].data
    t.det = performance.now()
    const boxes = sortBoxes(filterBoxes(dbPost(pred, rh, rw, h, w), h, w))
    t.post = performance.now()
    const crops = boxes.map((b) => crop(img, b))
    t.crop = performance.now()
    recRunMs = 0
    img.delete()
    const order = crops
      .map((_, i) => i)
      .sort((a, b) => crops[a].cols / crops[a].rows - crops[b].cols / crops[b].rows)
    const res = []
    for (let k = 0; k < order.length; k += batch) {
      const ids = order.slice(k, k + batch)
      const r = await recBatch(ids.map((i) => recInput(crops[i])))
      ids.forEach((i, j) => (res[i] = r[j]))
    }
    crops.forEach((c) => c.delete())
    t.rec = performance.now()
    const lines = boxes.map((b, i) => ({
      text: res[i][0],
      score: Math.round(res[i][1] * 1e4) / 1e4,
      box: b,
    }))
    return {
      lines,
      detInputHw: [rh, rw],
      ms: {
        pre: t.pre - t.start,
        det: t.det - t.pre,
        post: t.post - t.det,
        crop: t.crop - t.post,
        rec: t.rec - t.crop,
        recRun: recRunMs,
        total: t.rec - t.start,
      },
    }
  }

  // 골든 확인(verify): 이미 자른 조각 하나를 batch 1로 읽는다
  async function readCrop(image) {
    const img = toBgr(image)
    const [r] = await recBatch([recInput(img)])
    img.delete()
    return r
  }

  return { load, ocr, readCrop, chars }
}
