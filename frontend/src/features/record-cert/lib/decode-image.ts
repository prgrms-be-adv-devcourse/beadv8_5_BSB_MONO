/**
 * 사진을 픽셀로 푼다. EXIF 회전을 적용하고(좌표 기준), 색 프로필은 바꾸지 않는다(OpenCV · 서버와 같게, ocr-spec image).
 * 원본 파일은 그대로 올리므로 여기서 푼 값은 읽기에만 쓴다.
 */
export async function decodeImage(file: Blob): Promise<ImageData> {
  const bitmap = await createImageBitmap(file, {
    imageOrientation: 'from-image',
    colorSpaceConversion: 'none',
    premultiplyAlpha: 'none',
  })
  const canvas = new OffscreenCanvas(bitmap.width, bitmap.height)
  const context = canvas.getContext('2d', { willReadFrequently: true })
  if (!context) throw new Error('사진을 열지 못했어요')
  context.drawImage(bitmap, 0, 0)
  bitmap.close()
  return context.getImageData(0, 0, canvas.width, canvas.height)
}
