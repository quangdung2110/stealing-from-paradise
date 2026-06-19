import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react';
import ChatWidget from '../ChatWidget';

// The widget reads everything from the chat store; we provide a controllable
// double so each test can set state and assert action calls.
const h = vi.hoisted(() => ({ store: {} as any }));
vi.mock('@shared/store/chatStore', () => ({ useChatStore: () => h.store }));

function makeStore(over: any = {}) {
  return {
    isOpen: false,
    messages: [],
    isStreaming: false,
    toolStatus: null,
    pendingConfirmation: null,
    suggestions: [],
    isLoading: false,
    error: null,
    currentSessionId: null,
    toggleChat: vi.fn(),
    sendMessage: vi.fn(),
    confirmAction: vi.fn(),
    rejectAction: vi.fn(),
    fetchSuggestions: vi.fn(),
    createSession: vi.fn(),
    cancelStreaming: vi.fn(),
    ...over,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  window.localStorage.clear();
  h.store = makeStore();
});

describe('ChatWidget', () => {
  it('renders the floating launcher when closed', () => {
    h.store = makeStore({ isOpen: false });
    render(<ChatWidget />);
    expect(screen.getByTitle('Trợ lý AI')).toBeInTheDocument();
  });

  it('creates a session and loads suggestions on open (UC-AICHAT-001)', () => {
    h.store = makeStore({ isOpen: true, currentSessionId: null });
    render(<ChatWidget />);
    expect(h.store.createSession).toHaveBeenCalled();
    expect(h.store.fetchSuggestions).toHaveBeenCalled();
  });

  it('shows the tool-lookup status while streaming (UC-AICHAT-002)', () => {
    h.store = makeStore({ isOpen: true, currentSessionId: 's', isStreaming: true, toolStatus: 'Đang tra cứu sản phẩm...' });
    render(<ChatWidget />);
    expect(screen.getByText('Đang tra cứu sản phẩm...')).toBeInTheDocument();
  });

  it('renders the Level-3 confirmation card with a countdown and confirms (UC-AICHAT-003)', () => {
    h.store = makeStore({
      isOpen: true, currentSessionId: 's',
      pendingConfirmation: { confirmId: 'c1', actionType: 'CANCEL', summary: 'Huỷ đơn #1' },
    });
    render(<ChatWidget />);
    expect(screen.getByText('Huỷ đơn #1')).toBeInTheDocument();
    expect(screen.getByText(/Còn \d{2}:\d{2}/)).toBeInTheDocument();
    fireEvent.click(screen.getByText('Đồng ý xác nhận'));
    expect(h.store.confirmAction).toHaveBeenCalledWith('c1');
  });

  it('expires the confirmation after 5 minutes and disables the buttons (UC-AICHAT-003 A5)', () => {
    vi.useFakeTimers();
    try {
      h.store = makeStore({
        isOpen: true, currentSessionId: 's',
        pendingConfirmation: { confirmId: 'c1', actionType: 'CANCEL', summary: 'Huỷ đơn #1' },
      });
      render(<ChatWidget />);
      act(() => { vi.advanceTimersByTime(301_000); });
      expect(screen.getByText(/Hết thời gian xác nhận/i)).toBeInTheDocument();
      expect(screen.getByText('Đồng ý xác nhận')).toBeDisabled();
    } finally {
      vi.useRealTimers();
    }
  });

  it('minimizes the panel and remembers that preference', async () => {
    h.store = makeStore({ isOpen: true, currentSessionId: 's' });
    render(<ChatWidget />);

    fireEvent.click(screen.getByLabelText('Thu gọn trợ lý AI'));

    expect(screen.queryByPlaceholderText('Hỏi trợ lý AI...')).not.toBeInTheDocument();
    await waitFor(() => {
      expect(JSON.parse(window.localStorage.getItem('ai-chat-widget:prefs') || '{}')).toMatchObject({
        minimized: true,
      });
    });
  });

  it('restores minimized state from localStorage', () => {
    window.localStorage.setItem('ai-chat-widget:prefs', JSON.stringify({ minimized: true, size: 'comfortable' }));
    h.store = makeStore({ isOpen: true, currentSessionId: 's' });

    render(<ChatWidget />);

    expect(screen.getByLabelText('Mở rộng trợ lý AI')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Hỏi trợ lý AI...')).not.toBeInTheDocument();
  });

  it('toggles wide mode and persists the selected size', async () => {
    h.store = makeStore({ isOpen: true, currentSessionId: 's' });
    render(<ChatWidget />);

    fireEvent.click(screen.getByLabelText('Phóng to khung chat'));

    expect(screen.getByLabelText('Thu nhỏ khung chat')).toBeInTheDocument();
    await waitFor(() => {
      expect(JSON.parse(window.localStorage.getItem('ai-chat-widget:prefs') || '{}')).toMatchObject({
        size: 'wide',
      });
    });
  });
});
