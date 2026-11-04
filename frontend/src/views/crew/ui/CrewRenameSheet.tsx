'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'

import { ApiError, memberApi } from '@/shared/api'
import { BottomSheet, Button, TextField, useToast } from '@/shared/ui'

import { crewActionMessage } from '../model/crew-action-failure'
import { CREW_NAME_MAX, crewNameSchema, type CrewNameValues } from '../model/crew-name-schema'

const FORM_ID = 'crew-rename-form'

// 크루 이름 바꾸기(크루장만, PATCH /api/v1/member/crews/{crewId}). 시안에 없어 크루 만들기(M26)의 이름 칸을 그대로 쓴다.
export function CrewRenameSheet({
  crewId,
  name,
  open,
  onOpenChange,
}: {
  crewId: number
  name: string
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const showToast = useToast()
  const update = memberApi.useUpdateCrew()
  const {
    control,
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<CrewNameValues>({
    resolver: zodResolver(crewNameSchema),
    mode: 'onTouched',
    values: { name },
  })
  const empty = useWatch({ control, name: 'name' }).trim() === ''

  const close = () => {
    onOpenChange(false)
    reset({ name })
  }

  const onSubmit = handleSubmit((values) => {
    update.mutate(
      { crewId, data: values },
      {
        onSuccess: (response) => {
          queryClient.setQueryData(memberApi.getFindCrewQueryKey(crewId), response)
          void queryClient.invalidateQueries({ queryKey: memberApi.getFindCrewQueryKey(crewId) })
          void queryClient.invalidateQueries({ queryKey: memberApi.getFindMyCrewsQueryKey() })
          showToast({ tone: 'success', message: '크루 이름을 바꿨어요' })
          onOpenChange(false)
        },
        onError: (error) => {
          // 이름 규칙 위반(400)은 입력칸 아래에 서버 문구로 보여 준다
          if (error instanceof ApiError && error.status === 400) {
            setError('name', { message: error.serverMessage ?? '크루 이름을 확인해 주세요.' })
            return
          }
          showToast({ tone: 'error', message: crewActionMessage(error) })
          // 권한 · 크루가 바뀐 경우(4xx)는 창을 닫고 다시 불러온다. 서버 · 네트워크 오류는 입력을 남겨 다시 시도하게 한다.
          if (error instanceof ApiError && error.status < 500) {
            close()
            void queryClient.invalidateQueries({ queryKey: memberApi.getFindCrewQueryKey(crewId) })
          }
        },
      },
    )
  })

  return (
    <BottomSheet
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : !update.isPending && close())}
      title="크루 이름 바꾸기"
      footer={
        <Button size="l" type="submit" form={FORM_ID} disabled={empty} loading={update.isPending}>
          저장하기
        </Button>
      }
    >
      <form id={FORM_ID} onSubmit={onSubmit} noValidate className="px-2 pb-2">
        <TextField
          label="크루 이름"
          autoComplete="off"
          maxLength={CREW_NAME_MAX}
          helper="크루 홈과 초대 링크에 이 이름이 보여요"
          error={errors.name?.message}
          {...register('name')}
        />
      </form>
    </BottomSheet>
  )
}
