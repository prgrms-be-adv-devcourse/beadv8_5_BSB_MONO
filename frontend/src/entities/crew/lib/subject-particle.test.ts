import { describe, expect, it } from 'vitest'

import { withObjectParticle, withSubjectParticle } from './subject-particle'

describe('withSubjectParticle', () => {
  it.each([
    ['달리는곰', '달리는곰이'],
    ['새벽러너', '새벽러너가'],
    ['주말10K', '주말10K가'],
    ['러너1', '러너1이'],
    ['Run', 'Run이'],
  ])('%s → %s', (word, expected) => {
    expect(withSubjectParticle(word)).toBe(expected)
  })
})

describe('withObjectParticle', () => {
  it.each([
    ['성수런', '성수런을'],
    ['새벽한강크루', '새벽한강크루를'],
    ['러너1', '러너1을'],
    ['10K', '10K를'],
  ])('%s → %s', (word, expected) => {
    expect(withObjectParticle(word)).toBe(expected)
  })
})
