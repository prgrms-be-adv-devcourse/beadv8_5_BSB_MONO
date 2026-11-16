import { AppHeader } from '@/widgets/app-header'
import { Skeleton } from '@/shared/ui'

// 대회 상세 로딩: 헤더는 바로, 포스터 · 제목 · 정보 줄은 회색 틀
export default function Loading() {
  return (
    <>
      <AppHeader type="sub" title="" backHref="/races" />
      <main role="status" aria-label="불러오는 중" className="flex flex-1 flex-col gap-4 pb-8">
        <Skeleton shape="rect" className="aspect-square w-full" />
        <div className="flex flex-col gap-3 px-4">
          <Skeleton shape="rect" className="h-4 w-12" />
          <Skeleton className="w-3/4" />
          <Skeleton className="w-1/2" />
          <Skeleton shape="rect" className="h-24 rounded-lg" />
          <Skeleton shape="rect" className="h-24 rounded-lg" />
        </div>
      </main>
    </>
  )
}
