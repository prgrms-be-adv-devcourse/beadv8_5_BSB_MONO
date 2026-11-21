'use client'

import { Dialog } from '@base-ui/react/dialog'
import { useState } from 'react'
import { z } from 'zod'

import { ApiError, type orderModel, type productModel } from '@/shared/api'
import { orderZod } from '@/shared/api-zod'
import { formatNumber } from '@/shared/lib'
import { Alert, Button, buttonClassName, Radio, RadioGroup, TextField, useToast } from '@/shared/ui'

import { useExportRoster } from '../model/use-export-roster'

const ALL = 'all'

// 생성 스키마의 max는 영어 기본 문구라 먼저 걸리면 영어가 보인다. 같은 규칙(필수 · 200자)을 한국어 문구로 쓴다
const reasonSchema = z
  .string()
  .trim()
  .min(1, '내려받는 사유를 입력해 주세요.')
  .max(orderZod.exportRosterBodyReasonMax, '사유는 200자까지 쓸 수 있어요.')

/** 서버가 요청을 거절한 경우(4xx). 다시 보내도 같으니 창 안에 이유를 보여 준다 */
function rejectedReason(error: unknown) {
  return error instanceof ApiError && error.status >= 400 && error.status < 500
    ? error.serverMessage || '요청을 처리하지 못했어요.'
    : undefined
}

type Race = Pick<productModel.SellerRaceDto, 'raceId' | 'courses'>

// 시안 2070:66928 명단 내려받기 창. 연락처가 담긴 파일이라 사유를 받고, 서버가 받은 기록을 남긴다.
// 공용 Dialog(폭 400 · 버튼 간격 24)로는 시안(폭 480 · 버튼 간격 8 · 빨간 안내)을 그릴 수 없어 Base UI 창을 직접 쓴다.
export function RosterExportButton({ race }: { race: Race }) {
  const [open, setOpen] = useState(false)
  const [scope, setScope] = useState(ALL)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string>()
  const exportRoster = useExportRoster()
  const showToast = useToast()

  const courses = race.courses ?? []
  const total = courses.reduce((sum, c) => sum + (c.sold ?? 0), 0)
  const choices = [
    { value: ALL, label: `참가 확정자 전체 ${formatNumber(total)}명` },
    ...courses.map((c) => ({
      value: String(c.raceCourseId),
      label: `${c.name ?? ''} 참가 확정자 ${formatNumber(c.sold ?? 0)}명`,
    })),
  ]

  function changeOpen(next: boolean) {
    // 받는 중에 닫아도 요청은 멈추지 않아 파일이 저장된다. 취소한 줄 알지 않게 닫지 못하게 한다
    if (!next && exportRoster.isPending) return
    setOpen(next)
    if (!next) {
      setScope(ALL)
      setReason('')
      setReasonError(undefined)
      exportRoster.reset()
    }
  }

  function run(body: orderModel.SellerRosterExportRequestDto) {
    exportRoster.mutate(body, {
      onSuccess: () => changeOpen(false),
      onError: (error) => {
        if (rejectedReason(error)) return
        // 서버 · 연결 실패는 시안 2072:83305처럼 창을 닫고 다시 받기 알림을 띄운다.
        // 공용 토스트는 행동을 눌러도 닫히지 않아서, 여러 번 눌러도 한 번만 다시 보낸다
        changeOpen(false)
        let retried = false
        showToast({
          message: '명단을 받지 못했어요',
          tone: 'error',
          action: {
            label: '다시 받기',
            onClick: () => {
              if (retried) return
              retried = true
              run(body)
            },
          },
        })
      },
    })
  }

  function submit() {
    const parsed = reasonSchema.safeParse(reason)
    if (!parsed.success) {
      setReasonError(parsed.error.issues[0]?.message)
      return
    }
    run({
      raceId: race.raceId ?? 0,
      raceCourseId: scope === ALL ? undefined : Number(scope),
      reason: parsed.data,
    })
  }

  return (
    <Dialog.Root open={open} onOpenChange={changeOpen}>
      <Dialog.Trigger className={buttonClassName({ variant: 'secondary' })}>
        명단 내려받기
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-bg-overlay/40 transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <Dialog.Popup className="fixed top-1/2 left-1/2 z-50 flex w-120 max-w-[calc(100vw-var(--layout-page-margin)*2)] -translate-1/2 flex-col gap-6 rounded-lg bg-bg-surface p-6 shadow-lg outline-none">
          <Dialog.Title className="text-center text-title3 text-text-primary">
            명단 내려받기
          </Dialog.Title>
          <div className="flex flex-col items-center gap-2">
            <span id="roster-scope" className="text-body1-medium text-text-primary">
              범위
            </span>
            <RadioGroup
              aria-labelledby="roster-scope"
              value={scope}
              onValueChange={(value) => setScope(String(value))}
              className="gap-0"
            >
              {choices.map((choice) => (
                <Radio
                  key={choice.value}
                  value={choice.value}
                  label={choice.label}
                  tone="inverse"
                  className="gap-3 py-2"
                />
              ))}
            </RadioGroup>
          </div>
          <TextField
            label={
              <>
                내려받는 사유 <span className="text-caption-medium text-text-danger">필수</span>
              </>
            }
            helper="내려받은 기록은 1년 보관돼요"
            value={reason}
            maxLength={orderZod.exportRosterBodyReasonMax}
            error={reasonError}
            onChange={(event) => {
              setReason(event.target.value)
              setReasonError(undefined)
            }}
          />
          {rejectedReason(exportRoster.error) && (
            <Alert
              tone="danger"
              title="내려받지 못했어요"
              description={rejectedReason(exportRoster.error)}
            />
          )}
          <p className="text-center text-body1-medium text-text-danger">
            참가자 정보는 대회가 끝나고 1년 뒤 파기해야 해요
          </p>
          <div className="flex gap-2">
            <Dialog.Close render={<Button variant="secondary" className="flex-1" />}>
              닫기
            </Dialog.Close>
            <Button className="flex-1" loading={exportRoster.isPending} onClick={submit}>
              엑셀로 내려받기
            </Button>
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
