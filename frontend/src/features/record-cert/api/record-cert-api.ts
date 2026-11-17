import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'

import { customFetch, fileModel, unwrapData, type RsData } from '@/shared/api'

import type { MyRecordCertSubmission, RecordCertSubmitRequest } from '../model/types'

// TODO(BUK-274): 경로 · 모양은 제출 API가 생기면 orval 생성 함수로 바꾼다
export const RECORD_CERTS_PATH = '/api/v1/member/record-certs'

// TODO(BUK-278): file-service에 기록증 파일 종류가 생기면 fileModel 값으로 바꾼다
export const RECORD_CERT_FILE_TYPE = 'RECORD_CERT' as fileModel.FileUploadRequestDtoFileType

/** 내 제출 목록 (최신순 전부) */
export function myRecordCertsQueryOptions() {
  return queryOptions({
    queryKey: [RECORD_CERTS_PATH],
    queryFn: async ({ signal }) =>
      unwrapData(
        await customFetch<RsData<MyRecordCertSubmission[]>>(RECORD_CERTS_PATH, { signal }),
      ),
  })
}

export function useSubmitRecordCert() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: RecordCertSubmitRequest) =>
      unwrapData(
        await customFetch<RsData<{ submissionId: number; status: 'CHECKING' }>>(RECORD_CERTS_PATH, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [RECORD_CERTS_PATH] }),
  })
}
