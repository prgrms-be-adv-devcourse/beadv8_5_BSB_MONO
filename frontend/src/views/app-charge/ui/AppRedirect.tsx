'use client'

import { useEffect } from 'react'

/** 앱 주소(crewrun://)로 이동한다. 이동이 막힌 브라우저를 위해 링크도 함께 둔다 */
export function AppRedirect({ url }: { url: string }) {
  useEffect(() => {
    window.location.replace(url)
  }, [url])

  return (
    <p className="p-6 text-center">
      앱으로 돌아가는 중이에요
      <br />
      <a href={url} className="underline">
        앱으로 돌아가기
      </a>
    </p>
  )
}
