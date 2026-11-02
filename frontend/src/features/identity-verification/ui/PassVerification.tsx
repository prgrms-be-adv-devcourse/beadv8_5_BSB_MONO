'use client'

import { useState } from 'react'

import { BirthDateField } from '@/entities/member'
import { memberZod, type memberModel } from '@/shared/api'
import { BottomSheet, BottomSheetOption, Button, PhoneField, TextField } from '@/shared/ui'

import { verifyWithPass } from '../api/identity-verification'

type Carrier = memberModel.PassVerificationRequestDtoCarrier

const CARRIERS: { id: Carrier; label: string }[] = [
  { id: 'SKT', label: 'SKT' },
  { id: 'KT', label: 'KT' },
  { id: 'LGU', label: 'LG U+' },
  { id: 'MVNO', label: '알뜰폰' },
]

const FAILED = '본인인증을 마치지 못했어요. 입력한 정보를 확인해 주세요.'

export type PassVerificationProps = {
  /** PASS 인증을 마쳤는지. 마쳤으면 버튼 대신 완료 문구와 다시 인증 버튼을 보여 준다 */
  verified: boolean
  onVerified: (result: memberModel.IdentityVerificationDto) => void
  /** 잠근 이름 · 생년월일 · 휴대폰 번호를 풀고 처음부터 다시 인증한다 */
  onReset: () => void
}

/**
 * 「PASS로 본인인증하기」 버튼과 인증 시트. 통신사 선택 → PASS 인증 화면(체험용) 순서다.
 * 실제 PASS는 새 창을 띄우지만, 목업이라 화면 안 바텀시트에서 같은 순서를 흉내 낸다.
 * 인증을 마치면 확인된 이름 · 생년월일 · 휴대폰 번호를 onVerified로 넘긴다(가입 폼이 채우고 잠근다).
 */
export function PassVerification({ verified, onVerified, onReset }: PassVerificationProps) {
  const [open, setOpen] = useState(false)
  const [carrier, setCarrier] = useState<Carrier | null>(null)

  const openSheet = () => {
    setCarrier(null)
    setOpen(true)
  }

  if (verified) {
    return (
      <div className="flex items-center justify-between gap-2 rounded-md bg-bg-subtle px-4 py-3">
        <p role="status" className="text-body-s text-text-primary">
          PASS로 본인인증을 마쳤어요. 이름 · 생년월일 · 휴대폰 번호는 인증한 정보로 채웠어요.
        </p>
        <Button variant="ghost" size="s" className="shrink-0" onClick={onReset}>
          다시 인증하기
        </Button>
      </div>
    )
  }

  return (
    <>
      <Button variant="secondary" size="l" onClick={openSheet}>
        PASS로 본인인증하기
      </Button>
      <BottomSheet
        open={open}
        onOpenChange={setOpen}
        title={carrier ? 'PASS 본인인증 (체험용)' : '통신사를 골라 주세요'}
      >
        {carrier ? (
          <PassForm
            carrier={carrier}
            onDone={(result) => {
              setOpen(false)
              onVerified(result)
            }}
          />
        ) : (
          CARRIERS.map((item) => (
            <BottomSheetOption key={item.id} onClick={() => setCarrier(item.id)}>
              {item.label}
            </BottomSheetOption>
          ))
        )}
      </BottomSheet>
    </>
  )
}

/** PASS 창에 해당하는 입력 화면. 실제로는 PASS가 통신사 가입 정보와 맞는지 확인한다. */
function PassForm({
  carrier,
  onDone,
}: {
  carrier: Carrier
  onDone: (result: memberModel.IdentityVerificationDto) => void
}) {
  const [name, setName] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [phone, setPhone] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string>()

  const body = { carrier, name: name.trim(), birthDate, phone: phone.trim() }
  const valid = memberZod.VerifyIdentityWithPassBody.safeParse(body).success

  const submit = async () => {
    setError(undefined)
    setPending(true)
    try {
      onDone(await verifyWithPass(body))
    } catch {
      setError(FAILED)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex flex-col gap-4 px-2 pt-1 pb-3">
      <p className="text-body-s text-text-secondary">
        체험용 화면이라 실제 PASS 인증은 하지 않아요. 입력한 정보가 인증된 정보로 채워져요.
      </p>
      <TextField
        label="이름"
        autoComplete="name"
        value={name}
        onChange={(event) => setName(event.currentTarget.value)}
      />
      <BirthDateField
        label="생년월일"
        name="passBirthDate"
        onChange={(event) => setBirthDate(event.target.value)}
      />
      <PhoneField
        label="휴대폰 번호"
        placeholder="010-1234-5678"
        value={phone}
        onChange={(event) => setPhone(event.currentTarget.value)}
      />
      {error && (
        <p role="alert" className="text-caption text-text-danger">
          {error}
        </p>
      )}
      <Button size="l" disabled={!valid} loading={pending} onClick={() => void submit()}>
        본인인증 마치기
      </Button>
    </div>
  )
}
