// 필수 입력 항목의 라벨 옆 「필수」 표시. 화면 낭독기는 읽지 않는다(칸 이름이 길어지지 않게)
export function RequiredMark() {
  return (
    <span aria-hidden className="ml-1 text-caption-medium text-text-danger">
      필수
    </span>
  )
}
