// @vitest-environment node
// 시제품 extract.py self_check()의 어서트를 옮겼다(+ evaluate.py의 name_tokens 어서트 2개, 같은 값 하나로 세기)
import { describe, expect, it } from 'vitest'

import {
  courseClass,
  datesIn,
  extractFields,
  GUN_WORD,
  nameTokens,
  NET_ANCHOR,
  normBib,
  pickBib,
  pickCourse,
  pickDate,
  pickNet,
  pickRace,
  plausible,
  sameRace,
  sameTime,
  timesIn,
  type OcrLine,
} from './extract'

const L = (text: string, box: number[][]): OcrLine => ({ text, score: 1, box })
const ln = (t: string) =>
  L(t, [
    [0, 0],
    [10, 0],
    [10, 10],
    [0, 10],
  ])
const bx = (x: number, y: number, w = 60) => [
  [x, y],
  [x + w, y],
  [x + w, y + 30],
  [x, y + 30],
]
const B = (text: string, x: number, y: number, w = 60) => L(text, bx(x, y, w))

describe('times_in · plausible · course_class', () => {
  it('times_in', () => {
    expect(timesIn('기록 03:47:12')).toEqual([[3, 47, 12]])
    expect(timesIn('2시간 48분 1초')).toEqual([[2, 48, 1]])
    expect(timesIn('41:57')).toEqual([[0, 41, 57]])
    expect(timesIn('5\'30"/km')).toEqual([])
    expect(timesIn('기록:02:02:34.71')).toEqual([[2, 2, 34]]) // 003
    expect(timesIn('04.15:39')).toEqual([[4, 15, 39]]) // 099
    expect(timesIn('2025.10.25')).toEqual([])
    expect(timesIn('52분34초')).toEqual([[0, 52, 34]])
    expect(timesIn('1시간9분32초')).toEqual([[1, 9, 32]])
  })
  it('times_in: 같은 시간은 하나로 센다', () => {
    expect(timesIn('01:30:05 01:30:05')).toEqual([[1, 30, 5]])
    expect(timesIn('1:30:05 / 1시간 30분 5초')).toEqual([[1, 30, 5]])
    expect(timesIn('01:30:05 01:31:00')).toEqual([
      [1, 30, 5],
      [1, 31, 0],
    ])
  })
  it('same_time', () => expect(sameTime([3, 14, 5], [3, 14])).toBe(true))
  it('plausible', () => {
    expect(plausible([0, 6, 4], 22.07)).toBeNull() // 041
    expect(plausible([9, 43, 9], 10)).toBeNull() // 081
    expect(plausible([0, 3, 14], 42.195)).toEqual([3, 14]) // 001
  })
  it('course_class', () => {
    expect(courseClass('42.195km')).toBe('FULL')
    expect(courseClass('10.00 km')).toBe('10K')
    expect(courseClass('하프')).toBe('HALF')
    expect(courseClass('5K·10K')).toBeNull()
    expect(courseClass('5km 00:23:59')).toBeNull()
    expect(courseClass('full course 03:09:25')).toBe('FULL')
    for (const t of ['Full', 'FULL COURSE', 'Full코스', 'full course 03:09:25', 'fullmarathon'])
      expect(courseClass(t), t).toBe('FULL')
    for (const t of ['HALF', 'Half Marathon', 'half(21.0975km)'])
      expect(courseClass(t), t).toBe('HALF')
    for (const t of ['has a successfully completed', 'halfway', 'Fulltime'])
      expect(courseClass(t), t).toBeNull() // 175
  })
  it('GUN_WORD · NET_ANCHOR', () => {
    expect(GUN_WORD.test('Finish Time: 09:31:11.84') && GUN_WORD.test('오후1:02')).toBe(true) // 053 · 017
    expect(NET_ANCHOR.test('기록정리가 완료될때까지')).toBe(false) // 081
    expect(NET_ANCHOR.test('기록:02:02:34')).toBe(true) // 003
  })
})

