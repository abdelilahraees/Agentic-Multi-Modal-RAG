import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { ApiService, errorMessage } from '../core/api.service';
import { Transaction } from '../core/models';
import { SessionService } from '../core/session.service';

@Component({
  selector: 'app-transactions-panel',
  imports: [CurrencyPipe, DatePipe],
  template: `
    <div class="panel-header">
      <h2>Transactions <span class="muted small">({{ session.userId() }})</span></h2>
      <button type="button" class="btn ghost small" (click)="refresh()" aria-label="Rafraîchir">↻</button>
    </div>

    @if (error()) {
      <p class="error">{{ error() }}</p>
    }

    <ul class="list">
      @for (t of transactions(); track t.id) {
        <li>
          <div class="meta">
            <span class="name" [title]="t.label">{{ t.label }}</span>
            <span class="muted small">{{ t.createdAt | date: 'shortDate' }}</span>
          </div>
          <span class="amount" [class.negative]="t.amount < 0">
            {{ t.amount | currency: t.currency : 'symbol' : '1.2-2' }}
          </span>
        </li>
      } @empty {
        <li class="muted small">Aucune transaction.</li>
      }
    </ul>
  `,
})
export class TransactionsPanel {
  private readonly api = inject(ApiService);
  protected readonly session = inject(SessionService);

  protected readonly transactions = signal<Transaction[]>([]);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Recharge quand l'utilisateur change ou quand l'agent a modifié des données.
    effect(() => {
      this.session.dataVersion();
      this.load(this.session.userId());
    });
  }

  refresh(): void {
    this.load(this.session.userId());
  }

  private load(userId: string): void {
    if (!userId) return;
    this.api.listTransactions(userId).subscribe({
      next: (list) => {
        this.transactions.set(list);
        this.error.set(null);
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
