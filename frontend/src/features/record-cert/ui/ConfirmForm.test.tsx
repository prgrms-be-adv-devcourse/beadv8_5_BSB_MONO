import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it, vi } from 'vitest'

import { server } from '@/shared/testing'

import { RECORD_CERTS_PATH } from '../api/record-cert-api'
import { ConfirmForm } from './RecordCertFlow'

it('제출이 실패하면 알리고, 다시 누르면 사진을 다시 올리지 않고 같은 파일로 낸다', async () => {
  const sent: number[] = []
  let fail = true
  server.use(
    http.post(`*${RECORD_CERTS_PATH}`, async ({ request }) => {
      sent.push(((await request.json()) as { fileId: number }).fileId)
      if (fail) {
        fail = false
        return HttpResponse.json(
          { status: 503, message: '잠시 뒤 다시 시도해 주세요.', data: null },
          { status: 503 },
        )
      }
      return HttpResponse.json(
        { status: 201, message: '', data: { submissionId: 1, status: 'CHECKING' } },
        { status: 201 },
      )
    }),
  )
  const uploadPhoto = vi.fn().mockResolvedValue(77)
  const onDone = vi.fn()
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ConfirmForm
        photo={{
          file: new File(['x'], 'c.png'),
          url: '',
          image: { width: 10, height: 10 } as ImageData,
        }}
        roi={{ x: 0, y: 0, width: 10, height: 10 }}
        read={{
          values: { netTime: '1:30:05', raceDate: '2023-04-12', courseClass: 'HALF', bib: '' },
          boxes: {},
          recSha256: null,
          manual: null,
        }}
        orderItemId={1}
        uploadPhoto={uploadPhoto}
        onRetry={() => {}}
        onDone={onDone}
      />
    </QueryClientProvider>,
  )

  await userEvent.click(screen.getByRole('button', { name: '제출하기' }))
  expect(await screen.findByText('제출하지 못했어요')).toBeInTheDocument()
  expect(onDone).not.toHaveBeenCalled()

  await userEvent.click(screen.getByRole('button', { name: '제출하기' }))
  await vi.waitFor(() => expect(onDone).toHaveBeenCalledOnce())
  expect(uploadPhoto).toHaveBeenCalledOnce()
  expect(sent).toEqual([77, 77])
})
