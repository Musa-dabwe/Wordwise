/*
 * Runtime configuration.
 *
 * These three values used to be interpolated into this file while it lived in a
 * Kotlin raw string. They are now read from the document instead, which is what
 * lets the script be a real asset file: no inline <script>, so the Content
 * Security Policy does not need script-src 'unsafe-inline'.
 *
 *   - the selected theme and the default model ID ride on <body> as data
 *     attributes, rendered server-side
 *   - the theme catalogue is fetched from /api/themes, lazily, because the
 *     initial theme's CSS variables are already applied server-side to <html>
 *     and fetching first would only risk a flash of the wrong colours
 */
var WW_THEMES = {};
var WW_THEMES_LOADED = false;
var WW_THEME_RETRY = false;

function wwConfig(name) {
  return (document.body && document.body.dataset[name]) || '';
}

var WW_DEFAULT_MODEL = wwConfig('defaultModel') || 'openrouter/free';
var WW_THEME_KEY = wwConfig('theme') || 'peach';

/** Fetches the theme catalogue once, on first use. */
function wwLoadThemes() {
  if (WW_THEMES_LOADED) return Promise.resolve(WW_THEMES);
  WW_THEMES_LOADED = true;
  return fetch('/api/themes').then(function (r) {
    return r.ok ? r.json() : {};
  }).then(function (data) {
    WW_THEMES = data || {};
    return WW_THEMES;
  }).catch(function () {
    return WW_THEMES;
  });
}

var wwScreenUrl = '/screens/home';

function wwGo(url) { htmx.ajax('GET', url, { target: '#main-container', swap: 'innerHTML' }); }

function wwToast(msg) {
  var t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(t._h);
  t._h = setTimeout(function () { t.classList.remove('show'); }, 2200);
}

/* ---- native bridge ----
 * Every secret read and every settings write goes through WwNative, which only
 * the app's own WebView can reach. The local server has no mutating routes, so
 * a hostile page or a co-resident app cannot read the key or change settings.
 * Mutating bridge methods return "" on success or a reason to show the user. */
function wwBridge() { return window.WwNative; }

function wwOpenAccessibility() {
  var b = wwBridge();
  if (b && b.openAccessibilitySettings) b.openAccessibilitySettings();
}

document.body.addEventListener('htmx:afterSwap', function (e) {
  if (!e.detail.target) return;
  if (e.detail.target.id === 'main-container') {
    var path = e.detail.pathInfo && (e.detail.pathInfo.finalRequestPath || e.detail.pathInfo.requestPath);
    if (path && path.indexOf('/screens/') === 0) wwScreenUrl = path;
    var about = wwScreenUrl.indexOf('/screens/about') === 0;
    document.getElementById('nav-home').classList.toggle('active', !about);
    document.getElementById('nav-about').classList.toggle('active', about);
    wwCloseDrops();
    window.scrollTo(0, 0);
    wwPoll();
    wwRefreshKeyState();
  }
});

/* ---- API key ----
 * The stored key is write-only: the server never renders it, so the field
 * always starts empty and shows whether one exists instead. */

/*
 * Two distinct states, because they mean opposite things to the user:
 *   - the screen loads with a key already present -> how to replace it
 *   - the user just saved one -> confirmation, not a prompt to do it again
 * Showing the replace hint immediately after a save reads as "your old key is
 * still here", which is exactly the wrong thing to say at that moment.
 */
var KEY_REPLACE_HINT = 'A key is saved on this device. Paste a new one to replace it.';
var KEY_SAVED_NOTE = '✓ Key saved securely.';

function wwSetKeyNote(text) {
  var note = document.getElementById('key-state');
  if (note) note.textContent = text;
}

function wwKeyButtonLabel() {
  var b = wwBridge();
  return (b && b.hasApiKey && b.hasApiKey()) ? 'REPLACE API KEY' : 'SAVE API KEY';
}

/*
 * `note` overrides the default hint. Callers that just wrote the key pass
 * KEY_SAVED_NOTE, because the default hint would immediately contradict them.
 */
