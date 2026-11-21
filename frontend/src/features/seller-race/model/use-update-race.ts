import { useMutation, useQueryClient } from '@tanstack/react-query'

import { fileApi, productApi, productModel, unwrapData } from '@/shared/api'

const LINK_POLL_MS = 500
const LINK_POLL_TRIES = 10

/**
 * 이미지 연결은 메시지로 file 서비스에 넘어가 저장 응답보다 1초 넘게 늦게 반영된다.
 * 바로 다시 받으면 옛 이미지가 캐시에 남으므로, 보낸 이미지가 모두 연결될 때까지 기다린다.
 * shortcut: 5초만 기다리고 그래도 안 보이면 그냥 진행한다 (저장은 이미 끝났다). 더 걸리면 서버가 연결 완료를 알려 주게 바꾼다.
 */
async function waitForImageLink(raceId: number, request: productModel.RaceSaveRequestDto) {
  const targets = [
    { refType: 'Race', refId: raceId, images: request.images ?? [] },
    ...(request.courses ?? []).flatMap((c) =>
      c.courseId === undefined
        ? []
        : [{ refType: 'RaceCourse', refId: c.courseId, images: c.images ?? [] }],
    ),
  ].filter((t) => t.images.length > 0)
  for (let i = 0; i < LINK_POLL_TRIES; i++) {
    // 파일 목록을 못 받아도 저장은 끝났으므로 오류로 만들지 않고 진행한다
    const linked = await Promise.all(
      targets.map(async ({ refType, refId, images }) => {
        const files = unwrapData(await fileApi.findByRef({ refType, refId }))
        return images.every((image) => files.some((file) => file.id === image.fileId))
      }),
    ).catch(() => [true])
    if (linked.every(Boolean)) return
    await new Promise((resolve) => setTimeout(resolve, LINK_POLL_MS))
  }
}

/** 대회 수정 (대회 전체를 보낸다). 성공하면 이 브라우저의 내 대회 목록, 이 대회 상세(수정용 · 장바구니 · 결제 화면용), 대표 이미지를 새로 받는다. */
export function useUpdateRace(raceId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: productModel.RaceSaveRequestDto) => {
      const saved = unwrapData(await productApi.update(raceId, request))
      await waitForImageLink(raceId, request)
      return saved
    },
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: productApi.getFindMineQueryKey() }),
        queryClient.invalidateQueries({ queryKey: productApi.getFindRaceQueryKey(raceId) }),
        queryClient.invalidateQueries({
          queryKey: fileApi.getFindByRefQueryKey({ refType: 'Race', refId: raceId }),
        }),
      ]),
  })
}
