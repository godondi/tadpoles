import { Component, signal, inject, effect, computed } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { UserService } from './user.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
  imports: [RouterOutlet],
  host: {
    'role': 'application',
    'aria-label': 'Pole Trading Dashboard',
  },
})
export class App {
  private readonly userService = inject(UserService);
  private readonly router = inject(Router);

  readonly title = signal('Pole Trading');
  readonly showProfileMenu = signal(false);
  readonly searchQuery = signal('');
  readonly userProfile = this.userService.getProfile();
  readonly isLoginPage = computed(() => this.router.url === '/login');

  constructor() {
    effect(() => {
      this.showProfileMenu.set(false);
    });
  }

  toggleProfileMenu(): void {
    this.showProfileMenu.update(v => !v);
  }

  updateSearchQuery(value: string): void {
    this.searchQuery.set(value);
  }

  handleSearch(): void {
    const query = this.searchQuery();
    if (query) {
      alert(`Searching for: ${query}`);
    }
  }

  goToHome(): void {
    this.router.navigate(['']);
  }

  goToProfile(): void {
    this.toggleProfileMenu();
    this.router.navigate(['/profile']);
  }

  goToTrades(): void {
    this.router.navigate(['/trades']);
  }
}
