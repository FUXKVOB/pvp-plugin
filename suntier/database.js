import Database from 'better-sqlite3';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DB_PATH = path.join(__dirname, 'data', 'suntier.db');

// Initialize database
const db = new Database(DB_PATH);

// Enable WAL mode for better performance
db.pragma('journal_mode = WAL');

// Create tables if they don't exist
export function initDatabase() {
  // Players table
  db.exec(`
    CREATE TABLE IF NOT EXISTS players (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      score INTEGER DEFAULT 0,
      rank INTEGER DEFAULT 0,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    )
  `);

  // Player kits table (one-to-many relationship)
  db.exec(`
    CREATE TABLE IF NOT EXISTS player_kits (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      player_id INTEGER NOT NULL,
      kit_name TEXT NOT NULL,
      tier_rank TEXT NOT NULL,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
      UNIQUE(player_id, kit_name)
    )
  `);

  // Admin users table
  db.exec(`
    CREATE TABLE IF NOT EXISTS admin_users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      is_active BOOLEAN DEFAULT 1,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      last_login DATETIME
    )
  `);

  // Insert default admin if not exists
  const defaultAdmin = db.prepare('SELECT * FROM admin_users WHERE username = ?').get('admin');
  if (!defaultAdmin) {
    // Default password: admin123 (in production, use proper hashing)
    db.prepare(`
      INSERT INTO admin_users (username, password_hash) 
      VALUES (?, ?)
    `).run('admin', 'admin123');
    console.log('✅ Default admin created: username=admin, password=admin123');
  }

  // Seed mock players if database is empty
  const existingPlayers = db.prepare('SELECT COUNT(*) as count FROM players').get();
  if (existingPlayers.count === 0) {
    const mockPlayers = [
      { username: 'supminer', score: 15, kits: { Mace: 'HT1', Sword: 'HT2', Axe: 'MT1' } },
      { username: 'DarkPhoenix', score: 12, kits: { Sword: 'HT1', UHC: 'HT2' } },
      { username: 'NinjaStrike', score: 9, kits: { Axe: 'HT1', Mace: 'MT1', Potion: 'LT1' } },
      { username: 'CrystalMage', score: 6, kits: { Crystal: 'HT2', Sword: 'MT2' } },
      { username: 'ShadowBlade', score: 4, kits: { Sword: 'MT1', Axe: 'LT1' } }
    ];

    const insertPlayer = db.prepare('INSERT INTO players (username, score, rank) VALUES (?, ?, ?)');
    const insertKit = db.prepare('INSERT INTO player_kits (player_id, kit_name, tier_rank) VALUES (?, ?, ?)');

    mockPlayers.forEach((player, index) => {
      const result = insertPlayer.run(player.username, player.score, index + 1);
      const playerId = result.lastInsertRowid;
      
      Object.entries(player.kits).forEach(([kitName, tierRank]) => {
        insertKit.run(playerId, kitName, tierRank);
      });
    });

    console.log('✅ Mock players seeded successfully');
  }

  console.log('✅ Database initialized successfully');
}

// Player operations
export const PlayerDB = {
  // Get all players with their kits
  getAll() {
    const players = db.prepare(`
      SELECT id, username, score, rank, created_at
      FROM players
      ORDER BY score DESC, username ASC
    `).all();

    // Get kits for each player
    const getKits = db.prepare(`
      SELECT kit_name, tier_rank
      FROM player_kits
      WHERE player_id = ?
    `);

    return players.map((player, index) => {
      const kits = getKits.all(player.id);
      const kitsObject = {};
      kits.forEach(kit => {
        kitsObject[kit.kit_name] = kit.tier_rank;
      });

      return {
        id: player.id.toString(),
        username: player.username,
        score: player.score,
        rank: index + 1,
        kits: kitsObject
      };
    });
  },

  // Get player by ID
  getById(id) {
    const player = db.prepare('SELECT * FROM players WHERE id = ?').get(id);
    if (!player) return null;

    const kits = db.prepare('SELECT kit_name, tier_rank FROM player_kits WHERE player_id = ?').all(id);
    const kitsObject = {};
    kits.forEach(kit => {
      kitsObject[kit.kit_name] = kit.tier_rank;
    });

    return {
      id: player.id.toString(),
      username: player.username,
      score: player.score,
      rank: player.rank,
      kits: kitsObject
    };
  },

  // Get player by username
  getByUsername(username) {
    const player = db.prepare('SELECT * FROM players WHERE username = ?').get(username);
    if (!player) return null;

    const kits = db.prepare('SELECT kit_name, tier_rank FROM player_kits WHERE player_id = ?').all(player.id);
    const kitsObject = {};
    kits.forEach(kit => {
      kitsObject[kit.kit_name] = kit.tier_rank;
    });

    return {
      id: player.id.toString(),
      username: player.username,
      score: player.score,
      rank: player.rank,
      kits: kitsObject
    };
  },

  // Create new player
  create(username) {
    const result = db.prepare('INSERT INTO players (username, score, rank) VALUES (?, 0, 0)').run(username);
    return this.getById(result.lastInsertRowid);
  },

  // Update player
  update(id, data) {
    const { username, score } = data;
    
    if (username !== undefined) {
      db.prepare('UPDATE players SET username = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?').run(username, id);
    }
    if (score !== undefined) {
      db.prepare('UPDATE players SET score = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?').run(score, id);
    }

    return this.getById(id);
  },

  // Delete player
  delete(id) {
    // Kits will be deleted automatically due to ON DELETE CASCADE
    const result = db.prepare('DELETE FROM players WHERE id = ?').run(id);
    return result.changes > 0;
  },

  // Update all player ranks based on score
  updateRanks() {
    const players = db.prepare('SELECT id FROM players ORDER BY score DESC').all();
    const updateRank = db.prepare('UPDATE players SET rank = ? WHERE id = ?');
    
    players.forEach((player, index) => {
      updateRank.run(index + 1, player.id);
    });
  }
};

