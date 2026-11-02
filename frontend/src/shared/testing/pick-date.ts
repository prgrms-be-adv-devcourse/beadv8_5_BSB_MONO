import { screen, within } from '@testing-library/react'
import type userEvent from '@testing-library/user-event'

/**
 * 공용 달력 칸(DateTimeField, 날짜만)에서 날짜를 고른다. date는 'YYYY-MM-DD'.
 * 칸을 열고 → 연·월 휠에서 해와 달을 누르고 → 휠을 닫고 → 날짜를 누른 뒤 '선택 완료'로 확정한다.
 */
export async function pickDate(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  date: string,
) {
  const [year, month, day] = date.split('-').map(Number)
  await user.click(screen.getByRole('button', { name: new RegExp(`^${label}`) }))
  const popup = await screen.findByRole('dialog', { name: `${label} 고르기` })
  await user.click(within(popup).getByRole('button', { name: /연·월 고르기/ }))
  await user.click(within(popup).getByRole('option', { name: `${year}년` }))
  await user.click(within(popup).getByRole('option', { name: `${month}월` }))
  await user.click(within(popup).getByRole('button', { name: /연·월 고르기/ }))
  await user.click(
    within(popup).getByRole('button', { name: new RegExp(`${year}년 ${month}월 ${day}일`) }),
  )
  await user.click(within(popup).getByRole('button', { name: '선택 완료' }))
}
