import type { TdHTMLAttributes, ThHTMLAttributes } from 'react'

import { cn } from '@/shared/lib'

export type CellAlign = 'left' | 'right' | 'center'

const ALIGN: Record<CellAlign, string> = {
  left: 'text-left',
  right: 'text-right',
  center: 'text-center',
}

// 표의 열 제목 칸. 높이 44. 숫자 열은 align="right".
export function TableHeaderCell({
  align = 'left',
  className,
  ...props
}: ThHTMLAttributes<HTMLTableCellElement> & { align?: CellAlign }) {
  return (
    <th
      scope="col"
      className={cn(
        'h-11 max-w-0 truncate border-b border-border-default bg-bg-subtle px-4 text-label-m text-text-secondary',
        ALIGN[align],
        className,
      )}
      {...props}
    />
  )
}

// 표의 내용 칸. 높이 56, 넘치는 글자는 말줄임(…). 판매자·관리자 표는 align="center"가 기본이다.
export function TableCell({
  align = 'left',
  className,
  ...props
}: TdHTMLAttributes<HTMLTableCellElement> & { align?: CellAlign }) {
  return (
    <td
      className={cn(
        'h-14 max-w-0 truncate border-b border-border-default bg-bg-surface px-4 text-body-m text-text-primary',
        ALIGN[align],
        className,
      )}
      {...props}
    />
  )
}
