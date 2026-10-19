import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TopNavComponent } from '../shared/top-nav/top-nav.component';
import { AuthService } from '../core/auth/auth.service';
import { isInstrumentManagementRoute, WORKSPACES } from '../core/auth/workspace-policy';

@Component({
  selector: 'app-authenticated-shell',
  host: { '[style.--workspace-header-height.px]': 'headerHeight()' },
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TopNavComponent],
  template: `
    @if (auth.isAuthenticated()) {
      <a class="skip-link" href="#workspace-content" (click)="skipToContent($event)">
        Skip to page content
      </a>
      <app-top-nav #header />
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
                  >
                    {{ link[0] }}
                  </a>
                }
              </div>
            }
          </nav>
          <p class="sidebar-note">
            Clear records.
            <br />
            Informed decisions.
          </p>
        </aside>
        <main id="workspace-content" class="workspace-content" tabindex="-1">
          <router-outlet />
        </main>
      </div>
    }
  `,
})
export class AuthenticatedShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly menuOpen = signal(false);
  readonly headerHeight = signal(80);
  private readonly header = viewChild('header', { read: ElementRef });
  readonly workspaces = computed(() =>
    WORKSPACES.filter((w) => this.auth.roles().includes(w.role)).map((w) => ({
      ...w,
      links: w.links.filter(
        (link) => !isInstrumentManagementRoute(link[1]) || !this.auth.roles().includes('CLIENT'),
      ),
    })),
  );

  skipToContent(event: Event) {
    event.preventDefault();
    document.getElementById('workspace-content')?.focus();
  }

  constructor() {
    const destroy = inject(DestroyRef);
    afterNextRender(() => {
      const header = this.header()?.nativeElement;
      if (!header) return;
      const updateHeight = () => this.headerHeight.set(header.getBoundingClientRect().height);
      updateHeight();
      const observer = new ResizeObserver(updateHeight);
      observer.observe(header);
      destroy.onDestroy(() => observer.disconnect());
    });
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
          heading?.focus({ preventScroll: true });
        }, 0);
      });
  }
}
