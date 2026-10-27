import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
@Component({
  selector: 'app-message-page',
  imports: [RouterLink],
  template: ` <main class="standalone-page">
    <a class="brand-link" routerLink="/">Tadpoles</a>
    <section class="panel">
      <h1 tabindex="-1">{{ title }}</h1>
      <p>{{ message }}</p>
      @if (retryable) {
        <button (click)="retry()" [disabled]="busy()">
          {{ busy() ? 'Checking…' : 'Try again' }}
        </button>
      }
      <a [routerLink]="auth.isAuthenticated() ? auth.home() : '/login'">{{
        auth.isAuthenticated() ? 'Return to your workspace' : 'Sign in'
      }}</a>
      @if (auth.isAuthenticated()) {
        <button (click)="signOut()">Sign out</button>
      }
    </section>
  </main>`,
})
export class MessagePageComponent {
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly title = this.route.snapshot.data['title'];
  readonly message = this.route.snapshot.data['message'];
  readonly retryable = this.route.snapshot.data['retryable'];
  readonly busy = signal(false);
  async retry() {
    this.busy.set(true);
    if (await this.auth.ensureInitialized())
      await this.router.navigateByUrl(
        this.auth.returnUrl(this.route.snapshot.queryParamMap.get('redirectTo')),
      );
    this.busy.set(false);
  }
  signOut() {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
