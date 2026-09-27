import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('renders the chat and loads documents and transactions', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/documents').flush([
      { documentId: 'd1', source: 'rapport.pdf', owner: 'demo', mimeType: 'application/pdf',
        kind: 'pdf', ingestedAt: '2026-01-01T10:00:00Z', segments: 4 },
    ]);
    http.expectOne((r) => r.url === '/api/transactions' && r.params.get('userId') === 'demo').flush([
      { id: 1, userId: 'demo', label: 'Loyer', amount: -950, currency: 'EUR', createdAt: '2026-01-02T10:00:00Z' },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-chat h1')?.textContent).toContain('Assistant');
    expect(el.textContent).toContain('rapport.pdf');
    expect(el.textContent).toContain('Loyer');
    http.verify();
  });
});
