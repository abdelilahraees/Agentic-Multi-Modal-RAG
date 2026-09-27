import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ApiService, errorMessage } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends multipart when an image is attached', () => {
    const image = new File([new Uint8Array([1, 2])], 'ticket.png', { type: 'image/png' });
    api.chat('c1', 'alice', 'Ajoute ce ticket', image).subscribe();

    const req = http.expectOne('/api/chat');
    const body = req.request.body as FormData;
    expect(body instanceof FormData).toBeTrue();
    expect(body.get('conversationId')).toBe('c1');
    expect(body.get('userId')).toBe('alice');
    expect(body.get('image')).toBe(image);
    req.flush({});
  });

  it('uploads documents with owner id', () => {
    const file = new File(['hello'], 'notes.txt', { type: 'text/plain' });
    api.uploadDocument(file, 'alice').subscribe();

    const req = http.expectOne('/api/documents');
    expect((req.request.body as FormData).get('ownerId')).toBe('alice');
    req.flush({});
  });

  it('extracts ProblemDetail messages', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 415, error: { detail: 'Type non supporté' } })))
      .toBe('Type non supporté');
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('Backend injoignable');
  });
});
