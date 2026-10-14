import { RenderMode, ServerRoute } from '@angular/ssr';
// No identity or personal data is rendered into shared HTML or build artifacts.
export const serverRoutes: ServerRoute[] = [{ path: '**', renderMode: RenderMode.Client }];
