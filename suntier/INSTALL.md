# Installation Instructions

## Install Dependencies

Run the following command in the `suntier` directory:

```bash
npm install
```

This will install all dependencies including:
- React 19.2.4 (latest)
- React DOM 19.2.4
- Framer Motion 12.34.3 (latest)
- TypeScript 5.7.3
- Vite 7.3.1 (latest)
- ESLint for code quality

## Run Development Server

```bash
npm run dev
```

The app will be available at `http://localhost:5173`

## Build for Production

```bash
npm run build
```

## Lint Code

```bash
npm run lint
```

## Features

### Animations
- ✨ Smooth stagger animations with Framer Motion 12
- 🎭 Card entrance animations with spring physics
- 🌊 Kit badge cascade animations
- 💫 Shimmer effects on avatars
- 🏆 Pulsing glow for top 3 ranks
- ♿ Respects `prefers-reduced-motion` for accessibility

### Performance
- ⚡ Vite 7 with optimized build configuration
- 📦 Code splitting for vendors (React, Framer Motion)
- 🔥 Hot Module Replacement (HMR)
- 🚀 ESBuild minification (10-100x faster)
- 📊 Warmup configuration for faster dev server

### Design
- 🌫️ Glassmorphism blur effects
- 🎮 Player cards with kit badges
- 🏆 Top 3 players showcase with special styling
- 🔍 Search and filter functionality
- 📱 Responsive grid layout
- 🎨 Tier-based color coding

### Code Quality
- 📝 TypeScript strict mode
- 🔍 ESLint configuration
- 🎯 Type-safe props and state
- 🧹 No `any` types allowed

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+

## Performance Metrics

Target Web Vitals:
- LCP (Largest Contentful Paint) < 2.5s
- FID (First Input Delay) < 100ms
- CLS (Cumulative Layout Shift) < 0.1

## Troubleshooting

### Slow startup?
- Disable browser cache in DevTools
- Check for browser extensions interfering
- Run `npm install` to ensure all deps are installed

### Animations not working?
- Check if `prefers-reduced-motion` is enabled in OS
- Verify Framer Motion is installed correctly
- Clear browser cache

## Learn More

- [React 19 Documentation](https://react.dev)
- [Framer Motion 12 Docs](https://www.framer.com/motion/)
- [Vite 7 Guide](https://vitejs.dev)
- [Best Practices 2026](./BEST_PRACTICES_2026.md)

