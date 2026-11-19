import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Icon } from '../icon/icons'
import { TableCell, TableHeaderCell, type CellAlign } from './Table'

export type DataTableColumn<Row> = {
  key: string
  header: ReactNode
  /** 글자 열은 left, 숫자 열(금액 · 날짜 · 정원)은 right (10/10 정렬 규칙) */
  align?: CellAlign
  /** 열 너비 (예: 'w-42'). 없으면 남은 폭을 나눠 갖는다. */
  width?: string
  render: (row: Row) => ReactNode
}

export type DataTableProps<Row> = {
  columns: DataTableColumn<Row>[]
  rows: Row[]
  rowKey: (row: Row) => string
  /** 표 이름 (화면 낭독기용) */
  label: string
  /** 고른 줄 (옅은 브랜드 바탕). 여러 줄을 고를 땐 배열 */
  selected?: string | string[]
  /** 넘기면 줄을 눌러 고를 수 있다 */
  onRowClick?: (row: Row) => void
  /** true면 줄 오른쪽 끝에 > 화살표 칸을 둔다(관리자 목록, 2070:11384) */
  rowArrow?: boolean
  /** true면 표 바깥에 1px 테두리 + 그림자 + 둥글기 12(카드 밖에 놓는 관리자 표). 카드 안 표는 끈다 */
  framed?: boolean
  className?: string
}

// 판매자·관리자 화면의 표: 둥근 테두리(10) 안에 머리줄 44 + 내용줄 56. 숫자는 고정폭으로 자릿수를 맞춘다.
export function DataTable<Row>({
  columns,
  rows,
  rowKey,
  label,
  selected: selectedKeys,
  onRowClick,
  rowArrow = false,
  framed = false,
  className,
}: DataTableProps<Row>) {
  return (
    <div
      className={cn(
        'overflow-hidden',
        framed && 'rounded-md border border-border-default shadow-sm',
        className,
      )}
    >
      <table aria-label={label} className="w-full table-fixed border-collapse tabular-nums">
        <thead>
          <tr>
            {columns.map((column) => (
              // 표 고정 배치(table-fixed)는 머리줄 너비로 열을 나눈다. 칸의 max-w-0을 풀어 너비가 먹게 한다.
              <TableHeaderCell
                key={column.key}
                align={column.align}
                className={cn(column.width && [column.width, 'max-w-none'])}
              >
                {column.header}
              </TableHeaderCell>
            ))}
            {rowArrow && <TableHeaderCell aria-hidden className="w-12 max-w-none" />}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const key = rowKey(row)
            const selected = Array.isArray(selectedKeys)
              ? selectedKeys.includes(key)
              : key === selectedKeys
            return (
              <tr
                key={key}
                aria-selected={onRowClick ? selected : undefined}
                onClick={onRowClick && (() => onRowClick(row))}
                className={cn(
                  'last:*:border-b-0',
                  onRowClick && 'cursor-pointer hover:*:bg-bg-subtle',
                )}
              >
                {columns.map((column) => (
                  <TableCell
                    key={column.key}
                    align={column.align}
                    className={cn(selected && 'bg-bg-brand-subtle hover:bg-bg-brand-subtle')}
                  >
                    {column.render(row)}
                  </TableCell>
                ))}
                {rowArrow && (
                  <TableCell
                    align="right"
                    className={cn(selected && 'bg-bg-brand-subtle hover:bg-bg-brand-subtle')}
                  >
                    <Icon
                      name="ChevronRight"
                      aria-hidden
                      className="ml-auto size-5 text-text-tertiary"
                    />
                  </TableCell>
                )}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
