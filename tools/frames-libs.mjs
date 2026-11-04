#!/usr/bin/env node
/** Inflate the Amazon Linux 2023 shared libraries and SwiftShader bundled with
 *  @sparticuz/chromium so headless Chromium can run in the sandbox. Run once
 *  after installing the optional dev tooling. After this, launch Chromium with
 *  LD_LIBRARY_PATH=/tmp/al2023/lib:/tmp/al2023/usr/lib64. */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { brotliDecompressSync } from 'node:zlib';
import { execSync } from 'node:child_process';

const bin = 'node_modules/@sparticuz/chromium/bin';
if (!existsSync(bin)) {
  console.error('Run: npm install --no-save --package-lock=false @sparticuz/chromium playwright-core');
  process.exit(2);
}
mkdirSync('/tmp/al2023', { recursive: true });
writeFileSync('/tmp/al2023/al2023.tar', brotliDecompressSync(readFileSync(`${bin}/al2023.tar.br`)));
execSync('tar -xf /tmp/al2023/al2023.tar -C /tmp/al2023');
writeFileSync('/tmp/swiftshader.tar', brotliDecompressSync(readFileSync(`${bin}/swiftshader.tar.br`)));
execSync('tar -xf /tmp/swiftshader.tar -C /tmp');
console.log('Libraries inflated. Use LD_LIBRARY_PATH=/tmp/al2023/lib:/tmp/al2023/usr/lib64');
