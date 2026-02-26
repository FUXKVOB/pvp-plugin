import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Player, TierRank } from '../types';
import { KIT_ICONS, TIER_CONFIGS } from '../types';
import './Admin.css';

interface AdminProps {
  onLogout: () => void;
}

const AVAILABLE_KITS = ['Crystal', 'Mace', 'Sword', 'Axe', 'UHC', 'Potion'];
const AVAILABLE_TIERS: TierRank[] = ['HT5', 'LT5', 'HT4', 'LT4', 'HT3', 'LT3', 'HT2', 'LT2', 'HT1', 'LT1'];

export const Admin: React.FC<AdminProps> = ({ onLogout }) => {
  const navigate = useNavigate();
  const [players, setPlayers] = useState<Player[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingPlayer, setEditingPlayer] = useState<Player | null>(null);
  const [newPlayerUsername, setNewPlayerUsername] = useState('');

  useEffect(() => {
    loadPlayers();
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

  const saveToFile = async (data: Player[]) => {
    try {
      const response = await fetch('http://localhost:3001/api/players', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
      });

      if (!response.ok) {
        throw new Error('Failed to save players');
      }

      const result = await response.json();
      console.log('✅ Данные успешно сохранены:', result);
      
      // Показываем уведомление об успехе
      alert('✅ Данные успешно сохранены!');
    } catch (error) {
      console.error('❌ Ошибка сохранения:', error);
      alert('❌ Ошибка сохранения данных. Проверьте, что сервер запущен (npm run server)');
    }
  };

  const calculateScore = (kits: { [key: string]: TierRank }): number => {
    return Object.values(kits).reduce((sum, tier) => {
      const config = TIER_CONFIGS.find(t => t.rank === tier);
      return sum + (config?.position || 0);
    }, 0);
  };

  const handleAddPlayer = () => {
    if (!newPlayerUsername.trim()) return;

    const newPlayer: Player = {
      id: Date.now().toString(),
      username: newPlayerUsername.trim(),
      score: 0,
      rank: players.length + 1,
      kits: {}
    };

    const updatedPlayers = [...players, newPlayer];
    setPlayers(updatedPlayers);
    setNewPlayerUsername('');
    saveToFile(updatedPlayers);
  };

  const handleDeletePlayer = (playerId: string) => {
    if (!confirm('Удалить этого игрока?')) return;
    
    const updatedPlayers = players.filter(p => p.id !== playerId);
    setPlayers(updatedPlayers);
    saveToFile(updatedPlayers);
  };

  const handleEditPlayer = (player: Player) => {
    setEditingPlayer({ ...player });
  };

  const handleSavePlayer = () => {
    if (!editingPlayer) return;

    const score = calculateScore(editingPlayer.kits);
    const updatedPlayer = { ...editingPlayer, score };
    
    const updatedPlayers = players.map(p => 
      p.id === editingPlayer.id ? updatedPlayer : p
    ).sort((a, b) => b.score - a.score)
      .map((p, index) => ({ ...p, rank: index + 1 }));

    setPlayers(updatedPlayers);
    setEditingPlayer(null);
    saveToFile(updatedPlayers);
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
          <button onClick={() => navigate('/')} className="btn-back">
            На главную
          </button>
          <button onClick={onLogout} className="btn-logout">
            Выйти
          </button>
        </div>
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
