'use strict';

/**
 * scripts/syntax-check.js
 * Mengesahkan sintaks semua fail JavaScript projek (node --check)
 * dan memulangkan exit code bukan-sifar jika ada ralat.
 */

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOTS = ['src', 'scripts'];
let fileCount = 0;
let errorCount = 0;

function walk(directory) {
  const entries = fs.readdirSync(directory, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath);
    } else if (entry.isFile() && entry.name.endsWith('.js')) {
      fileCount += 1;
      try {
        execFileSync(process.execPath, ['--check', fullPath], { stdio: 'pipe' });
        console.log(`  OK  ${fullPath}`);
      } catch (err) {
        errorCount += 1;
        console.error(`  GAGAL  ${fullPath}`);
        console.error(String(err.stderr || err.message));
      }
    }
  }
}

for (const root of ROOTS) {
  if (fs.existsSync(root)) {
    walk(root);
  }
}

console.log(`\nSemak sintaks: ${fileCount} fail, ${errorCount} ralat.`);
process.exit(errorCount === 0 ? 0 : 1);
