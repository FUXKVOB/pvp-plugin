import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react({
      // React 19 automatic JSX runtime
      jsxRuntime: 'automatic',
      // Enable Fast Refresh
      fastRefresh: true,
    })
  ],
  
  // Performance optimizations
  build: {
    // Target modern browsers for better performance
    target: 'esnext',
    // Enable minification
    minify: 'esbuild',
    // Optimize chunk splitting
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom'],
          'animation-vendor': ['framer-motion']
        }
      }
    },
    // Increase chunk size warning limit
    chunkSizeWarningLimit: 1000,
    // Enable CSS code splitting
    cssCodeSplit: true,
    // Source maps for production debugging
    sourcemap: false,
  },
  
  // Development server optimizations
  server: {
    // Enable HMR
    hmr: true,
    // Warm up frequently used files
    warmup: {
      clientFiles: [
        './src/App.tsx',
        './src/components/**/*.tsx',
        './src/types.ts'
      ]
    }
  },
  
  // Optimize dependencies
  optimizeDeps: {
    include: ['react', 'react-dom', 'framer-motion'],
    // Force pre-bundling
    force: false
  },
  
  // Enable esbuild for faster builds
  esbuild: {
    logOverride: { 'this-is-undefined-in-esm': 'silent' }
  }
})
