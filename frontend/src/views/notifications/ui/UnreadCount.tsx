// 피그마 시나리오 37 · 안 읽은 수: 빨간 바탕 흰 글자, 높이 18
export function UnreadCount({ count }: { count: number }) {
  return (
    <span
      aria-label={`안 읽은 알림 ${count}개`}
      className="flex h-4.5 min-w-4.5 items-center justify-center rounded-full bg-text-danger px-1.5 text-label-s text-text-on-image"
    >
      {count}
    </span>
  )
}
