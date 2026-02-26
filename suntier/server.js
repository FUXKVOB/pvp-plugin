import express from 'express';
import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';
import cors from 'cors';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 3001;

app.use(cors());
app.use(express.json());

// Путь к файлу с данными
const PLAYERS_FILE = path.join(__dirname, 'public', 'api', 'players.json');

// GET - получить всех игроков
app.get('/api/players', async (req, res) => {
  try {
    const data = await fs.readFile(PLAYERS_FILE, 'utf-8');
    res.json(JSON.parse(data));
  } catch (error) {
    console.error('Error reading players:', error);
    res.status(500).json({ error: 'Failed to read players' });
  }
});

// POST - сохранить игроков
app.post('/api/players', async (req, res) => {
  try {
    const players = req.body;
    
    // Валидация данных
    if (!Array.isArray(players)) {
      return res.status(400).json({ error: 'Players must be an array' });
    }

    // Сохраняем в public/api/players.json
    await fs.writeFile(
      PLAYERS_FILE,
      JSON.stringify(players, null, 2),
      'utf-8'
    );

    // Также сохраняем в dist/api/players.json если папка существует
    const distFile = path.join(__dirname, 'dist', 'api', 'players.json');
    try {
      await fs.mkdir(path.dirname(distFile), { recursive: true });
      await fs.writeFile(distFile, JSON.stringify(players, null, 2), 'utf-8');
    } catch (distError) {
      console.warn('Could not write to dist folder:', distError.message);
    }

    res.json({ success: true, message: 'Players saved successfully' });
  } catch (error) {
    console.error('Error saving players:', error);
    res.status(500).json({ error: 'Failed to save players' });
  }
});

app.listen(PORT, () => {
  console.log(`API Server running on http://localhost:${PORT}`);
  console.log(`Players file: ${PLAYERS_FILE}`);
});
