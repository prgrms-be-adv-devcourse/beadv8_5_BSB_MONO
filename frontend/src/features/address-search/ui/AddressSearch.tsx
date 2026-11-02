'use client'

import Script from 'next/script'
import { useEffect, useRef, useState } from 'react'

import { Button } from '@/shared/ui'

// 카카오 우편번호 서비스(무료, 키 없음). 파일을 내려받아 두지 않고 카카오 주소에서 그대로 불러야 한다.
// 안내: https://postcode.map.kakao.com/guide
const POSTCODE_SCRIPT = 'https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'

export const ADDRESS_SEARCH_UNAVAILABLE =
  '주소 검색을 불러오지 못했어요. 주소를 직접 입력해 주세요.'

export type AddressSearchResult = {
  zipcode: string
  /** 도로명 주소 */
  address: string
  /** 줄이지 않은 시·도 이름 (예: 서울특별시) */
  sido: string
  /** 시·군·구. 세종특별자치시처럼 없는 곳은 빈 글자 */
  sigungu: string
}

/** oncomplete 값 중 쓰는 것. 없는 값은 빈 글자로 온다. */
type PostcodeData = {
  zonecode: string
  roadAddress: string
  autoRoadAddress: string
  jibunAddress: string
  sido: string
  sigungu: string
}

type PostcodeWindow = {
  kakao?: {
    Postcode: new (options: {
      oncomplete: (data: PostcodeData) => void
      shorthand: boolean
      width: string
      height: string
    }) => { embed: (element: HTMLElement) => void }
  }
}

// 「주소 검색」을 누르면 카카오 창을 이 자리(화면 안)에 붙인다. 팝업이 아니라 600 기둥 안에서 열린다.
// 지번을 골라도 배송에 쓰는 도로명 주소를 넘긴다. 도로명이 여럿이라 비면 카카오가 이어 준 것, 그것도 없으면 지번.
export function AddressSearch({
  onSelect,
  onUnavailable,
}: {
  onSelect: (result: AddressSearchResult) => void
  /** 스크립트를 못 불러왔을 때. 화면은 주소 칸을 직접 입력할 수 있게 연다. */
  onUnavailable?: () => void
}) {
  const [open, setOpen] = useState(false)
  const [failed, setFailed] = useState(false)
  const panel = useRef<HTMLDivElement>(null)
  // 창은 연 순간의 콜백을 기억하므로, 연 뒤 화면 상태(제출 여부 등)가 바뀌어도 최신 콜백을 부르게 한다
  const select = useRef(onSelect)
  useEffect(() => {
    select.current = onSelect
  })

  const embed = () => {
    if (!panel.current) return
    const { kakao } = window as unknown as PostcodeWindow
    // 광고 차단 등으로 스크립트가 비어 있으면 불러오기 실패와 같이 다룬다
    if (!kakao?.Postcode) return fail()
    new kakao.Postcode({
      // 「서울」이 아니라 「서울특별시」로 받아야 대회 찾기 지역 필터와 글자가 같다
      shorthand: false,
      width: '100%',
      height: '100%',
      oncomplete: (data) => {
        setOpen(false)
        select.current({
          zipcode: data.zonecode,
          address: data.roadAddress || data.autoRoadAddress || data.jibunAddress,
          sido: data.sido,
          sigungu: data.sigungu,
        })
      },
    }).embed(panel.current)
  }

  const fail = () => {
    setOpen(false)
    setFailed(true)
    onUnavailable?.()
  }

  return (
    <div className="flex flex-col gap-2">
      {/* 실패한 스크립트는 next/script가 성공으로 기억해 다시 열면 빈 창이 되므로, 실패하면 버튼을 숨긴다 */}
      {failed ? (
        <p role="alert" className="text-caption text-text-danger">
          {ADDRESS_SEARCH_UNAVAILABLE}
        </p>
      ) : (
        <Button
          type="button"
          variant="secondary"
          size="m"
          className="self-start"
          onClick={() => setOpen(true)}
        >
          주소 검색
        </Button>
      )}
      {open && (
        <div className="flex flex-col gap-2">
          <Script src={POSTCODE_SCRIPT} onReady={embed} onError={fail} />
          {/* 카카오 창의 최소 높이는 400. 아래 로고가 가려지지 않게 넉넉히 둔다 */}
          <div
            ref={panel}
            className="h-115 w-full overflow-hidden rounded-md inset-ring inset-ring-border-default"
          />
          <Button
            type="button"
            variant="ghost"
            size="s"
            className="self-end"
            aria-label="주소 검색 닫기"
            onClick={() => setOpen(false)}
          >
            닫기
          </Button>
        </div>
      )}
    </div>
  )
}
