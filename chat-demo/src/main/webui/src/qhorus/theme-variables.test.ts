import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve, join } from 'path';
import { readdirSync, statSync } from 'fs';

const QHORUS_DIR = resolve(__dirname);

const VALID_PREFIXES = [
  '--pages-neutral-',
  '--pages-accent-',
  '--pages-success-',
  '--pages-warning-',
  '--pages-danger-',
  '--pages-info-',
  '--pages-space-',
  '--pages-font-',
  '--pages-line-height-',
  '--pages-duration-',
  '--pages-ease-',
  '--pages-radius-',
  '--pages-shadow-',
  '--pages-surface-',
];

const INVALID_PAGES_VARS = /var\(--pages-(?:bg|text|white|border|font(?!-))[^)]*\)/g;

function collectTsFiles(dir: string): string[] {
  const files: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry.endsWith('.test.ts') || entry === 'types.ts' || entry === 'events.ts') continue;
    if (statSync(full).isDirectory()) {
      files.push(...collectTsFiles(full));
    } else if (entry.endsWith('.ts')) {
      files.push(full);
    }
  }
  return files;
}

describe('theme variable usage', () => {
  const files = collectTsFiles(QHORUS_DIR);

  for (const file of files) {
    const relative = file.replace(QHORUS_DIR + '/', '');

    it(`${relative} uses only valid pages-ui-tokens variable names`, () => {
      const content = readFileSync(file, 'utf-8');
      const matches = content.match(INVALID_PAGES_VARS);
      if (matches) {
        const unique = [...new Set(matches)];
        throw new Error(
          `Found non-theme CSS variables (use --pages-neutral-*, --pages-accent-* etc.):\n` +
          unique.map(m => `  ${m}`).join('\n')
        );
      }
    });
  }
});
