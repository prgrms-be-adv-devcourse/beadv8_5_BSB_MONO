// TextField·Textarea·Select가 함께 쓰는 모양. Field.Root에 group을 붙이고 상태는 data-* 로 받는다.
export const FIELD_ROOT = 'group flex w-full flex-col gap-2'

export const FIELD_LABEL = 'text-label-m text-text-primary group-data-disabled:text-text-disabled'

// 입력 상자: 기본 1px border/strong, 포커스(열림) 2px border/focus, 오류 2px border/danger
export const FIELD_BOX =
  'w-full rounded-md bg-bg-surface px-4 py-3 text-body-m text-text-primary inset-ring inset-ring-border-strong outline-none ' +
  'placeholder:text-text-tertiary ' +
  'group-data-invalid:inset-ring-2 group-data-invalid:inset-ring-border-danger ' +
  'group-data-disabled:cursor-not-allowed group-data-disabled:bg-bg-disabled group-data-disabled:text-text-disabled group-data-disabled:inset-ring-border-disabled'

export const FIELD_BOX_FOCUS = 'focus:inset-ring-2 focus:inset-ring-border-focus'

export const FIELD_HELPER =
  'text-caption text-text-tertiary group-data-invalid:text-text-danger group-data-disabled:text-text-disabled'
