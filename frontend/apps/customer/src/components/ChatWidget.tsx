import { useEffect, useRef, useState } from 'react';
import { useChatStore } from '@shared/store/chatStore';
import MarkdownRenderer from './MarkdownRenderer';

type ChatPanelSize = 'comfortable' | 'wide';

interface ChatWidgetPrefs {
  minimized: boolean;
  size: ChatPanelSize;
}

const CHAT_WIDGET_PREFS_KEY = 'ai-chat-widget:prefs';
const DEFAULT_CHAT_WIDGET_PREFS: ChatWidgetPrefs = {
  minimized: false,
  size: 'comfortable',
};

function readChatWidgetPrefs(): ChatWidgetPrefs {
  if (typeof window === 'undefined') return DEFAULT_CHAT_WIDGET_PREFS;
  try {
    const raw = window.localStorage.getItem(CHAT_WIDGET_PREFS_KEY);
    if (!raw) return DEFAULT_CHAT_WIDGET_PREFS;
    const parsed = JSON.parse(raw) as Partial<ChatWidgetPrefs>;
    return {
      minimized: !!parsed.minimized,
      size: parsed.size === 'wide' ? 'wide' : 'comfortable',
    };
  } catch (_) {
    return DEFAULT_CHAT_WIDGET_PREFS;
  }
}

function saveChatWidgetPrefs(prefs: ChatWidgetPrefs) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(CHAT_WIDGET_PREFS_KEY, JSON.stringify(prefs));
  } catch (_) {}
}

