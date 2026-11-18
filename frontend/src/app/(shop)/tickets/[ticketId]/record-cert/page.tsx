import { RecordCertPage } from '@/views/ticket-detail'

export default async function Page({ params }: PageProps<'/tickets/[ticketId]/record-cert'>) {
  const { ticketId } = await params
  return <RecordCertPage ticketId={ticketId} />
}
