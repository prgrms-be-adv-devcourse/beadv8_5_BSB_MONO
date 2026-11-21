/** 입력에서 숫자만 남겨 천 단위 쉼표를 넣는다 (최대 9자리: 서버 int 범위). 읽을 때는 쉼표를 지운다 */
export function groupedDigits(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 9)
  return digits ? Number(digits).toLocaleString('ko-KR') : ''
}
