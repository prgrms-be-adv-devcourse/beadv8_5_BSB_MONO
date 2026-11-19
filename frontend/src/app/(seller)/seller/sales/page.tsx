import { Suspense } from 'react'

import { SellerSalesPage } from '@/views/seller-sales'

// 화면이 useSearchParams로 고른 대회(?race=)를 읽는다. 미리 그리는 쪽이 막히지 않게 Suspense로 감싼다.
export default function Page() {
  return (
    <Suspense>
      <SellerSalesPage />
    </Suspense>
  )
}
