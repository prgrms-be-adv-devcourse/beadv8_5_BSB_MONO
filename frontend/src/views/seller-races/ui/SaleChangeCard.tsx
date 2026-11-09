'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useForm } from 'react-hook-form'

import { ConsoleCard } from '@/widgets/console-page'
import { useUpdateRace } from '@/features/seller-race'
import {
  isRaceEditable,
  isSaleStarted,
  raceForEditQueryOptions,
  type RaceForEdit,
  type SellerRaceRow,
} from '@/entities/race'
import { ApiError } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import {
  Alert,
  Button,
  Divider,
  FOCUS_RING,
  KeyValue,
  Skeleton,
  TextField,
  useToast,
} from '@/shared/ui'

import {
  saleChangeSchema,
  toSaleChangeValues,
  toUpdateRequest,
  type SaleChangeValues,
} from '../model/sale-change-form'

const n = formatNumber

// 피그마 V2 · S06 대회 관리 오른쪽 '판매 중 변경' 카드
export function SaleChangeCard({ row }: { row: SellerRaceRow }) {
  const edit = useQuery(raceForEditQueryOptions(row.id))
  return (
    <ConsoleCard
      title="판매 중 변경"
      aside={
        isRaceEditable(row.saleStatus) && (
          <Link
            href={`/seller/races/${row.id}/change`}
            className={cn('rounded-sm text-body2-medium text-text-brand', FOCUS_RING)}
          >
            일정 변경 · 대회 취소
          </Link>
        )
      }
      className="w-110 shrink-0"
    >
      <p className="text-body2-regular text-text-secondary">
        {row.title} · {row.badge.label}
      </p>
      {edit.isPending ? (
        <div role="status" aria-label="불러오는 중" className="flex flex-col gap-3">
          <Skeleton shape="rect" className="h-24 rounded-md" />
          <Skeleton shape="rect" className="h-24 rounded-md" />
        </div>
      ) : edit.isError ? (
        <Alert
          tone="danger"
          title="대회 정보를 불러오지 못했어요"
          description={errorMessage(edit.error)}
        />
      ) : (
        <SaleChangeForm key={edit.dataUpdatedAt} edit={edit.data} row={row} />
      )}
    </ConsoleCard>
  )
}

function SaleChangeForm({ edit, row }: { edit: RaceForEdit; row: SellerRaceRow }) {
  const toast = useToast()
  const update = useUpdateRace(row.id)
  const editable = isRaceEditable(row.saleStatus)
  const started = isSaleStarted(row.saleStatus)
  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<SaleChangeValues>({
    resolver: zodResolver(saleChangeSchema(edit)),
    mode: 'onChange',
    defaultValues: toSaleChangeValues(edit),
  })

  return (
    <form
      noValidate
      className="flex flex-col gap-4"
      onSubmit={handleSubmit((values) =>
        update.mutate(toUpdateRequest(edit, values), {
          onSuccess: () => toast({ message: '변경을 저장했어요', tone: 'success' }),
        }),
      )}
    >
      <div className="flex flex-col gap-3">
        <h3 className="text-body1-medium">정원 늘리기</h3>
        <KeyValue label="이미 판매된 수량" value={`${n(row.sold)}명`} />
        {edit.request.courses.map((course, i) => (
          <TextField
            key={course.courseId ?? i}
            label={`모집 정원 (${course.name})`}
            inputMode="numeric"
            disabled={!editable}
            helper={started ? '판매가 시작돼 늘리기만 할 수 있어요' : undefined}
            error={errors.courses?.[i]?.individualQuota?.message}
            {...register(`courses.${i}.individualQuota`)}
          />
        ))}
      </div>
      <Divider />
      <div className="flex flex-col gap-3">
        <h3 className="text-body1-medium">가격 · 환불 기한</h3>
        {edit.request.courses.map((course, i) => (
          <TextField
            key={course.courseId ?? i}
            label={`참가비 (${course.name})`}
            inputMode="numeric"
            disabled={!editable}
            error={errors.courses?.[i]?.price?.message}
            {...register(`courses.${i}.price`)}
          />
        ))}
        <TextField
          label="환불 기한"
          disabled={!editable}
          helper={started ? '판매가 시작돼 늦추기만 할 수 있어요' : '이 날짜(23:59)까지 전액 환불'}
          error={errors.refundDeadline?.message}
          {...register('refundDeadline')}
        />
        <Alert
          title="구매자에게 유리할 때만 바꿀 수 있어요"
          description="판매가 시작되면 참가비는 바꿀 수 없고, 정원 늘리기 · 환불 기한 늦추기만 돼요."
        />
      </div>
      {!editable && (
        <p className="text-body2-regular text-text-tertiary">
          판매가 끝났거나 취소된 대회는 바꿀 수 없어요.
        </p>
      )}
      {update.isError && (
        <Alert
          tone="danger"
          title="변경을 저장하지 못했어요"
          description={errorMessage(update.error)}
        />
      )}
      <Button
        type="submit"
        variant="secondary"
        className="w-full"
        disabled={!editable || !isDirty}
        loading={update.isPending}
      >
        변경 저장
      </Button>
    </form>
  )
}

function errorMessage(error: Error | null): string {
  if (error instanceof ApiError && error.serverMessage) return error.serverMessage
  return '잠시 후 다시 시도해 주세요.'
}
