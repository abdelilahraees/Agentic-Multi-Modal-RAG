import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService, errorMessage } from '../core/api.service';
import { DocumentSummary } from '../core/models';
import { SessionService } from '../core/session.service';

const KIND_ICONS: Record<string, string> = { pdf: '📄', image: '🖼', text: '📝' };

@Component({
  selector: 'app-documents-panel',
  imports: [DatePipe],
  template: `
    <div class="panel-header">
      <h2>Documents</h2>
      <button type="button" class="btn ghost small" (click)="refresh()" aria-label="Rafraîchir">↻</button>
    </div>

    <label class="dropzone" [class.busy]="uploading()">
      <input type="file" hidden multiple accept=".pdf,.txt,.md,.csv,.json,.xml,.html,image/*"
             (change)="onFiles($event)" [disabled]="uploading()" />
      @if (uploading()) {
        <span>Indexation en cours…</span>
      } @else {
        <span><strong>Importer</strong> PDF, texte ou images</span>
      }
    </label>

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <ul class="list">
      @for (d of documents(); track d.documentId) {
        <li>
          <span class="icon">{{ icon(d.kind) }}</span>
          <div class="meta">
            <span class="name" [title]="d.source">{{ d.source }}</span>
            <span class="muted small">{{ d.segments }} segment(s) · {{ d.ingestedAt | date: 'short' }}</span>
          </div>
          <button type="button" class="btn ghost small" (click)="remove(d)" [attr.aria-label]="'Supprimer ' + d.source">✕</button>
        </li>
      } @empty {
        <li class="muted small">Aucun document indexé.</li>
      }
    </ul>
  `,
})
export class DocumentsPanel implements OnInit {
  private readonly api = inject(ApiService);
  private readonly session = inject(SessionService);

  protected readonly documents = signal<DocumentSummary[]>([]);
  protected readonly uploading = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.api.listDocuments().subscribe({
      next: (docs) => this.documents.set(docs),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected async onFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) return;

    this.uploading.set(true);
    this.error.set(null);
    const failures: string[] = [];
    for (const file of files) {
      try {
        await new Promise<void>((resolve, reject) =>
          this.api.uploadDocument(file, this.session.userId()).subscribe({
            next: () => resolve(),
            error: reject,
          }),
        );
      } catch (err) {
        failures.push(`${file.name} : ${errorMessage(err)}`);
      }
    }
    this.uploading.set(false);
    if (failures.length) this.error.set(failures.join('\n'));
    this.refresh();
  }

  protected remove(doc: DocumentSummary): void {
    if (!confirm(`Supprimer « ${doc.source} » de la base documentaire ?`)) return;
    this.api.deleteDocument(doc.documentId).subscribe({
      next: () => this.documents.update((list) => list.filter((d) => d.documentId !== doc.documentId)),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected icon(kind: string): string {
    return KIND_ICONS[kind] ?? '📎';
  }
}
