// clipper-lib는 타입을 내지 않는다. ocr.js에만 넘기므로 모양은 보지 않는다
declare module 'clipper-lib' {
  const ClipperLib: unknown
  export default ClipperLib
}
