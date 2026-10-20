import { isPlatformBrowser } from '@angular/common';

export function buildApiUrl(path: string, platformId: object): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;

  if (isPlatformBrowser(platformId)) {
    return normalizedPath;
  }

  return `http://backend:8080${normalizedPath}`;
}

export function buildAnalyticsApiUrl(path: string, platformId: object): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const analyticsPort = 8090;
  if (isPlatformBrowser(platformId)) {
    const { protocol, hostname } = window.location;
    return `${protocol}//${hostname}:${analyticsPort}${normalizedPath}`;
  }
  return `http://localhost:${analyticsPort}${normalizedPath}`;
}

