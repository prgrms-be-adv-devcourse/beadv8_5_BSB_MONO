import piexif from 'piexifjs'
import { describe, expect, it } from 'vitest'

import { stripGps } from './strip-gps'

// EXIF 없는 최소 JPEG(SOI + SOS + EOI). piexif가 APP1을 SOI 뒤에 넣는다
const BASE_JPEG = bytesToDataUrl(new Uint8Array([0xff, 0xd8, 0xff, 0xda, 0x00, 0x02, 0xff, 0xd9]))

// 회전(6) + GPS를 넣은 사진
const WITH_GPS = piexif.insert(
  piexif.dump({
    '0th': { [piexif.ImageIFD.Orientation]: 6 },
    Exif: {},
    GPS: {
      [piexif.GPSIFD.GPSVersionID]: [2, 3, 0, 0],
      [piexif.GPSIFD.GPSLatitudeRef]: 'N',
      [piexif.GPSIFD.GPSLatitude]: [
        [37, 1],
        [30, 1],
        [0, 1],
      ],
    },
    Interop: {},
    '1st': {},
    thumbnail: null,
  }),
  BASE_JPEG,
)

describe('stripGps', () => {
  it('GPS만 지우고 회전과 픽셀은 그대로 둔다', async () => {
    expect(Object.keys(piexif.load(WITH_GPS).GPS).length).toBeGreaterThan(0) // 넣은 GPS 확인

    const stripped = await stripGps(fileOf(WITH_GPS))
    const after = piexif.load(await dataUrlOf(stripped))

    expect(Object.keys(after.GPS)).toHaveLength(0) // GPS 지워짐
    expect(after['0th'][piexif.ImageIFD.Orientation]).toBe(6) // 회전 유지
    // EXIF를 모두 뗀 순수 이미지가 같으면 픽셀이 그대로다
    expect(piexif.remove(await dataUrlOf(stripped))).toBe(piexif.remove(WITH_GPS))
  })

  it('GPS가 없으면 같은 파일을 그대로 돌려준다', async () => {
    const file = fileOf(BASE_JPEG)
    expect(await stripGps(file)).toBe(file)
  })

  it('JPEG가 아니면 그대로 둔다', async () => {
    const png = new File([new Uint8Array([1, 2, 3])], 'x.png', { type: 'image/png' })
    expect(await stripGps(png)).toBe(png)
  })
})

function bytesToDataUrl(bytes: Uint8Array): string {
  let binary = ''
  for (const b of bytes) {
    binary += String.fromCharCode(b)
  }
  return `data:image/jpeg;base64,${btoa(binary)}`
}

function fileOf(dataUrl: string): File {
  const binary = atob(dataUrl.slice(dataUrl.indexOf(',') + 1))
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new File([bytes], 'cert.jpg', { type: 'image/jpeg' })
}

async function dataUrlOf(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  for (const b of bytes) {
    binary += String.fromCharCode(b)
  }
  return `data:${file.type};base64,${btoa(binary)}`
}
