import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { useState } from 'react'

import { LoadMore, Pagination } from './Pagination'

const meta = {
  title: 'Navigation/Pagination',
  component: Pagination,
  args: { page: 5, totalPages: 20, onPageChange: () => {} },
} satisfies Meta<typeof Pagination>

export default meta
type Story = StoryObj<typeof meta>

/** 번호는 최대 7칸, 나머지는 …로 줄인다. 눌러서 바뀌는 모양을 확인한다. */
export const Interactive: Story = {
  render: function Render(args) {
    const [page, setPage] = useState(args.page)
    return <Pagination {...args} page={page} onPageChange={setPage} />
  },
}

export const FewPages: Story = {
  render: function Render() {
    const [page, setPage] = useState(1)
    return <Pagination page={page} totalPages={4} onPageChange={setPage} />
  },
}

/** 모바일 목록은 번호 대신 더보기 */
export const LoadMoreButton: Story = {
  name: 'LoadMore',
  render: function Render() {
    const [count, setCount] = useState(12)
    const [loading, setLoading] = useState(false)
    return (
      <div className="max-w-sm">
        <LoadMore
          countText={`${count} / 48개`}
          loading={loading}
          hasMore={count < 48}
          onLoadMore={() => {
            setLoading(true)
            setTimeout(() => {
              setCount((c) => Math.min(48, c + 12))
              setLoading(false)
            }, 600)
          }}
        />
      </div>
    )
  },
}
