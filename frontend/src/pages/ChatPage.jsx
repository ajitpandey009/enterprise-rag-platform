import { useState, useEffect, useRef } from 'react';
import { chatApi } from '../services/api';
import { Send, Plus, MessageSquare, Sparkles, Trash2 } from 'lucide-react';
import ReactMarkdown from 'react-markdown';

export default function ChatPage({ user }) {
  const [sessions, setSessions] = useState([]);
  const [activeSession, setActiveSession] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => { loadSessions(); }, []);

  const loadSessions = async () => {
    try {
      const res = await chatApi.listSessions();
      setSessions(res.data);
    } catch (err) { console.error('Failed to load sessions:', err); }
  };

  const loadSession = async (sessionId) => {
    try {
      const res = await chatApi.getSession(sessionId);
      setActiveSession(res.data);
      setMessages(res.data.messages || []);
    } catch (err) { console.error('Failed to load session:', err); }
  };

  const createNewChat = () => {
    setActiveSession(null);
    setMessages([]);
    setInput('');
  };

  const deleteSession = async (e, sessionId) => {
    e.stopPropagation();
    try {
      await chatApi.deleteSession(sessionId);
      if (activeSession?.id === sessionId) createNewChat();
      loadSessions();
    } catch (err) { console.error('Failed to delete session:', err); }
  };

  const handleSend = async () => {
    if (!input.trim() || loading) return;
    const question = input.trim();
    setInput('');
    setLoading(true);

    // Add user message immediately
    const userMsg = { id: Date.now(), role: 'USER', content: question, createdAt: new Date().toISOString() };
    setMessages(prev => [...prev, userMsg]);

    try {
      const res = await chatApi.ask(question, activeSession?.id);
      const { answer, sessionId, sources } = res.data;

      const assistantMsg = {
        id: Date.now() + 1, role: 'ASSISTANT', content: answer,
        sources: sources || [], createdAt: new Date().toISOString()
      };
      setMessages(prev => [...prev, assistantMsg]);

      // Update session context
      if (!activeSession) {
        setActiveSession({ id: sessionId, title: question.substring(0, 50) });
      }
      loadSessions();
    } catch (err) {
      const errorMsg = {
        id: Date.now() + 1, role: 'ASSISTANT',
        content: 'Sorry, I encountered an error. Please try again.',
        createdAt: new Date().toISOString()
      };
      setMessages(prev => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className="chat-layout">
      {/* Session Sidebar */}
      <div className="chat-sidebar">
        <div className="chat-sidebar-header">
          <button className="new-chat-btn" onClick={createNewChat}>
            <Plus size={16} /> New Chat
          </button>
        </div>
        <div className="session-list">
          {sessions.map(s => (
            <div
              key={s.id}
              className={`session-item ${activeSession?.id === s.id ? 'active' : ''}`}
              onClick={() => loadSession(s.id)}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div className="session-item-title">{s.title}</div>
                <button
                  className="doc-action-btn delete"
                  style={{ padding: '2px', border: 'none', minWidth: 'auto' }}
                  onClick={(e) => deleteSession(e, s.id)}
                >
                  <Trash2 size={12} />
                </button>
              </div>
              <div className="session-item-date">
                {new Date(s.updatedAt).toLocaleDateString()}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Chat Main Area */}
      <div className="chat-main">
        {messages.length === 0 ? (
          <div className="chat-empty">
            <div className="chat-empty-icon">
              <Sparkles size={32} color="var(--accent-secondary)" />
            </div>
            <h2>Ask your documents anything</h2>
            <p>Upload documents first, then ask questions. The AI will answer using only your enterprise knowledge base.</p>
          </div>
        ) : (
          <div className="chat-messages">
            {messages.map(msg => (
              <div key={msg.id} className={`message ${msg.role.toLowerCase()}`}>
                <div className="message-avatar">
                  {msg.role === 'USER' ? user?.username?.charAt(0).toUpperCase() : '✦'}
                </div>
                <div className="message-content">
                  <ReactMarkdown>{msg.content}</ReactMarkdown>
                  {msg.sources && msg.sources.length > 0 && (
                    <div className="message-sources">
                      <strong>Sources: </strong>
                      {msg.sources.map((s, i) => (
                        <span key={i} className="source-tag">{s.documentName}</span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
            {loading && (
              <div className="message assistant">
                <div className="message-avatar">✦</div>
                <div className="message-content">
                  <div className="typing-indicator">
                    <div className="typing-dot" />
                    <div className="typing-dot" />
                    <div className="typing-dot" />
                  </div>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        )}

        {/* Input */}
        <div className="chat-input-container">
          <div className="chat-input-wrapper">
            <input
              id="chat-input"
              className="chat-input"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Ask a question about your documents..."
              disabled={loading}
            />
            <button
              id="chat-send"
              className="send-btn"
              onClick={handleSend}
              disabled={!input.trim() || loading}
            >
              <Send size={16} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
