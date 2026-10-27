import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'displayCode', standalone: true })
export class DisplayCodePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return value ?? '';
    if (!/^[A-Z0-9]+(?:_[A-Z0-9]+)+$/.test(value)) return value;
    const words = value.toLowerCase().replaceAll('_', ' ');
    return words[0].toUpperCase() + words.slice(1);
  }
}