function wwRefreshKeyState(note) {
  var btn = document.getElementById('save-btn');
  var b = wwBridge();
  var has = !!(b && b.hasApiKey && b.hasApiKey());
  wwSetKeyNote(note !== undefined ? note : (has ? KEY_REPLACE_HINT : ''));
  if (btn) {
    /* Cancel a pending "Saved ✓" reset first: its timeout would otherwise
       write a stale label over the correct one (see wwSavedFeedback). */
    clearTimeout(btn._h);
    btn.textContent = wwKeyButtonLabel();
  }
  var rm = document.getElementById('key-remove');
  if (rm) rm.style.display = has ? 'inline-block' : 'none';
}

/*
 * The key is write-only, so there is no way to read it back — which also means
 * the user needs an explicit way to delete it. Previously clearing app data was
 * the only option.
 */
function wwRemoveKey() {
  var b = wwBridge();
  if (!b || !b.clearApiKey) return;
  if (!window.confirm('Remove the stored OpenRouter API key?\n\nWordWise will stop working until you add one again.')) return;
  var err = b.clearApiKey();
  if (err) { wwToast(err); return; }
  wwSetKeyNote('');
  wwToast('API key removed');
  wwRefreshKeyState();
}

function wwSaveKey() {
  var input = document.getElementById('key-input');
  var b = wwBridge();
  if (!input || !b || !b.saveApiKey) { wwToast('Unavailable'); return; }
  var err = b.saveApiKey(input.value);
  if (err) { wwToast(err); return; }
  input.value = '';
  input.type = 'password';
  var eye = document.querySelector('.key-eye');
  if (eye) eye.textContent = 'SHOW';
  /* The note must be passed in, not set first: wwRefreshKeyState writes the
     note itself and would overwrite it with the "already saved, replace it"
     hint — the exact message that prompted this change. */
  wwRefreshKeyState(KEY_SAVED_NOTE);
  wwSavedFeedback('save-btn', 'API key saved securely', wwKeyButtonLabel);
}

/*
 * Shared "Saved ✓" button animation for the bridge-driven forms.
 * `resetLabel` may be a function, which is then evaluated when the timer
 * fires rather than now: the key button's label depends on whether a key
 * exists, and that can change inside the 1700ms window (save, then remove).
 */
function wwSavedFeedback(btnId, toastMsg, resetLabel) {
  var b = document.getElementById(btnId);
  wwToast(toastMsg);
  if (!b) return;
  b.textContent = 'Saved ✓';
  b.classList.add('saved');
  clearTimeout(b._h);
  b._h = setTimeout(function () {
    b.textContent = typeof resetLabel === 'function' ? resetLabel() : resetLabel;
    b.classList.remove('saved');
  }, 1700);
}

/* Derived from live state rather than a fixed string: the key button reads
   REPLACE once a key exists, so hardcoding the reset label would put it out of
   sync with the note 1.7s after a successful save. See wwKeyButtonLabel above. */

function wwSaveModel() {
  var input = document.getElementById('model-input');
  var b = wwBridge();
  if (!input || !b || !b.setModel) { wwToast('Unavailable'); return; }
  var err = b.setModel(input.value);
  if (err) { wwToast(err); return; }
  wwSetModelField(b.getModel ? b.getModel() : input.value);
  wwSavedFeedback('model-save', 'Model saved', 'SAVE MODEL');
}

document.body.addEventListener('htmx:load', function () {
  wwSetModelField(wwBridge() && wwBridge().getModel ? wwBridge().getModel() : WW_DEFAULT_MODEL);
});

/* ---- live accessibility-service status poller ---- */
function wwApplyStatus(s) {
  var dot = document.getElementById('status-dot');
  if (!dot) return;
  dot.classList.toggle('on', !!s.enabled);
  document.getElementById('status-label').textContent = s.enabled ? 'SERVICE ACTIVE' : 'SERVICE PAUSED';
  document.getElementById('status-btn').textContent = s.enabled ? 'Enabled ✓' : 'Enable';
}
function wwPoll() {
  fetch('/api/status').then(function (r) { return r.json(); }).then(wwApplyStatus).catch(function () {});
}
setInterval(wwPoll, 2000);

/* ---- API key show/hide toggle ---- */
function wwToggleKey(btn) {
  var i = document.getElementById('key-input');
  var show = i.type === 'password';
  i.type = show ? 'text' : 'password';
  btn.textContent = show ? 'HIDE' : 'SHOW';
}

