import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard';

describe('Dashboard placeholder disclosure', () => {
  it('labels the portfolio overview as a placeholder in parentheses', () => {
    // Server rendering skips canvas chart setup; this verifies the visible heading.
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: PLATFORM_ID, useValue: 'server' }],
    });
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toBe('Portfolio overview (placeholder)');
  });
});
