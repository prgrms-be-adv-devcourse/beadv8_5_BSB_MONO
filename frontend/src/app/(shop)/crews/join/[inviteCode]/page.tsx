import { CrewJoinPage } from '@/views/crew-join'

export default async function Page({ params }: PageProps<'/crews/join/[inviteCode]'>) {
  const { inviteCode } = await params
  return <CrewJoinPage inviteCode={inviteCode} />
}
