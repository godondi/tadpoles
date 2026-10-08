import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
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
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  readonly searchQuery = signal('');
  readonly isMenuOpen = signal(false);
  readonly userProfile = this.userService.getProfile();
  readonly userInitials = computed(() => {
    const name = this.userProfile().name.trim();

    return name
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  });

  updateSearchQuery(value: string): void {
    this.searchQuery.set(value);
  }

  preventSearch(event?: Event): void {
    event?.preventDefault();
  }

  toggleProfileMenu(): void {
    this.isMenuOpen.update((open) => !open);
  }

  openProfile(): void {
    this.closeProfileMenu();
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