describe('pick_net', () => {
  const grid = [
    L('건타임', [
      [100, 0],
      [160, 0],
      [160, 20],
      [100, 20],
    ]),
    L('넷타임', [
      [200, 0],
      [260, 0],
      [260, 20],
      [200, 20],
    ]),
    L('3:48:30', [
      [100, 30],
      [170, 30],
      [170, 50],
      [100, 50],
    ]),
    L('3:47:12', [
      [200, 30],
      [270, 30],
      [270, 50],
      [200, 50],
    ]),
  ]
  it('표: box는 넷타임 아래 칸, order는 건타임 값', () => {
    expect(pickNet(grid, 'box')![1]).toEqual([3, 47, 12])
    expect(pickNet(grid, 'order')![1]).toEqual([3, 48, 30])
  })
  it('상자 셋으로 나뉜 기록을 잇는다', () => {
    const units = [
      B('공식 기록(NET TIME)', 0, 0, 200),
      B('1시간', 0, 40),
      B('9분', 70, 40),
      B('32초', 140, 40),
      B('5km00:34:46', 0, 300, 200),
    ]
    expect(pickNet(units, 'box', 10)![1]).toEqual([1, 9, 32])
  })
  it('진짜 기록이 걸러지면 구간 기록 대신 비운다', () => {
    const split = [
      B('넷타임(NET TIME)', 0, 0, 200),
      B('03:23:15', 0, 40),
      B('10km 00:48:10', 0, 300, 200),
    ]
    expect(pickNet(split, 'box', 10)).toBeNull()
    expect(pickNet(split, 'box', 42.195)![1]).toEqual([3, 23, 15])
  })
  it('159: 행 이름이 구간이면 그 행 값은 빼고 큰 기록', () => {
    const sec = [
      B('00:57:02.27', 0, 0, 200),
      B('Record', 300, 100),
      B('Section 1', 0, 150),
      B('09:38:17.03 (00:28:04.83)', 250, 150, 200),
    ]
    expect(pickNet(sec, 'box', 10)![1]).toEqual([0, 57, 2])
  })
  it('s-081: 도착시각 머리글 아래 값은 기록이 아니다', () => {
    const clock = [
      B('NET TIME', 0, 0),
      B('::', 0, 40),
      B('FINISHCLOCK [도착시각]', 300, 200, 200),
      B('1:57:04', 320, 260, 100),
    ]
    expect(pickNet(clock, 'box', 42.195)).toBeNull()
  })
  it('표 · 같은 행 Gun/Net', () => {
    const table = [
      B('건타임', 0, 0),
      B('넷타임', 100, 0),
      B('3:48:30', 0, 40),
      B('3:47:12', 100, 40),
    ]
    expect(pickNet(table, 'box', 42.195)![1]).toEqual([3, 47, 12])
    const gn = [B('Gun', 0, 0, 40), B('3:48:30', 50, 0), B('Net', 120, 0, 40), B('3:47:12', 170, 0)]
    expect(pickNet(gn, 'box', 42.195)![1]).toEqual([3, 47, 12])
  })
})

describe('dates', () => {
  it('dates_in', () => {
    expect(datesIn('November 5, 2023, Seoul')).toEqual([[2023, 11, 5]])
    expect(datesIn('APRIL 16-17, 2022')).toEqual([[2022, 4, 16]])
    expect(datesIn('16 March, 2025')).toEqual([[2025, 3, 16]])
    expect(datesIn('2025.03.16')).toEqual([[2025, 3, 16]])
    expect(datesIn('On28th October, 2018.')).toEqual([[2018, 10, 28]])
  })
  it('dates_in: 같은 날짜는 하나로 센다', () => {
    expect(datesIn('2025.03.16 (2025-03-16)')).toEqual([[2025, 3, 16]])
    expect(datesIn('2025.03.16 March 16, 2025')).toEqual([[2025, 3, 16]])
    expect(datesIn('2025.03.16 2025.03.17')).toEqual([
      [2025, 3, 16],
      [2025, 3, 17],
    ])
  })
  it('pick_date: 연도 · 월일이 따로', () => {
    expect(pickDate([ln('2026'), ln('3월 29일')])![1]).toBe('2026-03-29') // 004
    expect(pickDate([ln('2012 년'), ln('8 월 31 일')])![1]).toBe('2012-08-31') // 132
    expect(pickDate([ln('2026'), ln('3월 29일'), ln('4월 1일 발급')])).toBeNull()
    expect(pickDate([ln('2025'), ln('2026'), ln('3월 29일')])).toBeNull()
  })
})

