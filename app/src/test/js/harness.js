// Minimal DOM + native-bridge stub for running the Shell.kt inline JavaScript
// under Node's test runner. NOT a browser — just enough surface for the
// extracted script to parse, register listeners, and mutate a node tree.
'use strict';

let innerHTMLWrites = []; // every (node, html) innerHTML assignment, for XSS assertions

function matchSimple(el, sel) {
  // supports: #id, .cls, .a.b, tag
  if (sel[0] === '#') return el.attributes && el.attributes.id === sel.slice(1) ||
    (el.id && '#' + el.id === sel);
  if (sel.indexOf('.') === 0 || sel.match(/^[a-zA-Z]*(\.[a-zA-Z0-9_-]+)+$/)) {
    const parts = sel.split('.').filter(Boolean);
    const tag = sel[0] !== '.' ? sel.split('.')[0] : null;
    if (tag && el.tagName && el.tagName.toLowerCase() !== tag.toLowerCase()) return false;
    return parts.every(c => el.classList.contains(c));
  }
  return el.tagName && el.tagName.toLowerCase() === sel.toLowerCase();
}

function matchSelector(el, sel) {
  // compound with spaces: '#model-rows .ww-row', '.ww-drop.open'
  const parts = sel.trim().split(/\s+/);
  if (parts.length === 1) return matchSimple(el, parts[0]);
  if (!matchSimple(el, parts[parts.length - 1])) return false;
  let node = el.parent;
  for (let i = parts.length - 2; i >= 0; i--) {
    while (node && !matchSimple(node, parts[i])) node = node.parent;
    if (!node) return false;
    if (i > 0) node = node.parent;
  }
  return true;
}

class El {
  constructor(tag) {
    this.tagName = tag.toUpperCase();
    this.children = [];
    this.parent = null;
    this.attributes = {};
    this._text = '';
    this._innerHTML = '';
    this.value = '';
    this.type = '';
    this._classSet = new Set();
    this._listeners = {};
    this.style = {
      _set: {},
      setProperty(k, v) { this._set[k] = String(v); },
      getPropertyValue(k) { return this._set[k]; },
    };
    const self = this;
    this.classList = {
      add(...cs) { cs.forEach(c => self._classSet.add(c)); },
      remove(...cs) { cs.forEach(c => self._classSet.delete(c)); },
      contains(c) { return self._classSet.has(c); },
      toggle(c, force) {
        const want = force === undefined ? !self._classSet.has(c) : !!force;
        if (want) self._classSet.add(c); else self._classSet.delete(c);
        return want;
      },
    };
  }
  get className() { return Array.from(this._classSet).join(' '); }
  set className(v) { this._classSet = new Set(String(v).split(/\s+/).filter(Boolean)); }
  get textContent() {
    if (this.children.length) return this.children.map(c => c.textContent).join('');
    return this._text;
  }
  set textContent(v) { this.children = []; this._text = String(v); }
  // innerHTML setter RECORDS the write so tests can assert untrusted data never
  // flows through it (the XSS regression guard — model names must use textContent).
  get innerHTML() { return this._innerHTML; }
  set innerHTML(v) {
    innerHTMLWrites.push([this, String(v)]);
    this._innerHTML = String(v);
    this.children = [];
    this._text = '';
  }
  get id() { return this.attributes.id; }
  set id(v) { this.attributes.id = v; }
  appendChild(c) {
    if (c.isFragment) {
      c.children.slice().forEach(ch => this.appendChild(ch));
      c.children = [];
      return c;
    }
    c.parent = this;
    this.children.push(c);
    return c;
  }
  addEventListener(type, fn) { (this._listeners[type] = this._listeners[type] || []).push(fn); }
  dispatchEvent(ev) {
    ev.currentTarget = ev.currentTarget || this;
    (this._listeners[ev.type] || []).forEach(fn => fn(ev));
    return true;
  }
  setAttribute(k, v) { this.attributes[k] = String(v); }
  getAttribute(k) { return k in this.attributes ? this.attributes[k] : null; }
  querySelector(sel) { return this._all().find(el => matchSelector(el, sel)) || null; }
  querySelectorAll(sel) { return this._all().filter(el => matchSelector(el, sel)); }
  _all() {
    let out = [];
    for (const c of this.children) {
      out.push(c);
      out = out.concat(c._all());
    }
    return out;
  }
}

function makeFragment() {
  const f = new El('#fragment');
  f.isFragment = true;
  return f;
}

function getById(root, id) {
  if (root.id === id) return root;
  for (const c of root.children) {
    const hit = getById(c, id);
    if (hit) return hit;
  }
  return null;
}

