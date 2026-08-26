import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StatusLabelPipe } from '../../pipes/status-label.pipe';
import { StatusClassPipe } from '../../pipes/status-class.pipe';

@Component({
  selector: 'helix-status-chip',
  standalone: true,
  imports: [CommonModule, StatusLabelPipe, StatusClassPipe],
  template: `<span class="status-chip" [ngClass]="status | statusClass">{{ status | statusLabel }}</span>`,
})
export class StatusChipComponent {
  @Input({ required: true }) status!: string;
}
