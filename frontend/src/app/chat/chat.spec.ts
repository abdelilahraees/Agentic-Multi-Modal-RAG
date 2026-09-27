import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SessionService } from '../core/session.service';
import { Chat } from './chat';

describe('Chat', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Chat],
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends a message and renders reply, tools and sources', async () => {
    const fixture = TestBed.createComponent(Chat);
    const session = TestBed.inject(SessionService);
    const before = session.dataVersion();
    fixture.detectChanges();

    const chip = fixture.nativeElement.querySelector('.chip') as HTMLButtonElement;
    chip.click();

    const req = http.expectOne('/api/chat');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      conversationId: session.conversationId(),
      userId: 'demo',
      message: chip.textContent!.trim(),
    });
    req.flush({
      conversationId: session.conversationId(),
      reply: 'Voici vos transactions.',
      sources: [{ source: 'guide.pdf', snippet: 'extrait', score: 0.91 }],
      toolsUsed: ['createTransaction'],
      imageDescription: null,
    });
    fixture.detectChanges();
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.msg').length).toBe(2);
    expect(el.textContent).toContain('Voici vos transactions.');
    expect(el.textContent).toContain('createTransaction');
    expect(el.textContent).toContain('guide.pdf');
    expect(el.textContent).toContain('91 %');
    expect(session.dataVersion()).toBe(before + 1);
  });

  it('shows backend errors in the conversation', async () => {
    const fixture = TestBed.createComponent(Chat);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.chip') as HTMLButtonElement).click();

    http.expectOne('/api/chat').flush(
      { detail: 'Le fournisseur d’IA est indisponible' },
      { status: 502, statusText: 'Bad Gateway' },
    );
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.msg.error')?.textContent).toContain('indisponible');
  });

  it('starts a new conversation and resets server memory', () => {
    const fixture = TestBed.createComponent(Chat);
    const session = TestBed.inject(SessionService);
    const previous = session.conversationId();
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('.chat-header button') as HTMLButtonElement;
    button.click();

    http.expectOne({ method: 'DELETE', url: `/api/chat/${previous}` }).flush(null);
    expect(session.conversationId()).not.toBe(previous);
  });
});
