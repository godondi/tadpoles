import { Component, ElementRef, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { UserService } from '../../user.service';

interface WorkspaceLink {
  label: string;
  route: string;
}

@Component({
  selector: 'app-top-nav',
  imports: [RouterLink],
  templateUrl: './top-nav.component.html',
  styleUrl: './top-nav.component.css',
  host: {
    role: 'navigation',
    'aria-label': 'Primary navigation',
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'onEscapeKey()',
  },
})
export class TopNavComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  readonly searchQuery = signal('');
  readonly isMenuOpen = signal(false);
  readonly userProfile = this.userService.getProfile();
  readonly workspaceLinks = computed<WorkspaceLink[]>(() => {
    const links: WorkspaceLink[] = [];
    const roles = this.authService.roles();
    if (roles.includes('CLIENT')) {
      links.push({ label: 'Overview', route: '/app/overview' });
      links.push({ label: 'Markets', route: '/app/markets' });
      links.push({ label: 'Portfolio', route: '/app/portfolio' });
      links.push({ label: 'Orders', route: '/app/orders' });
    }
    if (roles.includes('TRADING_OPS')) {
      links.push({ label: 'Operations', route: '/operations/orders' });
    }
    if (roles.includes('COMPLIANCE')) {
      links.push({ label: 'Compliance', route: '/compliance/investigations' });
    }
    if (roles.includes('ANALYST')) {
      links.push({ label: 'Analytics', route: '/analytics/overview' });
    }
    if (roles.includes('FINANCE')) {
      links.push({ label: 'Finance', route: '/finance/overview' });
    }
    return links;
  });
  readonly showSearch = computed(() => this.authService.roles().includes('CLIENT'));
  readonly userInitials = computed(() => {
    const name = (this.userProfile().displayName || this.userProfile().clientName || this.userProfile().email).trim();

    return name
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  });

  updateSearchQuery(value: string): void {
    this.searchQuery.set(value);
  }

  search(event?: Event): void {
    event?.preventDefault();
    const ticker = this.searchQuery().trim().toUpperCase();
    if (!ticker) {
      return;
    }
    this.searchQuery.set(ticker);
    this.closeProfileMenu();
    void this.router.navigate(['/trade', ticker]);
  }

  openDefaultWorkspace(): void {
    this.closeProfileMenu();
    void this.router.navigateByUrl(this.authService.resolveLandingRoute());
  }

  toggleProfileMenu(): void {
    this.isMenuOpen.update((open) => !open);
  }

  openProfile(): void {
    this.closeProfileMenu();
    if (!this.authService.hasCompletedOnboarding() && this.authService.roles().includes('CLIENT')) {
      void this.router.navigate(['/onboarding']);
      return;
    }
    void this.router.navigate(['/profile']);
  }

  logout(): void {
    this.closeProfileMenu();
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  navigateTo(route: string): void {
    this.closeProfileMenu();
    void this.router.navigateByUrl(route);
  }

  private closeProfileMenu(): void {
    this.isMenuOpen.set(false);
  }

  onDocumentClick(event: MouseEvent): void {
    if (!this.isMenuOpen()) {
      return;
    }

    if (!this.elementRef.nativeElement.contains(event.target as Node)) {
      this.closeProfileMenu();
    }
  }

  onEscapeKey(): void {
    this.closeProfileMenu();
  }
}
