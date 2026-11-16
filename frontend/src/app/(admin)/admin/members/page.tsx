import { Suspense } from 'react'

import { AdminMembersPage } from '@/views/admin-members'

// 화면이 useSearchParams로 목록 상태(URL 쿼리)를 읽는다. 미리 그리는 쪽이 막히지 않게 Suspense로 감싼다.
export default function Page() {
  return (
    <Suspense>
      <AdminMembersPage />
    </Suspense>
  )
}