/* ---- model picker ---- */
/* Free models first, then alphabetical — most users want the free router. */
var WW_MODELS = [];
var WW_MODELS_LOADING = false;

/* Must be a consistent comparator: never returning 0 on a tie makes
   cmp(a,b) != -cmp(b,a), which can throw "comparison function violates general
   sorting algorithm" on the ~150-row catalog. Ties are common because
   ModelCatalog falls back to the id when a model has no name. */
function wwSortModels(a, b) {
  if (a.free !== b.free) return a.free ? -1 : 1;
  var na = a.name.toLowerCase(), nb = b.name.toLowerCase();
  if (na !== nb) return na < nb ? -1 : 1;
  return a.id < b.id ? -1 : (a.id > b.id ? 1 : 0);
}

/* WW_DEFAULT_MODEL is emitted by the server from ModelId.DEFAULT. */
function wwModelLabel(id) {
  return id === WW_DEFAULT_MODEL ? id + ' — Free Models Router' : id;
}

/* Keeps the dropdown label and checkmarks in step with the paste field. */
function wwSetModelField(id) {
  var i = document.getElementById('model-input');
  if (i) i.value = id;
  var l = document.getElementById('model-label');
  if (l) l.textContent = wwModelLabel(id);
  document.querySelectorAll('#model-rows .ww-row').forEach(function (row) {
    var c = row.querySelector('.check');
    if (c) c.style.visibility = row.getAttribute('data-id') === id ? 'visible' : 'hidden';
  });
}

function wwNote(text) {
  var d = document.createElement('div');
  d.className = 'ww-note';
  d.textContent = text;
  return d;
}

function wwLoadModels() {
  var rows = document.getElementById('model-rows');
  if (!rows) return;
  /* An htmx re-swap of #main-container replaces #model-rows with fresh
     "Loading…" markup, so a warm cache still has to re-render. Only skip the
     network call, never the render. */
  if (WW_MODELS.length) { wwFilterModels(); return; }
  if (WW_MODELS_LOADING) return;
  WW_MODELS_LOADING = true;

  rows.textContent = '';
  rows.appendChild(wwNote('Loading models…'));

  fetch('/api/models').then(function (r) {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
  }).then(function (data) {
    WW_MODELS_LOADING = false;
    if (!Array.isArray(data)) throw new Error('unexpected payload');
    WW_MODELS = data.filter(function (m) {
      return m && typeof m.id === 'string' && typeof m.name === 'string';
    }).sort(wwSortModels);
    wwFilterModels();
  }).catch(function () {
    WW_MODELS_LOADING = false;
    /* Re-query: the user may have navigated to About while this was in
       flight, which detaches the node we captured. */
    var live = document.getElementById('model-rows');
    if (!live) return;
    live.textContent = '';
    live.appendChild(wwNote('Could not load models — paste an ID below'));
  });
}

/*
 * Rows are built with createElement/textContent, never innerHTML. Model names
 * come from a third-party catalog and are untrusted; textContent makes that
 * data structurally incapable of becoming markup.
 */
function wwFilterModels() {
  var rows = document.getElementById('model-rows');
  if (!rows || !WW_MODELS.length) return;
  var search = document.getElementById('model-search');
  var input = document.getElementById('model-input');
  var q = ((search && search.value) || '').trim().toLowerCase();
  var current = (input && input.value) || '';

  var frag = document.createDocumentFragment();
  var shown = 0;

  for (var i = 0; i < WW_MODELS.length && shown < 60; i++) {
    var m = WW_MODELS[i];
    if (q && m.name.toLowerCase().indexOf(q) === -1 && m.id.toLowerCase().indexOf(q) === -1) continue;

    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'ww-row';
    b.setAttribute('data-id', m.id);
    b.addEventListener('click', wwChooseModel);

    var name = document.createElement('span');
    name.className = 'name';
    var val = document.createElement('span');
    val.className = 'val';
    val.textContent = m.name;
    name.appendChild(val);
    b.appendChild(name);

    /* The id disambiguates models that share a display name, and the context
       length is the main practical difference between candidates. */
    var sub = document.createElement('span');
    sub.className = 'sub';
    sub.textContent = (m.ctx ? Math.round(m.ctx / 1024) + 'k ctx · ' : '') + m.id;
    name.appendChild(sub);

    if (m.free === true) {
      var tag = document.createElement('span');
      tag.className = 'tag';
      tag.textContent = 'FREE';
      b.appendChild(tag);
    }

    var c = document.createElement('span');
    c.className = 'check';
    c.textContent = '✓';
    c.style.visibility = m.id === current ? 'visible' : 'hidden';
    b.appendChild(c);

    frag.appendChild(b);
    shown++;
  }

  rows.textContent = '';
  if (shown === 0) {
    rows.appendChild(wwNote(q ? 'No models match' : 'Could not load models — paste an ID below'));
  } else {
    rows.appendChild(frag);
  }
  var empty = document.getElementById('model-empty');
  if (empty) empty.style.display = (q && shown === 0) ? 'block' : 'none';
}

