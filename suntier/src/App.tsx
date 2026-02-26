import { useState, useEffect, useMemo } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { getPlayerAvatar, KIT_ICONS } from './types';
import type { Player } from './types';
import { PlayerCard } from './components/PlayerCard';
import { SearchIcon, CrownIcon } from './components/Icons';
import { Auth } from './components/Auth';
import { Profile } from './components/Profile';
import { Admin } from './components/Admin';
import './App.css';

function App() {
  const [currentUser, setCurrentUser] = useState<string | null>(
    localStorage.getItem('currentUser')
  );
  const [players, setPlayers] = useState<Player[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadPlayers = async () => {
      try {
        const response = await fetch('http://localhost:3001/api/players');
        if (!response.ok) throw new Error('Failed to fetch players');
        const data = await response.json();
        setPlayers(data);
      } catch (error) {
        console.error('Error loading players:', error);
        setPlayers([]);
      } finally {
        setLoading(false);
      }
    };
    
    loadPlayers();
    
    // Auto-refresh every 30 seconds
    const interval = setInterval(loadPlayers, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleLogin = (username: string) => {
    setCurrentUser(username);
  };

  const handleLogout = () => {
    setCurrentUser(null);
  };

  if (loading) {
    return (
      <div className="app">
        <div className="loading">
          <span>Загрузка данных...</span>
        </div>
      </div>
    );
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route 
          path="/auth" 
          element={
            currentUser ? 
              <Navigate to="/" replace /> : 
              <Auth onLogin={handleLogin} />
          } 
        />
        <Route 
          path="/profile" 
          element={
            currentUser ? 
              <Profile 
                username={currentUser} 
                players={players}
                onLogout={handleLogout}
              /> : 
              <Navigate to="/auth" replace />
          } 
        />
        <Route 
          path="/admin" 
          element={
            currentUser === 'admin' ? 
              <Admin onLogout={handleLogout} /> : 
              <Navigate to="/auth" replace />
          } 
        />
        <Route 
          path="/" 
          element={
            <Leaderboard 
              players={players} 
              currentUser={currentUser}
            />
          } 
        />
      </Routes>
    </BrowserRouter>
  );
}

interface LeaderboardProps {
  players: Player[];
  currentUser: string | null;
}

function Leaderboard({ players, currentUser }: LeaderboardProps) {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedKit, setSelectedKit] = useState<string>('all');

  // Получаем все уникальные киты из данных игроков
  const availableKits = useMemo(() => {
    const kitsSet = new Set<string>();
    players.forEach(player => {
      Object.keys(player.kits).forEach(kit => kitsSet.add(kit));
    });
    return Array.from(kitsSet).sort();
  }, [players]);

  const filteredPlayers = useMemo(() => {
    let filtered = players;
    
    // Фильтр по киту
    if (selectedKit !== 'all') {
      filtered = filtered.filter(player => 
        Object.keys(player.kits).includes(selectedKit)
      );
    }
    
    // Фильтр по поиску
    if (searchQuery) {
      filtered = filtered.filter(player => 
        player.username.toLowerCase().includes(searchQuery.toLowerCase())
      );
    }
    
    return filtered.sort((a, b) => b.score - a.score);
  }, [players, searchQuery, selectedKit]);

  const topPlayers = useMemo(() => 
    [...players].sort((a, b) => b.score - a.score).slice(0, 3),
    [players]
  );

  return (
    <div className="app">
      {/* Header */}
      <header className="header">
        <div className="header-content">
          <div className="logo">
            <img src="/assets/logo.svg" alt="Suntier" className="logo-icon" />
            <span className="logo-text">Suntier</span>
          </div>
          
          <nav className="nav">
          </nav>

          <div className="header-actions">
            <div className="search-box">
              <SearchIcon className="search-icon" />
              <input 
                type="text" 
                placeholder="Поиск игроков..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Поиск игроков"
              />
            </div>
            {currentUser ? (
              <>
                {currentUser === 'admin' && (
                  <button 
                    className="admin-button"
                    onClick={() => navigate('/admin')}
                    title="Админ-панель"
                  >
                    ⚙️ Админка
                  </button>
                )}
                <button 
                  className="profile-button"
                  onClick={() => navigate('/profile')}
                  title="Мой профиль"
                >
                  <img 
                    src={getPlayerAvatar(currentUser)} 
                    alt={currentUser}
                    className="profile-avatar-small"
                  />
                  <span>{currentUser}</span>
                </button>
              </>
            ) : (
              <button 
                className="login-button"
                onClick={() => navigate('/auth')}
              >
                Войти
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="main">
        <div>
          {/* Top 3 Featured */}
          {topPlayers.length > 0 && (
            <section className="top-players">
              <h2 className="section-title">
                <CrownIcon className="crown-icon" />
                Топ игроков
              </h2>
              <div className="top-players-grid">
                {topPlayers.map((player, index) => (
                  <PlayerCard 
                    key={player.id} 
                    player={player} 
                    rank={index + 1}
                    variant="podium"
                  />
                ))}
              </div>
            </section>
          )}

            {/* Filters */}
            <div className="filters">
              <div className="kit-filters">
                <button 
                  className={`kit-filter-btn ${selectedKit === 'all' ? 'active' : ''}`}
                  onClick={() => setSelectedKit('all')}
                  aria-label="Показать все режимы"
                >
                  <span className="filter-text">Все режимы</span>
                </button>
                {availableKits.map((kit) => (
                  <button
                    key={kit}
                    className={`kit-filter-btn ${selectedKit === kit ? 'active' : ''}`}
                    onClick={() => setSelectedKit(kit)}
                    title={kit}
                  >
                    <img 
                      src={KIT_ICONS[kit]} 
                      alt={kit} 
                      className="filter-icon"
                    />
                    <span className="filter-text">{kit}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Player List */}
            <section className="leaderboard">
              {filteredPlayers.length === 0 ? (
                <div className="empty-leaderboard">
                  <div className="empty-leaderboard-content">
                    <h2>Нет игроков в рейтинге</h2>
                    <p>Рейтинг пока пуст. Зайдите на сервер и начните играть!</p>
                    {currentUser === 'admin' && (
                      <button 
                        className="btn-go-admin"
                        onClick={() => navigate('/admin')}
                      >
                        Перейти в админку
                      </button>
                    )}
                  </div>
                </div>
              ) : (
                <div className="leaderboard-table">
                  <div className="table-header">
                    <div className="col-rank">Место</div>
                    <div className="col-player">Игрок</div>
                    <div className="col-kits">Киты</div>
                  </div>
                  <div className="table-body">
                    {filteredPlayers.map((player, index) => (
                      <PlayerCard 
                        key={player.id} 
                        player={player} 
                        rank={index + 1}
                        variant="list"
                      />
                    ))}
                  </div>
                </div>
              )}
            </section>
          </div>
      </main>

      {/* Footer */}
      <footer className="footer">
        <div className="footer-content">
          <p>Suntier - Профессиональный PvP рейтинг</p>
          <p className="footer-note">Данные обновляются в реальном времени каждые 30 секунд</p>
        </div>
      </footer>
    </div>
  );
}

export default App;
