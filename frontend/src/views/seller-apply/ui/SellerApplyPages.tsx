'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'

import { ConsoleCard } from '@/widgets/console-page'
import { loginHref, useAuthStatus } from '@/features/auth'
import { formatSellerDate, isNoApplication, SELLER_STATUS } from '@/entities/member'
import { memberApi, unwrapData, type memberModel } from '@/shared/api'
import { cn, trackEvent, useTrackOnce } from '@/shared/lib'
import {
  Alert,
  Badge,
  Button,
  buttonClassName,
  Checkbox,
  FOCUS_RING,
  Radio,
  RadioGroup,
  Skeleton,
  TextField,
  type BadgeTone,
} from '@/shared/ui'

import {
  applyErrorMessage,
  SELLER_API_ERRORS,
  toApplyRequest,
  toApplyStatus,
  toFormValues,
  type SellerApplyStatus,
} from '../model/seller-apply-api'
import {
  EMPTY_SELLER_APPLY_FORM,
  sellerApplyFormSchema,
  type SellerApplyFormValues,
} from '../model/seller-apply-form'

// 데스크톱 폼 화면의 본문: 위 48 · 아래 64, 가운데 고정 폭 칸
function FormBody({
  width,
  title,
  description,
  children,
}: {
  width: string
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <main className="flex justify-center pt-12 pb-16">
      <div className={cn('flex flex-col gap-6', width)}>
        <div className="flex flex-col gap-2 pb-2">
          <h1 className="text-heading-xl">{title}</h1>
          {description && <p className="text-body-m text-text-secondary">{description}</p>}
        </div>
        {children}
      </div>
    </main>
  )
}

// 피그마 V2 · S01 판매자 등록 신청
// 검사를 통과하면 판매자 신청 API(POST /api/v1/member/sellers)로 보내고, 성공하면 결과 화면으로 간다.
export function SellerApplyPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const apply = memberApi.useApply()
  const [formError, setFormError] = useState<string>()
  useTrackOnce('seller_apply_start', {})
  // 반려 뒤 다시 신청하면 전에 낸 값을 채운다. 신청 전(404)이면 빈 폼 그대로.
  const mine = memberApi.useFindMine({
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated', retry: false },
  })
  const previous = useMemo(
    () => (mine.data?.status === 'REJECTED' ? toFormValues(mine.data) : undefined),
    [mine.data],
  )
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<SellerApplyFormValues>({
    resolver: zodResolver(sellerApplyFormSchema),
    defaultValues: EMPTY_SELLER_APPLY_FORM,
    // 이미 고친 칸은 덮어쓰지 않는다
    values: previous,
    resetOptions: { keepDirtyValues: true },
  })
  const business = useWatch({ control, name: 'business' })
  const agreed = useWatch({ control, name: 'agreed' })

  const onSubmit = handleSubmit((values) => {
    setFormError(undefined)
    apply.mutate(
      { data: toApplyRequest(values) },
      {
        onSuccess: (response) => {
          // 응답이 방금 낸 신청이라 결과 화면이 다시 부르지 않고 바로 쓴다.
          queryClient.setQueryData(memberApi.getFindMineQueryKey(), response)
          trackEvent('seller_apply_submit', { isBusiness: values.business === 'business' })
          router.push('/seller-apply/result')
        },
        onError: (error) => setFormError(applyErrorMessage(error)),
      },
    )
  })

  return (
    <form onSubmit={onSubmit} noValidate>
      <FormBody
        width="w-160"
        title="판매자 등록 신청"
        description="승인되면 대회를 등록할 수 있어요. 결과는 알림으로 알려 드려요."
      >
        <ConsoleCard title="판매자 정보">
          <Controller
            control={control}
            name="business"
            render={({ field }) => (
              <RadioGroup
                value={field.value}
                onValueChange={(value) => field.onChange(value)}
                className="flex-row gap-6"
              >
                <Radio value="business" label="사업자예요" />
                <Radio value="personal" label="사업자가 아니에요" />
              </RadioGroup>
            )}
          />
          {business === 'business' ? (
            <>
              <div className="flex gap-4">
                <TextField
                  label="사업자등록번호"
                  className="flex-1"
                  error={errors.businessNumber?.message}
                  {...register('businessNumber')}
                />
                <TextField
                  label="상호"
                  className="flex-1"
                  error={errors.businessName?.message}
                  {...register('businessName')}
                />
              </div>
              <TextField
                label="대표자"
                error={errors.representative?.message}
                {...register('representative')}
              />
            </>
          ) : (
            <div className="flex gap-4">
              <TextField
                label="성명"
                className="flex-1"
                error={errors.name?.message}
                {...register('name')}
              />
              <TextField
                label="연락처"
                type="tel"
                className="flex-1"
                error={errors.phone?.message}
                {...register('phone')}
              />
            </div>
          )}
          <p className="text-caption text-text-tertiary">사업자가 아니면 성명 · 연락처만 받아요.</p>
        </ConsoleCard>

        <ConsoleCard title="정산 계좌">
          <SettlementAccountNotice />
        </ConsoleCard>

        <ConsoleCard title="약관 동의">
          <div className="flex items-center justify-between">
            <Controller
              control={control}
              name="agreed"
              render={({ field }) => (
                <Checkbox
                  checked={field.value}
                  onCheckedChange={field.onChange}
                  label="[필수] 판매자 이용약관에 동의해요"
                />
              )}
            />
            <Link
              href="/terms/service"
              className={cn('rounded-sm text-body-s text-text-tertiary underline', FOCUS_RING)}
            >
              보기
            </Link>
          </div>
          <p className="text-caption text-text-tertiary">동의한 약관 버전과 시각을 기록해요.</p>
        </ConsoleCard>

        <Alert
          title="크루런은 통신판매중개자예요"
          description="거래 당사자는 판매자님이에요. 판매자 정보는 대회 상세 화면에서 구매자에게 보여요."
        />

        {formError && <Alert tone="danger" title={formError} />}

        <div className="flex justify-end gap-2">
          {/* TODO(api): PRO-129 임시 저장 API가 생기면 켠다 */}
          <Button type="button" variant="secondary" size="l" disabled>
            임시 저장
          </Button>
          <Button type="submit" size="l" disabled={!agreed} loading={apply.isPending}>
            신청하기
          </Button>
        </div>
      </FormBody>
    </form>
  )
}

