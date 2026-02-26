import { useState } from 'react';
import './Auth.css';

interface AuthProps {
  onLogin: (username: string) => void;
}

export const Auth: React.FC<AuthProps> = ({ onLogin }) => {
  const [username, setUsername] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    
    if (!username.trim()) {
      setError('Введите никнейм');
      return;
    }

    if (username.length < 3 || username.length > 16) {
      setError('Никнейм должен быть от 3 до 16 символов');
      return;
    }

    if (!/^[a-zA-Z0-9_]+$/.test(username)) {
      setError('Никнейм может содержать только буквы, цифры и _');
      return;
    }

    setLoading(true);
    
    // Просто сохраняем никнейм
    setTimeout(() => {
      localStorage.setItem('currentUser', username);
      onLogin(username);
      setLoading(false);
    }, 500);
  };

  return (
    <div className="auth-container">
      <div className="auth-background"></div>
      <div className="auth-card">
        <div className="auth-header">
          <svg className="auth-logo" viewBox="0 0 40 40" fill="none">
            <circle cx="20" cy="20" r="18" stroke="url(#authGrad)" strokeWidth="2" />
            <circle cx="20" cy="20" r="12" fill="url(#authGrad)" />
            <defs>
              <linearGradient id="authGrad" x1="0" y1="0" x2="40" y2="40">
                <stop stopColor="#FFD700" />
                <stop offset="1" stopColor="#FF8C00" />
              </linearGradient>
            </defs>
          </svg>
          <h1 className="auth-title">Suntier</h1>
          <p className="auth-subtitle">Войдите с вашим игровым ником</p>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label htmlFor="username" className="form-label">
              Игровой никнейм
            </label>
            <input
              id="username"
              type="text"
              className="form-input"
              placeholder="Введите ваш ник"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={loading}
              autoFocus
            />
            {error && <div className="form-error">{error}</div>}
          </div>

          <button 
            type="submit" 
            className="auth-button"
            disabled={loading}
          >
            {loading ? 'Проверка...' : 'Войти'}
          </button>
        </form>

        <div className="auth-footer">
          <p className="auth-note">
            Введите любой никнейм для входа на сайт
          </p>
        </div>
      </div>
    </div>
  );
};
