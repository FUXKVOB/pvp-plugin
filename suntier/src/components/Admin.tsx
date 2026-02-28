import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Player, TierRank } from '../types';
import { KIT_ICONS } from '../types';
import './Admin.css';

interface AdminProps {
  onLogout: () => void;
}

interface AdminUser {
  id: number;
  username: string;
  is_active: number;
  created_at: string;
  last_login: string | null;
}

const AVAILABLE_KITS = ['Crystal', 'Mace', 'Sword', 'Axe', 'UHC', 'Potion'];
const AVAILABLE_TIERS: TierRank[] = ['HT5', 'LT5', 'HT4', 'LT4', 'HT3', 'LT3', 'HT2', 'LT2', 'HT1', 'LT1'];

export const Admin: React.FC<AdminProps> = ({ onLogout }) => {
  const navigate = useNavigate();
  const [players, setPlayers] = useState<Player[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingPlayer, setEditingPlayer] = useState<Player | null>(null);
  const [newPlayerUsername, setNewPlayerUsername] = useState('');
  
  // Admin management state
  const [admins, setAdmins] = useState<AdminUser[]>([]);
  const [showAdminPanel, setShowAdminPanel] = useState(false);
  const [newAdminUsername, setNewAdminUsername] = useState('');
  const [newAdminPassword, setNewAdminPassword] = useState('');
  const [stats, setStats] = useState({ playerCount: 0, kitDistribution: [] as any[] });

  useEffect(() => {
    loadPlayers();
    loadStats();
  }, []);

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

  const loadStats = async () => {
    try {
      const response = await fetch('http://localhost:3001/api/stats');
      if (response.ok) {
        const data = await response.json();
        setStats(data);
      }
    } catch (error) {
      console.error('Error loading stats:', error);
    }
  };

  const loadAdmins = async () => {
    try {
      const response = await fetch('http://localhost:3001/api/admin/users');
      if (response.ok) {
        const data = await response.json();
        setAdmins(data);
      }
    } catch (error) {
      console.error('Error loading admins:', error);
    }
  };

  // Score is now calculated on the server

  const handleAddPlayer = async () => {
    if (!newPlayerUsername.trim()) return;

    try {
      const response = await fetch('http://localhost:3001/api/players', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: newPlayerUsername.trim() })
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error || 'Failed to create player');
      }

      await loadPlayers();
      setNewPlayerUsername('');
      alert('✅ Игрок успешно добавлен!');
    } catch (error: any) {
      console.error('Error adding player:', error);
      alert('❌ ' + error.message);
    }
  };

  const handleDeletePlayer = async (playerId: string) => {
    if (!confirm('Удалить этого игрока?')) return;
    
    try {
      const response = await fetch(`http://localhost:3001/api/players/${playerId}`, {
        method: 'DELETE'
      });

      if (!response.ok) {
        throw new Error('Failed to delete player');
      }

      await loadPlayers();
      alert('✅ Игрок удален!');
    } catch (error) {
      console.error('Error deleting player:', error);
      alert('❌ Ошибка при удалении игрока');
    }
  };

  const handleEditPlayer = (player: Player) => {
    setEditingPlayer({ ...player });
  };

  const handleSavePlayer = async () => {
    if (!editingPlayer) return;

    try {
      const response = await fetch(`http://localhost:3001/api/players/${editingPlayer.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: editingPlayer.username,
          kits: editingPlayer.kits
        })
      });

      if (!response.ok) {
        throw new Error('Failed to update player');
      }

      await loadPlayers();
      setEditingPlayer(null);
      alert('✅ Изменения сохранены!');
    } catch (error) {
      console.error('Error saving player:', error);
      alert('❌ Ошибка при сохранении');
    }
  };

  // Admin management functions
  const handleShowAdminPanel = async () => {
    await loadAdmins();
    setShowAdminPanel(true);
  };

  const handleCreateAdmin = async () => {
    if (!newAdminUsername.trim() || !newAdminPassword.trim()) {
      alert('Введите логин и пароль');
      return;
    }

    try {
      const response = await fetch('http://localhost:3001/api/admin/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: newAdminUsername.trim(),
          password: newAdminPassword
        })
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error || 'Failed to create admin');
      }

      await loadAdmins();
      setNewAdminUsername('');
      setNewAdminPassword('');
      alert('✅ Администратор создан!');
    } catch (error: any) {
      alert('❌ ' + error.message);
    }
  };

  const handleToggleAdmin = async (id: number, isActive: boolean) => {
    try {
      await fetch(`http://localhost:3001/api/admin/users/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isActive })
      });
      await loadAdmins();
    } catch (error) {
      console.error('Error toggling admin:', error);
    }
  };

  const handleDeleteAdmin = async (id: number) => {
    if (!confirm('Удалить этого администратора?')) return;

    try {
      await fetch(`http://localhost:3001/api/admin/users/${id}`, {
        method: 'DELETE'
      });
      await loadAdmins();
    } catch (error) {
      console.error('Error deleting admin:', error);
    }
  };

  const handleAddKit = (kitName: string) => {
    if (!editingPlayer) return;
    if (editingPlayer.kits[kitName]) return;

    setEditingPlayer({
      ...editingPlayer,
      kits: {
        ...editingPlayer.kits,
        [kitName]: 'LT1'
      }
    });
  };

  const handleRemoveKit = (kitName: string) => {
    if (!editingPlayer) return;

    const { [kitName]: removed, ...remainingKits } = editingPlayer.kits;
    setEditingPlayer({
      ...editingPlayer,
      kits: remainingKits
    });
  };

  const handleChangeTier = (kitName: string, tier: TierRank) => {
    if (!editingPlayer) return;

    setEditingPlayer({
      ...editingPlayer,
      kits: {
        ...editingPlayer.kits,
        [kitName]: tier
      }
    });
  };

  const handleExport = () => {
    const jsonData = JSON.stringify(players, null, 2);
    const blob = new Blob([jsonData], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'players.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  if (loading) {
    return <div className="admin-loading">Загрузка...</div>;
  }

  return (
    <div className="admin-container">
      <div className="admin-header">
        <h1>Админ-панель</h1>
        <div className="admin-header-actions">
          <button onClick={handleExport} className="btn-export">
            Экспорт JSON
          </button>
          <button onClick={handleShowAdminPanel} className="btn-export">
            Управление админами
          </button>
          <button onClick={() => navigate('/')} className="btn-back">
            На главную
          </button>
          <button onClick={onLogout} className="btn-logout">
            Выйти
          </button>
        </div>
      </div>

      {/* Statistics */}
      <div className="stats-section" style={{ marginBottom: '24px', padding: '20px', background: 'rgba(255,255,255,0.02)', borderRadius: '16px', border: '1px solid rgba(255,255,255,0.06)' }}>
        <h3 style={{ marginBottom: '12px', color: '#fff' }}>Статистика</h3>
        <p style={{ color: 'rgba(255,255,255,0.7)' }}>Всего игроков: <strong style={{ color: '#fff' }}>{stats.playerCount}</strong></p>
      </div>

      <div className="admin-content">
        <div className="add-player-section">
          <h2>Добавить игрока</h2>
          <div className="add-player-form">
            <input
              type="text"
              placeholder="Имя игрока"
              value={newPlayerUsername}
              onChange={(e) => setNewPlayerUsername(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleAddPlayer()}
            />
            <button onClick={handleAddPlayer} className="btn-add">
              Добавить
            </button>
          </div>
        </div>

        <div className="players-list">
          <h2>Игроки ({players.length})</h2>
          {players.length === 0 ? (
            <div className="empty-state">
              <p>Нет игроков. Добавьте первого игрока выше.</p>
            </div>
          ) : (
            <div className="players-table">
              {players.map((player) => (
                <div key={player.id} className="player-item">
                  <div className="player-item-header">
                    <div className="player-item-info">
                      <span className="player-rank">#{player.rank}</span>
                      <span className="player-username">{player.username}</span>
                      <span className="player-score">Очки: {player.score}</span>
                    </div>
                    <div className="player-item-actions">
                      <button onClick={() => handleEditPlayer(player)} className="btn-edit">
                        Редактировать
                      </button>
                      <button onClick={() => handleDeletePlayer(player.id)} className="btn-delete">
                        Удалить
                      </button>
                    </div>
                  </div>
                  <div className="player-item-kits">
                    {Object.entries(player.kits).map(([kitName, tier]) => (
                      <div key={kitName} className="kit-badge-admin">
                        <img src={KIT_ICONS[kitName]} alt={kitName} />
                        <span>{tier}</span>
                      </div>
                    ))}
                    {Object.keys(player.kits).length === 0 && (
                      <span className="no-kits">Нет режимов</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Admin Management Modal */}
      {showAdminPanel && (
        <div className="modal-overlay" onClick={() => setShowAdminPanel(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '600px' }}>
            <div className="modal-header">
              <h2>Управление администраторами</h2>
              <button onClick={() => setShowAdminPanel(false)} className="btn-close">
                ✕
              </button>
            </div>

            <div className="modal-body">
              {/* Create new admin */}
              <div style={{ marginBottom: '24px', padding: '16px', background: 'rgba(255,255,255,0.03)', borderRadius: '12px' }}>
                <h4 style={{ marginBottom: '12px', color: '#fff' }}>Создать администратора</h4>
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <input
                    type="text"
                    placeholder="Логин"
                    value={newAdminUsername}
                    onChange={(e) => setNewAdminUsername(e.target.value)}
                    style={{ flex: 1, padding: '10px 14px', background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', color: '#fff', minWidth: '120px' }}
                  />
                  <input
                    type="password"
                    placeholder="Пароль"
                    value={newAdminPassword}
                    onChange={(e) => setNewAdminPassword(e.target.value)}
                    style={{ flex: 1, padding: '10px 14px', background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', color: '#fff', minWidth: '120px' }}
                  />
                  <button onClick={handleCreateAdmin} className="btn-add" style={{ padding: '10px 20px' }}>
                    Создать
                  </button>
                </div>
              </div>

              {/* Admin list */}
              <div>
                <h4 style={{ marginBottom: '12px', color: '#fff' }}>Список администраторов</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {admins.map(admin => (
                    <div key={admin.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.06)' }}>
                      <div>
                        <span style={{ color: '#fff', fontWeight: 600 }}>{admin.username}</span>
                        <span style={{ color: admin.is_active ? '#7BED9F' : '#FF6B6B', marginLeft: '12px', fontSize: '12px' }}>
                          {admin.is_active ? 'Активен' : 'Неактивен'}
                        </span>
                        {admin.last_login && (
                          <span style={{ color: 'rgba(255,255,255,0.4)', marginLeft: '12px', fontSize: '12px' }}>
                            Последний вход: {new Date(admin.last_login).toLocaleDateString()}
                          </span>
                        )}
                      </div>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        <button
                          onClick={() => handleToggleAdmin(admin.id, !admin.is_active)}
                          style={{ padding: '6px 12px', background: admin.is_active ? 'rgba(255,107,107,0.2)' : 'rgba(123,237,159,0.2)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '6px', color: '#fff', fontSize: '12px', cursor: 'pointer' }}
                        >
                          {admin.is_active ? 'Деактивировать' : 'Активировать'}
                        </button>
                        <button
                          onClick={() => handleDeleteAdmin(admin.id)}
                          style={{ padding: '6px 12px', background: 'rgba(255,80,80,0.1)', border: '1px solid rgba(255,80,80,0.3)', borderRadius: '6px', color: '#ff8080', fontSize: '12px', cursor: 'pointer' }}
                        >
                          Удалить
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="modal-footer">
              <button onClick={() => setShowAdminPanel(false)} className="btn-cancel">
                Закрыть
              </button>
            </div>
          </div>
        </div>
      )}

      {editingPlayer && (
        <div className="modal-overlay" onClick={() => setEditingPlayer(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Редактировать: {editingPlayer.username}</h2>
              <button onClick={() => setEditingPlayer(null)} className="btn-close">
                ✕
              </button>
            </div>

            <div className="modal-body">
              <div className="kits-editor">
                <h3>Режимы игрока</h3>
                
                <div className="available-kits">
                  <h4>Добавить режим:</h4>
                  <div className="kit-buttons">
                    {AVAILABLE_KITS.filter(kit => !editingPlayer.kits[kit]).map(kit => (
                      <button
                        key={kit}
                        onClick={() => handleAddKit(kit)}
                        className="btn-add-kit"
                      >
                        <img src={KIT_ICONS[kit]} alt={kit} />
                        {kit}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="player-kits-editor">
                  <h4>Текущие режимы:</h4>
                  {Object.keys(editingPlayer.kits).length === 0 ? (
                    <p className="no-kits">Нет режимов</p>
                  ) : (
                    <div className="kits-list">
                      {Object.entries(editingPlayer.kits).map(([kitName, tier]) => (
                        <div key={kitName} className="kit-editor-item">
                          <div className="kit-editor-info">
                            <img src={KIT_ICONS[kitName]} alt={kitName} />
                            <span className="kit-name">{kitName}</span>
                          </div>
                          <select
                            value={tier}
                            onChange={(e) => handleChangeTier(kitName, e.target.value as TierRank)}
                            className="tier-select"
                          >
                            {AVAILABLE_TIERS.map(t => (
                              <option key={t} value={t}>{t}</option>
                            ))}
                          </select>
                          <button
                            onClick={() => handleRemoveKit(kitName)}
                            className="btn-remove-kit"
                          >
                            Удалить
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>

            <div className="modal-footer">
              <button onClick={() => setEditingPlayer(null)} className="btn-cancel">
                Отмена
              </button>
              <button onClick={handleSavePlayer} className="btn-save">
                Сохранить
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
