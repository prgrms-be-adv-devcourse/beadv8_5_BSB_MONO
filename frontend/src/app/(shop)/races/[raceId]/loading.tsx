import { AppHeader } from '@/widgets/app-header'
import { Skeleton } from '@/shared/ui'

// 피그마 「대회 상세 · 로딩」: 사진 · 제목 · 정보 세 줄 · 가격 · 탭 · 종목 카드의 회색 틀
export default function Loading() {
  return (
    <>
      <AppHeader type="sub" title="" backHref="/races" />
      <main role="status" aria-label="불러오는 중" className="flex flex-1 flex-col pt-2 pb-8">
        <Skeleton shape="rect" className="h-84 w-full rounded-none" />
        <div className="flex flex-col gap-3 px-4 py-4">
          <Skeleton className="h-8 w-77 max-w-full" />
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-2">
              <Skeleton shape="rect" className="size-4 rounded-sm" />
              <Skeleton className={i === 0 ? 'h-4 w-28' : 'h-4 w-38'} />
            </div>
          ))}
          <div className="flex items-center gap-2">
            <Skeleton className="h-6 w-24" />
            <Skeleton className="h-3 w-6" />
          </div>
        </div>
        <div className="px-4 py-2">
          <div className="flex border-b border-border-default">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} shape="rect" className="h-12 flex-1 rounded-xl" />
            ))}
          </div>
        </div>
        <div className="flex flex-col gap-4 px-4 py-6">
          <Skeleton className="h-5 w-24" />
          <Skeleton shape="rect" className="h-27 rounded-lg" />
        </div>
      </main>
    </>
  )
}
