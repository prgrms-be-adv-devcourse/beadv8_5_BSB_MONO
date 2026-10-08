export function RaceDetailPage({ raceId }: { raceId: string }) {
  return (
    <main className="mx-auto w-full max-w-(--layout-content-max-width) px-4 py-8">
      <h1 className="text-heading-xl">대회 상세</h1>
      <p className="mt-2 text-text-secondary">종목·참가권·패키지 (raceId: {raceId})</p>
    </main>
  )
}
