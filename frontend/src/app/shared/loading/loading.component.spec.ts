import { TestBed } from '@angular/core/testing';
import { LoadingComponent } from './loading.component';

describe('LoadingComponent', () => {
  it('renders a circular spinner', () => {
    const fixture = TestBed.createComponent(LoadingComponent);
    fixture.detectChanges();
    const spinner = fixture.nativeElement.querySelector('.spinner') as HTMLElement;
    const style = getComputedStyle(spinner);
    expect(style.borderRadius).toBe('50%');
    expect(style.width).toBe(style.height);
  });

  it('announces the loading label while keeping the decorative spinner hidden', () => {
    const fixture = TestBed.createComponent(LoadingComponent);
    fixture.componentRef.setInput('label', 'Loading quote…');
    fixture.detectChanges();
    const status = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(status.getAttribute('aria-live')).toBe('polite');
    expect(status.textContent).toContain('Loading quote…');
    expect(status.querySelector('.spinner')?.getAttribute('aria-hidden')).toBe('true');
  });
});
