import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { useState } from 'react'

import { QuantityStepper } from './QuantityStepper'

const meta = {
  title: 'Inputs/QuantityStepper',
  component: QuantityStepper,
  args: { value: 1, min: 1, max: 5, size: 'm', onValueChange: () => {} },
  argTypes: { size: { control: 'inline-radio', options: ['s', 'm'] } },
  render: function Render(args) {
    const [value, setValue] = useState(args.value)
    return <QuantityStepper {...args} value={value} onValueChange={setValue} />
  },
} satisfies Meta<typeof QuantityStepper>

export default meta
type Story = StoryObj<typeof meta>

/** min·max에 닿으면 −·+ 버튼이 막힌다. */
export const Playground: Story = {}

export const Small: Story = { args: { size: 's' } }

export const AtMax: Story = { args: { value: 5 } }

export const Disabled: Story = { args: { disabled: true } }
