// 이미지 파일 검사와 1:1 자르기 계산.
// 브라우저 검사는 판매자에게 빨리 알려 주는 용도다. 서버도 같은 규칙으로 다시 검사해야 한다.

export const IMAGE_ACCEPT = ['image/jpeg', 'image/png', 'image/webp']
export const IMAGE_MAX_BYTES = 10 * 1024 * 1024

export type ImageRules = {
  /** 가로 최소 픽셀. 소개 이미지는 1080 */
  minWidth?: number
  maxBytes?: number
}

/** 형식·용량만 바로 검사한다. 문제가 없으면 null */
export function checkImageFile(file: File, { maxBytes = IMAGE_MAX_BYTES }: ImageRules = {}) {
  if (!IMAGE_ACCEPT.includes(file.type)) return 'JPG · PNG · WebP만 올릴 수 있어요'
  if (file.size > maxBytes) return `${Math.round(maxBytes / 1024 / 1024)}MB가 넘는 파일이에요`
  return null
}

export function readImageSize(file: File) {
  return new Promise<{ width: number; height: number }>((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      resolve({ width: img.naturalWidth, height: img.naturalHeight })
      URL.revokeObjectURL(url)
    }
    img.onerror = () => {
      reject(new Error('이미지를 읽지 못했어요'))
      URL.revokeObjectURL(url)
    }
    img.src = url
  })
}

/** 형식·용량에 더해 가로 크기까지 검사한다. */
export async function validateImageFile(file: File, rules: ImageRules = {}) {
  const basic = checkImageFile(file, rules)
  if (basic) return basic
  if (!rules.minWidth) return null
  try {
    const { width } = await readImageSize(file)
    return width < rules.minWidth ? `가로 ${rules.minWidth}px보다 작아요` : null
  } catch {
    return '이미지를 읽지 못했어요'
  }
}

export type CropOffset = { x: number; y: number }

// 자르기 틀(frame, 화면 px) 안을 원본 이미지가 항상 꽉 채우는 배율. zoom 1이 최소다.
export function coverScale(
  natural: { width: number; height: number },
  frame: number,
  zoom: number,
) {
  return (frame / Math.min(natural.width, natural.height)) * zoom
}

/** 틀 밖으로 빈 곳이 보이지 않게 이미지 위치(틀 왼쪽 위 기준)를 막는다. */
export function clampOffset(
  offset: CropOffset,
  natural: { width: number; height: number },
  frame: number,
  scale: number,
): CropOffset {
  const minX = frame - natural.width * scale
  const minY = frame - natural.height * scale
  return {
    x: Math.min(0, Math.max(minX, offset.x)),
    y: Math.min(0, Math.max(minY, offset.y)),
  }
}

/** 확대·축소할 때 틀 가운데가 가리키는 지점을 그대로 둔다. */
export function zoomOffset(offset: CropOffset, frame: number, from: number, to: number) {
  const center = frame / 2
  return {
    x: center - (center - offset.x) * (to / from),
    y: center - (center - offset.y) * (to / from),
  }
}

/** 틀 안에 보이는 영역을 원본 픽셀 좌표로 바꾼다. */
export function cropRect(offset: CropOffset, frame: number, scale: number) {
  // 0 - x는 -0이 되지 않는다.
  return { sx: 0 - offset.x / scale, sy: 0 - offset.y / scale, size: frame / scale }
}

/** 원본에서 정사각형을 잘라 output×output 이미지로 만든다. PNG·WebP는 형식을 지키고 나머지는 JPEG. */
export function cropToSquare(
  img: HTMLImageElement,
  rect: { sx: number; sy: number; size: number },
  output: number,
  type: string,
) {
  const canvas = document.createElement('canvas')
  canvas.width = output
  canvas.height = output
  const ctx = canvas.getContext('2d')
  if (!ctx) return Promise.reject(new Error('이미지를 자르지 못했어요'))
  ctx.drawImage(img, rect.sx, rect.sy, rect.size, rect.size, 0, 0, output, output)
  const outType = type === 'image/png' || type === 'image/webp' ? type : 'image/jpeg'
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('이미지를 자르지 못했어요'))),
      outType,
      0.9,
    )
  })
}