type ResultCard = {
  tone: BadgeTone
  badge: string
  title: string
  description: string
  actions: ReactNode
}

// 반려 사유는 서버가 준 값(rejectReason)을 그대로 보여 준다.
function resultCard(seller: memberModel.SellerDto): ResultCard {
  const status: SellerApplyStatus = toApplyStatus(seller.status)
  if (status === 'rejected') {
    return {
      tone: 'danger',
      badge: '반려',
      title: '신청이 반려됐어요',
      description: seller.rejectReason ? `사유: ${seller.rejectReason}` : '',
      actions: (
        <Link href="/seller-apply" className={buttonClassName()}>
          고쳐서 다시 신청하기
        </Link>
      ),
    }
  }
  if (status === 'approved') {
    return {
      tone: 'success',
      badge: '승인',
      title: '판매자로 승인됐어요',
      description:
        '이제 대회를 등록할 수 있어요. 정산 계좌가 없으면 정산금 지급이 보류되니 먼저 확인해 주세요.',
      actions: (
        <>
          <Link href="/seller" className={buttonClassName()}>
            판매자 홈으로
          </Link>
          <Link href="/seller/settings" className={buttonClassName({ variant: 'secondary' })}>
            정산 계좌 확인
          </Link>
        </>
      ),
    }
  }
  return {
    tone: 'neutral',
    badge: '심사 중',
    title: '관리자가 신청 내용을 확인하고 있어요',
    description: '결과가 나오면 알림(M25)으로 알려 드려요. 심사 중에는 신청 내용을 고칠 수 없어요.',
    actions: (
      <Link href="/seller-apply/mine" className={buttonClassName({ variant: 'secondary' })}>
        신청 내용 보기
      </Link>
    ),
  }
}

