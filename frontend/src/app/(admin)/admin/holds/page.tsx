import { redirect } from 'next/navigation'

// 지급보류는 정산 관리 안의 「지급보류」 칩으로 합쳤다
export default function Page() {
  redirect('/admin/settlements?tab=지급보류')
}
