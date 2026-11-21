import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'

import { customFetch, unwrapData, type RsData } from '@/shared/api'

import type {
  RecordCertDenyRequest,
  SellerRecordCertDetail,
  SellerRecordCertSubmission,
  SubmissionStatus,
} from '../model/types'

// TODO(BUK-276): member 명세에 판매자 기록증 API가 들어오면 orval 생성 함수로 바꾼다
const SELLER_PATH = '/api/v1/member/seller'
const RECORD_CERTS_KEY = [SELLER_PATH, 'record-certs'] as const

/** 내 대회의 기록증 제출 (최신순 전부). 남의 대회면 403 */
export function raceRecordCertsQueryOptions(raceId: number, status?: SubmissionStatus) {
  const query = status ? `?status=${status}` : ''
  return queryOptions({
    queryKey: [...RECORD_CERTS_KEY, 'race', raceId, status ?? 'ALL'],
    queryFn: async ({ signal }) =>
      unwrapData(
        await customFetch<RsData<SellerRecordCertSubmission[]>>(
          `${SELLER_PATH}/races/${raceId}/record-certs${query}`,
          { signal },
        ),
      ),
  })
}

/** 제출 하나 (원본 사진 보기 주소는 30분 유효) */
export function recordCertDetailQueryOptions(submissionId: number) {
  return queryOptions({
    queryKey: [...RECORD_CERTS_KEY, 'detail', submissionId],
    queryFn: async ({ signal }) =>
      unwrapData(
        await customFetch<RsData<SellerRecordCertDetail>>(
          `${SELLER_PATH}/record-certs/${submissionId}`,
          { signal },
        ),
      ),
  })
}

function useReview<T>(action: 'approve' | 'deny', toBody: (input: T) => object | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ submissionId, input }: { submissionId: number; input: T }) => {
      const body = toBody(input)
      return unwrapData(
        await customFetch<RsData<SellerRecordCertSubmission>>(
          `${SELLER_PATH}/record-certs/${submissionId}/${action}`,
          body
            ? {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
              }
            : { method: 'POST' },
        ),
      )
    },
    // 심사 결과는 목록 · 상세 · 대시보드 건수 모두에 보이므로 판매자 기록증 키를 통째로 새로 받는다
    onSuccess: () => queryClient.invalidateQueries({ queryKey: RECORD_CERTS_KEY }),
  })
}

export function useApproveRecordCert() {
  return useReview<void>('approve', () => undefined)
}

export function useDenyRecordCert() {
  return useReview<RecordCertDenyRequest>('deny', (input) => input)
}
