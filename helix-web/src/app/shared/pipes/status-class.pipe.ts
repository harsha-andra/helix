import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'statusClass', standalone: true })
export class StatusClassPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '';
    return `status-chip--${value.toLowerCase().replace(/_/g, '-')}`;
  }
}
