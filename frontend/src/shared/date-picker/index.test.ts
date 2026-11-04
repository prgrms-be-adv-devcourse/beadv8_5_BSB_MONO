import { describe, expect, it } from 'vitest'

import * as datePicker from '@/shared/date-picker'
import * as ui from '@/shared/ui'

// 공용 UI 묶음은 모든 화면이 받는다. 달력(react-day-picker · date-fns 언어 파일)은 쓰는 화면만 받도록 따로 꺼낸다
describe('달력 공개 경로', () => {
  it('공용 UI 묶음에는 달력이 없다', () => {
    expect(ui).not.toHaveProperty('DatePicker')
    expect(ui).not.toHaveProperty('DateTimeField')
  })

  it('달력은 @/shared/date-picker에서 가져온다', () => {
    expect(datePicker.DatePicker).toBeTypeOf('function')
    expect(datePicker.DateTimeField).toBeTypeOf('function')
  })
})
