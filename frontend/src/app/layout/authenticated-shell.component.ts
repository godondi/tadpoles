import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TopNavComponent } from '../shared/top-nav/top-nav.component';

@Component({
  selector: 'app-authenticated-shell',
  imports: [RouterOutlet, TopNavComponent],
  template: `
    <app-top-nav></app-top-nav>
    <router-outlet></router-outlet>
  `,
  host: {
    role: 'application',
    'aria-label': 'Tadpoles trading workspace',
  },
})
export class AuthenticatedShellComponent {}


