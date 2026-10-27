import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { signal } from '@angular/core';
import { ComplianceApi } from './compliance-api.service';
import { ComplianceInvestigationsPageComponent } from './investigations-page.component';

describe('Compliance investigations workspace', () => {
  beforeEach(() => TestBed.resetTestingModule());
  afterEach(() => TestBed.resetTestingModule());

  it('renders a dedicated purpose-aware investigation screen', () => {
    const api = {
      context: signal(null),
      changed: new Subject<void>(),
      contextMessage: signal(''),
      policy: () =>
        of({
          configured: false,
          purposes: [],
          scopeLabel: 'No approved scope',
          policyVersion: 'unavailable',
        }),
      createContext: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [ComplianceInvestigationsPageComponent],
      providers: [provideRouter([]), { provide: ComplianceApi, useValue: api }],
    });

    const fixture = TestBed.createComponent(ComplianceInvestigationsPageComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Investigations');
    expect(fixture.nativeElement.textContent).toContain('Access purpose');
    expect(fixture.nativeElement.textContent).toContain('approved account scope');
  });
});