export default function ChatWidget() {
  const {
    isOpen,
    messages,
    isStreaming,
    toolStatus,
    pendingConfirmation,
    suggestions,
    isLoading,
    error,
    currentSessionId,
    toggleChat,
    sendMessage,
    confirmAction,
    rejectAction,
    fetchSuggestions,
    createSession,
    cancelStreaming,
  } = useChatStore();

  const [input, setInput] = useState('');
  const [isMinimized, setIsMinimized] = useState(() => readChatWidgetPrefs().minimized);
  const [panelSize, setPanelSize] = useState<ChatPanelSize>(() => readChatWidgetPrefs().size);
  // UC-AICHAT-003 A5: 5-minute confirmation window countdown.
  const CONFIRM_TTL = 300;
  const [secondsLeft, setSecondsLeft] = useState(CONFIRM_TTL);
  const confirmExpired = !!pendingConfirmation && secondsLeft <= 0;
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    saveChatWidgetPrefs({ minimized: isMinimized, size: panelSize });
  }, [isMinimized, panelSize]);

  // On open: load suggestions and, per UC-AICHAT-001, start a session (which
  // seeds the greeting) if one isn't active yet.
  useEffect(() => {
    if (isOpen) {
      fetchSuggestions();
      if (!currentSessionId) createSession();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen]);

  // Run the confirmation countdown while a pending confirmation is shown.
  useEffect(() => {
    if (!pendingConfirmation) { setSecondsLeft(CONFIRM_TTL); return; }
    const deadline = Date.now() + CONFIRM_TTL * 1000;
    const tick = () => setSecondsLeft(Math.max(0, Math.ceil((deadline - Date.now()) / 1000)));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [pendingConfirmation?.confirmId]);

  // Auto-scroll to bottom when messages or streaming status changes
  useEffect(() => {
    if (isMinimized) return;
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming, pendingConfirmation, isMinimized]);

  // Cancel streaming on unmount to prevent memory leaks and dangling SSE connections
  useEffect(() => {
    return () => {
      cancelStreaming();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!input.trim() || isStreaming) return;

    const messageText = input.trim();
    setInput('');
    await sendMessage(messageText);
  };

  const handleSuggestionClick = async (text: string) => {
    if (isStreaming) return;
    await sendMessage(text);
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
    }).format(price);
  };

  const togglePanelSize = () => {
    setPanelSize(size => size === 'wide' ? 'comfortable' : 'wide');
  };

  const panelSizeClass = isMinimized
    ? 'h-auto md:w-[360px]'
    : panelSize === 'wide'
      ? 'h-[min(680px,calc(100vh-8rem))] md:w-[520px]'
      : 'h-[min(550px,calc(100vh-8rem))] md:w-[400px]';

  if (!isOpen) {
    return (
      <button
        onClick={toggleChat}
        className="fixed bottom-20 right-4 md:bottom-6 md:right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow-lg transition-all duration-300 hover:scale-110 hover:shadow-xl active:scale-95"
        title="Trợ lý AI"
        id="ai-chat-widget-button"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={2}
          stroke="currentColor"
          className="h-7 w-7 animate-pulse"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H8.25m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H12m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 01-2.555-.337A5.972 5.972 0 015.41 20.97a.75.75 0 01-1.074-.765 6.002 6.002 0 013.003-4.484C6.042 14.433 5 13.317 5 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25z"
          />
        </svg>
      </button>
    );
  }

  return (
    <div
      className={`fixed bottom-24 left-4 right-4 z-50 flex flex-col overflow-hidden rounded-2xl border border-gray-200/50 bg-white/90 shadow-2xl backdrop-blur-md transition-all duration-300 animate-in slide-in-from-bottom-5 md:left-auto md:right-6 ${panelSizeClass}`}
      id="ai-chat-widget-panel"
    >
      {/* Header */}
      <div className="flex items-center justify-between bg-gradient-to-r from-blue-600 to-indigo-600 px-4 py-3 text-white">
        <div className="flex items-center space-x-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-white/20">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2.5}
              stroke="currentColor"
              className="h-5 w-5 text-white"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 17.25v1.007a3 3 0 01-.879 2.122L7.5 21h9l-.621-.621A3 3 0 0115 18.257V17.25m6-12V15a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 15V5.25m18 0A2.25 2.25 0 0018.75 3H5.25A2.25 2.25 0 003 5.25m18 0V12a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 12V5.25"
              />
            </svg>
          </div>
          <div>
            <h3 className="font-semibold text-sm">Trợ lý AI Paradise</h3>
            <span className="text-[10px] text-blue-100 flex items-center gap-1">
              <span className="h-1.5 w-1.5 rounded-full bg-green-400 animate-ping"></span>
              Sẵn sàng hỗ trợ
            </span>
          </div>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setIsMinimized(value => !value)}
            className="rounded-full p-1.5 hover:bg-white/10 active:bg-white/20"
            aria-label={isMinimized ? 'Mở rộng trợ lý AI' : 'Thu gọn trợ lý AI'}
            title={isMinimized ? 'Mở rộng' : 'Thu gọn'}
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2.5}
              stroke="currentColor"
              className="h-4 w-4"
            >
              {isMinimized ? (
                <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 15.75 12 8.25l7.5 7.5" />
              ) : (
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25 12 15.75l-7.5-7.5" />
              )}
            </svg>
          </button>
          <button
            type="button"
            onClick={togglePanelSize}
            className="rounded-full p-1.5 hover:bg-white/10 active:bg-white/20"
            aria-label={panelSize === 'wide' ? 'Thu nhỏ khung chat' : 'Phóng to khung chat'}
            title={panelSize === 'wide' ? 'Thu nhỏ' : 'Phóng to'}
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2.2}
              stroke="currentColor"
              className="h-4 w-4"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 9.75V4.5h5.25M19.5 14.25v5.25h-5.25M4.5 4.5l6.75 6.75M19.5 19.5l-6.75-6.75" />
            </svg>
          </button>
          <button
            type="button"
            onClick={toggleChat}
            className="rounded-full p-1.5 hover:bg-white/10 active:bg-white/20"
            aria-label="Đóng trợ lý AI"
            title="Đóng"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2.5}
              stroke="currentColor"
              className="h-4 w-4"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>

      {/* Messages */}
      <div
        ref={messagesContainerRef}
        className={`${isMinimized ? 'hidden' : ''} flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50/50`}
      >
        {messages.map((msg, index) => {
          const isUser = msg.role === 'USER';
          return (
            <div
              key={index}
              className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} space-y-1`}
            >
              <div
                className={`max-w-[85%] rounded-2xl px-4 py-2.5 text-sm shadow-sm ${
                  isUser
                    ? 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-tr-none'
                    : 'bg-white text-gray-800 border border-gray-100 rounded-tl-none'
                }`}
              >
                {isUser ? (
                  <span className="whitespace-pre-wrap">{msg.content}</span>
                ) : (
                  <MarkdownRenderer text={msg.content} />
                )}

                {/* Structured Products Output */}
                {msg.products && msg.products.length > 0 && (
                  <div className="mt-3 grid grid-cols-1 gap-2 pt-2 border-t border-gray-100">
                    {msg.products.map((p: any, pIdx: number) => (
                      <a
                        key={pIdx}
                        href={`/products/${p.id || p.productId}`}
                        className="flex items-center space-x-3 rounded-lg border border-gray-100 bg-gray-50 p-2 hover:bg-gray-100 transition-colors"
                      >
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded bg-white font-bold text-xs text-blue-600 border border-gray-200">
                          {p.name.charAt(0)}
                        </div>
                        <div className="overflow-hidden">
                          <p className="truncate text-xs font-semibold text-gray-800">{p.name}</p>
                          <p className="text-[11px] font-bold text-blue-600">{formatPrice(p.price)}</p>
                        </div>
                      </a>
                    ))}
                  </div>
                )}
              </div>
              <span className="text-[10px] text-gray-400 px-1">
                {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
              </span>
            </div>
          );
        })}

        {/* Level 3 Confirmation Card */}
        {pendingConfirmation && (
          <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-4 shadow-sm animate-in fade-in zoom-in-95 duration-200">
            <div className="flex items-start space-x-2">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={2}
                stroke="currentColor"
                className="h-5 w-5 text-amber-600 shrink-0 mt-0.5"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                />
              </svg>
              <div>
                <h4 className="font-bold text-xs text-amber-800">Yêu cầu xác nhận giao dịch</h4>
                <p className="text-xs text-amber-700 mt-1 leading-relaxed">{pendingConfirmation.summary}</p>
                {pendingConfirmation.orderId && (
                  <p className="text-[11px] font-semibold text-amber-600 mt-0.5">Mã đơn hàng: #{pendingConfirmation.orderId}</p>
                )}
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between border-t border-amber-100 pt-3">
              {/* Countdown / expiry (UC-AICHAT-003 A5) */}
              <span className={`text-[11px] font-medium ${confirmExpired ? 'text-red-600' : 'text-amber-600'}`}>
                {confirmExpired
                  ? 'Hết thời gian xác nhận'
                  : `Còn ${String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:${String(secondsLeft % 60).padStart(2, '0')}`}
              </span>
              <div className="flex space-x-2">
                <button
                  onClick={() => rejectAction(pendingConfirmation.confirmId)}
                  disabled={confirmExpired}
                  className="rounded-lg bg-white px-3 py-1.5 text-xs font-semibold text-amber-800 border border-amber-200 hover:bg-amber-100 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Từ chối
                </button>
                <button
                  onClick={() => confirmAction(pendingConfirmation.confirmId)}
                  disabled={confirmExpired}
                  className="rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 px-3 py-1.5 text-xs font-semibold text-white shadow hover:from-blue-700 hover:to-indigo-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed disabled:from-gray-400 disabled:to-gray-400"
                >
                  Đồng ý xác nhận
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Tool-lookup status (UC-AICHAT-002 step 8) */}
        {isStreaming && toolStatus && !pendingConfirmation && (
          <div className="flex items-center gap-2 text-xs text-blue-600 bg-blue-50 border border-blue-100 rounded-xl px-3 py-2 w-fit shadow-sm">
            <span className="h-3 w-3 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
            {toolStatus}
          </div>
        )}

        {/* Loading / Streaming typing indicator */}
        {isStreaming && !toolStatus && !pendingConfirmation && (
          <div className="flex items-center space-x-1.5 text-gray-400 bg-white border border-gray-100 rounded-2xl rounded-tl-none px-4 py-2 w-16 shadow-sm">
            <span className="h-1.5 w-1.5 rounded-full bg-gray-400 animate-bounce"></span>
            <span className="h-1.5 w-1.5 rounded-full bg-gray-400 animate-bounce delay-150"></span>
            <span className="h-1.5 w-1.5 rounded-full bg-gray-400 animate-bounce delay-300"></span>
          </div>
        )}

        {/* Error message */}
        {error && (
          <div className="rounded-lg bg-red-50 border border-red-100 p-3 text-xs text-red-600 flex items-start gap-2">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2}
              stroke="currentColor"
              className="h-4 w-4 shrink-0"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
            <span>{error}</span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Suggestion Chips */}
      {!isMinimized && suggestions.length > 0 && !isStreaming && !pendingConfirmation && (
        <div className="border-t border-gray-100 bg-gray-50 px-3 py-2">
          <div className="flex flex-wrap gap-1.5 max-h-20 overflow-y-auto">
            {suggestions.map((sug, sIdx) => (
              <button
                key={sIdx}
                onClick={() => handleSuggestionClick(typeof sug === 'string' ? sug : (sug as any).text)}
                className="rounded-full bg-white px-2.5 py-1 text-left text-xs text-gray-600 border border-gray-200/60 hover:bg-blue-50 hover:text-blue-600 hover:border-blue-200 transition-colors duration-200 truncate max-w-full"
              >
                {typeof sug === 'string' ? sug : `${(sug as any).icon || '💡'} ${(sug as any).text}`}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Footer input form */}
      {!isMinimized && (
      <form onSubmit={handleSend} className="border-t border-gray-100 bg-white p-3 flex items-center space-x-2">
        {isStreaming ? (
          <button
            type="button"
            onClick={cancelStreaming}
            className="flex-1 flex justify-center items-center gap-1.5 rounded-lg border border-red-200 bg-red-50 py-2.5 text-xs font-semibold text-red-600 hover:bg-red-100 active:scale-[0.98] transition-all duration-200"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2}
              stroke="currentColor"
              className="h-4 w-4"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Dừng AI trả lời
          </button>
        ) : (
          <>
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              disabled={isStreaming || !!pendingConfirmation}
              placeholder={pendingConfirmation ? 'Vui lòng phản hồi xác nhận ở trên...' : 'Hỏi trợ lý AI...'}
              className="flex-1 rounded-lg border border-gray-200 px-3.5 py-2 text-sm focus:border-blue-500 focus:outline-none disabled:bg-gray-100 disabled:text-gray-400"
              id="ai-chat-input"
            />
            <button
              type="submit"
              disabled={!input.trim() || isStreaming || !!pendingConfirmation}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 text-white shadow transition-all duration-200 hover:from-blue-700 hover:to-indigo-700 disabled:from-gray-300 disabled:to-gray-300 disabled:shadow-none"
              id="ai-chat-send"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={2.5}
                stroke="currentColor"
                className="h-4 w-4 transform rotate-90"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5"
                />
              </svg>
            </button>
          </>
        )}
      </form>
      )}
    </div>
  );
}
