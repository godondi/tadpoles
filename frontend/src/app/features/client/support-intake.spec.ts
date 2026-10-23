import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { SupportIntakeComponent } from './support-intake.component';
import { AuthService } from '../../core/auth/auth.service';
describe('versioned category intake', () => {
  it.each([
    ['ORDER_QUESTION', 'REJECTED', 'relatedOrderId', '12'],
    ['ACCOUNT_REVIEW', 'CASH_BALANCE', 'movementId', '11111111-1111-4111-8111-111111111111'],
    ['HISTORICAL_RECORD', 'PROVENANCE', 'exceptionId', '42'],
    ['TRADING_AVAILABILITY', 'CANNOT_SELL', 'instrumentId', '7'],
    ['OTHER', 'PRODUCT', 'affectedArea', 'Orders view'],
  ])(
    'retains reason-specific fields and untrusted entry hints for %s',
    (category, reason, field, hint) => {
      TestBed.configureTestingModule({
        imports: [SupportIntakeComponent],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: AuthService,
            useValue: { identity: signal({ userId: 17 }), sessionChanged: new Subject<void>() },
          },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({ [field]: hint, reason }) } },
          },
        ],
      });
      const f = TestBed.createComponent(SupportIntakeComponent);
      f.componentRef.setInput('category', category);
      const emitted: {
        enabled: boolean;
        valid: boolean;
        intake: Record<string, string | number>;
      }[] = [];
      f.componentInstance.changed.subscribe((value) => emitted.push(value));
      f.detectChanges();
      const http = TestBed.inject(HttpTestingController);
      http
        .expectOne((r) => r.url.endsWith('/intake-policy'))
        .flush({
          intakeEnabled: true,
          schemaVersion: 1,
          categories: [{ code: category, reasons: [reason] }],
        });
      f.detectChanges();
      expect(emitted.at(-1)?.valid).toBe(true);
      expect(emitted.at(-1)?.intake[field]).toEqual(
        ['exceptionId', 'relatedOrderId', 'instrumentId'].includes(field) ? Number(hint) : hint,
      );
      f.componentRef.setInput('disabled', true);
      f.detectChanges();
      expect(f.componentInstance.reason).toBe(reason);
      http.verify();
    },
  );
});
