import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const webFiles = new Set(['index.html', 'app.js', 'styles.css', 'sw.js', 'manifest.webmanifest', 'icon.svg']);

export function needsUpdateHistory(paths) {
  return paths.some(path => webFiles.has(path)
    || path === '.gitignore'
    || /^android\/(?:app\/src\/|gradle\/|.*\.(?:gradle|properties|ps1)$|gradlew(?:\.bat)?$)/.test(path)
    || /^ios\/(?:GuHealth\/|GuHealthCoreTests\/|GuHealthUITests\/|scripts\/|GuHealth\.xcodeproj\/|Package\.swift$)/.test(path)
    || /^\.github\/(?:scripts|workflows)\//.test(path));
}

export function validateUpdateHistory(paths, changelogPatch) {
  if (!needsUpdateHistory(paths)) return [];
  if (!paths.includes('CHANGELOG.md')) return ['程式有變更，請一併更新 CHANGELOG.md。'];
  const additions = changelogPatch.split('\n').filter(line => line.startsWith('+') && !line.startsWith('+++'));
  const hasVersion = additions.some(line => /^\+## \[[^\]\r\n]+\] - \d{4}-\d{2}-\d{2}\s*$/.test(line));
  const hasDescription = additions.some(line => /^\+- \S/.test(line));
  return hasVersion && hasDescription ? [] : ['請新增版本日期標題與至少一條變更說明。'];
}

function git(...args) {
  return execFileSync('git', args, { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
}

function validateRef(ref) {
  if (!/^[a-f0-9]{7,40}$/i.test(ref)) throw new Error('檢查範圍必須使用提交 SHA。');
  return ref;
}

function run() {
  const args = process.argv.slice(2);
  let diffArgs;
  if (args.includes('--staged') || !process.env.GITHUB_EVENT_PATH) {
    diffArgs = ['diff', '--cached'];
  } else {
    const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'));
    const head = validateRef(event.pull_request?.head?.sha ?? event.after);
    let base = event.pull_request?.base?.sha ?? event.before;
    if (event.pull_request) {
      base = git('merge-base', validateRef(base), head).trim();
    }
    if (!base || /^0+$/.test(base)) {
      diffArgs = ['diff-tree', '--root', '--no-commit-id', '-r', head];
    } else {
      diffArgs = ['diff', validateRef(base), head];
    }
  }
  const paths = git(...diffArgs, '--name-only', '-z').split('\0').filter(Boolean);
  const patch = paths.includes('CHANGELOG.md') ? git(...diffArgs, '--unified=0', '--', 'CHANGELOG.md') : '';
  const errors = validateUpdateHistory(paths, patch);
  if (errors.length) {
    errors.forEach(error => console.error(error));
    process.exitCode = 1;
  } else {
    console.log(needsUpdateHistory(paths) ? '更新紀錄檢查通過。' : '本次沒有需要更新紀錄的程式變更。');
  }
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) run();
