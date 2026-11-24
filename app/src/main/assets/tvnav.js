/*
 * Home Assistant TV – remote-control navigation.
 *
 * Injected into every Home Assistant page. Makes the TV remote's arrow keys move a
 * clearly visible highlight between tiles, view tabs and buttons (spatial navigation,
 * looking inside Home Assistant's shadow DOM), OK activates the highlighted item, and
 * window.__hatvBack() lets the app's Back button close pop-ups first.
 */
(function () {
  if (window.__hatvInstalled) return;
  window.__hatvInstalled = true;

  var HIGHLIGHT = '4px solid #22D3C5';
  var current = null;

  var ROLES = ['button', 'tab', 'switch', 'checkbox', 'slider', 'link', 'menuitem', 'option', 'radio', 'listitem'];
  var TAGS = ['BUTTON', 'A', 'INPUT', 'SELECT', 'TEXTAREA'];
  var DIALOG_TAGS = ['HA-DIALOG', 'HA-MD-DIALOG', 'HA-ADAPTIVE-DIALOG', 'HA-MORE-INFO-DIALOG', 'DIALOG-BOX', 'HA-WA-DIALOG', 'WA-DIALOG', 'HA-BOTTOM-SHEET'];

  // ---- DOM helpers (piercing shadow roots) ---------------------------------

  function collect(root, out) {
    var els = root.querySelectorAll('*');
    for (var i = 0; i < els.length; i++) {
      out.push(els[i]);
      if (els[i].shadowRoot) collect(els[i].shadowRoot, out);
    }
    return out;
  }

  function isInside(el, container) {
    var n = el;
    while (n) {
      if (n === container) return true;
      n = n.parentNode ? (n.parentNode.nodeType === 11 ? n.parentNode.host : n.parentNode) : null;
    }
    return false;
  }

  function isVisible(el) {
    var r = el.getBoundingClientRect();
    if (r.width < 16 || r.height < 16) return false;
    // Allow items scrolled above/below the screen (we scroll to them), but not off to the sides.
    if (r.bottom < -window.innerHeight * 3 || r.right < 0 || r.top > window.innerHeight * 4 || r.left > window.innerWidth) return false;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' || parseFloat(cs.opacity) === 0) return false;
    if (el.closest && el.closest('[hidden],[inert]')) return false;
    return true;
  }

  // Roles that use "roving focus": only one item in the group is focusable at a time
  // (e.g. Home Assistant's view tabs), so the others must be reachable anyway.
  var ROVING = ['tab', 'menuitem', 'option', 'radio', 'treeitem'];

  // The sidebar and its ☰ menu button are not used on the TV (the app has its own
  // area picker); opening the sidebar drawer would trap the highlight behind it.
  var EXCLUDE_INSIDE = ['HA-SIDEBAR', 'HA-MENU-BUTTON', 'HA-DRAWER', 'MWC-DRAWER'];

  function hasExcludedAncestor(el) {
    var n = el;
    while (n) {
      if (n.tagName && EXCLUDE_INSIDE.indexOf(n.tagName) >= 0) return true;
      n = n.parentNode ? (n.parentNode.nodeType === 11 ? n.parentNode.host : n.parentNode) : null;
    }
    return false;
  }

  function composedParent(n) {
    return n.parentNode ? (n.parentNode.nodeType === 11 ? n.parentNode.host : n.parentNode) : null;
  }

  // Cards with a tap action (Mushroom cards, many HA cards) are not keyboard-focusable,
  // but they all show a pointer ("hand") cursor. Treat the outermost element of each
  // pointer area as a target. Cursor is inherited, so skip children of a pointer parent.
  function isPointerTarget(el, r) {
    if (r.width < 40 || r.height < 28) return false;
    // Cheap check first: Home Assistant's tap/hold handler (used by Mushroom and most
    // cards) stores an `actionHandler` property on the element it is attached to.
    if (el.actionHandler) return true;
    // Fallback for other cards: an HA card showing the pointer cursor.
    if (el.tagName !== 'HA-CARD') return false;
    var cs = getComputedStyle(el);
    if (cs.cursor !== 'pointer') return false;
    var p = composedParent(el);
    if (p && p.nodeType === 1 && getComputedStyle(p).cursor === 'pointer') return false;
    return true;
  }

  function isCandidate(el) {
    if (el.disabled || (el.getAttribute && el.getAttribute('aria-disabled') === 'true')) return false;
    var role = el.getAttribute && el.getAttribute('role');
    var tag = el.tagName;
    var focusable = el.tabIndex >= 0 || TAGS.indexOf(tag) >= 0 || ROVING.indexOf(role) >= 0;
    var r = el.getBoundingClientRect();
    if (r.width < 16 || r.height < 16) return false;
    // Skip whole-page containers.
    if (r.width * r.height > window.innerWidth * window.innerHeight * 0.6) return false;
    var ok = false;
    if (focusable && (TAGS.indexOf(tag) >= 0 || ROLES.indexOf(role) >= 0 || el.hasAttribute('tabindex'))) ok = true;
    else if (isPointerTarget(el, r)) ok = true;
    if (!ok) return false;
    if (hasExcludedAncestor(el)) return false;
    return isVisible(el);
  }

  function openDialog(all) {
    var found = null;
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var tag = el.tagName;
      var isDlg = DIALOG_TAGS.indexOf(tag) >= 0 || (el.getAttribute && el.getAttribute('role') === 'dialog') || tag === 'DIALOG';
      if (!isDlg) continue;
      if (tag === 'DIALOG' && !el.open) continue;
      if (el.hasAttribute && el.hasAttribute('open') === false && tag !== 'DIALOG' && DIALOG_TAGS.indexOf(tag) >= 0 && el.getBoundingClientRect().width === 0) continue;
      if (!isVisible(el)) continue;
      found = el; // last (topmost) wins
    }
    return found;
  }

  var cache = null, cacheAt = 0;

  function candidates() {
    // Scanning the whole page is costly on TV boxes; reuse the result for a moment.
    var now = Date.now();
    if (cache && now - cacheAt < 1200 && (!current || cache.indexOf(current) >= 0)) {
      var stillOk = cache.every(function (el) { return el.isConnected; });
      if (stillOk) return cache;
    }
    cache = scan();
    cacheAt = now;
    return cache;
  }

  function scan() {
    var all = collect(document, []);
    var dialog = openDialog(all);
    var list = [];
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (!isCandidate(el)) continue;
      if (dialog && !isInside(el, dialog)) continue;
      list.push(el);
    }
    // Drop elements that sit exactly on top of another candidate (nested wrappers).
    return list.filter(function (el) {
      var r = el.getBoundingClientRect();
      for (var j = 0; j < list.length; j++) {
        var o = list[j];
        if (o === el || !isInside(o, el)) continue;
        var q = o.getBoundingClientRect();
        if (Math.abs(q.width - r.width) < 4 && Math.abs(q.height - r.height) < 4) return false;
      }
      return true;
    });
  }

  // ---- Highlight ------------------------------------------------------------

  // The highlight is a separate frame floating above the page, so cards with rounded,
  // clipped corners (Mushroom, tiles) can't hide it.
  var ring = null;

  function ensureRing() {
    if (ring && ring.isConnected) return ring;
    ring = document.createElement('div');
    ring.id = 'hatv-ring';
    ring.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;display:none;' +
      'border:' + HIGHLIGHT + ';border-radius:14px;box-sizing:border-box;' +
      'box-shadow:0 0 22px 6px rgba(34,211,197,0.55), inset 0 0 12px rgba(34,211,197,0.25);' +
      'transition:left .12s ease, top .12s ease, width .12s ease, height .12s ease;';
    document.documentElement.appendChild(ring);
    return ring;
  }

  function placeRing() {
    var r0 = ensureRing();
    if (!current || !current.isConnected) { r0.style.display = 'none'; return; }
    var r = current.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) { r0.style.display = 'none'; return; }
    var pad = 5;
    r0.style.left = (r.left - pad) + 'px';
    r0.style.top = (r.top - pad) + 'px';
    r0.style.width = (r.width + pad * 2) + 'px';
    r0.style.height = (r.height + pad * 2) + 'px';
    r0.style.display = 'block';
  }

  function setCurrent(el) {
    current = el;
    if (!el) { placeRing(); return; }
    try { el.focus({ preventScroll: true }); } catch (e) {}
    try { el.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (e) {}
    placeRing();
  }

  // Keep the frame on its card while the page scrolls, re-renders or animates.
  window.addEventListener('scroll', placeRing, true);
  window.addEventListener('resize', placeRing);
  setInterval(placeRing, 300);

  function center(r) { return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }

  function pickStart(list) {
    // First item in the page content (below the header/tab bar), top-left first.
    var content = list.filter(function (el) { return el.getBoundingClientRect().top > 70; });
    var pool = content.length ? content : list;
    pool.sort(function (a, b) {
      var ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect();
      return (Math.round(ra.top / 20) - Math.round(rb.top / 20)) || (ra.left - rb.left);
    });
    return pool[0] || null;
  }

  function move(dir) {
    var list = candidates();
    if (!list.length) return;
    if (!current || list.indexOf(current) < 0 || !current.isConnected) { setCurrent(pickStart(list)); return; }

    var r = current.getBoundingClientRect(), c = center(r);
    var best = null, bestScore = Infinity;
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el === current) continue;
      var q = el.getBoundingClientRect(), p = center(q);
      var dx = p.x - c.x, dy = p.y - c.y, along, across;
      if (dir === 'right') { if (q.left < r.right - 6 && dx <= 6) continue; along = dx; across = Math.abs(dy); }
      else if (dir === 'left') { if (q.right > r.left + 6 && dx >= -6) continue; along = -dx; across = Math.abs(dy); }
      else if (dir === 'down') { if (q.top < r.bottom - 6 && dy <= 6) continue; along = dy; across = Math.abs(dx); }
      else { if (q.bottom > r.top + 6 && dy >= -6) continue; along = -dy; across = Math.abs(dx); }
      if (along <= 0) continue;
      // Prefer items roughly in line with the current one.
      var overlap = (dir === 'left' || dir === 'right')
        ? Math.min(r.bottom, q.bottom) - Math.max(r.top, q.top)
        : Math.min(r.right, q.right) - Math.max(r.left, q.left);
      // Left/right stay on the same row (e.g. along the tab bar); up/down may change column.
      if ((dir === 'left' || dir === 'right') && overlap <= 0) continue;
      var score = along + across * (overlap > 0 ? 0.3 : 1.0);
      if (score < bestScore) { bestScore = score; best = el; }
    }
    if (best) setCurrent(best);
    else if (dir === 'down' || dir === 'up') window.scrollBy({ top: dir === 'down' ? 300 : -300, behavior: 'smooth' });
  }

  function deepActive() {
    var a = document.activeElement;
    while (a && a.shadowRoot && a.shadowRoot.activeElement) a = a.shadowRoot.activeElement;
    return a;
  }

  function isTextInput(el) {
    if (!el) return false;
    if (el.tagName === 'TEXTAREA') return true;
    if (el.tagName === 'INPUT') {
      var t = (el.type || 'text').toLowerCase();
      return ['text', 'password', 'email', 'number', 'search', 'url', 'tel'].indexOf(t) >= 0;
    }
    return el.isContentEditable;
  }

  // Press like a real finger/mouse would (down, up, click at the card's centre).
  // Home Assistant's tap/hold handler (used by Mushroom and most cards) listens to the
  // press and release, while plain buttons react to the click: either way it fires once.
  function tap(el) {
    var r = el.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    var base = { bubbles: true, cancelable: true, composed: true, clientX: x, clientY: y, button: 0, view: window };
    function fire(type, Ctor, extra) {
      var init = Object.assign({}, base, extra || {});
      var ev;
      try { ev = new Ctor(type, init); } catch (e) { ev = new MouseEvent(type, init); }
      el.dispatchEvent(ev);
    }
    var P = window.PointerEvent || MouseEvent;
    fire('pointerdown', P, { pointerId: 1, pointerType: 'mouse', isPrimary: true, buttons: 1 });
    fire('mousedown', MouseEvent, { buttons: 1 });
    fire('pointerup', P, { pointerId: 1, pointerType: 'mouse', isPrimary: true, buttons: 0 });
    fire('mouseup', MouseEvent, { buttons: 0 });
    fire('click', MouseEvent, { detail: 1 });
  }

  function activate() {
    if (!current || !current.isConnected) { move('down'); return; }
    var role = current.getAttribute('role');
    if (isTextInput(current)) {
      current.focus();
      if (window.HATV && HATV.showKeyboard) HATV.showKeyboard(); // ask the app for the on-screen keyboard
      return;
    }
    if (role === 'slider') return; // left/right adjust sliders natively
    tap(current);
  }

  // ---- Status label (helps diagnose pages where nothing can be selected) ----

  var statusEl = null, statusTimer = null;
  function status(text, ms) {
    if (!statusEl || !statusEl.isConnected) {
      statusEl = document.createElement('div');
      statusEl.style.cssText = 'position:fixed;right:18px;bottom:18px;z-index:2147483647;pointer-events:none;' +
        'background:rgba(11,18,32,0.88);color:#E6EDF5;border:2px solid #22D3C5;border-radius:12px;' +
        'padding:8px 14px;font:16px sans-serif;transition:opacity .3s;';
      document.documentElement.appendChild(statusEl);
    }
    statusEl.textContent = text;
    statusEl.style.opacity = '1';
    clearTimeout(statusTimer);
    statusTimer = setTimeout(function () { statusEl.style.opacity = '0'; }, ms || 3000);
  }

  // ---- Key handling ---------------------------------------------------------

  // The app forwards the remote's keys here directly (so it works whatever Android thinks
  // has focus): 'up' | 'down' | 'left' | 'right' | 'ok'.
  window.__hatvKey = function (key) {
    try {
      if (key === 'ok') { activate(); return 'ok'; }
      if (current && current.getAttribute('role') === 'slider' && (key === 'left' || key === 'right')) {
        current.dispatchEvent(new KeyboardEvent('keydown', { key: key === 'left' ? 'ArrowLeft' : 'ArrowRight', bubbles: true, composed: true }));
        return 'slider';
      }
      var list = candidates();
      if (!list.length) { status('No selectable items found on this page'); return 'none'; }
      move(key);
      return 'moved';
    } catch (e) {
      status('Navigation error: ' + e.message, 6000);
      return 'error';
    }
  };

  // Tell the app when a text box has focus, so it lets typing keys through normally.
  function reportTyping() {
    if (window.HATV && HATV.typing) HATV.typing(isTextInput(deepActive()));
  }
  document.addEventListener('focusin', reportTyping, true);
  document.addEventListener('focusout', function () { setTimeout(reportTyping, 0); }, true);
  reportTyping(); // e.g. the sign-in page focuses its username box before we are injected

  var KEYS = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Up: 'up', Down: 'down', Left: 'left', Right: 'right' };

  window.addEventListener('keydown', function (e) {
    var dir = KEYS[e.key];
    var active = deepActive();
    var typing = isTextInput(active);

    if (dir) {
      // While typing, left/right move the text cursor; up/down still leave the field.
      if (typing && (dir === 'left' || dir === 'right')) return;
      // Sliders keep left/right for adjusting their value.
      if (current && current.getAttribute('role') === 'slider' && (dir === 'left' || dir === 'right')) return;
      e.preventDefault();
      e.stopPropagation();
      move(dir);
      return;
    }
    if ((e.key === 'Enter' || e.key === ' ' || e.keyCode === 23) && !typing) {
      e.preventDefault();
      e.stopPropagation();
      activate();
    }
  }, true);

  // Called by the app when Back is pressed. Returns 'closed', 'back' or 'none'.
  window.__hatvBack = function () {
    var all = collect(document, []);
    var dialog = openDialog(all);
    if (dialog) {
      var esc = { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true, composed: true };
      try { dialog.dispatchEvent(new KeyboardEvent('keydown', esc)); } catch (e) {}
      try { if (typeof dialog.close === 'function') dialog.close(); } catch (e) {}
      try { if (typeof dialog.closeDialog === 'function') dialog.closeDialog(); } catch (e) {}
      setCurrent(null);
      return 'closed';
    }
    // On another view/page of this area, go back towards the area's start page
    // (window.__hatvRoot is set by the app). On the start page, the app shows its area picker.
    var root = window.__hatvRoot || '';
    if (root && location.pathname !== root && history.length > 1) {
      history.back();
      setCurrent(null);
      return 'back';
    }
    return 'none';
  };

  // Highlight the first item once the dashboard has rendered (checks once a second,
  // for up to 15 seconds), and show how many selectable items were found.
  var tries = 0;
  var timer = setInterval(function () {
    tries++;
    if (current && current.isConnected) { clearInterval(timer); return; }
    var t0 = Date.now();
    var list = scan();
    cache = list; cacheAt = Date.now();
    if (list.length > 2) {
      setCurrent(pickStart(list));
      clearInterval(timer);
      status('TV remote ready · ' + list.length + ' items (' + (Date.now() - t0) + ' ms)');
    } else if (tries >= 15) {
      clearInterval(timer);
      status('TV remote: found only ' + list.length + ' selectable items', 6000);
    }
  }, 1000);
})();
