import { hasPositiveDecimal, validReasoning } from './advisor-utils';

describe('Advisor eligibility helpers', () => {
  it.each([
    ['0', false],
    ['0.000', false],
    ['0.25', true],
    ['2', true],
    ['9007199254740993.01', true],
    [' 1.0 ', true],
  ])('checks decimal ownership without binary floating point: %s', (value, expected) => {
    expect(hasPositiveDecimal(value)).toBe(expected);
  });

  it('bounds trimmed recommendation reasoning', () => {
    expect(validReasoning('  Review diversification.  ')).toBe(true);
    expect(validReasoning('   ')).toBe(false);
    expect(validReasoning('x'.repeat(2001))).toBe(false);
  });
});
