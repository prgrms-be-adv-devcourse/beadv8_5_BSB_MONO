import type { StorybookConfig } from '@storybook/nextjs-vite'

// 시안 컴포넌트를 PR 전에 눈으로 확인하는 용도. 스토리는 컴포넌트 옆에 *.stories.tsx로 둔다.
const config: StorybookConfig = {
  stories: ['../src/**/*.mdx', '../src/**/*.stories.@(ts|tsx)'],
  addons: ['@storybook/addon-docs', '@storybook/addon-themes'],
  framework: '@storybook/nextjs-vite',
  staticDirs: ['../public'],
}

export default config
