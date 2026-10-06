export function CrewJoinPage({ inviteCode }: { inviteCode: string }) {
  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-8">
      <h1 className="text-2xl font-bold">크루 참가</h1>
      <p className="mt-2 text-neutral-600">
        초대 링크로 들어온 크루원이 종목과 정보를 입력하고 결제 (inviteCode: {inviteCode})
      </p>
    </main>
  )
}