// 피그마 V2 · S01-1 판매자 등록 신청 · 결과 (심사 중 · 반려 · 승인 중 내 신청 상태에 맞는 카드 하나)
// 내 신청 조회(GET /api/v1/member/sellers/me)로 상태를 가져온다. 신청 내역이 없으면(404 SELLER_NOT_FOUND) 신청하러 가도록 안내한다.
// 승인 · 반려 결과를 처음 보여 주면 결과 확인(POST /api/v1/member/sellers/me/result-check)을 한 번 부른다.
// 그 뒤로 마이의 "판매자 센터"는 판매자 홈으로 바로 간다. 실패하면(409 심사 중 포함) 화면은 그대로 두고 다음 방문 때 다시 부른다.
export function SellerApplyResultPage() {
  const queryClient = useQueryClient()
  const mine = memberApi.useFindMine({
    query: {
      select: unwrapData,
      enabled: useAuthStatus() === 'authenticated',
      // 신청 내역이 없는 404는 다시 불러도 같아서 재시도하지 않는다.
      retry: false,
    },
  })
  const checkResult = memberApi.useCheckMyResult({
    mutation: {
      // 마이의 판매자 메뉴가 바로 바뀌게 내 신청을 다시 불러온다
      onSuccess: () => queryClient.invalidateQueries({ queryKey: memberApi.getFindMineQueryKey() }),
    },
  })
  const { mutate: checkMyResult } = checkResult
  const checked = useRef(false)
  // 서버가 아직 resultCheckedAt을 주지 않으면(undefined) 안 본 것으로 친다
  const unchecked =
    (mine.data?.status === 'APPROVED' || mine.data?.status === 'REJECTED') &&
    !mine.data.resultCheckedAt
  useEffect(() => {
    if (!unchecked || checked.current) return
    checked.current = true
    checkMyResult()
  }, [unchecked, checkMyResult])

  return (
    <FormBody width="w-180" title="판매자 등록 신청 결과">
      {mine.data ? (
        <ResultSection card={resultCard(mine.data)} />
      ) : mine.isError ? (
        isNoApplication(mine.error) ? (
          <section className="flex flex-col items-start gap-3 rounded-lg bg-bg-surface p-5 inset-ring inset-ring-border-default">
            <h2 className="text-heading-s">아직 판매자 신청을 하지 않았어요</h2>
            <Link href="/seller-apply" className={buttonClassName()}>
              판매자 신청하러 가기
            </Link>
          </section>
        ) : (
          <Alert tone="danger" title={SELLER_API_ERRORS.loadFailed} />
        )
      ) : (
        <Skeleton shape="rect" className="h-40 w-full" />
      )}
    </FormBody>
  )
}

function ResultSection({ card }: { card: ResultCard }) {
  return (
    <section className="flex flex-col items-start gap-3 rounded-lg bg-bg-surface p-5 inset-ring inset-ring-border-default">
      <Badge tone={card.tone}>{card.badge}</Badge>
      <h2 className="text-heading-s">{card.title}</h2>
      {card.description && <p className="text-body-s text-text-secondary">{card.description}</p>}
      <div className="flex gap-2">{card.actions}</div>
    </section>
  )
}

// 신청 때는 계좌를 받지 않는다(서버가 저장하지 않음). 승인 뒤 판매자 설정(S11)에서 등록한다.
function SettlementAccountNotice() {
  return (
    <Alert
      title="정산 계좌는 판매자 설정에서 등록해요"
      description="승인된 뒤 판매자 설정에서 등록해요. 등록 전까지는 정산금 지급이 보류돼요."
    />
  )
}