function wwChooseModel(ev) {
  wwSetModelField(ev.currentTarget.getAttribute('data-id'));
  wwCloseDrops();
  wwToast('Model selected — tap SAVE MODEL to apply');
}

/* ---- themes ---- */
function wwSetTheme(key) {
  var b = wwBridge();
  if (!b || !b.setTheme) return;
  var err = b.setTheme(key);
  if (err) { wwToast(err); return; }
  wwApplyTheme(key);
  wwCloseDrops();
  var t = WW_THEMES[key];
  if (!t) { wwLoadThemes().then(function () { if (WW_THEMES[key]) wwSetTheme(key); }); return; }
  var name = document.getElementById('theme-name');
  if (name) name.textContent = t.name;
  var sw = document.getElementById('theme-swatch');
  if (sw) sw.style.background = t.swatch;
  document.querySelectorAll('#theme-drop .ww-row').forEach(function (row) {
    var on = row.getAttribute('data-k') === key;
    var c = row.querySelector('.check');
    if (c) c.style.visibility = on ? 'visible' : 'hidden';
  });
}

/* ---- dropdowns ---- */
function wwToggleDrop(id) {
  var d = document.getElementById(id);
  var open = d.classList.contains('open');
  wwCloseDrops();
  if (!open) {
    d.classList.add('open');
    document.getElementById('ww-backdrop').classList.add('show');
    /* Lazy-load the catalog on first open so leaving the picker untouched
       never spends an OpenRouter request. */
    if (id === 'model-drop') {
      var s = document.getElementById('model-search');
      if (s) s.value = '';
      wwLoadModels();
    }
  }
}
function wwCloseDrops() {
  document.querySelectorAll('.ww-drop.open').forEach(function (d) { d.classList.remove('open'); });
  document.getElementById('ww-backdrop').classList.remove('show');
}

/* ---- full-theme switching ---- */
function wwApplyTheme(key) {
  var t = WW_THEMES[key];
  // Not loaded yet (first paint): fetch, then re-apply. Guarded so a failed or
  // unparseable response cannot spin the event loop — an unbounded retry here
  // starves the page and the settings screen never paints.
  if (!t) {
    if (WW_THEME_RETRY) return;
    WW_THEME_RETRY = true;
    wwLoadThemes().then(function () {
      WW_THEME_RETRY = false;
      if (WW_THEMES[key]) wwApplyTheme(key);
    });
    return;
  }
  var r = document.documentElement.style;
  for (var v in t.vars) r.setProperty(v, t.vars[v]);
  if (document.body) document.body.dataset.theme = key;
  if (window.WwNative && WwNative.setStatusBarColor) WwNative.setStatusBarColor(t.statusBar);
}
document.addEventListener('DOMContentLoaded', function () {
  // Fetch the catalogue first so the first theme switch is instant. The colours
  // for the current theme are already on <html> from the server.
  wwLoadThemes().then(function () {
    wwApplyTheme(WW_THEME_KEY);
  });
  wwGo('/screens/home');
  wwPoll();
});

/* back-button support: Android calls wwBack() */
function wwBack() {
  if (document.querySelector('.ww-drop.open')) { wwCloseDrops(); return 'handled'; }
  if (wwScreenUrl !== '/screens/home') { wwGo('/screens/home'); return 'handled'; }
  return 'exit';
}
