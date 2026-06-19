import { vi, describe, it, expect, beforeEach } from 'vitest';

// Mock the chat API + SSE streamer so the store logic is tested in isolation.
vi.mock('../api/chat.api', () => ({
  chatApi: {
    createSession: vi.fn(),
    closeSession: vi.fn(() => Promise.resolve({})),
    getHistory: vi.fn(() => Promise.resolve({ data: { data: [] } })),
    getSuggestions: vi.fn(() => Promise.resolve({ data: { data: [] } })),
    confirmAction: vi.fn(() => Promise.resolve({ data: { data: {} } })),
  },
  streamChat: vi.fn(),
}));

import { chatApi, streamChat } from '../api/chat.api';
import { useChatStore } from '../store/chatStore';

const reset = () => useChatStore.setState({
  currentSessionId: null, messages: [], isStreaming: false, toolStatus: null,
  pendingConfirmation: null, suggestions: [], isOpen: false, isLoading: false,
  error: null, abortController: null,
});

const sessionOk = () =>
  (chatApi.createSession as any).mockResolvedValue({ data: { data: { id: 'sess1', status: 'ACTIVE', createdAt: '2026-01-01' } } });

/** Make streamChat capture the callbacks so the test can drive SSE events. */
function captureStream() {
  const ref: any = {};
  (streamChat as any).mockImplementation((_s: string, _m: string, c: any) => {
    ref.cbs = c;
    ref.abort = vi.fn();
    return { abort: ref.abort };
  });
  return ref;
}

beforeEach(() => { vi.clearAllMocks(); reset(); });

describe('chatStore.createSession (UC-AICHAT-001)', () => {
  it('stores the sessionId and seeds a greeting message', async () => {
    sessionOk();
    const id = await useChatStore.getState().createSession();
    const s = useChatStore.getState();
    expect(id).toBe('sess1');
    expect(s.currentSessionId).toBe('sess1');
    expect(s.messages[0].role).toBe('ASSISTANT');
    expect(s.messages[0].content).toMatch(/Xin chào/);
  });

  it('sets an error and returns null on failure', async () => {
    (chatApi.createSession as any).mockRejectedValue({ response: { data: { message: 'boom' } } });
    const id = await useChatStore.getState().createSession();
    expect(id).toBeNull();
    expect(useChatStore.getState().error).toBe('boom');
  });
});

describe('chatStore.sendMessage (UC-AICHAT-002)', () => {
  it('auto-creates a session then appends user + assistant messages', async () => {
    sessionOk();
    captureStream();
    await useChatStore.getState().sendMessage('Xin chào');
    const msgs = useChatStore.getState().messages;
    // greeting + user + (empty) assistant
    expect(msgs.some(m => m.role === 'USER' && m.content === 'Xin chào')).toBe(true);
    expect(msgs[msgs.length - 1].role).toBe('ASSISTANT');
    expect(useChatStore.getState().isStreaming).toBe(true);
    expect(streamChat).toHaveBeenCalledWith('sess1', 'Xin chào', expect.any(Object));
  });

  it('accumulates streamed deltas into the assistant message and clears toolStatus', async () => {
    sessionOk();
    const ref = captureStream();
    await useChatStore.getState().sendMessage('hi');
    ref.cbs.onEvent('tool_start', { toolName: 'searchProducts' });
    expect(useChatStore.getState().toolStatus).toBe('searchProducts');
    ref.cbs.onDelta('Xin ');
    ref.cbs.onDelta('chào!');
    const last = useChatStore.getState().messages.at(-1)!;
    expect(last.content).toBe('Xin chào!');
    expect(useChatStore.getState().toolStatus).toBeNull();
    ref.cbs.onDone();
    expect(useChatStore.getState().isStreaming).toBe(false);
  });

  it('raises a Level-3 confirmation on confirmation_required (→ UC-AICHAT-003)', async () => {
    sessionOk();
    const ref = captureStream();
    await useChatStore.getState().sendMessage('huỷ đơn');
    ref.cbs.onEvent('confirmation_required', { confirmId: 'c1', actionType: 'CANCEL', summary: 'Huỷ đơn #1' });
    expect(useChatStore.getState().pendingConfirmation?.confirmId).toBe('c1');
  });

  it('drops the session on a 422 (expired) error so the next message restarts it', async () => {
    sessionOk();
    const ref = captureStream();
    await useChatStore.getState().sendMessage('hi');
    ref.cbs.onError('Kết nối thất bại với mã lỗi: 422');
    const s = useChatStore.getState();
    expect(s.currentSessionId).toBeNull();
    expect(s.error).toMatch(/hết hạn/i);
    expect(s.isStreaming).toBe(false);
  });
});

describe('chatStore.confirmAction / rejectAction (UC-AICHAT-003)', () => {
  it('confirms with confirmId + sessionId and clears the pending card', async () => {
    useChatStore.setState({ currentSessionId: 'sess1', pendingConfirmation: { confirmId: 'c1', actionType: 'CANCEL', summary: 's' } });
    await useChatStore.getState().confirmAction('c1');
    expect(chatApi.confirmAction).toHaveBeenCalledWith('c1', true, 'sess1');
    expect(chatApi.getHistory).toHaveBeenCalledWith('sess1', expect.any(Object));
    expect(useChatStore.getState().pendingConfirmation).toBeNull();
    expect(useChatStore.getState().isStreaming).toBe(false);
  });

  it('rejects with confirmed=false', async () => {
    useChatStore.setState({ currentSessionId: 'sess1', pendingConfirmation: { confirmId: 'c1', actionType: 'CANCEL', summary: 's' } });
    await useChatStore.getState().rejectAction('c1');
    expect(chatApi.confirmAction).toHaveBeenCalledWith('c1', false, 'sess1');
    expect(useChatStore.getState().isStreaming).toBe(false);
  });
});

describe('chatStore.cancelStreaming', () => {
  it('aborts the active stream', async () => {
    sessionOk();
    const ref = captureStream();
    await useChatStore.getState().sendMessage('hi');
    useChatStore.getState().cancelStreaming();
    expect(ref.abort).toHaveBeenCalled();
    expect(useChatStore.getState().isStreaming).toBe(false);
  });
});
