/** 글자를 클립보드에 쓴다. 권한이 없거나 클립보드를 쓸 수 없으면 false */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
