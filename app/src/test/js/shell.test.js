// Extracts the inline <script> from Shell.kt (stubbing the Kotlin
// interpolations), syntax-checks it with `node --check`, evals it under the
// harness DOM, and asserts the shipped key-state/model/theme behaviors.
'use strict';

const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');

const { install, makeBridge } = require('./harness.js');

function findShellKt() {
  const candidates = [
    path.resolve(process.cwd(), 'src/main/kotlin/com/musa/wordwise/server/Shell.kt'),
    path.resolve(__dirname, '../../main/kotlin/com/musa/wordwise/server/Shell.kt'),
  ];
  for (const c of candidates) if (fs.existsSync(c)) return c;
  throw new Error('Shell.kt not found; cwd=' + process.cwd());
}

// Same extraction the repo history did by hand, now codified.
function extractShellJs() {
  const src = fs.readFileSync(findShellKt(), 'utf8');
  const m = src.match(/return """([\s\S]*?)"""/);
  assert.ok(m, 'raw string body not found in Shell.kt');
  const raw = m[1]
    .split('${Themes.styleVars(themeKey)}').join('')
    .split('${Themes.toJs()}').join('{}')
    .split('${jsonStr(themeKey)}').join('"peach"')
    .split('${jsonStr(ModelId.DEFAULT)}').join('"openrouter/free"');
  const sm = raw.match(/<script>([\s\S]*?)<\/script>/);
  assert.ok(sm, 'inline <script> block not found');
  return sm[1];
}

const JS = extractShellJs();

// Fake timers for the whole file: several behaviors (wwSavedFeedback's 1700ms
// reset, wwToast) race a setTimeout, and tests tick explicitly. Enabling once
// avoids per-test enable/restore bookkeeping.
const { mock } = require('node:test');
mock.timers.enable({ apis: ['setTimeout'] });

// node --check must pass, or every later failure would be a syntax error.
test('extracted script parses (node --check)', () => {
  const tmp = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'wwjs-')), 'shell.js');
  fs.writeFileSync(tmp, JS);
  execFileSync(process.execPath, ['--check', tmp]); // throws on syntax error
});

let ctx;
function loadScript(bridge) {
  ctx = install();
  globalThis.WwNative = bridge; // harness aliases window === globalThis
  // Seeded AFTER eval: the extracted script runs `var WW_THEMES = {}` (the
  // extraction stub), so seeding first would be clobbered.
  (0, eval)(JS); // indirect eval: vars/functions land on globalThis, like a <script>
  globalThis.WW_THEMES = {
    peach: { name: 'Peach', swatch: '#ffc9b3', statusBar: '#ffc9b3', vars: { '--bg': '#fff5f0' } },
    slate: { name: 'Slate', swatch: '#4a5568', statusBar: '#4a5568', vars: { '--bg': '#1a202c' } },
  };
  return ctx;
}

const SAVED = '✓ Key saved securely.';
const REPLACE_HINT = 'A key is saved on this device. Paste a new one to replace it.';
const $ = id => ctx.document.getElementById(id);

// ---------- key state machine ----------

test('1 fresh screen with no key: empty note, SAVE button, remove hidden', () => {
  const bridge = makeBridge();
  loadScript(bridge);
  wwRefreshKeyState();
  assert.strictEqual($('key-state').textContent, '');
  assert.strictEqual($('save-btn').textContent, 'SAVE API KEY');
  assert.strictEqual($('key-remove').style.display, 'none');
});

test('2 BUG: save with no prior key shows SAVED note, not the replace hint', () => {
  const bridge = makeBridge();
  loadScript(bridge);
  $('key-input').value = 'sk-test';
  wwSaveKey();
  // Shipped bug: wwRefreshKeyState() overwrote the confirmation with
  // KEY_REPLACE_HINT, reading as "your old key is still here".
  assert.strictEqual($('key-state').textContent, SAVED);
  assert.notStrictEqual($('key-state').textContent, REPLACE_HINT);
  assert.strictEqual($('save-btn').textContent, 'Saved ✓');
});

test('3 save with existing key: SAVED note, button becomes REPLACE after 1700ms', () => {
  const bridge = makeBridge();
  bridge.hasKey = true;
  loadScript(bridge);
  $('key-input').value = 'sk-new';
  wwSaveKey();
  assert.strictEqual($('key-state').textContent, SAVED);
  mock.timers.tick(1700);
  assert.strictEqual($('save-btn').textContent, 'REPLACE API KEY');
  assert.strictEqual($('key-remove').style.display, 'inline-block');
});

