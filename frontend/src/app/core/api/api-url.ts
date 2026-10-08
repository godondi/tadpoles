import { isPlatformBrowser } from '@angular/common';

export function buildApiUrl(path: string, platformId: object): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const backend_port = 8082;

  if (isPlatformBrowser(platformId)) {
    const { protocol, hostname, port } = window.location;
    // Route to backend for dev/container ports (4200, 4000, 8086, etc.)
    // Only use same-origin for production scenarios
    if (port === '4200' || port === '4000' || port === '8086' || port === '3000') {
      return `${protocol}//${hostname}:${backend_port}${normalizedPath}`;
    }

    // For production with reverse proxy or same-origin backend
    return `${protocol}//${hostname}:${backend_port}${normalizedPath}`;
  }

  return `http://localhost:${backend_port}${normalizedPath}`;
}