describe('pick_course', () => {
  it('앱 화면의 거리(km) 아래 · 위 숫자', () => {
    const app = [
      L('거리 (km)', [
        [0, 0],
        [80, 0],
        [80, 20],
        [0, 20],
      ]),
      L('22.07', [
        [0, 30],
        [70, 30],
        [70, 60],
        [0, 60],
      ]),
    ]
    expect(pickCourse(app)![1]).toBe('22.07K')
    const below = [
      L('22.07', [
        [0, 0],
        [70, 0],
        [70, 30],
        [0, 30],
      ]),
      L('거리 (km)', [
        [0, 40],
        [80, 40],
        [80, 60],
        [0, 60],
      ]),
    ]
    expect(pickCourse(below)![1]).toBe('22.07K')
  })
})

describe('pick_bib', () => {
  it('같은 줄 · 아래 칸', () => {
    expect(pickBib([B('배번:F-3877|성명:서O0', 0, 0, 200)])![1]).toBe('F-3877')
    expect(pickBib([B('BIB 3142', 0, 0)])![1]).toBe('3142')
    expect(pickBib([B('배번(BIB)', 0, 0), B('206103', 0, 40)])![1]).toBe('206103')
    expect(pickBib([B('11133', 0, 0)])).toBeNull()
    expect(pickBib([B('BIB: 5826  RUNNER: 임', 0, 0)])![1]).toBe('5826')
    expect(normBib('m-8363')).toBe(normBib('M8363'))
    for (const [t, v] of [
      ['참가번호·12237', '12237'],
      ['하프코스(bibnumber:1292)', '1292'],
      ['배번Number : 8434', '8434'],
    ])
      expect(pickBib([B(t, 0, 0, 200)])![1], t).toBe(v)
    expect(pickBib([B('참가번호(BIB):', 0, 0), B(':124', 70, 0)])![1]).toBe('124')
  })
  it('순위표 · 가려진 배번은 비운다', () => {
    expect(pickBib([B('배번호·이름', 0, 0), B('124·류0근', 0, 40)])).toBeNull()
    expect(
      pickBib([B('배번호·이름', 0, 0), B('171·장0덕', 0, 40), B('124·류0근', 0, 80)]),
    ).toBeNull() // 007
    expect(pickBib([B('배번 40', 0, 0)])).toBeNull()
  })
})

describe('pick_race · same_race · name_tokens', () => {
  it('제목이 크다, 조직위 줄은 뺀다', () => {
    const title = [
      L('2023 JTBC 서울마라톤', [
        [0, 0],
        [300, 0],
        [300, 60],
        [0, 60],
      ]),
      L('서울마라톤 조직위원회', [
        [0, 500],
        [300, 500],
        [300, 560],
        [0, 560],
      ]),
      L('런 페이스 5:30', [
        [0, 200],
        [300, 200],
        [300, 220],
        [0, 220],
      ]),
    ]
    expect(pickRace(title)![1]).toBe('2023 JTBC 서울마라톤')
  })
  it('same_race · name_tokens', () => {
    expect(sameRace('JTBC서울마라톤', '2023 JTBC 서울마라톤')).toBe(true)
    expect(sameRace('서울마라톤', '2023 JTBC 서울마라톤')).toBe(false)
    expect(nameTokens('2025 서울마라톤')).toEqual(['서울'])
    expect(nameTokens('제23회 인천국제하프마라톤대회')).toEqual(['인천국제하프'])
  })
})

describe('extractFields: 서버 확인 상자', () => {
  it('넷타임 값 아래 칸이면 표시 단어는 위 머리글 줄', () => {
    const table = [
      B('건타임', 0, 0),
      B('넷타임', 100, 0),
      B('3:48:30', 0, 40),
      B('3:47:12', 100, 40),
    ]
    const f = extractFields(table)
    expect(f.netTime!.quad).toEqual(bx(100, 40))
    expect(f.netLabel).toEqual({ text: '넷타임', quad: bx(100, 0) })
  })
  it('값 줄에 표시 단어가 있으면 같은 줄', () => {
    const f = extractFields([B('넷타임 3:47:12', 0, 0, 200)])
    expect(f.netLabel!.quad).toEqual(f.netTime!.quad)
  })
  it('표시 단어가 없으면 비운다', () => {
    expect(extractFields([B('3:47:12', 0, 0)]).netLabel).toBeNull()
  })
})
