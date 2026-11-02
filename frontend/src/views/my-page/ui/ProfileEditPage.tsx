'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData, type memberModel } from '@/shared/api'
import { BottomCta, Button, TextField } from '@/shared/ui'

import { modifyErrorMessage, profileSchema, type ProfileValues } from '../model/profile-schema'

// 피그마 V2 · M30-1 회원 정보 수정. 계정 설정(/me/settings)의 수정 버튼으로 들어온다.
// 고칠 수 있는 값은 닉네임 하나다. 이름 · 생년월일 · 휴대폰은 본인인증으로 확인한 값이라 계정 설정에서 보기만 한다.
// 내 정보 수정(PUT /api/v1/member/members/me)은 4칸을 통째로 받으므로, 나머지 3칸은 조회한 값을 그대로 보낸다.
export function ProfileEditPage() {
  const me = memberApi.useFindMe({
    // 로그인 확인(재발급)이 끝난 뒤에만 부른다. 로그아웃으로 캐시를 지운 뒤 다시 부르지도 않는다.
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  return (
    <>
      <AppHeader type="sub" title="닉네임 수정" />
      {me.data ? (
        <ProfileForm member={me.data} />
      ) : (
        me.isError && (
          <p role="alert" className="px-4 py-6 text-body-s text-text-danger">
            회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        )
      )}
    </>
  )
}

function ProfileForm({ member }: { member: memberModel.MemberDto }) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const modify = memberApi.useModifyMe()
  const [formError, setFormError] = useState<string>()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isValid },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    mode: 'onTouched',
    defaultValues: { nickname: member.nickname ?? '' },
  })

  const onSubmit = handleSubmit((values) => {
    setFormError(undefined)
    modify.mutate(
      {
        data: {
          name: member.name ?? '',
          birthDate: member.birthDate ?? '',
          phone: member.phone ?? '',
          nickname: values.nickname,
        },
      },
      {
        onSuccess: (response) => {
          // 응답이 바뀐 내 정보라 다시 부르지 않고 캐시에 넣는다.
          queryClient.setQueryData(memberApi.getFindMeQueryKey(), response)
          router.push('/me/settings')
        },
        onError: (error) => {
          const { target, message } = modifyErrorMessage(error)
          if (target === 'form') setFormError(message)
          else setError(target, { message }, { shouldFocus: true })
        },
      },
    )
  })

  return (
    <>
      <main className="flex flex-1 flex-col gap-5 px-4 py-6">
        <h2 className="text-heading-m">닉네임을 고쳐요</h2>
        <TextField
          label="닉네임"
          helper="다른 사람에게는 실명 대신 닉네임이 보여요. 한글 2~8자, 영문 4~16자"
          error={errors.nickname?.message}
          {...register('nickname')}
        />
        {formError && (
          <p role="alert" className="text-caption text-text-danger">
            {formError}
          </p>
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" disabled={!isValid || modify.isPending} onClick={onSubmit}>
            저장하기
          </Button>
        }
      />
    </>
  )
}
