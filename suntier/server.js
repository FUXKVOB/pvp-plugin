import express from 'express';
import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import compression from 'compression';
import morgan from 'morgan';
import { z } from 'zod';
import { initDatabase, PlayerDB, KitDB, AdminDB, StatsDB } from './database.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 3001;

// Security middleware
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      scriptSrc: ["'self'"],
      imgSrc: ["'self'", "data:", "https:", "http:"],
      connectSrc: ["'self'", "http://localhost:*"],
    },
  },
  crossOriginEmbedderPolicy: false,
}));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // limit each IP to 100 requests per windowMs
  message: 'Too many requests from this IP, please try again later.',
  standardHeaders: true,
  legacyHeaders: false,
});

const strictLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20, // stricter limit for write operations
  message: 'Too many write requests, please try again later.',
});

app.use('/api/', limiter);

// CORS with specific origin
app.use(cors({
  origin: process.env.NODE_ENV === 'production' 
    ? ['https://yourdomain.com'] 
    : ['http://localhost:5173', 'http://localhost:3000'],
  credentials: true,
}));

// Compression
app.use(compression());

// Logging
app.use(morgan('combined'));

// Body parsing with size limits
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Validation schemas
const playerSchema = z.object({
  username: z.string().min(1).max(50).trim(),
});

const playerUpdateSchema = z.object({
  username: z.string().min(1).max(50).trim().optional(),
  score: z.number().int().min(0).optional(),
  kits: z.record(z.string()).optional(),
});

const adminLoginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
});

// Initialize database
initDatabase();

// ========== PLAYERS API ==========

// GET - получить всех игроков
app.get('/api/players', (req, res) => {
  try {
    const players = PlayerDB.getAll();
    res.json(players);
  } catch (error) {
    console.error('Error reading players:', error);
    res.status(500).json({ error: 'Failed to read players' });
  }
});

// GET - получить игрока по ID
app.get('/api/players/:id', (req, res) => {
  try {
    const player = PlayerDB.getById(req.params.id);
    if (!player) {
      return res.status(404).json({ error: 'Player not found' });
    }
    res.json(player);
  } catch (error) {
    console.error('Error reading player:', error);
    res.status(500).json({ error: 'Failed to read player' });
  }
});

// POST - создать нового игрока
app.post('/api/players', strictLimiter, (req, res) => {
  try {
    // Validate input
    const validation = playerSchema.safeParse(req.body);
    if (!validation.success) {
      return res.status(400).json({ 
        error: 'Invalid input', 
        details: validation.error.errors 
      });
    }

    const { username } = validation.data;
    const player = PlayerDB.create(username);
    res.status(201).json({ success: true, player });
  } catch (error) {
    console.error('Error creating player:', error);
    if (error.message.includes('UNIQUE constraint failed')) {
      return res.status(409).json({ error: 'Username already exists' });
    }
    res.status(500).json({ error: 'Failed to create player' });
  }
});

// PUT - обновить игрока
app.put('/api/players/:id', strictLimiter, (req, res) => {
  try {
    const { id } = req.params;
    
    // Validate input
    const validation = playerUpdateSchema.safeParse(req.body);
    if (!validation.success) {
      return res.status(400).json({ 
        error: 'Invalid input', 
        details: validation.error.errors 
      });
    }
    
    const { username, score, kits } = validation.data;
    
    // Update player basic info
    const player = PlayerDB.update(id, { username, score });
    if (!player) {
      return res.status(404).json({ error: 'Player not found' });
    }

    // Update kits if provided
    if (kits && typeof kits === 'object') {
      // Remove existing kits
      KitDB.removeAllPlayerKits(id);
      
      // Add new kits
      Object.entries(kits).forEach(([kitName, tierRank]) => {
        KitDB.setKit(id, kitName, tierRank);
      });
      
      // Recalculate score based on kits
      const TIER_POSITIONS = {
        'HT5': 10, 'LT5': 9, 'HT4': 8, 'LT4': 7, 'HT3': 6,
        'LT3': 5, 'HT2': 4, 'LT2': 3, 'HT1': 2, 'LT1': 1
      };
      
      const newScore = Object.values(kits).reduce((sum, tier) => {
        return sum + (TIER_POSITIONS[tier] || 0);
      }, 0);
      
      PlayerDB.update(id, { score: newScore });
    }

    // Update ranks
    PlayerDB.updateRanks();

    // Return updated player
    const updatedPlayer = PlayerDB.getById(id);
    res.json({ success: true, player: updatedPlayer });
  } catch (error) {
    console.error('Error updating player:', error);
    res.status(500).json({ error: 'Failed to update player' });
  }
});

