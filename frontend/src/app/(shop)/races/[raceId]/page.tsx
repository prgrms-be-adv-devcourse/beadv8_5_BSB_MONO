import { RaceDetailPage } from '@/views/race-detail'

export default async function Page({ params }: PageProps<'/races/[raceId]'>) {
  const { raceId } = await params
  return <RaceDetailPage raceId={raceId} />
}
