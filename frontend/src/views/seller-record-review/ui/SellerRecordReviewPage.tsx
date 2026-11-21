'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { ConsoleCard, ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import {
  COURSE_CLASS_LABEL,
  formatNetTime,
  raceRecordCertsQueryOptions,
  recordCertDetailQueryOptions,
  reviewReasonSentences,
  reviewReasonShort,
  useApproveRecordCert,
  type SellerRecordCertSubmission,
} from '@/features/seller-record-review'
import { myRacesQueryOptions } from '@/entities/race'
import { ApiError } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Alert, Button, EmptyState, FOCUS_RING, Icon, KeyValue, useToast } from '@/shared/ui'

import { DenyDialog } from './DenyDialog'

const serverText = (error: unknown) =>
  (error instanceof ApiError && error.serverMessage) || '잠시 후 다시 시도해 주세요.'

// 피그마 V4 · 판매자 · 기록증 심사(2070:66040 목록, 2070:62040 빈 화면): 심사 필요 칩 → 왼쪽 목록 · 오른쪽 기록증
export function SellerRecordReviewPage({ raceId }: { raceId: number }) {
  const races = useQuery(myRacesQueryOptions())
  const list = useQuery(raceRecordCertsQueryOptions(raceId, 'NEEDS_REVIEW'))
  const [pickedId, setPickedId] = useState<number>()
  const items = list.data ?? []
  // 심사한 제출은 목록에서 빠지므로, 고른 제출이 없어지면 첫 제출을 연다
  const selected = items.find((s) => s.submissionId === pickedId) ?? items[0]

  return (
    <ConsolePage
      title="기록증 심사"
      description={races.data?.find((r) => r.raceId === raceId)?.title}
    >
      {list.isPending ? (
        <ConsoleLoading />
      ) : list.isError ? (
        <Alert
          tone="danger"
          title="기록증을 불러오지 못했어요"
          description={serverText(list.error)}
        />
      ) : !selected ? (
        <ConsoleCard className="py-24">
          <EmptyState icon={null} title="심사할 기록증이 없어요" className="p-0" />
        </ConsoleCard>
      ) : (
        <>
          <p className="inline-flex h-10 items-center gap-1 self-start rounded-full bg-bg-inverse px-4 text-body1-medium text-text-inverse">
            심사 필요<span>{items.length}</span>
          </p>
          <div className="flex items-start gap-6">
            <ul
              aria-label="심사 목록"
              className="flex w-90 shrink-0 flex-col gap-1 rounded-md bg-bg-surface p-3 shadow-sm inset-ring inset-ring-border-default"
            >
              {items.map((s) => (
                <li key={s.submissionId}>
                  <SubmissionItem
                    submission={s}
                    selected={s.submissionId === selected.submissionId}
                    onSelect={() => setPickedId(s.submissionId)}
                  />
                </li>
              ))}
            </ul>
            <SubmissionDetail key={selected.submissionId} submission={selected} />
          </div>
        </>
      )}
    </ConsolePage>
  )
}

function SubmissionItem({
  submission: s,
  selected,
  onSelect,
}: {
  submission: SellerRecordCertSubmission
  selected: boolean
  onSelect: () => void
}) {
  const reason = reviewReasonShort(s)
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onSelect}
      className={cn(
        'flex w-full flex-col gap-1 rounded-sm px-4 py-3 text-left hover:bg-bg-subtle',
        selected && 'border-2 border-bg-inverse hover:bg-transparent',
        FOCUS_RING,
      )}
    >
      <span className="text-body1-medium text-text-primary">제출 {s.submissionId}</span>
      <span className="text-caption-regular text-text-secondary">
        {[s.raceDate, COURSE_CLASS_LABEL[s.courseClass], formatNetTime(s.netTimeSeconds)].join(
          ' · ',
        )}
      </span>
      {reason && <span className="text-caption-regular text-text-secondary">사유 · {reason}</span>}
    </button>
  )
}

function SubmissionDetail({ submission: s }: { submission: SellerRecordCertSubmission }) {
  const detail = useQuery(recordCertDetailQueryOptions(s.submissionId))
  const approve = useApproveRecordCert()
  const [denyOpen, setDenyOpen] = useState(false)
  const toast = useToast()
  const sentences = reviewReasonSentences(s)
  const fileUrl = detail.data?.fileUrl

  return (
    // 기록 사진과 배번은 개인정보라 화면 녹화(Clarity)에서 가린다
    <section
      aria-labelledby="record-cert-values"
      data-clarity-mask="true"
      className="flex min-w-0 flex-1 gap-6 rounded-md bg-bg-surface p-6 shadow-sm inset-ring inset-ring-border-default"
    >
      <div className="flex h-110 w-80 shrink-0 items-center justify-center overflow-hidden rounded-sm bg-bg-muted">
        {fileUrl ? (
          // 30분 뒤 만료되는 S3 주소라 next/image로 최적화하지 않는다
          // eslint-disable-next-line @next/next/no-img-element
          <img src={fileUrl} alt="제출한 기록증 사진" className="size-full object-contain" />
        ) : (
          <Icon
            name="Image"
            className="size-8 text-text-tertiary"
            role="img"
            aria-hidden={false}
            aria-label={detail.isPending ? '사진을 불러오는 중' : '사진 없음'}
          />
        )}
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-4 self-stretch">
        <h2 id="record-cert-values" className="text-title3 text-text-primary">
          참가자가 확인한 값
        </h2>
        <div className="flex flex-col">
          <Row label="대회일" value={s.raceDate} />
          <Row label="종목" value={COURSE_CLASS_LABEL[s.courseClass]} />
          <Row label="넷타임" value={formatNetTime(s.netTimeSeconds)} />
          <Row label="배번" value={detail.isPending ? '' : (detail.data?.bib ?? '-')} />
        </div>
        {detail.isError && (
          <p role="alert" className="text-body1-regular text-text-danger">
            {serverText(detail.error)}
          </p>
        )}
        {sentences.length > 0 && (
          <div className="flex flex-col gap-1">
            <h3 className="text-body1-bold text-text-primary">확인할 점</h3>
            {sentences.map((text) => (
              <p key={text} className="text-body1-regular text-text-primary">
                {text}
              </p>
            ))}
          </div>
        )}
        <div className="mt-auto flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setDenyOpen(true)}>
            승인 불가
          </Button>
          <Button
            loading={approve.isPending}
            onClick={() =>
              approve.mutate(
                { submissionId: s.submissionId, input: undefined },
                {
                  onSuccess: () => toast({ message: '기록증을 승인했어요', tone: 'success' }),
                  onError: (error) => toast({ message: serverText(error), tone: 'error' }),
                },
              )
            }
          >
            승인하기
          </Button>
        </div>
      </div>
      <DenyDialog
        submissionId={s.submissionId}
        open={denyOpen}
        onOpenChange={setDenyOpen}
        onDenied={() => {
          setDenyOpen(false)
          toast({ message: '승인 불가를 보냈어요', tone: 'success' })
        }}
      />
    </section>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return <KeyValue className="items-baseline py-3" label={label} value={value} />
}
