// TextField·Textarea·Select가 함께 쓰는 모양. Field.Root에 group을 붙이고 상태는 data-* 로 받는다.
export const FIELD_ROOT = 'group flex w-full flex-col gap-2'

export const FIELD_LABEL =
  'text-body1-medium text-text-primary group-data-disabled:text-text-disabled'

// 입력 상자(v4 판매자 시안): 높이 48, 둥글기 8, 1px 선(border/default). 포커스(열림) 2px border/focus, 오류 2px border/danger
export const FIELD_BOX =
  'min-h-12 w-full rounded-field bg-bg-surface px-4 py-3 text-body1-regular text-text-primary inset-ring inset-ring-border-default outline-none ' +
  'placeholder:text-text-tertiary ' +
  'group-data-invalid:inset-ring-2 group-data-invalid:inset-ring-border-danger ' +
  'group-data-disabled:cursor-not-allowed group-data-disabled:bg-bg-surface group-data-disabled:text-text-disabled group-data-disabled:inset-ring-border-disabled'

export const FIELD_BOX_FOCUS = 'focus:inset-ring-2 focus:inset-ring-border-focus'

export const FIELD_HELPER =
  'flex items-center gap-1 text-caption-regular text-text-tertiary group-data-invalid:text-text-danger group-data-disabled:text-text-disabled'
