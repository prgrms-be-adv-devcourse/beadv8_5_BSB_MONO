'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import type { Route } from 'next'
import { useRouter } from 'next/navigation'
import { useForm, useWatch } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { ApiError, memberApi } from '@/shared/api'
import { BottomCta, Button, TextField } from '@/shared/ui'

import { CREW_NAME_MAX, crewNameSchema, type CrewNameValues } from '../model/crew-name-schema'
import { CrewBand } from './CrewBand'

const FORM_ID = 'crew-new-form'

// 피그마 시안v4 · M26 크루 만들기 (기본 · 이름 비어 있음). 만들면 크루 홈(M09)으로 간다.
// 크루를 만든 회원이 크루장이다(정책 7장 「역할과 초대」 1번).
export function CrewNewPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const create = memberApi.useCreateCrew()
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CrewNameValues>({
    resolver: zodResolver(crewNameSchema),
    mode: 'onTouched',
    defaultValues: { name: '' },
  })
  const empty = useWatch({ control, name: 'name' }).trim() === ''

  const onSubmit = handleSubmit(({ name }) => {
    create.mutate(
      { data: { name } },
      {
        onSuccess: (response) => {
          void queryClient.invalidateQueries({ queryKey: memberApi.getFindMyCrewsQueryKey() })
          router.replace(`/crews/${response.data?.crewId}` as Route)
        },
        onError: (error) => {
          setError('name', {
            message:
              (error instanceof ApiError && error.serverMessage) ||
              '크루를 만들지 못했어요. 잠시 후 다시 시도해 주세요.',
          })
        },
      },
    )
  })

  return (
    <>
      <AppHeader type="sub" title="크루 만들기" />
      <main className="flex flex-1 flex-col">
        <CrewBand>
          <div className="flex flex-col gap-2">
            <h2 className="text-heading-m">크루 이름을 정해 주세요</h2>
            <p className="text-body-s text-text-secondary">
              크루원에게 보이는 이름이에요. 크루를 만들면 내가 크루장이 돼요.
            </p>
          </div>
          <form id={FORM_ID} onSubmit={onSubmit} noValidate>
            <TextField
              label="크루 이름"
              autoComplete="off"
              maxLength={CREW_NAME_MAX}
              helper="크루 홈과 초대 링크에 이 이름이 보여요"
              error={errors.name?.message}
              {...register('name')}
            />
          </form>
        </CrewBand>
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" type="submit" form={FORM_ID} disabled={empty} loading={create.isPending}>
            크루 만들기
          </Button>
        }
      />
    </>
  )
}
