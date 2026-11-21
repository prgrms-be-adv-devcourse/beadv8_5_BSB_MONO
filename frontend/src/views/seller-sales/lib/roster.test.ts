import { describe, expect, it } from 'vitest'

import {
  applyMethod,
  kitOptionText,
  ORDER_STATUS,
  parsePartyNo,
  PARTY_STATUS,
  partyNo,
} from './roster'

describe('판매 현황 명단 글', () => {
  it('주문 상태를 시안의 이름과 색으로 바꾼다', () => {
    expect(ORDER_STATUS.PAID).toEqual({ label: '참가 확정', tone: 'success' })
    expect(ORDER_STATUS.ORDERED).toEqual({ label: '결제 대기', tone: 'default' })
    expect(ORDER_STATUS.CANCELED).toEqual({ label: '취소', tone: 'danger' })
    expect(ORDER_STATUS.CANCELING.tone).toBe('danger')
  })

  it('파티 상태를 시안의 이름과 색으로 바꾼다', () => {
    expect(PARTY_STATUS.PAYING).toEqual({ label: '결제 중', tone: 'muted' })
    expect(PARTY_STATUS.PAID).toEqual({ label: '참가 확정', tone: 'success' })
    expect(PARTY_STATUS.CLOSED).toEqual({ label: '신청 안 됨', tone: 'danger' })
    expect(PARTY_STATUS.RECRUITING.tone).toBe('muted')
  })

  it('키트 옵션은 이름과 값을 · 로 잇고, 옛 주문은 티셔츠 크기를 쓴다', () => {
    expect(
      kitOptionText({
        kitOptions: [
          { kitOptionId: 1, name: '티셔츠', value: 'M' },
          { kitOptionId: 2, name: '양말', value: '255-280' },
        ],
      }),
    ).toBe('티셔츠 M · 양말 255-280')
    expect(kitOptionText({ kitOptions: [], tshirtSize: 'L' })).toBe('티셔츠 L')
    expect(kitOptionText({ kitOptions: [] })).toBe('-')
  })

  it('파티 번호가 있으면 파티 신청, 없으면 개인 신청이다', () => {
    expect(applyMethod(1023)).toBe('파티 P-1023')
    expect(applyMethod(null)).toBe('개인')
    expect(applyMethod(undefined)).toBe('개인')
    expect(partyNo(3)).toBe('P-3')
  })

  it('찾기 칸의 파티 번호는 P- 를 붙여도 떼어도 읽는다', () => {
    expect(parsePartyNo('P-1023')).toBe(1023)
    expect(parsePartyNo(' p1023 ')).toBe(1023)
    expect(parsePartyNo('1023')).toBe(1023)
    expect(parsePartyNo('김철수')).toBeUndefined()
    expect(parsePartyNo('')).toBeUndefined()
    expect(parsePartyNo('0')).toBeUndefined()
  })
})
