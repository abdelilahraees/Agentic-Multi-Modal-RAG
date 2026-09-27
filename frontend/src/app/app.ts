import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Chat } from './chat/chat';
import { SessionService } from './core/session.service';
import { DocumentsPanel } from './documents/documents-panel';
import { TransactionsPanel } from './transactions/transactions-panel';

@Component({
  selector: 'app-root',
  imports: [FormsModule, Chat, DocumentsPanel, TransactionsPanel],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly session = inject(SessionService);

  protected changeUser(value: string): void {
    const userId = value.trim();
    if (userId && userId !== this.session.userId()) {
      this.session.userId.set(userId);
      this.session.newConversation();
    }
  }
}
