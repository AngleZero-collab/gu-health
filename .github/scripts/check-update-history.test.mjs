import test from 'node:test';
import assert from 'node:assert/strict';
import { needsUpdateHistory, validateUpdateHistory } from './check-update-history.mjs';

const newEntry = '+## [android-2.1.1] - 2026-11-09\n+\n+- 修正完成日期顯示。\n';

test('requires history for web, Android, build and automation changes', () => {
  for (const path of ['app.js', 'android/app/src/main/java/Report.java', 'android/app/src/main/res/layout/report.xml',
    'android/build-android.ps1', 'android/gradlew.bat', 'android/gradle/wrapper/gradle-wrapper.properties',
    '.github/workflows/update-history.yml']) {
    assert.equal(needsUpdateHistory([path]), true, path);
  }
});

test('allows documentation-only updates', () => {
  assert.deepEqual(validateUpdateHistory(['README.md'], ''), []);
});

test('rejects missing or removed history and heading-only updates', () => {
  assert.equal(validateUpdateHistory(['app.js'], '').length, 1);
  assert.equal(validateUpdateHistory(['app.js', 'CHANGELOG.md'], '-## [old] - 2026-09-29').length, 1);
  assert.equal(validateUpdateHistory(['app.js', 'CHANGELOG.md'], '+## [web-v9] - 2026-11-09').length, 1);
});

test('accepts a versioned update with a description', () => {
  assert.deepEqual(validateUpdateHistory(['app.js', 'CHANGELOG.md'], newEntry), []);
});
