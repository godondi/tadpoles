import { TestBed } from '@angular/core/testing';
import { ClientRecommendationsComponent } from './client-recommendations.component';

describe('ClientRecommendationsComponent', () => {
  it('omits exact positive holdings but keeps zero holdings and escapes rationale', () => {
    const fixture = TestBed.createComponent(ClientRecommendationsComponent);
    fixture.componentRef.setInput('positions', [
      { instrumentId: 1, owned: '0.00000000000000000001' },
      { instrumentId: 2, owned: '0.000' },
    ]);
    fixture.componentRef.setInput('advisory', {
      guidance: [], source: 'test', asOf: '2026-10-22T18:00:00Z',
      recommendations: [1, 2, 3].map((id) => ({ recommendationId: id, instrumentId: id, ticker: `T${id}`, instrumentName: `Instrument ${id}`, reasoning: '<script>not executable</script>', version: 1, updatedAt: '2026-10-22T18:00:00Z' })),
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Instrument 1');
    expect(fixture.nativeElement.textContent).toContain('Instrument 2');
    expect(fixture.nativeElement.textContent).toContain('Instrument 3');
    expect(fixture.nativeElement.querySelector('script')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('<script>not executable</script>');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});