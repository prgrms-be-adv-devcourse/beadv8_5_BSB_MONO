'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { BirthDateField } from '@/entities/member'
import { memberApi, unwrapData, type memberModel } from '@/shared/api'
import { BottomCta, Button, PhoneField, TextField } from '@/shared/ui'

import { modifyErrorMessage, profileSchema, type ProfileValues } from '../model/profile-schema'

// 피그마 V2 · M30-1 회원 정보 수정
// 내 정보 조회(GET /api/v1/member/members/me)로 채우고, 내 정보 수정(PUT 같은 주소)으로 4칸을 통째로 바꾼다.
export function ProfileEditPage() {
  const me = memberApi.useFindMe({
    // 로그인 확인(재발급)이 끝난 뒤에만 부른다. 로그아웃으로 캐시를 지운 뒤 다시 부르지도 않는다.
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  return (
    <>
      <AppHeader type="sub" title="정보 수정" />
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
    defaultValues: {
      name: member.name ?? '',
      birth: member.birthDate ?? '',
      phone: member.phone ?? '',
      nickname: member.nickname ?? '',
    },
  })

  const onSubmit = handleSubmit((values) => {
    setFormError(undefined)
    modify.mutate(
      {
        // 가입 뒤에는 닉네임만 바꿀 수 있다(BUK-33). 이름 · 생년월일 · 휴대폰 번호는 서버가 받지 않는다
        data: { nickname: values.nickname },
      },
      {
        onSuccess: (response) => {
          // 응답이 바뀐 내 정보라 다시 부르지 않고 캐시에 넣는다.
          queryClient.setQueryData(memberApi.getFindMeQueryKey(), response)
          router.push('/me')
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
        <div className="flex flex-col gap-1">
          <h2 className="text-heading-m">회원 정보를 고쳐요</h2>
          <p className="text-body-s text-text-tertiary">바꾼 정보는 다음 대회 신청부터 쓰여요.</p>
        </div>
        <TextField
          label="이름"
          autoComplete="name"
          error={errors.name?.message}
          {...register('name')}
        />
        <BirthDateField
          label="생년월일"
          helper="만 14세 미만은 가입할 수 없어요"
          error={errors.birth?.message}
          {...register('birth')}
        />
        <PhoneField
          label="휴대폰 번호"
          placeholder="010-1234-5678"
          error={errors.phone?.message}
          {...register('phone')}
        />
        <TextField
          label="닉네임"
          helper="다른 사람에게는 실명 대신 닉네임이 보여요. 한글 2~8자, 영문 4~16자"
          error={errors.nickname?.message}
          {...register('nickname')}
        />
        <p className="text-caption text-text-tertiary">
          이름 · 생년월일 · 휴대폰 번호는 대회 신청 때 참가자 정보로 그대로 가져와요.
        </p>
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