test('4 remove confirmed: clearApiKey called, state reset', () => {
  const bridge = makeBridge();
  bridge.hasKey = true;
  loadScript(bridge);
  wwRefreshKeyState();
  globalThis.__confirmAnswer = true;
  wwRemoveKey();
  assert.strictEqual(bridge.clearApiKeyCalls, 1);
  assert.strictEqual($('key-state').textContent, '');
  assert.strictEqual($('save-btn').textContent, 'SAVE API KEY');
  assert.strictEqual($('key-remove').style.display, 'none');
});

test('5 remove declined: clearApiKey NOT called, state unchanged', () => {
  const bridge = makeBridge();
  bridge.hasKey = true;
  loadScript(bridge);
  wwRefreshKeyState();
  globalThis.__confirmAnswer = false;
  wwRemoveKey();
  assert.strictEqual(bridge.clearApiKeyCalls, 0);
  assert.strictEqual($('key-state').textContent, REPLACE_HINT);
  assert.strictEqual($('save-btn').textContent, 'REPLACE API KEY');
  assert.strictEqual($('key-remove').style.display, 'inline-block');
  globalThis.__confirmAnswer = true;
});

test('6 BUG: save then remove inside 1700ms window leaves correct final label', () => {
  const bridge = makeBridge();
  loadScript(bridge);
  $('key-input').value = 'sk-x';
  wwSaveKey();            // arms the 1700ms "Saved ✓" reset timer
  globalThis.__confirmAnswer = true;
  wwRemoveKey();          // must clear that timer / re-render
  mock.timers.tick(1700); // any stale timer would now clobber the label
  assert.strictEqual(bridge.clearApiKeyCalls, 1);
  assert.strictEqual($('save-btn').textContent, 'SAVE API KEY'); // not stale REPLACE
  assert.strictEqual($('key-state').textContent, '');
});

test('7 failed save shows error toast, note and button unchanged', () => {
  const bridge = makeBridge({ saveErr: 'invalid key' });
  loadScript(bridge);
  wwRefreshKeyState();
  $('key-input').value = 'sk-bad';
  wwSaveKey();
  assert.strictEqual($('toast').textContent, 'invalid key');
  assert.strictEqual($('key-state').textContent, '');
  assert.strictEqual($('save-btn').textContent, 'SAVE API KEY');
  assert.strictEqual(bridge.hasKey, false);
});

test('8 missing bridge: every function degrades without throwing', () => {
  loadScript(undefined);
  delete globalThis.WwNative;
  const fns = [
    () => wwRefreshKeyState(), () => wwSaveKey(), () => wwRemoveKey(),
    () => wwKeyButtonLabel(), () => wwSetTheme('peach'), () => wwPoll(),
    () => wwOpenAccessibility(), () => wwBack(), () => wwCloseDrops(),
    () => wwGo('/screens/home'),
  ];
  for (const fn of fns) assert.doesNotThrow(fn);
  assert.strictEqual(wwKeyButtonLabel(), 'SAVE API KEY');
});

// ---------- model picker ----------

test('9 wwSortModels is a consistent comparator (shipped ~150-row throw)', () => {
  loadScript(makeBridge());
  const rows = [];
  for (let i = 0; i < 40; i++) {
    rows.push({ id: 'm' + i, name: 'dup', free: i % 3 === 0 });
    rows.push({ id: 'x' + i, name: 'Model ' + i, free: i % 2 === 0 });
  }
  for (const a of rows) for (const b of rows) {
    assert.ok(wwSortModels(a, b) === -wwSortModels(b, a) ||
              (wwSortModels(a, b) === 0 && wwSortModels(b, a) === 0),
      `cmp(a,b)!==-cmp(b,a) for ${JSON.stringify(a)} / ${JSON.stringify(b)}`);
  }
  assert.doesNotThrow(() => rows.slice().sort(wwSortModels));
  const sorted = rows.slice().sort(wwSortModels);
  assert.ok(sorted.every((m, i, arr) => i === 0 || wwSortModels(arr[i - 1], m) <= 0));
  const firstPaid = sorted.findIndex(m => !m.free);
  assert.ok(sorted.slice(0, Math.max(firstPaid, 0)).every(m => m.free), 'free models first');
});

