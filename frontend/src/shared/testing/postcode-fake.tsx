import { act, useEffect } from 'react'

// 카카오 우편번호 창 대신 쓰는 가짜. 테스트 파일에서 next/script를 FakeScript로 바꿔 쓴다:
// vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

/** 카카오 우편번호 oncomplete가 넘기는 값 중 화면이 읽는 것. 없는 값은 빈 글자로 온다. */
export type FakePostcodeData = {
  zonecode: string
  roadAddress: string
  jibunAddress: string
  autoRoadAddress: string
  userSelectedType: 'R' | 'J'
  sido: string
  sigungu: string
}

type PostcodeOptions = { oncomplete: (data: FakePostcodeData) => void; [key: string]: unknown }

export const postcodeFake = {
  /** true면 스크립트를 못 불러온 것처럼 onError를 부른다 */
  failLoad: false,
  /** 마지막으로 만든 창에 넘긴 설정 */
  options: undefined as PostcodeOptions | undefined,
  /** 창을 붙인 칸 */
  embeddedIn: undefined as HTMLElement | undefined,
  /** 창을 붙인 횟수 */
  embedCount: 0,
}

export function installPostcodeFake() {
  postcodeFake.failLoad = false
  postcodeFake.options = undefined
  postcodeFake.embeddedIn = undefined
  postcodeFake.embedCount = 0
  scriptState = 'none'
  Object.assign(window, {
    kakao: {
      Postcode: class {
        constructor(options: PostcodeOptions) {
          postcodeFake.options = options
        }
        embed(element: HTMLElement) {
          postcodeFake.embeddedIn = element
          postcodeFake.embedCount += 1
        }
      },
    },
  })
}

/** 창에서 주소 하나를 고른다. 주지 않은 값은 서울숲 도로명 주소로 채운다. */
export function completePostcode(data: Partial<FakePostcodeData> = {}) {
  const options = postcodeFake.options
  if (!options) throw new Error('우편번호 창이 열리지 않았어요')
  act(() =>
    options.oncomplete({
      zonecode: '04768',
      roadAddress: '서울특별시 성동구 뚝섬로 273',
      jibunAddress: '서울특별시 성동구 성수동1가 678-1',
      autoRoadAddress: '',
      userSelectedType: 'R',
      sido: '서울특별시',
      sigungu: '성동구',
      ...data,
    }),
  )
}

// 진짜 next/script는 같은 주소를 한 번만 불러오고 결과를 기억한다 (installPostcodeFake가 비운다)
let scriptState: 'none' | 'loaded' | 'failed' = 'none'

/**
 * next/script 자리. 진짜와 같이 처음 붙을 때만 성공(onLoad·onReady)이나 실패(onError)를 알린다.
 * 다시 붙으면 성공했던 주소는 onReady만, 실패했던 주소는 onLoad만 부른다(진짜가 실패를 삼키기 때문).
 */
export function FakeScript({
  onLoad,
  onReady,
  onError,
}: {
  onLoad?: () => void
  onReady?: () => void
  onError?: (e: unknown) => void
}) {
  useEffect(() => {
    if (scriptState === 'loaded') onReady?.()
    else if (scriptState === 'failed') onLoad?.()
    else if (postcodeFake.failLoad) {
      scriptState = 'failed'
      onError?.(new Event('error'))
    } else {
      scriptState = 'loaded'
      onLoad?.()
      onReady?.()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return null
}
