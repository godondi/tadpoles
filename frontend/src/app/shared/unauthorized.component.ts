import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-unauthorized',
  imports: [RouterLink],
  template: `
    <main class="state-page" role="main">
      <h1>Access denied</h1>
      <p>You are signed in, but this workspace is not assigned to your account.</p>
      <a class="state-link" [routerLink]="landingRoute()">Go to my workspace</a>
      <a class="state-link secondary" routerLink="/login">Sign in with a different account</a>
    </main>
  `,
  styles: [`
    :host { display: block; padding: 2rem; }
    .state-page { max-width: 44rem; margin: 0 auto; padding: 2rem; background: #fff; border: 1px solid #dde2e8; }
    h1 { color: #006044; margin-bottom: 0.75rem; }
    .state-link { display: inline-block; margin-right: 1rem; color: #006044; font-weight: 700; }
    .state-link.secondary { color: #AF8A49; }
  `],
})
export class UnauthorizedComponent {
  private readonly authService = inject(AuthService);

  landingRoute(): string {
    return this.authService.resolveLandingRoute();
  }
}
