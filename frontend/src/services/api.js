import axios from 'axios';

const API_BASE = '/api';

const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' }
});

// JWT interceptor — attach token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 — redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ==================== Auth ====================
export const authApi = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
};

// ==================== Documents ====================
export const documentsApi = {
  upload: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  list: () => api.get('/documents'),
  get: (id) => api.get(`/documents/${id}`),
  delete: (id) => api.delete(`/documents/${id}`),
};

// ==================== Chat ====================
export const chatApi = {
  ask: (question, sessionId) => api.post('/chat/ask', { question, sessionId }),
  listSessions: () => api.get('/chat/sessions'),
  getSession: (id) => api.get(`/chat/sessions/${id}`),
  createSession: (title) => api.post('/chat/sessions', { title }),
  deleteSession: (id) => api.delete(`/chat/sessions/${id}`),
};

/**
 * Stream a RAG response via SSE (Server-Sent Events).
 * Returns an EventSource that emits tokens in real-time.
 */
export function streamChat(question, onToken, onDone, onError) {
  const token = localStorage.getItem('token');
  const url = `${API_BASE}/chat/stream?question=${encodeURIComponent(question)}`;

  const eventSource = new EventSource(url);

  eventSource.addEventListener('token', (event) => {
    onToken(event.data);
  });

  eventSource.addEventListener('done', () => {
    eventSource.close();
    onDone();
  });

  eventSource.addEventListener('error', (event) => {
    eventSource.close();
    onError(event.data || 'Stream error');
  });

  eventSource.onerror = () => {
    eventSource.close();
    onError('Connection lost');
  };

  return eventSource;
}

export default api;
