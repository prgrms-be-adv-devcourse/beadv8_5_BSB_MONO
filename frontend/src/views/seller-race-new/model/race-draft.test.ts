import { afterEach, describe, expect, it, vi } from 'vitest'

import { clearRaceDraft, loadRaceDraft, raceDraftKey, saveRaceDraft } from './race-draft'
import { RACE_FORM_DEFAULTS, type RaceFormValues } from './race-form'
import { SAMPLE_COURSES } from './race-form.fixtures'

const values: RaceFormValues = {
  ...RACE_FORM_DEFAULTS,
  title: '가을 마라톤',
  raceDate: '2026-10-25',
  raceTime: '08:00',
  crewOpen: true,
  crewSaleStartAt: '2026-10-05 10:00',
  crewMaxCount: '20',
  courses: SAMPLE_COURSES,
  kitOptions: [{ name: '티셔츠', value: 'M', stock: '100' }],
}

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('대회 등록 임시 저장 (회원별 localStorage)', () => {
  it('저장한 값을 같은 회원이 다시 불러온다', () => {
    saveRaceDraft(7, values)
    expect(localStorage.getItem(raceDraftKey(7))).toContain('가을 마라톤')
    expect(loadRaceDraft(7)).toEqual(values)
  })

  it('키가 회원마다 달라 다른 회원은 남의 작성 내용을 불러오지 못한다', () => {
    saveRaceDraft(7, values)
    expect(raceDraftKey(7)).not.toBe(raceDraftKey(8))
    expect(loadRaceDraft(8)).toBeNull()
  })

  it('지우면 불러오지 않는다', () => {
    saveRaceDraft(7, values)
    clearRaceDraft(7)
    expect(loadRaceDraft(7)).toBeNull()
  })

  it('숫자가 아닌 정원(비운 칸)은 0으로 저장해 다시 불러올 수 있다', () => {
    saveRaceDraft(7, {
      ...values,
      courses: [{ ...SAMPLE_COURSES[0], crewQuota: Number.NaN, individualQuota: Number.NaN }],
    })
    expect(loadRaceDraft(7)?.courses[0]).toMatchObject({ crewQuota: 0, individualQuota: 0 })
  })

  it('깨진 글자나 모양이 다른 값은 불러오지 않는다', () => {
    localStorage.setItem(raceDraftKey(7), '{깨짐')
    expect(loadRaceDraft(7)).toBeNull()
    localStorage.setItem(raceDraftKey(7), JSON.stringify({ title: 123 }))
    expect(loadRaceDraft(7)).toBeNull()
  })

  it('저장소를 읽거나 쓰지 못해도(차단 · 용량 초과) 오류를 던지지 않는다', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota')
    })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(() => saveRaceDraft(7, values)).not.toThrow()
    expect(loadRaceDraft(7)).toBeNull()
    expect(() => clearRaceDraft(7)).not.toThrow()
  })

  it('크루 최대 인원을 저장하고, 이 칸이 없던 옛 저장 내용도 비워서 불러온다', () => {
    saveRaceDraft(7, values)
    expect(loadRaceDraft(7)?.crewMaxCount).toBe('20')

    const { crewMaxCount: _omit, ...older } = values
    void _omit
    localStorage.setItem(raceDraftKey(7), JSON.stringify(older))
    expect(loadRaceDraft(7)?.crewMaxCount).toBe('')
  })
})
