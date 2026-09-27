import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { ChatMessage } from '../core/models';
import { SessionService } from '../core/session.service';

const MUTATING_TOOLS = ['createTransaction'];

@Component({
  selector: 'app-chat',
  imports: [FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class Chat {
  private readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly draft = signal('');
  protected readonly image = signal<File | null>(null);
  protected readonly imagePreview = signal<string | null>(null);
  protected readonly sending = signal(false);
  protected readonly canSend = computed(() => this.draft().trim().length > 0 && !this.sending());

  protected readonly suggestions = [
    'Quelles sont mes 5 dernières transactions ?',
    'Quel est mon solde ?',
    'Ajoute une dépense de 12,50 € pour un déjeuner',
    'Résume les documents que j’ai importés',
  ];

  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  protected send(text = this.draft()): void {
    const message = text.trim();
    if (!message || this.sending()) return;

    const image = this.image();
    this.push({ role: 'user', text: message, imagePreview: this.imagePreview() ?? undefined });
    this.draft.set('');
    this.clearImage();
    this.sending.set(true);

    this.api.chat(this.session.conversationId(), this.session.userId(), message, image).subscribe({
      next: (reply) => {
        this.push({
          role: 'assistant',
          text: reply.reply,
          sources: reply.sources,
          toolsUsed: reply.toolsUsed,
        });
        if (reply.toolsUsed.some((t) => MUTATING_TOOLS.includes(t))) {
          this.session.notifyDataChanged();
        }
        this.sending.set(false);
      },
      error: (err) => {
        this.push({ role: 'error', text: errorMessage(err) });
        this.sending.set(false);
      },
    });
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  protected pickImage(): void {
    this.fileInput()?.nativeElement.click();
  }

  protected onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.push({ role: 'error', text: 'Seules les images peuvent être jointes au chat.' });
      return;
    }
    this.image.set(file);
    const reader = new FileReader();
    reader.onload = () => this.imagePreview.set(reader.result as string);
    reader.readAsDataURL(file);
  }

  protected clearImage(): void {
    this.image.set(null);
    this.imagePreview.set(null);
  }

  protected newConversation(): void {
    const previous = this.session.conversationId();
    this.api.resetConversation(previous).subscribe({ error: () => undefined });
    this.session.newConversation();
    this.messages.set([]);
  }

  protected formatScore(score: number | null): string {
    return score == null ? '' : `${Math.round(score * 100)} %`;
  }

  private push(message: ChatMessage): void {
    this.messages.update((list) => [...list, message]);
    queueMicrotask(() => {
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
