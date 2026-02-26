import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { TIER_CONFIGS, getPlayerAvatar, KIT_ICONS } from '../types';
import type { Player } from '../types';
import './Profile.css';

interface ProfileProps {
  username: string;
  players: Player[];
  onLogout: () => void;
}

export const Profile: React.FC<ProfileProps> = ({ username, players, onLogout }) => {
  const navigate = useNavigate();
  
  const handleLogout = () => {
    localStorage.removeItem('currentUser');
    onLogout();
    navigate('/');
  };
  
  const player = useMemo(() => 
    players.find(p => p.username.toLowerCase() === username.toLowerCase()),
    [players, username]
  );

  const playerRank = useMemo(() => {
    const sorted = [...players].sort((a, b) => b.score - a.score);
    return sorted.findIndex(p => p.username.toLowerCase() === username.toLowerCase()) + 1;
  }, [players, username]);

  if (!player) {
    // Если игрока нет в рейтинге, показываем пустой профиль
    return (
      <div className="profile-container">
        <div className="profile-header">
          <button onClick={() => navigate('/')} className="back-button">
            ← Назад к рейтингу
          </button>
          <button onClick={handleLogout} className="logout-button">
            Выйти
          </button>
        </div>

        <div className="profile-content">
          <div className="profile-main">
            <div className="profile-avatar-section">
              <div className="profile-avatar-wrapper">
                <img 
                  src={getPlayerAvatar(username)} 
                  alt={username}
                  className="profile-avatar"
                />
              </div>
              <h1 className="profile-username">{username}</h1>
            </div>

            <div className="profile-stats">
              <h2 className="section-title">Статистика по режимам</h2>
              <div className="empty-state">
                <p className="empty-text">Вы еще не участвуете в рейтинге</p>
                <p className="empty-hint">Зайдите на сервер и начните играть!</p>
              </div>
            </div>
          </div>

          <div className="profile-sidebar">
            <div className="info-card">
              <h3 className="info-title">Информация</h3>
              <div className="info-list">
                <div className="info-item">
                  <span className="info-label">Всего режимов</span>
                  <span className="info-value">0</span>
                </div>
                <div className="info-item">
                  <span className="info-label">Позиция</span>
                  <span className="info-value">Не в рейтинге</span>
                </div>
              </div>
            </div>

            <div className="achievements-card">
              <h3 className="info-title">Достижения</h3>
              <div className="empty-state">
                <p className="empty-text">Пока нет достижений</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="profile-container">
      <div className="profile-header">
        <button onClick={() => navigate('/')} className="back-button">
          ← Назад к рейтингу
        </button>
        <button onClick={handleLogout} className="logout-button">
          Выйти
        </button>
      </div>

      <div className="profile-content">
        <div className="profile-main">
          <div className="profile-avatar-section">
            <div className="profile-avatar-wrapper">
              <img 
                src={getPlayerAvatar(player.username)} 
                alt={player.username}
                className="profile-avatar"
              />
              <div className="profile-rank-badge">
                <span className="rank-label">Место</span>
                <span className="rank-value">#{playerRank}</span>
              </div>
            </div>
            <h1 className="profile-username">{player.username}</h1>
          </div>

          <div className="profile-stats">
            <h2 className="section-title">Статистика по режимам</h2>
            <div className="kits-grid">
              {Object.entries(player.kits).map(([kitName, tier]) => {
                const tierConfig = TIER_CONFIGS.find(t => t.rank === tier);
                return (
                  <div 
                    key={kitName}
                    className="kit-stat-card"
                    style={{
                      background: tierConfig?.color || '#666'
                    }}
                  >
                    <img 
                      src={KIT_ICONS[kitName]} 
                      alt={kitName}
                      className="kit-stat-icon"
                    />
                    <div className="kit-stat-info">
                      <span className="kit-stat-name">{kitName}</span>
                      <span className="kit-stat-tier">{tier}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        <div className="profile-sidebar">
          <div className="info-card">
            <h3 className="info-title">Информация</h3>
            <div className="info-list">
              <div className="info-item">
                <span className="info-label">Всего режимов</span>
                <span className="info-value">{Object.keys(player.kits).length}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Лучший тир</span>
                <span className="info-value">
                  {Object.values(player.kits).sort()[0]}
                </span>
              </div>
              <div className="info-item">
                <span className="info-label">Позиция</span>
                <span className="info-value">#{playerRank} из {players.length}</span>
              </div>
            </div>
          </div>

          <div className="achievements-card">
            <h3 className="info-title">Достижения</h3>
            <div className="achievements-list">
              {playerRank <= 3 && (
                <div className="achievement">
                  <svg className="achievement-icon" width="28" height="28" viewBox="0 0 24 24" fill="none">
                    <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="currentColor"/>
                  </svg>
                  <span className="achievement-text">Топ-3 игрок</span>
                </div>
              )}
              {Object.keys(player.kits).length >= 5 && (
                <div className="achievement">
                  <svg className="achievement-icon" width="28" height="28" viewBox="0 0 24 24" fill="none">
                    <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="currentColor"/>
                  </svg>
                  <span className="achievement-text">Мастер всех режимов</span>
                </div>
              )}
              {player.score >= 100 && (
                <div className="achievement">
                  <svg className="achievement-icon" width="28" height="28" viewBox="0 0 24 24" fill="none">
                    <circle cx="12" cy="12" r="10" fill="currentColor"/>
                  </svg>
                  <span className="achievement-text">100+ очков</span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
