import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <main class="state-page" role="main">
      <h1>Page not found</h1>
      <p>The route you requested is unavailable.</p>
      <a class="state-link" routerLink="/login">Go to sign in</a>
    </main>
  `,
  styles: [`
    :host { display: block; padding: 2rem; }
    .state-page { max-width: 44rem; margin: 0 auto; padding: 2rem; background: #fff; border: 1px solid #dde2e8; }
    h1 { color: #006044; margin-bottom: 0.75rem; }
    .state-link { color: #006044; font-weight: 700; }
  `],
})
export class NotFoundComponent {}
