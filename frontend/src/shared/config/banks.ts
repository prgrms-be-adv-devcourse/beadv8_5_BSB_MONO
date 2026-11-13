/** 계좌를 받는 API(예치금 환급, 판매자 정산 계좌)는 은행 이름이 아니라 금융결제원 은행 코드를 받는다 */
export const BANKS = [
  { value: '004', label: '국민은행' },
  { value: '088', label: '신한은행' },
  { value: '020', label: '우리은행' },
  { value: '081', label: '하나은행' },
  { value: '090', label: '카카오뱅크' },
  { value: '092', label: '토스뱅크' },
] as const

export type BankCode = (typeof BANKS)[number]['value']

export const bankName = (code: string) => BANKS.find((b) => b.value === code)?.label ?? code
