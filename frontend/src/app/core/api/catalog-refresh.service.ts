import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { EMPTY, filter, fromEvent, merge, takeUntil, timer } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/** Refresh visible catalogs across accounts without navigating or clearing their rows. */
@Injectable({ providedIn: 'root' })
export class CatalogRefreshService {
  private readonly document = inject(DOCUMENT);
  private readonly platform = inject(PLATFORM_ID);
  private readonly auth = inject(AuthService);

  watch() {
    const window = this.document.defaultView;
    if (!isPlatformBrowser(this.platform) || !window) return EMPTY;
    return merge(
      timer(15000, 15000),
      fromEvent(window, 'focus'),
      fromEvent(this.document, 'visibilitychange'),
    ).pipe(
      filter(() => this.document.visibilityState !== 'hidden'),
      takeUntil(this.auth.sessionChanged),
    );
  }
}