// Kit operations
export const KitDB = {
  // Add or update kit for player
  setKit(playerId, kitName, tierRank) {
    const existing = db.prepare('SELECT id FROM player_kits WHERE player_id = ? AND kit_name = ?').get(playerId, kitName);
    
    if (existing) {
      db.prepare('UPDATE player_kits SET tier_rank = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?')
        .run(tierRank, existing.id);
    } else {
      db.prepare('INSERT INTO player_kits (player_id, kit_name, tier_rank) VALUES (?, ?, ?)')
        .run(playerId, kitName, tierRank);
    }
  },

  // Remove kit from player
  removeKit(playerId, kitName) {
    const result = db.prepare('DELETE FROM player_kits WHERE player_id = ? AND kit_name = ?').run(playerId, kitName);
    return result.changes > 0;
  },

  // Get all kits for a player
  getPlayerKits(playerId) {
    return db.prepare('SELECT kit_name, tier_rank FROM player_kits WHERE player_id = ?').all(playerId);
  },

  // Remove all kits for a player
  removeAllPlayerKits(playerId) {
    db.prepare('DELETE FROM player_kits WHERE player_id = ?').run(playerId);
  }
};

// Admin operations
export const AdminDB = {
  // Verify admin credentials
  verify(username, password) {
    const admin = db.prepare('SELECT * FROM admin_users WHERE username = ? AND is_active = 1').get(username);
    if (!admin) return null;
    
    // Simple password check (in production, use bcrypt)
    if (admin.password_hash !== password) return null;
    
    // Update last login
    db.prepare('UPDATE admin_users SET last_login = CURRENT_TIMESTAMP WHERE id = ?').run(admin.id);
    
    return {
      id: admin.id,
      username: admin.username
    };
  },

  // Create new admin
  create(username, password) {
    try {
      const result = db.prepare('INSERT INTO admin_users (username, password_hash) VALUES (?, ?)').run(username, password);
      return { id: result.lastInsertRowid, username };
    } catch (error) {
      if (error.message.includes('UNIQUE constraint failed')) {
        throw new Error('Username already exists');
      }
      throw error;
    }
  },

  // Get all admins
  getAll() {
    return db.prepare('SELECT id, username, is_active, created_at, last_login FROM admin_users').all();
  },

  // Toggle admin active status
  toggleActive(id, isActive) {
    db.prepare('UPDATE admin_users SET is_active = ? WHERE id = ?').run(isActive ? 1 : 0, id);
  },

  // Delete admin
  delete(id) {
    const result = db.prepare('DELETE FROM admin_users WHERE id = ?').run(id);
    return result.changes > 0;
  }
};

// Statistics
export const StatsDB = {
  // Get player count
  getPlayerCount() {
    return db.prepare('SELECT COUNT(*) as count FROM players').get().count;
  },

  // Get kit distribution
  getKitDistribution() {
    return db.prepare(`
      SELECT kit_name, tier_rank, COUNT(*) as count
      FROM player_kits
      GROUP BY kit_name, tier_rank
      ORDER BY kit_name, tier_rank
    `).all();
  },

  // Get top players by kit
  getTopPlayersByKit(kitName, limit = 10) {
    return db.prepare(`
      SELECT p.username, pk.tier_rank
      FROM players p
      JOIN player_kits pk ON p.id = pk.player_id
      WHERE pk.kit_name = ?
      ORDER BY p.score DESC
      LIMIT ?
    `).all(kitName, limit);
  }
};

export default db;
