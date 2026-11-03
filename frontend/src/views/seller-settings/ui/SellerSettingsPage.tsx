'use client'

import Link from 'next/link'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { loginHref, useAuthStatus } from '@/features/auth'
import { formatSellerDate, SELLER_STATUS, SELLER_TYPE_LABEL } from '@/entities/member'
import { ApiError, memberApi, unwrapData, type memberModel } from '@/shared/api'
import { Alert, Badge, buttonClassName, EmptyState, Skeleton } from '@/shared/ui'

// 피그마 V2 · S11 판매자 설정 (세미: 정산 계좌는 준비 중 안내)
// 판매자 정보는 내 신청 조회(GET /api/v1/member/sellers/me)로 보여 준다. 바꾸려면 관리자 재확인이 필요해 보기 전용이다.
export function SellerSettingsPage() {
  const auth = useAuthStatus()
  const mine = memberApi.useFindMine({
    // 신청 내역이 없는 404는 다시 불러도 같아서 재시도하지 않는다
    query: { select: unwrapData, enabled: auth === 'authenticated', retry: false },
  })

  return (
    <ConsolePage title="설정" description="판매자 정보와 정산 계좌를 확인해요.">
      {auth === 'anonymous' ? (
        <EmptyState
          title="로그인이 필요해요"
          action={
            <Link
              href={loginHref('/seller/settings')}
              className={buttonClassName({ variant: 'secondary' })}
            >
              로그인하러 가기
            </Link>
          }
        />
      ) : mine.data ? (
        <SellerInfoCard seller={mine.data} />
      ) : mine.isError && isNoApplication(mine.error) ? (
        <EmptyState
          title="아직 판매자 신청을 하지 않았어요"
          description="신청이 승인되면 여기에서 판매자 정보를 볼 수 있어요."
          action={
            <Link href="/seller-apply" className={buttonClassName({ variant: 'secondary' })}>
              판매자 신청하러 가기
            </Link>
          }
        />
      ) : mine.isError ? (
        <Alert
          tone="danger"
          title="판매자 정보를 불러오지 못했어요"
          description="잠시 뒤 다시 시도해 주세요."
        />
      ) : (
        <Skeleton className="h-70 w-full" />
      )}

      {/* TODO(api): cash 서비스의 판매자 정산 계좌 API가 생기면 S11-1(계좌 입력 · 저장)로 바꾼다 */}
      <ConsoleCard title="정산 계좌">
        <Alert
          tone="warning"
          title="정산 계좌 등록은 준비 중이에요"
          description="등록 기능이 열리면 여기에서 등록할 수 있어요. 등록 전까지는 정산금 지급이 보류돼요."
        />
      </ConsoleCard>
    </ConsolePage>
  )
}

function SellerInfoCard({ seller }: { seller: memberModel.SellerDto }) {
  const status = SELLER_STATUS[seller.status]
  const business = seller.sellerType === 'BUSINESS'
  const fields: [string, string][] = [
    ['판매자 유형', SELLER_TYPE_LABEL[seller.sellerType]],
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
    <ConsoleCard title="판매자 정보" aside={<Badge tone={status.tone}>{status.label}</Badge>}>
      {/* 사업자등록번호 · 상호 · 대표자 이름은 Clarity 화면 녹화에서 가린다 */}
      <dl className="grid grid-cols-2 gap-x-4 gap-y-4" data-clarity-mask="true">
        {fields.map(([label, value]) => (
          <div key={label} className="flex flex-col gap-0.5">
            <dt className="text-caption text-text-secondary">{label}</dt>
            <dd className="text-body-s text-text-primary">{value}</dd>
          </div>
        ))}
      </dl>
      <p className="text-caption text-text-tertiary">
        판매자 정보를 바꾸려면 관리자 확인이 필요해요.
      </p>
    </ConsoleCard>
  )
}

/** 내 신청이 없을 때(404 SELLER_NOT_FOUND)는 오류가 아니라 "아직 신청 전"이다 */
function isNoApplication(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'SELLER_NOT_FOUND'
}
