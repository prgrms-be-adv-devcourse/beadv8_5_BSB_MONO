import { SettlementDetailPage } from '@/views/seller-settlements'

export default async function Page({ params }: PageProps<'/seller/settlements/[payoutCode]'>) {
  const { payoutCode } = await params
  return <SettlementDetailPage payoutCode={decodeURIComponent(payoutCode)} />
}
