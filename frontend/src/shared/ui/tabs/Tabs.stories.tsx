import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Tab, TabBar, TabPanel, Tabs } from './Tabs'

const meta = {
  title: 'Navigation/Tabs',
  component: TabBar,
  args: { layout: 'fill', children: null },
  argTypes: {
    layout: { control: 'inline-radio', options: ['fill', 'hug'] },
    children: { control: false },
  },
  render: (args) => (
    <Tabs defaultValue="info" className="max-w-md">
      <TabBar layout={args.layout}>
        <Tab value="info">대회 정보</Tab>
        <Tab value="course">코스</Tab>
        <Tab value="review" count={128}>
          후기
        </Tab>
        <Tab value="qna" disabled>
          문의
        </Tab>
      </TabBar>
      <TabPanel value="info" className="p-4 text-body-m">
        대회 정보 내용
      </TabPanel>
      <TabPanel value="course" className="p-4 text-body-m">
        코스 내용
      </TabPanel>
      <TabPanel value="review" className="p-4 text-body-m">
        후기 내용
      </TabPanel>
    </Tabs>
  ),
} satisfies Meta<typeof TabBar>

export default meta
type Story = StoryObj<typeof meta>

/** 모바일: 탭이 폭을 똑같이 나눈다. */
export const Fill: Story = {}

/** 태블릿·데스크톱: 내용 폭만큼 왼쪽부터 */
export const Hug: Story = { args: { layout: 'hug' } }
