import { isPlatformBrowser } from '@angular/common';

export function buildApiUrl(path: string, platformId: object): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;

  if (isPlatformBrowser(platformId)) {
    return normalizedPath;
  }

  return `http://backend:8080${normalizedPath}`;
}

