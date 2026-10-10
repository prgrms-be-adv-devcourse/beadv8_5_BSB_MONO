export function CrewJoinPage({ inviteCode }: { inviteCode: string }) {
  return (
    <main className="mx-auto w-full max-w-(--layout-content-max-width) px-4 py-8">
      <h1 className="text-heading-xl">크루 참가</h1>
      <p className="mt-2 text-text-secondary">
        초대 링크로 들어온 크루원이 종목과 정보를 입력하고 결제 (inviteCode: {inviteCode})
      </p>
    </main>
  )
}
