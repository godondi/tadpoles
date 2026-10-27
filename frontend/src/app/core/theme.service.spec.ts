import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('persistent application theme', () => {
  afterEach(() => { localStorage.removeItem('tadpoles-theme'); document.documentElement.removeAttribute('data-theme'); });

  it('initializes the saved theme before any account menu is rendered', () => {
    localStorage.setItem('tadpoles-theme', 'dark');
    const theme = TestBed.inject(ThemeService);
    expect(theme.dark()).toBe(true);
    expect(document.documentElement.dataset['theme']).toBe('dark');
    theme.toggle();
    expect(document.documentElement.dataset['theme']).toBe('light');
    expect(localStorage.getItem('tadpoles-theme')).toBe('light');
  });
});
