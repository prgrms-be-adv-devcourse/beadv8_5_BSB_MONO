'use client'

import { useState } from 'react'

import { BirthDateField } from '@/entities/member'
import { type memberModel } from '@/shared/api'
import { memberZod } from '@/shared/api-zod'
import {
  Alert,
  Badge,
  BottomSheet,
  Button,
  Dialog,
  EmptyState,
  PhoneField,
  Radio,
  RadioGroup,
  TextField,
} from '@/shared/ui'

import {
  IDENTITY_MESSAGES,
  verifyWithMockPass,
  type VerifiedIdentity,
} from '../api/identity-verification'

type Carrier = memberModel.PassVerificationRequestDtoCarrier
type Method = 'app' | 'sms'

const CARRIERS: { id: Carrier; label: string }[] = [
  { id: 'SKT', label: 'SKT' },
  { id: 'KT', label: 'KT' },
  { id: 'LGU', label: 'LG U+' },
  { id: 'MVNO', label: '알뜰폰' },
]

const PROGRESS_TITLE: Record<Method, string> = {
  app: 'PASS 앱에서 인증을 마쳐 주세요',
  sms: '문자로 받은 링크에서 인증을 마쳐 주세요',
}

export type MockPassSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  onVerified: (identity: VerifiedIdentity) => void
}

/**
 * 가짜 응답 모드(API_MOCKING=on)의 체험용 본인인증 창 (시안 M04-P). 실제 백엔드 모드는 포트원 창이 대신한다.
 * 통신사 · 인증 방법 · 정보 입력 → 진행 중 → 「인증 완료」로 끝난다. 실제 PASS 로고 · 색은 쓰지 않는다.
 * 진행 중에 닫으려 하면 그만둘지 묻는다. 그만둬도 가입 화면의 입력값은 그대로다.
 */
export function MockPassSheet({ open, onOpenChange, onVerified }: MockPassSheetProps) {
  const [carrier, setCarrier] = useState<Carrier>('SKT')
  const [method, setMethod] = useState<Method>('app')
  const [name, setName] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [phone, setPhone] = useState('')
  const [step, setStep] = useState<'form' | 'progress'>('form')
  const [pending, setPending] = useState(false)
  const [failed, setFailed] = useState(false)
  const [confirmQuit, setConfirmQuit] = useState(false)

  const body = { carrier, name: name.trim(), birthDate, phone: phone.trim() }
  const valid = memberZod.VerifyIdentityWithMockPassBody.safeParse(body).success
  const carrierLabel = CARRIERS.find((item) => item.id === carrier)?.label

  const close = () => {
    setStep('form')
    onOpenChange(false)
  }
  const handleOpenChange = (next: boolean) => {
    if (next) return onOpenChange(true)
    if (step === 'progress') return setConfirmQuit(true)
    close()
  }
  const finish = async () => {
    setPending(true)
    const outcome = await verifyWithMockPass(body)
    setPending(false)
    if (outcome.status === 'verified') {
      close()
      onVerified(outcome.identity)
      return
    }
    setStep('form')
    setFailed(true)
  }

  return (
    <>
      <BottomSheet open={open} onOpenChange={handleOpenChange} title="휴대폰 본인인증">
        <div className="flex flex-col gap-4 px-2 pt-1 pb-3">
          <div className="flex flex-col gap-2 rounded-md bg-bg-subtle p-3">
            <div className="flex items-center gap-2">
              <Badge tone="neutral">인증 업체 화면</Badge>
              <span className="text-caption text-text-secondary">
                크루런 밖에서 여는 본인인증 창이에요
              </span>
            </div>
            <Alert tone="warning" title="체험용 화면이에요. 실제 인증은 하지 않아요" />
          </div>
          {step === 'form' ? (
            <>
              <fieldset className="flex flex-col gap-2">
                <legend className="mb-2 text-label-strong text-text-primary">통신사</legend>
                <RadioGroup
                  value={carrier}
                  onValueChange={(value) => setCarrier(value as Carrier)}
                  className="grid grid-cols-2 gap-3"
                >
                  {CARRIERS.map((item) => (
                    <Radio key={item.id} value={item.id} label={item.label} />
                  ))}
                </RadioGroup>
              </fieldset>
              <fieldset className="flex flex-col gap-2">
                <legend className="mb-2 text-label-strong text-text-primary">인증 방법</legend>
                <RadioGroup value={method} onValueChange={(value) => setMethod(value as Method)}>
                  <Radio value="app" label="PASS 앱으로 인증" />
                  <Radio value="sms" label="문자로 인증" />
                </RadioGroup>
              </fieldset>
              <TextField
                label="이름"
                autoComplete="name"
                value={name}
                onChange={(event) => setName(event.currentTarget.value)}
              />
              <BirthDateField
                label="생년월일"
                name="mockPassBirthDate"
                onChange={(event) => setBirthDate(event.target.value)}
              />
              <PhoneField
                label="휴대폰 번호"
                placeholder="010-1234-5678"
                helper={`${carrierLabel}로 개통한 번호를 적어 주세요`}
                value={phone}
                onChange={(event) => setPhone(event.currentTarget.value)}
              />
              <Button size="l" disabled={!valid} onClick={() => setStep('progress')}>
                인증 요청
              </Button>
            </>
          ) : (
            <EmptyState
              className="py-10"
              title={PROGRESS_TITLE[method]}
              description="체험용 화면이라 알림은 오지 않아요. 아래 「인증 완료」를 누르면 인증이 끝나요"
              action={
                <Button size="l" loading={pending} onClick={() => void finish()}>
                  인증 완료
                </Button>
              }
            />
          )}
        </div>
      </BottomSheet>
      <Dialog
        open={failed}
        onOpenChange={setFailed}
        type="alert"
        title="본인인증을 마치지 못했어요"
        body={IDENTITY_MESSAGES.failed}
        confirmLabel="확인"
        onConfirm={() => setFailed(false)}
      />
      <Dialog
        open={confirmQuit}
        onOpenChange={setConfirmQuit}
        title="본인인증을 그만둘까요?"
        body="가입 화면으로 돌아가요. 입력한 가입 정보는 그대로예요."
        cancelLabel="계속하기"
        confirmLabel="그만두기"
        onConfirm={() => {
          setConfirmQuit(false)
          close()
        }}
      />
    </>
  )
}
