import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Badge } from '../badge/Badge'
import { TableCell, TableHeaderCell } from './Table'

const meta = {
  title: 'Display/Table',
  component: TableCell,
} satisfies Meta<typeof TableCell>

export default meta
type Story = StoryObj<typeof meta>

const ORDERS = [
  {
    id: 'A-1042',
    name: '서울 하프 마라톤 · 하프',
    buyer: '김러너',
    amount: '45,000',
    status: '결제 완료',
  },
  {
    id: 'A-1043',
    name: '한강 나이트런 10K 기념 티셔츠와 메달 세트',
    buyer: '이크루',
    amount: '32,000',
    status: '결제 대기',
  },
  {
    id: 'A-1044',
    name: '춘천 마라톤 · 풀',
    buyer: '박페이서',
    amount: '60,000',
    status: '환불 완료',
  },
]

/** 숫자 열은 오른쪽 정렬, 넘치는 글자는 말줄임 */
export const Orders: Story = {
  render: () => (
    <table className="w-full max-w-3xl table-fixed border-collapse">
      <thead>
        <tr>
          <TableHeaderCell className="w-28">주문번호</TableHeaderCell>
          <TableHeaderCell>상품</TableHeaderCell>
          <TableHeaderCell className="w-28">구매자</TableHeaderCell>
          <TableHeaderCell align="right" className="w-28">
            금액
          </TableHeaderCell>
          <TableHeaderCell align="center" className="w-28">
            상태
          </TableHeaderCell>
        </tr>
      </thead>
      <tbody>
        {ORDERS.map((order) => (
          <tr key={order.id}>
            <TableCell>{order.id}</TableCell>
            <TableCell title={order.name}>{order.name}</TableCell>
            <TableCell>{order.buyer}</TableCell>
            <TableCell align="right">{order.amount}</TableCell>
            <TableCell align="center">
              <Badge
                tone={
                  order.status === '결제 완료'
                    ? 'success'
                    : order.status === '결제 대기'
                      ? 'info'
                      : 'neutral'
                }
              >
                {order.status}
              </Badge>
            </TableCell>
          </tr>
        ))}
      </tbody>
    </table>
  ),
}
