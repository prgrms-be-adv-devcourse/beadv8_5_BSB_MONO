import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { Alert } from './alert/Alert'
import { Avatar } from './avatar/Avatar'
import { BottomCta } from './bottom-cta/BottomCta'
import { Carousel, CarouselSlide } from './carousel/Carousel'
import { Chip } from './chip/Chip'
import { Checkbox } from './choice/Checkbox'
import { Radio } from './choice/Radio'
import { Dialog } from './dialog/Dialog'
import { EmptyState } from './empty-state/EmptyState'
import { Select } from './field/Select'
import { Textarea } from './field/Textarea'
import { TextField } from './field/TextField'
import { KeyValue } from './key-value/KeyValue'
import { ProgressBar } from './progress-bar/ProgressBar'
import { StatStrip, StatTile } from './stat-tile/StatTile'
import { DataTable } from './table/DataTable'
import { Tab, TabBar, Tabs } from './tabs/Tabs'
import { TimerBar } from './timer-bar/TimerBar'

const dialogOpen = { open: true, onOpenChange: () => {}, confirmLabel: '확인', onConfirm: () => {} }

describe('v4 시안 반영으로 더한 prop', () => {
  it('Dialog stacked: 버튼을 세로로 쌓고 확인이 위에 온다', () => {
    render(<Dialog {...dialogOpen} title="t" stacked />)
    const confirm = screen.getByRole('button', { name: '확인' })
    expect(confirm.parentElement?.className).toMatch(/\bflex-col\b/)
    expect(confirm.parentElement?.firstElementChild).toBe(confirm)
  })

  it('Carousel showDots={false}: 점을 그리지 않는다', () => {
    Element.prototype.scrollTo = vi.fn()
    render(
      <Carousel label="추천" autoPlayMs={false} showDots={false}>
        <CarouselSlide image={null} title="a" />
        <CarouselSlide image={null} title="b" />
      </Carousel>,
    )
    expect(screen.queryByRole('button', { name: '2번째 배너 보기' })).not.toBeInTheDocument()
  })

  it('BottomCta bordered={false}: 위쪽 선이 없다', () => {
    const { container } = render(<BottomCta primary={<button>확인</button>} bordered={false} />)
    expect(container.firstElementChild?.className).not.toMatch(/border-t/)
  })

  it('KeyValue align="right": 값을 오른쪽에 붙인다', () => {
    render(<KeyValue label="금액" value="1,000원" align="right" />)
    expect(screen.getByText('1,000원').className).toMatch(/\btext-right\b/)
  })

  it('TextField compact·leading: 높이 40으로 줄이고 앞 아이콘 자리를 비운다', () => {
    render(<TextField aria-label="검색" compact leading={<i data-testid="lead" />} />)
    const input = screen.getByRole('textbox', { name: '검색' })
    expect(input.className).toMatch(/min-h-10/)
    expect(input.className).toMatch(/pl-11/)
    expect(screen.getByTestId('lead')).toBeInTheDocument()
  })

  it('TextField hideErrorBorder: 오류 문구는 보이고 빨간 테두리 클래스는 빠진다', () => {
    render(<TextField aria-label="비밀번호" error="맞지 않아요" hideErrorBorder />)
    expect(screen.getByText('맞지 않아요')).toBeInTheDocument()
    expect(screen.getByLabelText('비밀번호').className).not.toMatch(/inset-ring-border-danger/)
  })

  it('Select compact: 높이 40', () => {
    render(<Select aria-label="택배사" compact items={[{ value: 'a', label: 'A' }]} />)
    expect(screen.getByRole('combobox', { name: '택배사' }).className).toMatch(/min-h-10/)
  })

  it('EmptyState icon={null}: 아이콘 없이 글만 그린다', () => {
    const { container } = render(<EmptyState icon={null} title="없어요" />)
    expect(container.querySelector('svg')).not.toBeInTheDocument()
  })

  it('EmptyState: 제목 20 굵게, 설명 12', () => {
    render(<EmptyState icon={null} title="제목" description="설명" />)
    expect(screen.getByText('제목').className).toMatch(/text-title3/)
    expect(screen.getByText('설명').className).toMatch(/text-caption-regular/)
  })

  it('Radio tone="inverse": 선택 색이 검정 계열이다', () => {
    render(<Radio tone="inverse" aria-label="사유" value="a" />)
    expect(screen.getByRole('radio').className).toMatch(/inset-ring-bg-inverse/)
  })

  it('Checkbox: 검정 계열 선택 색', () => {
    render(<Checkbox label="동의" />)
    expect(screen.getByRole('checkbox').className).toMatch(/data-checked:bg-bg-inverse/)
  })

  it('Textarea counterAlign="left": 글자 수가 왼쪽 줄로 내려간다', () => {
    render(<Textarea label="메모" maxLength={10} showCounter counterAlign="left" />)
    expect(screen.getByText('0/10').parentElement?.className).toMatch(/\bflex-col\b/)
  })

  it('Avatar 2xl: 96 크기', () => {
    const { container } = render(<Avatar size="2xl" />)
    expect(container.firstElementChild?.className).toMatch(/\bsize-24\b/)
  })

  it('Alert: 톤과 관계없이 흰 바탕과 1px 테두리, danger만 alert 역할', () => {
    render(<Alert tone="danger" title="연결이 끊겼어요" />)
    const alert = screen.getByRole('alert')
    expect(alert.className).toMatch(/\bborder\b/)
    expect(alert.className).toMatch(/bg-bg-surface/)
    expect(alert.className).not.toMatch(/bg-bg-danger-subtle/)
  })

  it('ProgressBar indeterminate: 회색 바탕 줄을 그리지 않는다', () => {
    render(<ProgressBar indeterminate aria-label="대기 중" />)
    expect(screen.getByRole('progressbar').className).toMatch(/bg-transparent/)
  })

  it('TimerBar: 헤더 아래에 고정하지 않는다', () => {
    render(<TimerBar message="m" remainingSeconds={5} />)
    expect(screen.getByRole('timer').className).not.toMatch(/sticky/)
  })

  it('StatStrip + StatTile layout="cell": 라벨·숫자·단위를 왼쪽 정렬로 담고 danger는 빨강', () => {
    render(
      <StatStrip>
        <StatTile layout="cell" label="판매" value="1,780" unit="명" />
        <StatTile layout="cell" label="보류" value="1" danger />
      </StatStrip>,
    )
    expect(screen.getByText('판매')).toBeInTheDocument()
    expect(screen.getByText('명')).toBeInTheDocument()
    expect(screen.getByText('1').className).toMatch(/text-text-danger/)
  })

  it('Tab countTone="danger": 건수가 빨강, 기본은 탭 글자 색', () => {
    render(
      <Tabs defaultValue="a">
        <TabBar layout="hug">
          <Tab value="a" count={2} countTone="danger">
            보류
          </Tab>
          <Tab value="b" count={3}>
            회수
          </Tab>
        </TabBar>
      </Tabs>,
    )
    expect(screen.getByText('2').className).toMatch(/text-text-danger/)
    expect(screen.getByText('3').className).not.toMatch(/text-text-danger/)
  })

  it('Chip count: 선택한 칩은 글자와 같은 크기, 아닌 칩은 작게', () => {
    render(
      <>
        <Chip selected count={3}>
          대기
        </Chip>
        <Chip count={12}>완료</Chip>
      </>,
    )
    expect(screen.getByText('3').className).not.toMatch(/caption/)
    expect(screen.getByText('12').className).toMatch(/text-caption-medium/)
  })

  it('DataTable rowArrow·framed: 줄 끝 > 칸과 바깥 테두리', async () => {
    const onRowClick = vi.fn()
    const { container } = render(
      <DataTable
        label="목록"
        rows={[{ id: '1', name: '가' }]}
        rowKey={(r) => r.id}
        columns={[{ key: 'n', header: '이름', render: (r) => r.name }]}
        rowArrow
        framed
        onRowClick={onRowClick}
      />,
    )
    expect(container.firstElementChild?.className).toMatch(/\bborder\b/)
    expect(container.querySelectorAll('tbody td')).toHaveLength(2)
    await userEvent.click(screen.getByText('가'))
    expect(onRowClick).toHaveBeenCalledTimes(1)
  })
})
