'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import { ConsoleCard } from '@/widgets/console-page'
import { ApiError, memberApi, unwrapData, type memberModel } from '@/shared/api'
import { bankName, BANKS, type BankCode } from '@/shared/config'
import { Alert, Button, Radio, RadioGroup, Select, Skeleton, TextField } from '@/shared/ui'

// 피그마 판매자 센터 · S11-1 정산 계좌. 승인된 판매자가 계좌를 보고, 바로 등록·수정한다.
// 서버가 신청 때와 같은 예금주 확인을 거쳐 저장하며, 확인에 실패하면 기존 계좌는 그대로다.

const ERRORS = {
  accountRequired: '계좌번호를 입력해 주세요.',
  accountFormat: '계좌번호는 숫자 6~16자리로 입력해 주세요.',
  loadFailed: '정산 계좌를 불러오지 못했어요.',
  saveFailed: '저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.',
} as const

const digitsOnly = (value: string) => value.replace(/[\s-]/g, '')
const digitsAndHyphen = (value: string) => value.replace(/[^\d-]/g, '')

const formSchema = z.object({
  bankCode: z.enum(BANKS.map((b) => b.value) as [BankCode, ...BankCode[]]),
  accountNumber: z
    .string()
    .trim()
    .superRefine((value, ctx) => {
      const digits = digitsOnly(value)
      if (!digits) ctx.addIssue({ code: 'custom', message: ERRORS.accountRequired })
      else if (!/^\d{6,16}$/.test(digits)) {
        ctx.addIssue({ code: 'custom', message: ERRORS.accountFormat })
      }
    }),
  accountHolder: z.enum(['business', 'personal']),
})
type FormValues = z.infer<typeof formSchema>

/** 서버가 주는 계좌 확인 실패·횟수 초과·장애 문구를 그대로 보여 준다. */
function saveErrorMessage(error: unknown): string {
  return error instanceof ApiError && error.serverMessage ? error.serverMessage : ERRORS.saveFailed
}

export function PayoutAccountCard({ seller }: { seller: memberModel.SellerDto }) {
  if (seller.status !== 'APPROVED') {
    return (
      <ConsoleCard title="정산 계좌">
        <Alert tone="warning" title="승인된 뒤에 정산 계좌를 등록할 수 있어요" />
      </ConsoleCard>
    )
  }
  return <ApprovedPayoutAccountCard business={seller.sellerType === 'BUSINESS'} />
}

function ApprovedPayoutAccountCard({ business }: { business: boolean }) {
  const [editing, setEditing] = useState(false)
  const account = memberApi.useFindMyPayoutAccount({ query: { select: unwrapData, retry: false } })

  return (
    <ConsoleCard title="정산 계좌">
      {account.isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : account.isError ? (
        <Alert tone="danger" title={ERRORS.loadFailed} description="잠시 뒤 다시 시도해 주세요." />
      ) : account.data.registered && !editing ? (
        <RegisteredAccount account={account.data} onEdit={() => setEditing(true)} />
      ) : (
        <>
          {!account.data.registered && (
            <Alert
              tone="warning"
              title="등록된 정산 계좌가 없어요"
              description="등록 전까지는 정산금 지급이 보류돼요."
            />
          )}
          <AccountForm
            business={business}
            canCancel={account.data.registered}
            onDone={() => setEditing(false)}
          />
        </>
      )}
    </ConsoleCard>
  )
}

function RegisteredAccount({
  account,
  onEdit,
}: {
  account: memberModel.MyPayoutAccountDto
  onEdit: () => void
}) {
  const fields: [string, string][] = [
    ['은행', bankName(account.bankCode ?? '')],
    ['예금주', account.accountHolderName ?? '-'],
    ['계좌번호', `**** ${account.accountNumberLast4 ?? ''}`],
  ]
  return (
    <>
      {/* 예금주 이름은 Clarity 화면 녹화에서 가린다 */}
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
      <Button type="button" variant="secondary" onClick={onEdit}>
        수정하기
      </Button>
    </>
  )
}

function AccountForm({
  business,
  canCancel,
  onDone,
}: {
  business: boolean
  canCancel: boolean
  onDone: () => void
}) {
  const queryClient = useQueryClient()
  const modify = memberApi.useModifyMyPayoutAccount()
  const [formError, setFormError] = useState<string>()
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: { bankCode: '004', accountNumber: '', accountHolder: 'personal' },
  })

  const onSubmit = handleSubmit((values) => {
    setFormError(undefined)
    modify.mutate(
      {
        data: {
          bankCode: values.bankCode,
          accountNumber: digitsOnly(values.accountNumber),
          accountHolderType: values.accountHolder === 'business' ? 'BUSINESS' : 'PERSONAL',
        },
      },
      {
        onSuccess: (response) => {
          // 응답이 저장된 계좌라 다시 부르지 않고 바로 보여 준다
          queryClient.setQueryData(memberApi.getFindMyPayoutAccountQueryKey(), response)
          onDone()
        },
        onError: (error) => setFormError(saveErrorMessage(error)),
      },
    )
  })

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <Controller
        control={control}
        name="bankCode"
        render={({ field }) => (
          <Select
            label="은행"
            items={[...BANKS]}
            value={field.value}
            onValueChange={(value) => value && field.onChange(value)}
          />
        )}
      />
      <TextField
        label="계좌번호"
        inputMode="numeric"
        filter={digitsAndHyphen}
        filterMessage="숫자와 -만 입력할 수 있어요"
        error={errors.accountNumber?.message}
        {...register('accountNumber')}
      />
      <span className="text-body1-medium">예금주 명의</span>
      <Controller
        control={control}
        name="accountHolder"
        render={({ field }) => (
          <RadioGroup
            value={field.value}
            onValueChange={(value) => field.onChange(value)}
            className="flex-row gap-6"
          >
            <Radio tone="inverse" value="personal" label="본인 명의" />
            <Radio tone="inverse" value="business" label="사업자 명의" disabled={!business} />
          </RadioGroup>
        )}
      />
      {formError && <Alert tone="danger" title={formError} />}
      <div className="flex gap-2">
        {canCancel && (
          <Button type="button" variant="secondary" onClick={onDone} disabled={modify.isPending}>
            취소
          </Button>
        )}
        <Button type="submit" loading={modify.isPending}>
          저장하기
        </Button>
      </div>
    </form>
  )
}
