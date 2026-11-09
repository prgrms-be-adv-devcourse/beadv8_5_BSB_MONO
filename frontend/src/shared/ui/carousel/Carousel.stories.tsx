import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Carousel, CarouselSlide } from './Carousel'

// 실제 배너 이미지 대신 색 면을 쓴다. 화면에서는 next/image(fill, object-cover)를 넣는다.
function Placeholder({ className }: { className: string }) {
  return <div className={`absolute inset-0 ${className}`} />
}

const meta = {
  title: 'Navigation/Carousel',
  component: Carousel,
  args: { label: '추천 대회', autoPlayMs: 5000, children: null },
  argTypes: {
    autoPlayMs: { control: 'select', options: [5000, 2000, false] },
    children: { control: false },
  },
  render: (args) => (
    <div className="max-w-(--layout-app-max-width) bg-bg-default">
      <Carousel {...args}>
        <CarouselSlide
          image={<Placeholder className="bg-bg-brand" />}
          chip="이번 주 접수 마감"
          title="2026 용감한 쿠키RUN in 서울"
          description="10/31까지 · 5K · 10K · 서울숲 일대"
        />
        <CarouselSlide
          image={<Placeholder className="bg-bg-hero" />}
          title="긍정 하프 마라톤 YOUTH 10K"
          description="10/12 (월) 18:00 접수 마감"
        />
        <CarouselSlide image={<Placeholder className="bg-bg-accent" />} />
      </Carousel>
    </div>
  ),
} satisfies Meta<typeof Carousel>

export default meta
type Story = StoryObj<typeof meta>

/** 5초마다 다음 장으로. 마우스를 올리거나 만지면 멈춘다. */
export const Default: Story = {}

/** 자동으로 넘기지 않는다. */
export const Manual: Story = { args: { autoPlayMs: false } }