// 피그마 V2 · S01-2 신청 내용 보기: 내가 낸 신청(GET /api/v1/member/sellers/me)을 S01 폼에 채우고 모든 칸을 잠근다.
// 서버가 받지 않는 연락처 · 정산 계좌는 빈 칸으로 두지 않고 뺀다.
export function SellerApplicationPage() {
  const auth = useAuthStatus()
  const mine = memberApi.useFindMine({
    query: { select: unwrapData, enabled: auth === 'authenticated', retry: false },
  })

  return (
    <FormBody
      width="w-160"
      title="신청 내용"
      description={mine.data ? APPLICATION_NOTICE[mine.data.status] : undefined}
    >
      {auth === 'anonymous' ? (
        <section className="flex flex-col items-start gap-3 rounded-lg bg-bg-surface p-5 inset-ring inset-ring-border-default">
          <h2 className="text-heading-s">로그인이 필요해요</h2>
          <Link href={loginHref('/seller-apply/mine')} className={buttonClassName()}>
            로그인하러 가기
          </Link>
        </section>
      ) : mine.data ? (
        <LockedApplication seller={mine.data} />
      ) : mine.isError ? (
        isNoApplication(mine.error) ? (
          <section className="flex flex-col items-start gap-3 rounded-lg bg-bg-surface p-5 inset-ring inset-ring-border-default">
            <h2 className="text-heading-s">아직 판매자 신청을 하지 않았어요</h2>
            <Link href="/seller-apply" className={buttonClassName()}>
              판매자 신청하러 가기
            </Link>
          </section>
        ) : (
          <Alert tone="danger" title={SELLER_API_ERRORS.loadFailed} />
        )
      ) : (
        <Skeleton shape="rect" className="h-80 w-full" />
      )}
    </FormBody>
  )
}

// 상태마다 위 안내 문구가 다르다
const APPLICATION_NOTICE: Record<memberModel.SellerDtoStatus, string> = {
  PENDING: '심사 중에는 고칠 수 없어요. 반려되면 이 값을 채운 채로 다시 신청할 수 있어요.',
  REJECTED: '반려된 신청이에요. 아래 값을 채운 채로 고쳐서 다시 신청할 수 있어요.',
  APPROVED: '승인된 신청이에요. 판매자 정보는 판매자 설정에서도 볼 수 있어요.',
}

function LockedApplication({ seller }: { seller: memberModel.SellerDto }) {
  const status = SELLER_STATUS[seller.status]
  const business = seller.sellerType === 'BUSINESS'
  return (
    <>
      <div className="flex items-center gap-3">
        <Badge tone={status.tone}>{status.label}</Badge>
        <p className="text-body-s text-text-secondary">
          신청일 {formatSellerDate(seller.requestedAt)}
        </p>
      </div>

      <ConsoleCard title="판매자 정보">
        <RadioGroup value={business ? 'business' : 'personal'} disabled className="flex-row gap-6">
          <Radio value="business" label="사업자예요" />
          <Radio value="personal" label="사업자가 아니에요" />
        </RadioGroup>
        {business ? (
          <>
            <div className="flex gap-4">
              <TextField
                label="사업자등록번호"
                className="flex-1"
                value={seller.businessNumber ?? ''}
                disabled
                readOnly
              />
              <TextField
                label="상호"
                className="flex-1"
                value={seller.businessName ?? ''}
                disabled
                readOnly
              />
            </div>
            <TextField label="대표자" value={seller.representativeName} disabled readOnly />
          </>
        ) : (
          <TextField label="성명" value={seller.representativeName} disabled readOnly />
        )}
        <p className="text-caption text-text-tertiary">
          {business
            ? '사업자등록번호 · 상호 · 대표자를 심사해요.'
            : '비사업자는 성명만 보여요. 연락처는 아직 서버에 저장하지 않아요.'}
        </p>
      </ConsoleCard>

      <ConsoleCard title="정산 계좌">
        <SettlementAccountNotice />
      </ConsoleCard>

      <div className="flex justify-end gap-2">
        {seller.status === 'REJECTED' && (
          <Link href="/seller-apply" className={buttonClassName({ size: 'l' })}>
            고쳐서 다시 신청하기
          </Link>
        )}
        <Link
          href="/seller-apply/result"
          className={buttonClassName({ variant: 'secondary', size: 'l' })}
        >
          신청 결과로
        </Link>
      </div>
    </>
  )
}
