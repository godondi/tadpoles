import { DisplayCodePipe } from './display-code.pipe';

describe('DisplayCodePipe', () => {
  const pipe = new DisplayCodePipe();

  it('renders enum codes in sentence case while retaining their values elsewhere', () => {
    expect(pipe.transform('AWAITING_REVIEW')).toBe('Awaiting review');
    expect(pipe.transform('CLIENT-7')).toBe('CLIENT-7');
    expect(pipe.transform('Current status')).toBe('Current status');
    expect(pipe.transform(null)).toBe('');
  });
});