// DELETE - удалить игрока
app.delete('/api/players/:id', strictLimiter, (req, res) => {
  try {
    const { id } = req.params;
    const success = PlayerDB.delete(id);
    
    if (!success) {
      return res.status(404).json({ error: 'Player not found' });
    }

    // Update ranks after deletion
    PlayerDB.updateRanks();
    
    res.json({ success: true, message: 'Player deleted successfully' });
  } catch (error) {
    console.error('Error deleting player:', error);
    res.status(500).json({ error: 'Failed to delete player' });
  }
});

// ========== ADMIN API ==========

// POST - admin login
app.post('/api/admin/login', strictLimiter, (req, res) => {
  try {
    // Validate input
    const validation = adminLoginSchema.safeParse(req.body);
    if (!validation.success) {
      return res.status(400).json({ 
        error: 'Invalid input', 
        details: validation.error.errors 
      });
    }
    
    const { username, password } = validation.data;

    const admin = AdminDB.verify(username, password);
    
    if (!admin) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    res.json({ success: true, admin });
  } catch (error) {
    console.error('Error during login:', error);
    res.status(500).json({ error: 'Login failed' });
  }
});

// GET - get all admins (for admin management)
app.get('/api/admin/users', (req, res) => {
  try {
    const admins = AdminDB.getAll();
    res.json(admins);
  } catch (error) {
    console.error('Error fetching admins:', error);
    res.status(500).json({ error: 'Failed to fetch admins' });
  }
});

// POST - create new admin
app.post('/api/admin/users', (req, res) => {
  try {
    const { username, password } = req.body;
    
    if (!username || !password) {
      return res.status(400).json({ error: 'Username and password are required' });
    }

    const admin = AdminDB.create(username, password);
    res.status(201).json({ success: true, admin });
  } catch (error) {
    console.error('Error creating admin:', error);
    if (error.message.includes('Username already exists')) {
      return res.status(409).json({ error: 'Username already exists' });
    }
    res.status(500).json({ error: 'Failed to create admin' });
  }
});

// PUT - toggle admin active status
app.put('/api/admin/users/:id', (req, res) => {
  try {
    const { id } = req.params;
    const { isActive } = req.body;
    
    AdminDB.toggleActive(id, isActive);
    res.json({ success: true });
  } catch (error) {
    console.error('Error updating admin:', error);
    res.status(500).json({ error: 'Failed to update admin' });
  }
});

// DELETE - delete admin
app.delete('/api/admin/users/:id', (req, res) => {
  try {
    const { id } = req.params;
    const success = AdminDB.delete(id);
    
    if (!success) {
      return res.status(404).json({ error: 'Admin not found' });
    }
    
    res.json({ success: true });
  } catch (error) {
    console.error('Error deleting admin:', error);
    res.status(500).json({ error: 'Failed to delete admin' });
  }
});

// ========== STATISTICS API ==========

// GET - get statistics
app.get('/api/stats', (req, res) => {
  try {
    const stats = {
      playerCount: StatsDB.getPlayerCount(),
      kitDistribution: StatsDB.getKitDistribution()
    };
    res.json(stats);
  } catch (error) {
    console.error('Error fetching stats:', error);
    res.status(500).json({ error: 'Failed to fetch statistics' });
  }
});

// Legacy endpoint for backward compatibility
app.post('/api/players/bulk', (req, res) => {
  try {
    const players = req.body;
    
    if (!Array.isArray(players)) {
      return res.status(400).json({ error: 'Players must be an array' });
    }

    // Clear existing data
    const allPlayers = PlayerDB.getAll();
    allPlayers.forEach(p => PlayerDB.delete(p.id));

    // Insert new players
    players.forEach(playerData => {
      const player = PlayerDB.create(playerData.username);
      
      // Add kits
      if (playerData.kits) {
        Object.entries(playerData.kits).forEach(([kitName, tierRank]) => {
          KitDB.setKit(player.id, kitName, tierRank);
        });
      }
    });

    // Update ranks
    PlayerDB.updateRanks();

    res.json({ success: true, message: 'Players saved successfully' });
  } catch (error) {
    console.error('Error saving players:', error);
    res.status(500).json({ error: 'Failed to save players' });
  }
});

// Also save to JSON file for backup
async function backupToJson() {
  try {
    const players = PlayerDB.getAll();
    const PLAYERS_FILE = path.join(__dirname, 'public', 'api', 'players.json');
    await fs.mkdir(path.dirname(PLAYERS_FILE), { recursive: true });
    await fs.writeFile(PLAYERS_FILE, JSON.stringify(players, null, 2), 'utf-8');
  } catch (error) {
    console.warn('Could not create JSON backup:', error.message);
  }
}

app.listen(PORT, () => {
  console.log(`✅ API Server running on http://localhost:${PORT}`);
  console.log(`📊 Database: SQLite (suntier.db)`);
  console.log(`🔑 Default admin: username=admin, password=admin123`);
});
