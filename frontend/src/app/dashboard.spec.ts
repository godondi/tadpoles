import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard';

describe('Dashboard placeholder disclosure', () => {
  it('renders the client overview heading', () => {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideRouter([]), { provide: PLATFORM_ID, useValue: 'server' }],
    });
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toBe('Client Overview');
  });
});
