import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  readonly dark = signal(false);

  constructor() {
    if (this.browser) {
      try { this.dark.set(localStorage.getItem('tadpoles-theme') === 'dark'); } catch { /* Storage may be disabled. */ }
    }
    this.apply();
  }

  toggle() {
    this.dark.update(value => !value);
    if (this.browser) {
      try { localStorage.setItem('tadpoles-theme', this.dark() ? 'dark' : 'light'); } catch { /* Keep the in-memory preference. */ }
    }
    this.apply();
  }

  private apply() {
    this.document.documentElement.dataset['theme'] = this.dark() ? 'dark' : 'light';
  }
}
