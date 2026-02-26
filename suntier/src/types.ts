export type TierRank = 'HT5' | 'LT5' | 'HT4' | 'LT4' | 'HT3' | 'LT3' | 'HT2' | 'LT2' | 'HT1' | 'LT1';

export interface Player {
  id: string;
  username: string;
  score: number;
  rank: number;
  kits: {
    [kitName: string]: TierRank;
  };
}

export interface TierConfig {
  rank: TierRank;
  position: number;
  color: string;
  bgColor: string;
  glowColor: string;
}

export const KIT_ICONS: { [key: string]: string } = {
  'Crystal': '/assets/cryst.png',
  'Mace': '/assets/mace.png',
  'Sword': '/assets/sword.png',
  'Axe': '/assets/Axe.png',
  'UHC': '/assets/UHC.png',
  'Potion': '/assets/Potion.png'
};

export const TIER_CONFIGS: TierConfig[] = [
  { rank: 'HT5', position: 10, color: '#FFD700', bgColor: '#1A1510', glowColor: 'rgba(255, 215, 0, 0.4)' },
  { rank: 'LT5', position: 9, color: '#FFA500', bgColor: '#1A1310', glowColor: 'rgba(255, 165, 0, 0.4)' },
  { rank: 'HT4', position: 8, color: '#FF6B35', bgColor: '#1A1210', glowColor: 'rgba(255, 107, 53, 0.4)' },
  { rank: 'LT4', position: 7, color: '#FF4757', bgColor: '#1A1012', glowColor: 'rgba(255, 71, 87, 0.4)' },
  { rank: 'HT3', position: 6, color: '#FF6B81', bgColor: '#1A1014', glowColor: 'rgba(255, 107, 129, 0.4)' },
  { rank: 'LT3', position: 5, color: '#70A1FF', bgColor: '#101418', glowColor: 'rgba(112, 161, 255, 0.4)' },
  { rank: 'HT2', position: 4, color: '#5F9EA0', bgColor: '#101618', glowColor: 'rgba(95, 158, 160, 0.4)' },
  { rank: 'LT2', position: 3, color: '#7BED9F', bgColor: '#101812', glowColor: 'rgba(123, 237, 159, 0.4)' },
  { rank: 'HT1', position: 2, color: '#A4B0BE', bgColor: '#141618', glowColor: 'rgba(164, 176, 190, 0.4)' },
  { rank: 'LT1', position: 1, color: '#636E72', bgColor: '#121416', glowColor: 'rgba(99, 110, 114, 0.4)' },
];

export function getTierByPosition(position: number): TierConfig {
  const tier = TIER_CONFIGS.find(t => t.position === position);
  return tier ?? TIER_CONFIGS[TIER_CONFIGS.length - 1]!;
}

export function getPlayerAvatar(username: string): string {
  return `https://storage.cistiers.com/renders/bust/${username}.webp`;
}
