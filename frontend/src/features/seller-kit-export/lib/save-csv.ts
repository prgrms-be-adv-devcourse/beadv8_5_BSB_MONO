const BOM = '﻿'

/**
 * 받은 CSV 글을 파일로 저장한다.
 * 응답을 글로 읽으면 맨 앞 BOM이 빠지는데, BOM이 없으면 엑셀이 한글을 깨뜨려서 다시 붙인다.
 */
export function saveCsv(text: string, fileName: string) {
  const blob = new Blob([text.startsWith(BOM) ? text : BOM + text], {
    type: 'text/csv;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  // 문서에 붙여야 옛 Firefox도 받고, 주소를 바로 지우면 Safari가 받기 전에 파일이 사라진다
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** 서버가 붙이는 이름과 같은 꼴: kit-shipments-<대회 ID 또는 all>-<날짜>.csv */
export function kitExportFileName(raceId: number | undefined, now: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  const day = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
  return `kit-shipments-${raceId ?? 'all'}-${day}.csv`
}
