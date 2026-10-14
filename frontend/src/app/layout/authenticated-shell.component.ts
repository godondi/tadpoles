import { Component, computed, effect, inject, signal, DestroyRef } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TopNavComponent } from '../shared/top-nav/top-nav.component';
import { AuthService } from '../core/auth/auth.service';
import { WORKSPACES, routeRole } from '../core/auth/workspace-policy';
@Component({
  selector: 'app-authenticated-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TopNavComponent],
  template: ` @if (auth.isAuthenticated()) {
    <a class="skip-link" href="#workspace-content" (click)="skipToContent($event)"
      >Skip to page content</a
    ><app-top-nav />
    <div class="workspace-layout">
      <aside class="workspace-sidebar">
        <button
          class="navigation-toggle"
          (click)="menuOpen.set(!menuOpen())"
          [attr.aria-expanded]="menuOpen()"
          aria-controls="workspace-navigation"
        >
          Workspace menu
        </button>
        <nav
          id="workspace-navigation"
          aria-label="Workspace navigation"
          [class.mobile-open]="menuOpen()"
        >
          @for (workspace of workspaces(); track workspace.role) {
            <div class="workspace-group">
              <p>{{ workspace.label }}</p>
              @for (link of workspace.links; track link[1]) {
                <a
                  [routerLink]="link[1]"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: true }"
                  ariaCurrentWhenActive="page"
                  (click)="menuOpen.set(false)"
                  >{{ link[0] }}</a
                >
              }
            </div>
          }
        </nav>
        <p class="sidebar-note">Clear records.<br />Informed decisions.</p>
      </aside>
      <main id="workspace-content" class="workspace-content" tabindex="-1"><router-outlet /></main>
    </div>
  }`,
})
export class AuthenticatedShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly menuOpen = signal(false);
  readonly workspaces = computed(() =>
    WORKSPACES.filter((w) => this.auth.roles().includes(w.role)),
  );
  skipToContent(event: Event) {
    event.preventDefault();
    document.getElementById('workspace-content')?.focus();
  }
  constructor() {
    effect(() => {
      if (!this.auth.isAuthenticated()) void this.router.navigate(['/login']);
    });
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        setTimeout(() => {
          const heading = document.querySelector<HTMLElement>('#workspace-content h1');
          heading?.setAttribute('tabindex', '-1');
          heading?.focus();
        }, 0);
      });
  }
}
