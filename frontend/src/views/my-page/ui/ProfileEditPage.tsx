'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData, type memberModel } from '@/shared/api'
import { useGoBack } from '@/shared/lib'
import { BottomCta, Button, Dialog, TextField } from '@/shared/ui'

import { toDotDate } from '../model/member-format'
import { modifyErrorMessage, profileSchema, type ProfileValues } from '../model/profile-schema'

// 피그마 M30-E 회원 정보 수정(1333:21303) · 닉네임이 겹칠 때(1333:21315) · 저장 중(1333:21327) · 저장하지 않고 나가기(1333:21339).
// 고칠 수 있는 값은 닉네임 하나다. 이름 · 생년월일 · 휴대폰은 본인인증으로 확인한 값이라 보기만 한다.
// 내 정보 수정(PUT /api/v1/member/members/me)은 닉네임만 받는다(BUK-33).
// TODO(api): 시안의 「본인인증 다시 하기」는 인증 결과를 회원 정보에 반영하는 API가 없어 두지 않았다.
export function ProfileEditPage() {
  const me = memberApi.useFindMe({
    // 로그인 확인(재발급)이 끝난 뒤에만 부른다. 로그아웃으로 캐시를 지운 뒤 다시 부르지도 않는다.
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  return (
    <>
      {me.data ? (
        <ProfileForm member={me.data} />
      ) : (
        <>
          <AppHeader type="sub" title="회원 정보 수정" backHref="/me/settings" />
          {me.isError && (
            <p role="alert" className="px-4 py-6 text-body2-regular text-text-danger">
              회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          )}
        </>
      )}
    </>
  )
}

function ProfileForm({ member }: { member: memberModel.MemberDto }) {
  const goBack = useGoBack()
  const queryClient = useQueryClient()
  const modify = memberApi.useModifyMe()
  const [formError, setFormError] = useState<string>()
  const [leaveOpen, setLeaveOpen] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isValid, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    mode: 'onTouched',
    defaultValues: { nickname: member.nickname ?? '' },
  })

  const onSubmit = handleSubmit((values) => {
    setFormError(undefined)
    modify.mutate(
      {
        data: { nickname: values.nickname },
      },
      {
        onSuccess: (response) => {
          // 응답이 바뀐 내 정보라 다시 부르지 않고 캐시에 넣는다.
          queryClient.setQueryData(memberApi.getFindMeQueryKey(), response)
          goBack('/me/settings')
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
      {/* 고친 내용이 있으면 뒤로 가기를 가로채 나가기 확인 창을 띄운다. contents라 머리말의 sticky가 그대로 먹는다 */}
      <div
        className="contents"
        onClickCapture={(event) => {
          if (isDirty && (event.target as HTMLElement).closest('[aria-label="뒤로 가기"]')) {
            event.preventDefault()
            event.stopPropagation()
            setLeaveOpen(true)
          }
        }}
      >
        <AppHeader type="sub" title="회원 정보 수정" backHref="/me/settings" />
      </div>
      <main className="flex flex-1 flex-col gap-4 px-4 py-6">
        <TextField
          label="닉네임"
          helper="다른 사람에게는 실명 대신 닉네임이 보여요. 한글 2~8자, 영문 4~16자"
          error={errors.nickname?.message}
          {...register('nickname')}
        />
        <TextField label="이름" value={member.name ?? ''} disabled readOnly />
        <TextField
          label="생년월일"
          value={member.birthDate ? toDotDate(member.birthDate) : ''}
          disabled
          readOnly
        />
        <TextField label="휴대폰 번호" value={member.phone ?? ''} disabled readOnly />
        {formError && (
          <p role="alert" className="text-caption-regular text-text-danger">
            {formError}
          </p>
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button
            size="l"
            disabled={!isValid || modify.isPending}
            loading={modify.isPending}
            onClick={onSubmit}
          >
            저장
          </Button>
        }
      />
      <Dialog
        open={leaveOpen}
        onOpenChange={setLeaveOpen}
        title="고친 내용을 저장하지 않고 나갈까요?"
        body="나가면 지금 고친 내용이 사라져요."
        cancelLabel="나가기"
        onCancel={() => goBack('/me/settings')}
        confirmLabel="계속 고치기"
        onConfirm={() => setLeaveOpen(false)}
      />
    </>
  )
}
