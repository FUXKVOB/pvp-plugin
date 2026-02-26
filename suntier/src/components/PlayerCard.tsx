import React, { memo } from 'react';
import type { Player } from '../types';
import { TIER_CONFIGS, getPlayerAvatar, KIT_ICONS } from '../types';
import './PlayerCard.css';

interface PlayerCardProps {
  player: Player;
  rank?: number;
  variant?: 'default' | 'compact' | 'featured' | 'list' | 'podium';
}

export const PlayerCard: React.FC<PlayerCardProps> = memo(({ 
  player, 
  rank,
  variant = 'default'
}) => {
  if (variant === 'podium') {
    return (
      <div className={`podium-card rank-${rank}`}>
        <div className="podium-rank-badge">
          #{rank}
        </div>
        
        <div className="podium-avatar">
          <img src={getPlayerAvatar(player.username)} alt={player.username} loading="lazy" />
        </div>
        
        <div className="podium-info">
          <h3 className="podium-username">{player.username}</h3>
        </div>
        
        <div className="podium-kits">
          {Object.entries(player.kits).map(([kitName, tier]) => {
            const tierConfig = TIER_CONFIGS.find(t => t.rank === tier);
            return (
              <div 
                key={kitName}
                className="podium-kit"
                style={{
                  backgroundColor: tierConfig?.color || '#666'
                }}
                title={`${kitName} - ${tier}`}
              >
                <img src={KIT_ICONS[kitName]} alt={kitName} loading="lazy" />
                <span className="podium-kit-tier">{tier}</span>
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  if (variant === 'list') {
    return (
      <div className="player-row">
        <div className="col-rank">
          <span className={`rank-badge ${rank && rank <= 3 ? `top-${rank}` : ''}`}>
            #{rank}
          </span>
        </div>
        <div className="col-player">
          <img 
            src={getPlayerAvatar(player.username)} 
            alt={player.username} 
            className="player-avatar-small"
            loading="lazy" 
          />
          <span className="player-name">{player.username}</span>
        </div>
        <div className="col-kits">
          {Object.entries(player.kits).map(([kitName, tier]) => {
            const tierConfig = TIER_CONFIGS.find(t => t.rank === tier);
            return (
              <div 
                key={kitName}
                className="kit-badge-small"
                style={{
                  backgroundColor: tierConfig?.color || '#666'
                }}
                data-tier={tier}
                title={`${kitName} - ${tier}`}
              >
                <img src={KIT_ICONS[kitName]} alt={kitName} loading="lazy" />
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <div className={`player-card-new ${variant}`}>
      <div className="player-card-header">
        <div className="player-avatar-large">
          <img src={getPlayerAvatar(player.username)} alt={player.username} loading="lazy" />
        </div>
        <div className="player-name-score">
          <h3 className="player-username">{player.username}</h3>
        </div>
      </div>
      
      {rank && (
        <div className="player-rank-badge">
          <span className={`rank-number ${rank <= 3 ? `top-${rank}` : ''}`}>
            #{rank}
          </span>
        </div>
      )}

      <div className="player-kits">
        {Object.entries(player.kits).map(([kitName, tier]) => {
          const tierConfig = TIER_CONFIGS.find(t => t.rank === tier);
          return (
            <div 
              key={kitName}
              className="kit-card"
              style={{
                background: tierConfig?.color || '#666'
              }}
              title={`${kitName} - ${tier}`}
            >
              <img src={KIT_ICONS[kitName]} alt={kitName} loading="lazy" className="kit-card-icon" />
              <span className="kit-card-tier">{tier}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
});
