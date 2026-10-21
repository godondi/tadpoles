import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { PageResource } from './page-resource';
it('clears protected cached data on denied access even during a background refresh', () => {
  TestBed.configureTestingModule({
    providers: [{ provide: AuthService, useValue: { sessionChanged: new Subject<void>() } }],
  });
  const resource = TestBed.runInInjectionContext(() => new PageResource<{ subject: string }>());
  resource.load(of({ subject: 'Owned record' }));
  resource.load(
    throwError(() => new HttpErrorResponse({ status: 403 })),
    true,
  );
  expect(resource.data()).toBeNull();
  expect(resource.status()).toBe(403);
});
