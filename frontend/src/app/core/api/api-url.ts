import { isPlatformBrowser } from '@angular/common';

export function buildApiUrl(path: string, platformId: object): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;

  if (isPlatformBrowser(platformId)) {
    const { protocol, hostname, origin, port } = window.location;
    if (port === '4200' || port === '4000') {
      return `${protocol}//${hostname}:8080${normalizedPath}`;
    }

    return `${origin}${normalizedPath}`;
  }

  return `http://localhost:8080${normalizedPath}`;
}

