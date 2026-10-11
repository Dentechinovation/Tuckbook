# TuckBook Build & Deployment Guide

## Overview
TuckBook is a modern school tuck-shop management system built with:
- **Frontend**: React 19 + TanStack Router + TanStack Query
- **Styling**: Tailwind CSS 4 + shadcn/ui components
- **Backend**: TanStack Start (SSR-capable)
- **Storage**: LocalStorage (client-side, can be extended)
- **Deployment**: Cloudflare Workers (or any Node.js host)

---

## Prerequisites

- **Node.js**: v20+ (or Bun)
- **Package Manager**: pnpm, npm, or bun
- **Git**: For version control

---

## Local Development

### 1. Setup

```bash
# Clone the repository
git clone https://github.com/yourusername/tuckbook.git
cd tuckbook

# Install dependencies
pnpm install
# or: npm install
# or: bun install
```

### 2. Run Development Server

```bash
pnpm dev
```

The app will be available at `http://localhost:5173`

### 3. Development Features

- **Hot Module Reload (HMR)**: Changes reflect instantly
- **Type Safety**: Full TypeScript support
- **Route Auto-Generation**: Routes auto-generated from file structure
- **Demo Data**: Load sample school tuck-shop data from Settings

---

## Building for Production

### 1. Build the App

```bash
pnpm build
# or: npm run build
```

This creates:
- `.output/` - SSR server files
- `dist/` - Client bundle (static assets)

### 2. Preview Production Build

```bash
pnpm preview
```

Test the production build locally before deploying.

---

## Deployment Options

### Option 1: Cloudflare Workers (Recommended)

**Why Cloudflare Workers?**
- Global edge deployment (ultra-fast)
- Serverless (no server management)
- Free tier includes generous limits
- SSR support via `@tanstack/react-start`

#### Setup

1. **Install Wrangler CLI**
   ```bash
   pnpm add -D wrangler
   ```

2. **Configure `wrangler.toml`** (create if missing):
   ```toml
   name = "tuckbook"
   main = ".output/server/index.js"
   compatibility_date = "2024-01-15"
   
   [env.production]
   name = "tuckbook-prod"
   route = "yourdomain.com/*"
   zone_id = "your_zone_id"
   ```

3. **Deploy**
   ```bash
   pnpm build
   wrangler deploy
   ```

4. **Setup Custom Domain** (in Cloudflare Dashboard)
   - Add your domain
   - Point nameservers to Cloudflare
   - Set route in `wrangler.toml`

---

### Option 2: Vercel

**Why Vercel?**
- Next.js-like simplicity
- Automatic CI/CD
- Preview deployments for PRs
- Edge Functions support

#### Setup

1. **Push to GitHub**
   ```bash
   git push origin main
   ```

