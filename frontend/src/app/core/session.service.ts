import { Injectable, effect, signal } from '@angular/core';

function read(key: string, fallback: string): string {
  try {
    return localStorage.getItem(key) || fallback;
  } catch {
    return fallback;
  }
}

function newConversationId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `conv-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * État partagé de la session : utilisateur courant et identifiant de conversation
 * (mémoire de l'agent côté backend). Persisté dans le localStorage.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  readonly userId = signal(read('rag.userId', 'demo'));
  readonly conversationId = signal(read('rag.conversationId', newConversationId()));
  /** Incrémenté quand l'agent a pu modifier des données (ex. transaction créée). */
  readonly dataVersion = signal(0);

  constructor() {
    effect(() => this.store('rag.userId', this.userId()));
    effect(() => this.store('rag.conversationId', this.conversationId()));
  }

  newConversation(): void {
    this.conversationId.set(newConversationId());
  }

  notifyDataChanged(): void {
    this.dataVersion.update((v) => v + 1);
  }

  private store(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* stockage indisponible (navigation privée) : on reste en mémoire */
    }
  }
}
