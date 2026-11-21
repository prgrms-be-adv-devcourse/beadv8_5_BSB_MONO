import { TaxInvoiceDetailPage } from '@/views/seller-settlements'

export default async function Page({
  params,
}: PageProps<'/seller/settlements/tax-invoices/[invoiceId]'>) {
  const { invoiceId } = await params
  return <TaxInvoiceDetailPage invoiceId={invoiceId} />
}
