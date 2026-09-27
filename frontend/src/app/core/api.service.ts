import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChatReply, DocumentSummary, IngestionResult, Transaction } from './models';

/** Client HTTP du backend Spring (`/api`, proxifié en dev comme en prod). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  chat(conversationId: string, userId: string, message: string, image?: File | null): Observable<ChatReply> {
    if (image) {
      const form = new FormData();
      form.append('conversationId', conversationId);
      form.append('userId', userId);
      form.append('message', message);
      form.append('image', image);
      return this.http.post<ChatReply>(`${this.base}/chat`, form);
    }
    return this.http.post<ChatReply>(`${this.base}/chat`, { conversationId, userId, message });
  }

  resetConversation(conversationId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/chat/${encodeURIComponent(conversationId)}`);
  }

  listDocuments(): Observable<DocumentSummary[]> {
    return this.http.get<DocumentSummary[]>(`${this.base}/documents`);
  }

  uploadDocument(file: File, ownerId: string): Observable<IngestionResult> {
    const form = new FormData();
    form.append('file', file);
    form.append('ownerId', ownerId);
    return this.http.post<IngestionResult>(`${this.base}/documents`, form);
  }

  deleteDocument(documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/documents/${encodeURIComponent(documentId)}`);
  }

  listTransactions(userId: string, limit = 20): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`${this.base}/transactions`, {
      params: { userId, limit },
    });
  }
}

/** Extrait un message lisible d'une erreur HTTP (ProblemDetail RFC 7807 côté backend). */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Backend injoignable : vérifiez que rag-backend tourne sur le port 8080.';
    }
    const detail = err.error?.detail ?? err.error?.message;
    return detail ? String(detail) : `Erreur ${err.status} ${err.statusText}`;
  }
  return err instanceof Error ? err.message : String(err);
}
