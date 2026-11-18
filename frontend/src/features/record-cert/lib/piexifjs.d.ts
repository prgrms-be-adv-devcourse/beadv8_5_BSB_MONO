// piexifjs는 타입을 내지 않는다. 쓰는 API만 적는다(GPS만 지우고 회전은 남기는 용도).
declare module 'piexifjs' {
  type IfdTags = Record<number, unknown>
  interface ExifDict {
    '0th': IfdTags
    Exif: IfdTags
    GPS: IfdTags
    Interop: IfdTags
    '1st': IfdTags
    thumbnail: string | null
  }
  const piexif: {
    load(jpegData: string): ExifDict
    dump(exifDict: Partial<ExifDict>): string
    insert(exifStr: string, jpegData: string): string
    remove(jpegData: string): string
    GPSIFD: Record<string, number>
    ImageIFD: Record<string, number>
    ExifIFD: Record<string, number>
  }
  export default piexif
}
