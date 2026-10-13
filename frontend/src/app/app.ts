import { Component } from '@angular/core';
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
export class App {}
