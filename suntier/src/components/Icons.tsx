import React from 'react';

interface TierBadgeProps {
  tier: string;
  size?: 'sm' | 'md' | 'lg';
}

export const TierBadge: React.FC<TierBadgeProps> = ({ tier, size = 'md' }) => {
  const getTierColor = (tier: string): string => {
    const colors: Record<string, string> = {
      'S+': '#FFD700',
      'S': '#FF6B35',
      'A+': '#FF4757',
      'A': '#FF6B81',
      'B+': '#70A1FF',
      'B': '#5F9EA0',
      'C+': '#7BED9F',
      'C': '#A4B0BE',
      'D': '#636E72',
      'Unranked': '#2D3436',
    };
    return colors[tier] || '#636E72';
  };

  const sizeClasses = {
    sm: 'tier-badge-sm',
    md: 'tier-badge-md',
    lg: 'tier-badge-lg',
  };

  return (
    <div 
      className={`tier-badge ${sizeClasses[size]}`}
      style={{ 
        '--tier-color': getTierColor(tier),
        boxShadow: `0 0 20px ${getTierColor(tier)}40`
      } as React.CSSProperties}
    >
      <svg viewBox="0 0 40 40" className="tier-shield">
        <defs>
          <linearGradient id={`grad-${tier}`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor={getTierColor(tier)} stopOpacity="1" />
            <stop offset="100%" stopColor={getTierColor(tier)} stopOpacity="0.6" />
          </linearGradient>
        </defs>
        <path 
          d="M20 2 L36 10 L36 22 C36 30 28 36 20 38 C12 36 4 30 4 22 L4 10 Z" 
          fill={`url(#grad-${tier})`}
          stroke={getTierColor(tier)}
          strokeWidth="1"
        />
      </svg>
      <span className="tier-text">{tier}</span>
    </div>
  );
};

interface CountryFlagProps {
  code: string;
  size?: number;
}

export const CountryFlag: React.FC<CountryFlagProps> = ({ code, size = 16 }) => {
  return (
    <img 
      src={`https://flagcdn.com/w20/${code.toLowerCase()}.png`}
      alt={code}
      width={size * 1.25}
      height={size}
      className="country-flag"
      loading="lazy"
    />
  );
};

interface SwordIconProps {
  className?: string;
}

export const SwordIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M14.5 17.5L3 6V3h3l11.5 11.5" />
    <path d="M13 19l6-6" />
    <path d="M16 16l4 4" />
    <path d="M19 21l2-2" />
  </svg>
);

export const TrophyIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6" />
    <path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18" />
    <path d="M4 22h16" />
    <path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22" />
    <path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22" />
    <path d="M18 2H6v7a6 6 0 0 0 12 0V2Z" />
  </svg>
);

export const FireIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z" />
  </svg>
);

export const SkullIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="12" r="8" />
    <path d="M8 14s1.5 2 4 2 4-2 4-2" />
    <line x1="9" y1="9" x2="9.01" y2="9" />
    <line x1="15" y1="9" x2="15.01" y2="9" />
  </svg>
);

export const ClockIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="12" r="10" />
    <polyline points="12 6 12 12 16 14" />
  </svg>
);

export const ChartIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <line x1="12" y1="20" x2="12" y2="10" />
    <line x1="18" y1="20" x2="18" y2="4" />
    <line x1="6" y1="20" x2="6" y2="16" />
  </svg>
);

export const SearchIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="11" cy="11" r="8" />
    <line x1="21" y1="21" x2="16.65" y2="16.65" />
  </svg>
);

export const StarIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="currentColor">
    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
  </svg>
);

export const CrownIcon: React.FC<SwordIconProps> = ({ className = '' }) => (
  <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="currentColor">
    <path d="M2 4l3 12h14l3-12-6 7-4-7-4 7-6-7zm3 14h14v2H5v-2z" />
  </svg>
);
