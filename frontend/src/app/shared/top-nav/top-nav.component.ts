import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth/auth.service';
import { UserService } from '../../user.service';

@Component({
  selector: 'app-top-nav',
  templateUrl: './top-nav.component.html',
  styleUrl: './top-nav.component.css',
  host: {
    role: 'navigation',
    'aria-label': 'Primary navigation',
  },
})
export class TopNavComponent {
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  readonly currentUrl = signal(this.router.url);
  constructor() {
    this.router.events.pipe(takeUntilDestroyed()).subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.currentUrl.set(event.urlAfterRedirects);
        this.searchQuery.set('');
        this.closeProfileMenu();
      }
    });
  }
  readonly clientWorkspace = computed(
    () =>
      this.authService.roles().includes('CLIENT') &&
      !/^\/(operations|compliance|analytics|finance)/.test(this.currentUrl()),
  );
  readonly searchQuery = signal('');
  readonly isMenuOpen = signal(false);
  readonly userProfile = this.userService.getProfile();
  readonly userInitials = computed(() => {
    const name = (
      this.userProfile().displayName ||
      this.userProfile().clientName ||
      this.userProfile().email
    ).trim();

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
    if (!ticker) return;
    this.searchQuery.set('');
    this.closeProfileMenu();
    void this.router.navigate(['/trade', ticker]);
  }

  openDashboard(): void {
    if (this.router.url.split(/[?#]/)[0] === '/') return;
    this.closeProfileMenu();
    void this.router.navigate(['/']);
  }

  toggleProfileMenu(): void {
    this.isMenuOpen.update((open) => !open);
  }

  openProfile(): void {
    this.closeProfileMenu();
    if (!this.authService.hasCompletedOnboarding()) {
      void this.router.navigate(['/onboarding']);
      return;
    }
    void this.router.navigate(['/profile']);
  }

  openTrades(): void {
    this.closeProfileMenu();
    void this.router.navigate(['/trades']);
  }

  logout(): void {
    this.closeProfileMenu();
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  private closeProfileMenu(): void {
    this.isMenuOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  handleDocumentClick(event: MouseEvent): void {
    if (!this.isMenuOpen()) {
      return;
    }

    if (!this.elementRef.nativeElement.contains(event.target as Node)) {
      this.closeProfileMenu();
    }
  }

  @HostListener('document:keydown.escape')
  handleEscapeKey(): void {
    this.closeProfileMenu();
  }
}
