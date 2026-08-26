import { CommonModule } from '@angular/common';
import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Store } from '@ngrx/store';
import { WizardActions } from '../../../store/wizard/wizard.actions';
import { selectDocuments } from '../../../store/wizard/wizard.reducer';

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function iconFor(contentType: string): string {
  if (contentType.startsWith('image/')) return 'image';
  if (contentType === 'application/pdf') return 'picture_as_pdf';
  return 'insert_drive_file';
}

@Component({
  selector: 'app-wizard-step4-documents',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  templateUrl: './wizard-step4-documents.component.html',
  styleUrl: './wizard-step.shared.scss',
})
export class WizardStep4DocumentsComponent {
  private readonly store = inject(Store);
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  readonly documents = this.store.selectSignal(selectDocuments);
  readonly dragOver = signal(false);

  readonly formatBytes = formatBytes;
  readonly iconFor = iconFor;

  browse(): void {
    this.fileInput.nativeElement.click();
  }

  onFilesSelected(event: Event): void {
    const files = (event.target as HTMLInputElement).files;
    this.addFiles(files);
    (event.target as HTMLInputElement).value = '';
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    this.addFiles(event.dataTransfer?.files ?? null);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave(): void {
    this.dragOver.set(false);
  }

  removeDocument(tempId: string): void {
    this.store.dispatch(WizardActions.removeDocument({ tempId }));
  }

  private addFiles(files: FileList | null): void {
    if (!files) return;
    // Demo mode simulates the upload — only metadata is captured, no bytes leave the
    // browser, so this is safe to try with any real file on disk.
    Array.from(files).forEach((file) => {
      this.store.dispatch(
        WizardActions.addDocument({
          document: {
            fileName: file.name,
            contentType: file.type || 'application/octet-stream',
            sizeBytes: file.size,
          },
        }),
      );
    });
  }
}