// Standard screen fixture: every element the extracted script can touch.
function freshDocument() {
  innerHTMLWrites = [];
  const docEl = new El('html');
  const head = new El('head');
  const body = new El('body');
  docEl.appendChild(head);
  docEl.appendChild(body);

  function node(tag, id, parent) {
    const el = new El(tag);
    if (id) el.id = id;
    (parent || body).appendChild(el);
    return el;
  }

  node('button', 'nav-home');
  node('button', 'nav-about');
  node('div', 'main-container');
  node('div', 'ww-backdrop');
  node('div', 'toast');

  // API key section
  node('div', 'key-state');
  node('button', 'save-btn');
  node('button', 'key-remove');
  node('input', 'key-input');
  node('button', null).className = 'key-eye';

  // Model picker
  node('div', 'model-drop').className = 'ww-drop';
  const rows = node('div', 'model-rows');
  node('input', 'model-search');
  node('input', 'model-input');
  node('span', 'model-label');
  node('div', 'model-empty');
  node('button', 'model-save');

  // Theme picker
  const themeDrop = node('div', 'theme-drop');
  themeDrop.className = 'ww-drop';
  node('span', 'theme-name');
  node('div', 'theme-swatch');
  for (const k of ['peach', 'slate']) {
    const row = new El('button');
    row.className = 'ww-row';
    row.setAttribute('data-k', k);
    const check = new El('span');
    check.className = 'check';
    row.appendChild(check);
    themeDrop.appendChild(row);
  }

  // Status pill
  node('div', 'status-dot');
  node('div', 'status-label');
  node('button', 'status-btn');

  const document = {
    documentElement: docEl,
    body,
    _listeners: {},
    getElementById(id) { return getById(docEl, id); },
    createElement(tag) { return new El(tag); },
    createDocumentFragment() { return makeFragment(); },
    querySelector(sel) { return docEl.querySelector(sel); },
    querySelectorAll(sel) { return docEl.querySelectorAll(sel); },
    addEventListener(t, fn) { (this._listeners[t] = this._listeners[t] || []).push(fn); },
    dispatchEvent(ev) { (this._listeners[ev.type] || []).forEach(fn => fn(ev)); return true; },
  };
  void rows;
  return document;
}

// Installs the stub onto globalThis. `window` aliases globalThis so the
// script's bare `WwNative` / `WW` global references and `window.X` references
// stay in sync. Returns helpers for tests.
function install() {
  const document = freshDocument();
  const alerts = [];
  const toasts = [];
  globalThis.window = globalThis;
  globalThis.document = document;
  globalThis.alert = msg => alerts.push(String(msg));
  globalThis.confirm = () => globalThis.__confirmAnswer !== false;
  globalThis.__confirmAnswer = true;
  globalThis.confirmCalls = 0;
  const realConfirm = globalThis.confirm;
  globalThis.confirm = () => { globalThis.confirmCalls++; return realConfirm(); };
  globalThis.scrollTo = () => {};
  globalThis.location = { href: 'http://localhost/' };
  globalThis.htmx = { ajax: () => {} };
  globalThis.setInterval = () => 0; // never fire real polls
  globalThis.fetch = () => Promise.reject(new Error('no fetch stub'));
  const toastEl = document.getElementById('toast');
  // record wwToast messages by watching textContent writes to #toast
  return { document, alerts, toastEl, getInnerHTMLWrites: () => innerHTMLWrites };
}

// Configurable fake of the @JavascriptInterface bridge.
function makeBridge(over = {}) {
  const state = {
    hasKey: false,
    model: 'openrouter/free',
    theme: 'peach',
    saveApiKeyCalls: [],
    clearApiKeyCalls: 0,
    setThemeCalls: [],
    setModelCalls: [],
    hasApiKey() { return this.hasKey; },
    saveApiKey(key) { this.saveApiKeyCalls.push(key); if (over.saveErr) return over.saveErr; this.hasKey = true; return ''; },
    clearApiKey() { this.clearApiKeyCalls++; if (over.clearErr) return over.clearErr; this.hasKey = false; return ''; },
    getModel() { return this.model; },
    setModel(id) { this.setModelCalls.push(id); this.model = id; return ''; },
    getTheme() { return this.theme; },
    setTheme(key) {
      this.setThemeCalls.push(key);
      if (over.themeErr) return over.themeErr;
      if (over.knownThemes && !over.knownThemes.includes(key)) return 'Unknown theme';
      this.theme = key;
      return '';
    },
    openAccessibilitySettings() {},
    setStatusBarColor(hex) { this.statusBarCalls = (this.statusBarCalls || []).concat(hex); },
  };
  return state;
}

module.exports = { install, makeBridge, El, freshDocument };
