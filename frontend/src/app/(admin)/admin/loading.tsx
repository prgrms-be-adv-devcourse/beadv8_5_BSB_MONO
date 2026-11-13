import { ConsoleLoading, ConsolePage } from '@/widgets/console-page'

// 관리자 화면 로딩: 공통 뼈대(숫자 타일 + 줄)를 보여 준다
export default function Loading() {
  return (
    <ConsolePage title="불러오는 중">
      <ConsoleLoading />
    </ConsolePage>
  )
}
