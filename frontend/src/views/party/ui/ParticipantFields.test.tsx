import { render, screen } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { describe, expect, it } from 'vitest'

import { EMPTY_PARTICIPANT, type ParticipantValues } from '../model/participant-form'
import { ParticipantFields } from './ParticipantFields'

function Harness({ defaults }: { defaults: Partial<ParticipantValues> }) {
  const form = useForm<ParticipantValues>({ defaultValues: { ...EMPTY_PARTICIPANT, ...defaults } })
  return <ParticipantFields form={form} kit />
}

describe('ParticipantFields · 키트 배송지', () => {
  it('이미 들어 있는 주소는 입력칸 없이 글자로 보여 준다', () => {
    render(<Harness defaults={{ zipcode: '06236', address: '서울특별시 강남구 테헤란로 123' }} />)

    expect(screen.getByText('(06236) 서울특별시 강남구 테헤란로 123')).toBeInTheDocument()
    expect(screen.queryByLabelText('키트 배송 우편번호')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('키트 배송 주소')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 검색' })).toBeInTheDocument()
  })

  it('주소가 비어 있으면 글자 영역 없이 주소 검색 버튼만 보여 준다', () => {
    render(<Harness defaults={{}} />)

    expect(screen.getByRole('button', { name: '주소 검색' })).toBeInTheDocument()
    expect(screen.queryByText(/^\(/)).not.toBeInTheDocument()
  })
})
