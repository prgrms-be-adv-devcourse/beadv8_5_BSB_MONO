import piexif from 'piexifjs'

/**
 * 업로드 전 사진에서 위치(GPS) EXIF만 지운다. 회전 태그(0th)와 픽셀은 그대로 둬서,
 * 서버가 같은 자리를 다시 읽어 비교하는 위변조 탐지(ai-service CertReader)가 깨지지 않게 한다.
 * 원본을 그대로 올리면 휴대폰 사진의 촬영 위치가 판매자에게 노출되는 것을 막는다.
 * shortcut: JPEG만 처리한다(휴대폰 사진 대부분). PNG·WebP·HEIC는 그대로 올린다 — 그 형식의 GPS도 지워야 하면 올린다.
 */
export async function stripGps(file: File): Promise<File> {
  if (file.type !== 'image/jpeg') {
    return file
  }
  try {
    const dataUrl = await toDataUrl(file)
    const exif = piexif.load(dataUrl)
    if (Object.keys(exif.GPS).length === 0) {
      return file // GPS가 없으면 바이트를 바꾸지 않는다
    }
    exif.GPS = {}
    const stripped = piexif.insert(piexif.dump(exif), dataUrl)
    // Uint8Array는 유효한 BlobPart지만 TS lib의 제네릭(ArrayBufferLike)이 File 생성자와 안 맞아 한 번 좁힌다
    return new File([dataUrlToBytes(stripped) as BlobPart], file.name, { type: file.type })
  } catch {
    // EXIF를 못 읽는 JPEG는 업로드를 막지 않고 원본 그대로 올린다(파싱 실패로 제출을 못 하게 하지 않는다)
    return file
  }
}

async function toDataUrl(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  const chunk = 0x8000 // 큰 사진에서 String.fromCharCode 인자가 스택을 넘지 않게 나눠 붙인다
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return `data:${file.type};base64,${btoa(binary)}`
}

function dataUrlToBytes(dataUrl: string): Uint8Array {
  const binary = atob(dataUrl.slice(dataUrl.indexOf(',') + 1))
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}
