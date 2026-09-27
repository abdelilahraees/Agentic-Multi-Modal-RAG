export interface SourceRef {
  source: string;
  snippet: string;
  score: number | null;
}

export interface ChatReply {
  conversationId: string;
  reply: string;
  sources: SourceRef[];
  toolsUsed: string[];
  imageDescription: string | null;
}

export interface DocumentSummary {
  documentId: string;
  source: string;
  owner: string;
  mimeType: string;
  kind: string;
  ingestedAt: string;
  segments: number;
}

export interface IngestionResult {
  documentId: string;
  filename: string;
  segments: number;
  mimeType: string;
  kind: string;
}

export interface Transaction {
  id: number;
  userId: string;
  label: string;
  amount: number;
  currency: string;
  createdAt: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant' | 'error';
  text: string;
  imagePreview?: string;
  sources?: SourceRef[];
  toolsUsed?: string[];
}