2. **Import on Vercel**
   - Go to [vercel.com](https://vercel.com)
   - Click "Add New Project"
   - Select your GitHub repo
   - Vercel auto-detects TanStack Start

3. **Deploy**
   - Vercel automatically deploys on push to main
   - Preview URLs for every PR

#### Environment Variables (if needed)
```bash
# In Vercel Dashboard: Settings > Environment Variables
VITE_API_URL=https://your-api.com
```

---

### Option 3: Self-Hosted (Node.js)

**Why Self-Host?**
- Full control
- Custom domain without intermediary
- On-premise deployment possible

#### Setup with PM2

1. **Install PM2**
   ```bash
   npm install -g pm2
   ```

2. **Build**
   ```bash
   pnpm build
   ```

3. **Create `ecosystem.config.js`**
   ```javascript
   module.exports = {
     apps: [{
       name: "tuckbook",
       script: "./.output/server/index.js",
       instances: "max",
       exec_mode: "cluster",
       env: {
         NODE_ENV: "production",
         PORT: 3000
       }
     }]
   };
   ```

4. **Start**
   ```bash
   pm2 start ecosystem.config.js
   pm2 save
   pm2 startup
   ```

5. **Setup Nginx Reverse Proxy**
   ```nginx
   server {
     listen 80;
     server_name yourdomain.com;
   
     location / {
       proxy_pass http://localhost:3000;
       proxy_http_version 1.1;
       proxy_set_header Upgrade $http_upgrade;
       proxy_set_header Connection 'upgrade';
       proxy_set_header Host $host;
       proxy_cache_bypass $http_upgrade;
     }
   }
   ```

6. **SSL with Let's Encrypt**
   ```bash
   sudo certbot --nginx -d yourdomain.com
   ```

---

### Option 4: Docker

#### Dockerfile

```dockerfile
FROM node:20-alpine AS builder

WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN npm install -g pnpm && pnpm install

COPY . .
RUN pnpm build

# Production image
FROM node:20-alpine

WORKDIR /app
COPY --from=builder /app/.output ./.output
COPY package.json pnpm-lock.yaml ./
RUN npm install -g pnpm && pnpm install --prod

EXPOSE 3000
ENV NODE_ENV=production
CMD ["node", "./.output/server/index.js"]
```

#### Build & Run

```bash
# Build
docker build -t tuckbook:latest .

# Run locally
docker run -p 3000:3000 tuckbook:latest

# Deploy to Docker Hub
docker tag tuckbook:latest yourusername/tuckbook:latest
docker push yourusername/tuckbook:latest
```

#### Deploy to Railway, Fly.io, or Render
- Push Docker image to their registries
- They auto-deploy from image

---

## Testing

### Run Tests

```bash
pnpm test
# Watch mode
pnpm test:watch
```

### Test Coverage

```bash
pnpm test -- --coverage
```

Tests include:
- `cart.test.ts` - Shopping cart logic
- `app-routing.test.tsx` - Route matching

---

## Code Quality

### Linting

```bash
pnpm lint
```

### Format Code

```bash
pnpm format
```

Configured with:
- ESLint 9
- Prettier
- TypeScript strict mode

---

## Environment Variables

Create `.env.local` (not committed):

```env
# API endpoints
VITE_API_URL=http://localhost:3000

# Feature flags
VITE_ENABLE_DEMO=true

# Analytics (optional)
VITE_GA_ID=UA-XXXXXXXXX-X
```

---

## Performance Optimization

### Bundle Analysis

```bash
# Check bundle size
pnpm build
# Look at dist/ size
```

### Image Optimization

- All images are SVGs from Lucide React (no rasterization needed)
- Tekzor logo loaded via JSON reference

### Code Splitting

- Routes auto-split via TanStack Router
- Lazy load heavy components (jsPDF, charts)

---

## Mobile Support

✅ **Already Responsive**
- Mobile navigation (bottom bar)
- Touch-optimized
- Camera barcode scanning
- Works offline (with sync queue)

### PWA Enhancement (Optional)

Add to `public/manifest.json`:
```json
{
  "name": "TuckBook",
  "short_name": "TuckBook",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#228b54",
  "icons": [...]
}
```

---

## Monitoring & Logging

### Sentry (Error Tracking)

```bash
pnpm add @sentry/react @sentry/tracing
```

In `src/start.ts`:
```typescript
import * as Sentry from "@sentry/react";

Sentry.init({
  dsn: process.env.VITE_SENTRY_DSN,
  environment: process.env.NODE_ENV,
});
```

### Server Logging

Already implemented:
- `error-capture.ts` - Catches and logs SSR errors
- `error-page.ts` - Error boundary UI
- Browser console for development

---

## Database Integration (Future)

Currently uses **LocalStorage**. To add a backend:

### Option 1: Supabase (PostgreSQL)
```typescript
import { createClient } from '@supabase/supabase-js'

const supabase = createClient(URL, KEY)
// Replace localStorage with supabase calls
```

### Option 2: Firebase
```typescript
import { initializeApp } from 'firebase/app'
import { getFirestore } from 'firebase/firestore'

const db = getFirestore(app)
// Use Firestore instead of localStorage
```

### Option 3: Custom Backend
- Use TanStack Start server functions
- Connect to any SQL/NoSQL database

---

## Authentication (Future)

Add with:
- **Clerk**: `npm install @clerk/clerk-react`
- **Auth0**: `npm install @auth0/auth0-react`
- **Supabase Auth**: Built into Supabase
- **Custom**: Session-based with TanStack Start

---

## Troubleshooting

### Build Fails
```bash
# Clear cache
rm -rf node_modules .pnpm-store dist .output
pnpm install
pnpm build
```

### Port Already in Use
```bash
# Change port
pnpm dev -- --port 5174
```

### TypeScript Errors
```bash
# Regenerate route types
rm src/routeTree.gen.ts
pnpm dev  # Auto-regenerates
```

### Build Size Issues
```bash
# Audit dependencies
pnpm audit
pnpm why <package-name>
```

---

## CI/CD Pipeline

### GitHub Actions Example

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy TuckBook

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v3
        with:
          node-version: "20"
          cache: "pnpm"
      
      - run: pnpm install
      - run: pnpm lint
      - run: pnpm test
      - run: pnpm build
      
      - name: Deploy to Cloudflare
        env:
          CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}
        run: pnpm exec wrangler deploy
```

---

## Support & Resources

- **TanStack Start Docs**: https://tanstack.com/start
- **React Router**: https://tanstack.com/router
- **Tailwind CSS**: https://tailwindcss.com
- **shadcn/ui**: https://ui.shadcn.com

---

## License

MIT - See LICENSE file

---

## Next Steps

1. ✅ Build locally: `pnpm dev`
2. ✅ Test: `pnpm test`
3. ✅ Deploy: Choose option above
4. 📱 Enable barcode scanning
5. 🔒 Add authentication
6. 📊 Connect database for multi-user
7. 📧 Setup email/SMS notifications

Happy building! 🚀
