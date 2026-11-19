'use client'

import Link from 'next/link'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { loginHref, useAuthStatus } from '@/features/auth'
import { formatSellerDate, SELLER_STATUS, SELLER_TYPE_LABEL } from '@/entities/member'
import { ApiError, memberApi, unwrapData, type memberModel } from '@/shared/api'
import { Alert, buttonClassName, EmptyState, Skeleton } from '@/shared/ui'

import { PayoutAccountCard } from './PayoutAccountCard'

// 피그마 판매자 센터 · S06-5 판매자 정보. 정산 계좌는 승인된 판매자가 바로 보고 등록·수정한다(PayoutAccountCard)
// 판매자 정보는 내 신청 조회(GET /api/v1/member/sellers/me)로 보여 주며 보기 전용이다.
export function SellerSettingsPage() {
  const auth = useAuthStatus()
  const mine = memberApi.useFindMine({
    // 신청 내역이 없는 404는 다시 불러도 같아서 재시도하지 않는다
    query: { select: unwrapData, enabled: auth === 'authenticated', retry: false },
  })

  return (
    <ConsolePage title="판매자 정보">
      <div className="max-lg:grid-cols-1 grid grid-cols-2 items-start gap-6">
        {auth === 'anonymous' ? (
          <ConsoleCard className="max-lg:col-span-1 col-span-2">
            <EmptyState
              title="로그인이 필요해요"
              action={
                <Link href={loginHref('/seller/settings')} className={buttonClassName()}>
                  로그인하러 가기
                </Link>
              }
            />
          </ConsoleCard>
        ) : mine.data ? (
          <SellerInfoCard seller={mine.data} />
        ) : mine.isError && isNoApplication(mine.error) ? (
          <ConsoleCard className="max-lg:col-span-1 col-span-2">
            <EmptyState
              title="판매자만 쓸 수 있어요"
              action={
                <Link href="/seller-apply" className={buttonClassName()}>
                  판매자 신청하기
                </Link>
              }
            />
          </ConsoleCard>
        ) : mine.isError ? (
          <Alert
            tone="danger"
            title="판매자 정보를 불러오지 못했어요"
            description="잠시 뒤 다시 시도해 주세요."
          />
        ) : (
          <Skeleton className="h-70 w-full" />
        )}

        {mine.data && <PayoutAccountCard seller={mine.data} />}
      </div>
    </ConsolePage>
  )
}

function SellerInfoCard({ seller }: { seller: memberModel.SellerDto }) {
  const status = SELLER_STATUS[seller.status]
  const business = seller.sellerType === 'BUSINESS'
  const fields: [string, string][] = [
    ['판매자 종류', SELLER_TYPE_LABEL[seller.sellerType]],
    ...(business
      ? ([
          ['사업자등록번호', seller.businessNumber ?? '-'],
          ['상호', seller.businessName ?? '-'],
          ['대표자', seller.representativeName],
        ] as [string, string][])
      : ([['성명', seller.representativeName]] as [string, string][])),
    seller.status === 'APPROVED' && seller.reviewedAt
      ? ['승인일', formatSellerDate(seller.reviewedAt)]
      : ['신청일', formatSellerDate(seller.requestedAt)],
  ]

  return (
    <ConsoleCard
      title="판매자 정보"
      aside={<span className="text-body2-medium text-text-secondary">{status.label}</span>}
    >
      {/* 사업자등록번호 · 상호 · 대표자 이름은 Clarity 화면 녹화에서 가린다 */}
      <dl className="flex flex-col gap-4" data-clarity-mask="true">
        {fields.map(([label, value]) => (
          <div key={label} className="flex flex-col gap-2">
            <dt className="text-body1-medium">{label}</dt>
            <dd className="flex h-12 items-center rounded-md bg-bg-subtle px-4 text-body1-regular text-text-secondary shadow-sm">
              {value}
            </dd>
          </div>
        ))}
      </dl>
    </ConsoleCard>
  )
}

/** 내 신청이 없을 때(404 SELLER_NOT_FOUND)는 오류가 아니라 "아직 신청 전"이다 */
function isNoApplication(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'SELLER_NOT_FOUND'
}
