'use client'

import { useRef, useState, type DragEvent } from 'react'

function hasFiles(event: DragEvent) {
  return event.dataTransfer.types.includes('Files')
}

// 파일을 끌어다 놓는 영역. 자식 요소를 지날 때 dragleave가 계속 생기므로 들어온 깊이를 센다.
// 파일이 아닌 끌기(순서 바꾸기 등)는 무시한다.
export function useFileDrop(onFiles: (files: File[]) => void) {
  const depth = useRef(0)
  const [over, setOver] = useState(false)

  const dropProps = {
    onDragEnter(event: DragEvent) {
      if (!hasFiles(event)) return
      event.preventDefault()
      depth.current += 1
      setOver(true)
    },
    onDragOver(event: DragEvent) {
      if (hasFiles(event)) event.preventDefault()
    },
    onDragLeave(event: DragEvent) {
      if (!hasFiles(event)) return
      depth.current = Math.max(0, depth.current - 1)
      if (depth.current === 0) setOver(false)
    },
    onDrop(event: DragEvent) {
      if (!hasFiles(event)) return
      event.preventDefault()
      depth.current = 0
      setOver(false)
      onFiles(Array.from(event.dataTransfer.files))
    },
  }

  return { over, dropProps }
}
