import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { TableCell, TableHeaderCell } from './Table'

// 판매자 시안: 표 머리 높이 44, 줄 높이 64
describe('Table', () => {
  it('머리 칸 h-11, 내용 칸 h-16', () => {
    render(
      <table>
        <thead>
          <tr>
            <TableHeaderCell>제목</TableHeaderCell>
          </tr>
        </thead>
        <tbody>
          <tr>
            <TableCell>값</TableCell>
          </tr>
        </tbody>
      </table>,
    )
    expect(screen.getByText('제목').className).toMatch(/\bh-11\b/)
    expect(screen.getByText('값').className).toMatch(/\bh-16\b/)
  })
})
