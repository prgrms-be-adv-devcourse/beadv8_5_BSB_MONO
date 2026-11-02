const HANGUL_START = 0xac00
const HANGUL_END = 0xd7a3
const JONGSEONG_COUNT = 28

// 한글이 아닌 끝 글자는 읽는 소리로 받침을 정한다. 0(영) 1(일) 3(삼) 6(육) 7(칠) 8(팔), L(엘) M(엠) N(엔) R(알)
const ENDS_WITH_CONSONANT = new Set(['0', '1', '3', '6', '7', '8', 'l', 'm', 'n', 'r'])

/** 닉네임 뒤에 붙는 주격 조사. 「달리는곰이」 · 「새벽러너가」 */
export function withSubjectParticle(word: string): string {
  const last = word.trim().slice(-1)
  const code = last.charCodeAt(0)
  const hasFinal =
    code >= HANGUL_START && code <= HANGUL_END
      ? (code - HANGUL_START) % JONGSEONG_COUNT !== 0
      : ENDS_WITH_CONSONANT.has(last.toLowerCase())
  return `${word}${hasFinal ? '이' : '가'}`
}
