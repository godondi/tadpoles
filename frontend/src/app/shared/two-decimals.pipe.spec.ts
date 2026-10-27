import { displayFact, TwoDecimalsPipe, twoDecimals } from './two-decimals.pipe';

describe('two decimal display', () => {
  it.each([
    ['1000', '1,000.00'], ['1.005', '1.01'], ['-1.005', '-1.01'],
    ['1.999', '2.00'], ['0.000042', '0.00'], ['-0.0001', '0.00'],
    ['9007199254740993.025', '9,007,199,254,740,993.03'], [1e-7, '0.00'],
    ['1.234e3', '1,234.00'], ['1e21', '1,000,000,000,000,000,000,000.00'],
    [null, '—'], [undefined, '—'], ['Unavailable', 'Unavailable'],
  ])('formats %s as %s', (input, expected) => {
    expect(new TwoDecimalsPipe().transform(input)).toBe(expected);
  });

  it('does not change the original financial values', () => {
    const model = { quantity: '0.12345678', amount: '9007199254740993.025' };
    expect(twoDecimals(model.quantity)).toBe('0.12');
    expect(model).toEqual({ quantity: '0.12345678', amount: '9007199254740993.025' });
  });

  it('formats nested evidence amounts and preserves references and integer counts', () => {
    const record = { instrumentId: 42, count: 3, reference: '1.10', policyVersion: '1.2',
      snapshot: { price: '123.45678', availableCash: '1000', quantity: '0.12345678', ratio: 1.23456 }, absent: null };
    expect(displayFact(record, '')).toEqual({ instrumentId: 42, count: 3, reference: '1.10', policyVersion: '1.2',
      snapshot: { price: '123.46', availableCash: '1,000.00', quantity: '0.12', ratio: '1.23' }, absent: null });
    expect(record.snapshot.price).toBe('123.45678');
  });
});
