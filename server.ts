import express from 'express';
import fs from 'fs';
import path from 'path';
import JSZip from 'jszip';
import { createServer as createViteServer } from 'vite';

const ROOT_DIR = path.resolve(process.cwd());
const ANDROID_DIR = path.resolve(ROOT_DIR, 'android');
const WORKFLOW_FILE = path.resolve(ROOT_DIR, '.github', 'workflows', 'android-build.yml');

interface FileEntry {
  path: string;
  name: string;
  sizeBytes: number;
  category: string;
}

function classifyAndroidFile(relPath: string): string {
  if (relPath.startsWith('.github/')) return 'ci';
  if (relPath.endsWith('.gradle.kts') || relPath.endsWith('.toml') || relPath.endsWith('.properties') || relPath.endsWith('.pro')) return 'gradle';
  if (relPath.includes('/src/test/')) return 'unit-test';
  if (relPath.includes('/src/androidTest/')) return 'android-test';
  if (relPath.endsWith('.xml')) return 'resources';
  if (relPath.endsWith('.md')) return 'docs';
  if (relPath.includes('/core/security/')) return 'security';
  if (relPath.includes('/transfer/')) return 'transfer';
  if (relPath.includes('/data/network/')) return 'network';
  if (relPath.includes('/data/files/')) return 'files';
  if (relPath.includes('/data/local/')) return 'database';
  if (relPath.includes('/ui/') || relPath.includes('/core/designsystem/')) return 'ui';
  return 'core';
}

function collectAndroidFiles(): FileEntry[] {
  const results: FileEntry[] = [];

  function walk(dir: string) {
    if (!fs.existsSync(dir)) return;
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.name === '.gradle' || entry.name === 'build') continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else {
        const rel = path.relative(ROOT_DIR, full).split(path.sep).join('/');
        const stat = fs.statSync(full);
        results.push({
          path: rel,
          name: entry.name,
          sizeBytes: stat.size,
          category: classifyAndroidFile(rel),
        });
      }
    }
  }

  walk(ANDROID_DIR);

  if (fs.existsSync(WORKFLOW_FILE)) {
    const rel = path.relative(ROOT_DIR, WORKFLOW_FILE).split(path.sep).join('/');
    results.push({
      path: rel,
      name: 'android-build.yml',
      sizeBytes: fs.statSync(WORKFLOW_FILE).size,
      category: 'ci',
    });
  }

  return results.sort((a, b) => a.path.localeCompare(b.path));
}

function resolveAllowedFile(requestedRelPath: string): string | null {
  if (!requestedRelPath || typeof requestedRelPath !== 'string') return null;
  const normalized = requestedRelPath.replace(/^\/+/, '');
  const resolved = path.resolve(ROOT_DIR, normalized);

  const isInsideAndroid = resolved.startsWith(ANDROID_DIR + path.sep);
  const isWorkflow = resolved === WORKFLOW_FILE;
  if (!isInsideAndroid && !isWorkflow) return null;
  if (!fs.existsSync(resolved) || fs.statSync(resolved).isDirectory()) return null;
  return resolved;
}

async function startServer() {
  const app = express();

  app.get('/api/project/tree', (_req, res) => {
    const files = collectAndroidFiles();
    const totalBytes = files.reduce((acc, f) => acc + f.sizeBytes, 0);
    res.json({
      packageName: 'com.speedora.app',
      minSdk: 26,
      targetSdk: 35,
      fileCount: files.length,
      totalBytes,
      files,
    });
  });

  app.get('/api/project/file', (req, res) => {
    const relPath = typeof req.query.path === 'string' ? req.query.path : '';
    const resolved = resolveAllowedFile(relPath);
    if (!resolved) {
      res.status(404).json({ error: 'File not found or outside project boundary' });
      return;
    }
    const normalizedPath = path.relative(ROOT_DIR, resolved).split(path.sep).join('/');
    if (resolved.endsWith('.jar')) {
      const size = fs.statSync(resolved).size;
      res.json({
        path: normalizedPath,
        content: `[Binary JAR archive: ${path.basename(resolved)} (${size} bytes) — included intact in speedora-android-project.zip]`,
      });
      return;
    }
    const content = fs.readFileSync(resolved, 'utf8');
    res.json({
      path: normalizedPath,
      content,
    });
  });

  app.get('/api/project/download.zip', async (_req, res) => {
    try {
      const zip = new JSZip();
      const files = collectAndroidFiles();
      for (const file of files) {
        const resolved = resolveAllowedFile(file.path);
        if (resolved) {
          const isExec = file.path === 'android/gradlew';
          zip.file(file.path, fs.readFileSync(resolved), {
            unixPermissions: isExec ? '755' : '644',
          });
        }
      }
      const buffer = await zip.generateAsync({
        type: 'nodebuffer',
        compression: 'DEFLATE',
        platform: 'UNIX',
      });
      res.setHeader('Content-Type', 'application/zip');
      res.setHeader('Content-Disposition', 'attachment; filename="speedora-android-project.zip"');
      res.send(buffer);
    } catch {
      res.status(500).json({ error: 'Failed to generate project archive' });
    }
  });

  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distDir = path.resolve(ROOT_DIR, 'dist');
    app.use(express.static(distDir));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  }

  const port = Number(process.env.PORT) || 3000;
  app.listen(port, '0.0.0.0', () => {
    console.log(`SPEEDORA Project Portal listening on port ${port}`);
  });
}

startServer();