test('10 wwFilterModels caps at 60 rows and shows empty note', () => {
  loadScript(makeBridge());
  globalThis.WW_MODELS = [];
  for (let i = 0; i < 100; i++) globalThis.WW_MODELS.push({ id: 'id' + i, name: 'Model ' + i, free: false });
  $('model-search').value = '';
  $('model-input').value = '';
  wwFilterModels();
  const rows = $('model-rows').querySelectorAll('.ww-row');
  assert.strictEqual(rows.length, 60);
  $('model-search').value = 'zz-no-match';
  wwFilterModels();
  assert.strictEqual($('model-rows').querySelectorAll('.ww-row').length, 0);
  assert.strictEqual($('model-rows').textContent, 'No models match');
  assert.strictEqual($('model-empty').style.display, 'block');
});

test('11 XSS: hostile catalog name never becomes an element or executes', () => {
  loadScript(makeBridge());
  globalThis.WW_MODELS = [{ id: 'evil', name: '<img src=x onerror=alert(1)>', free: false }];
  $('model-search').value = '';
  wwFilterModels();
  const rows = $('model-rows');
  assert.strictEqual(rows.querySelectorAll('img').length, 0, 'hostile name became markup');
  assert.deepStrictEqual(ctx.alerts, [], 'onerror executed');
  // the name must appear as inert text only
  assert.ok(rows.textContent.indexOf('<img src=x onerror=alert(1)>') !== -1);
  // and nothing anywhere used innerHTML
  assert.deepStrictEqual(ctx.getInnerHTMLWrites(), []);
});

test('12 wwSetModelField syncs label field and checkmarks', () => {
  loadScript(makeBridge());
  const rowsEl = $('model-rows');
  rowsEl.textContent = '';
  for (const id of ['a/b', 'openrouter/free']) {
    const b = ctx.document.createElement('button');
    b.className = 'ww-row';
    b.setAttribute('data-id', id);
    const c = ctx.document.createElement('span');
    c.className = 'check';
    b.appendChild(c);
    rowsEl.appendChild(b);
  }
  wwSetModelField('openrouter/free');
  assert.strictEqual($('model-input').value, 'openrouter/free');
  assert.strictEqual($('model-label').textContent, 'openrouter/free — Free Models Router');
  const checks = rowsEl.querySelectorAll('.ww-row').map(r => r.querySelector('.check').style.visibility);
  assert.deepStrictEqual(checks, ['hidden', 'visible']);
  wwSetModelField('a/b');
  const checks2 = rowsEl.querySelectorAll('.ww-row').map(r => r.querySelector('.check').style.visibility);
  assert.deepStrictEqual(checks2, ['visible', 'hidden']);
  assert.strictEqual($('model-label').textContent, 'a/b');
});

// ---------- theme / status ----------

test('13 wwSetTheme updates bridge, swatch, name, checkmark; unknown key errors', () => {
  const bridge = makeBridge({ knownThemes: ['peach', 'slate'] });
  loadScript(bridge);
  wwSetTheme('slate');
  assert.deepStrictEqual(bridge.setThemeCalls, ['slate']);
  assert.strictEqual($('theme-name').textContent, 'Slate');
  assert.strictEqual($('theme-swatch').style.background, '#4a5568');
  const rows = ctx.document.querySelectorAll('#theme-drop .ww-row');
  assert.strictEqual(rows[0].querySelector('.check').style.visibility, 'hidden');
  assert.strictEqual(rows[1].querySelector('.check').style.visibility, 'visible');
  // unknown key: bridge returns error, nothing is applied
  wwSetTheme('nope');
  assert.strictEqual($('toast').textContent, 'Unknown theme');
  assert.strictEqual(window.WW.theme, 'slate');
  assert.strictEqual($('theme-name').textContent, 'Slate');
});

test('14 wwApplyStatus handles {enabled} shape (no hasKey anymore)', () => {
  loadScript(makeBridge());
  wwApplyStatus({ enabled: true });
  assert.ok($('status-dot').classList.contains('on'));
  assert.strictEqual($('status-label').textContent, 'SERVICE ACTIVE');
  assert.strictEqual($('status-btn').textContent, 'Enabled ✓');
  wwApplyStatus({ enabled: false });
  assert.ok(!$('status-dot').classList.contains('on'));
  assert.strictEqual($('status-label').textContent, 'SERVICE PAUSED');
  assert.strictEqual($('status-btn').textContent, 'Enable');
});
