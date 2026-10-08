import { isPlatformBrowser } from '@angular/common';

export function buildApiUrl(path: string, platformId: object): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const backend_port = 8082;

  if (isPlatformBrowser(platformId)) {
    const { protocol, hostname, origin, port } = window.location;
    if (port === '4200' || port === '4000') {
      return `${protocol}//${hostname}:${backend_port}${normalizedPath}`;
    }

    return `${origin}${normalizedPath}`;
  }

  return `http://localhost:${backend_port}${normalizedPath}`;
}

