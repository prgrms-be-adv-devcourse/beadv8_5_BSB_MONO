'use client'

import { useState } from 'react'

import { BODY_MAX, inquiryErrorMessage, TITLE_MAX, useCreateInquiry } from '@/entities/inquiry'
import { useGoBack } from '@/shared/lib'
import { Button, Textarea, TextField, useToast } from '@/shared/ui'

// 피그마 S14 크루런에 문의하기: 승인된 판매자가 대회 · 주문 없이 관리자에게 보낸다
export function SellerNewInquiry() {
  const goBack = useGoBack()
  const toast = useToast()
  const create = useCreateInquiry()
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')

  const send = () =>
    create.mutate(
      { type: 'SELLER_SUPPORT', title: title.trim(), body: body.trim() },
      {
        onSuccess: () => {
          toast({ message: '문의를 보냈어요' })
          goBack('/seller/inquiries')
        },
        onError: (error) => toast({ message: inquiryErrorMessage(error), tone: 'error' }),
      },
    )

  return (
    <div className="flex flex-col gap-4 rounded-xl bg-bg-surface p-6 inset-ring inset-ring-border-default">
      <TextField
        label="제목"
        value={title}
        maxLength={TITLE_MAX}
        onChange={(e) => setTitle(e.target.value)}
        helper={`${title.length}/${TITLE_MAX}`}
      />
      <Textarea
        label="문의 내용"
        value={body}
        maxLength={BODY_MAX}
        showCounter
        onChange={(e) => setBody(e.target.value)}
      />
      <Button
        className="self-end"
        disabled={title.trim() === '' || body.trim() === ''}
        loading={create.isPending}
        onClick={send}
      >
        문의 보내기
      </Button>
    </div>
  )
}
