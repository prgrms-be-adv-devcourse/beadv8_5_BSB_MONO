export type SaleAction = 'open' | 'stop' | 'resume'

export type SaleActionCopy = {
  action: SaleAction
  label: string
  confirmTitle: string
  confirmBody: string
  done: string
}

const COPY: Record<string, SaleActionCopy> = {
  SCHEDULED: {
    action: 'open',
    label: '지금 판매 시작',
    confirmTitle: '지금 판매를 시작할까요?',
    confirmBody: '판매 시작 일정이 지금으로 바뀌고 바로 예매를 받아요.',
    done: '판매를 시작했어요',
  },
  ON_SALE: {
    action: 'stop',
    label: '판매 멈추기',
    confirmTitle: '판매를 멈출까요?',
    confirmBody: '새 예매와 대기열 입장이 막혀요. 결제 대기 중인 자리는 결제할 수 있어요.',
    done: '판매를 멈췄어요',
  },
  STOPPED: {
    action: 'resume',
    label: '판매 다시 열기',
    confirmTitle: '판매를 다시 열까요?',
    confirmBody: '예매와 대기열 입장을 다시 받아요.',
    done: '판매를 다시 열었어요',
  },
}

/** 판매 상태에 맞는 버튼. 판매 종료 · 취소된 대회는 바꿀 수 없어 null이다. */
export function saleActionOf(status: string | undefined): SaleActionCopy | null {
  return (status && COPY[status]) || null
}
