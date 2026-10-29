import express from 'express';
import { createServer as createViteServer } from 'vite';
import path from 'node:path';

const app = express();
const port = process.env.PORT ? Number.parseInt(process.env.PORT, 10) : 3000;

async function startServer() {
  if (process.env.NODE_ENV === 'production') {
    const outputDir = path.resolve('dist');
    app.use(express.static(outputDir));
    app.get('*', (_request, response) => response.sendFile(path.join(outputDir, 'index.html')));
  } else {
    const vite = await createViteServer({
      server: {
        middlewareMode: true,
        allowedHosts: ['.e2b.app', 'localhost', '127.0.0.1'],
      },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(port, '0.0.0.0', () => {
    console.log(`EditorsHub is running on port ${port}`);
  });
}

void startServer();
