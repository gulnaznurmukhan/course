import { describe, expect, it } from 'vitest'

import { formatHundredths, multiplyHalfUp, toHundredths } from './money'

describe('money preview helpers', () => {
  it('parses amounts exactly', () => {
    expect(toHundredths('0.1')).toBe(10n)
    expect(toHundredths('1 500,5')).toBe(150050n)
    expect(toHundredths('1.234')).toBeNull()
    expect(toHundredths('-1')).toBeNull()
  })

  it('rounds half up like the backend', () => {
    // 1.5 * 333.33 = 499.995 -> 500.00
    expect(formatHundredths(multiplyHalfUp(150n, 33333n))).toBe('500.00')
    // 0.1 + 0.2 stays exact
    expect(formatHundredths(toHundredths('0.1')! + toHundredths('0.2')!)).toBe('0.30')
  })
})
