import { useState } from 'react';
import { authApi } from '../services/api';
import { Brain, Eye, EyeOff } from 'lucide-react';

export default function LoginPage({ onLogin }) {
  const [isRegister, setIsRegister] = useState(false);
  const [form, setForm] = useState({ username: '', password: '', email: '', fullName: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = isRegister
        ? await authApi.register(form)
        : await authApi.login({ username: form.username, password: form.password });

      const { accessToken, user } = res.data;
      onLogin(user, accessToken);
    } catch (err) {
      setError(err.response?.data?.message || 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-bg" />
      <div className="login-card">
        <div className="login-logo">
          <div className="login-logo-icon">
            <Brain size={28} color="white" />
          </div>
          <h1>Enterprise RAG</h1>
          <p>AI-powered knowledge platform</p>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              id="login-username"
              className="form-input"
              type="text"
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
              placeholder="Enter username"
              required
            />
          </div>

          {isRegister && (
            <>
              <div className="form-group">
                <label className="form-label">Full Name</label>
                <input
                  id="register-fullname"
                  className="form-input"
                  type="text"
                  value={form.fullName}
                  onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                  placeholder="Enter full name"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Email</label>
                <input
                  id="register-email"
                  className="form-input"
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  placeholder="Enter email"
                />
              </div>
            </>
          )}

          <div className="form-group">
            <label className="form-label">Password</label>
            <div style={{ position: 'relative' }}>
              <input
                id="login-password"
                className="form-input"
                type={showPassword ? 'text' : 'password'}
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                placeholder="Enter password"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute', right: 12, top: '50%',
                  transform: 'translateY(-50%)', background: 'none',
                  border: 'none', color: 'var(--text-muted)', cursor: 'pointer'
                }}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          {error && <div className="form-error">{error}</div>}

          <button id="login-submit" className="login-btn" type="submit" disabled={loading}>
            {loading ? 'Please wait...' : (isRegister ? 'Create Account' : 'Sign In')}
          </button>
        </form>

        <div className="login-toggle">
          {isRegister ? 'Already have an account? ' : "Don't have an account? "}
          <a onClick={() => { setIsRegister(!isRegister); setError(''); }}>
            {isRegister ? 'Sign In' : 'Register'}
          </a>
        </div>
      </div>
    </div>
  );
}
