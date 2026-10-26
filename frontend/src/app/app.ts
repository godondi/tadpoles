import { Component, inject } from '@angular/core';
import { ThemeService } from './core/theme.service';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  imports: [RouterOutlet],
  host: {
    'role': 'application',
    'aria-label': 'Tadpoles trading application',
  },
})
export class App { private readonly theme = inject(ThemeService); }
