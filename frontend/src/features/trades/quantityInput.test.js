import { describe, expect, it } from 'vitest'
import { isPositiveQuantity, normalizeQuantityInput } from './quantityInput'

describe('trade quantity input', () => {
  it('allows an empty value while editing', () => {
    expect(normalizeQuantityInput('')).toBe('')
  })

  it('normalizes comma decimals and leading decimal separators', () => {
    expect(normalizeQuantityInput('0,01')).toBe('0.01')
    expect(normalizeQuantityInput(',5')).toBe('0.5')
  })

  it('accepts at most eight decimal places', () => {
    expect(normalizeQuantityInput('1.12345678')).toBe('1.12345678')
    expect(normalizeQuantityInput('1.123456789')).toBeNull()
  })

  it('rejects negative, alphabetic, and multiple-separator input', () => {
    expect(normalizeQuantityInput('-1')).toBeNull()
    expect(normalizeQuantityInput('1a')).toBeNull()
    expect(normalizeQuantityInput('1.2.3')).toBeNull()
  })

  it('requires a positive value at submit time', () => {
    expect(isPositiveQuantity('')).toBe(false)
    expect(isPositiveQuantity('0')).toBe(false)
    expect(isPositiveQuantity('0.00000001')).toBe(true)
  })
})
