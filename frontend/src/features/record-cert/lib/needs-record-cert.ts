/**
 * 이 종목에 기록증 규칙이 있는가. 공개 종목 조회의 "기록증 필요 여부" 칸을 본다.
 * TODO(BUK-270): 칸이 생기면 productModel 타입의 칸 이름으로 바꾼다. 지금은 칸이 없어 늘 false다.
 */
export function needsRecordCert(course: object | undefined): boolean {
  return (course as { recordCertRequired?: unknown } | undefined)?.recordCertRequired === true
}
