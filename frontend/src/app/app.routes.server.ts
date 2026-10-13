import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  { path: 'login', renderMode: RenderMode.Prerender },
  { path: 'signup', renderMode: RenderMode.Prerender },
  { path: 'register', renderMode: RenderMode.Prerender },
  { path: 'unauthorized', renderMode: RenderMode.Prerender },
  { path: 'not-found', renderMode: RenderMode.Prerender },
  { path: '**', renderMode: RenderMode.Client },
];
