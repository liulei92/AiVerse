/* AiVerse 站点增强（全站所有 VitePress 页面生效）
 * 1. 移动菜单注入「返回首页」+「中英文切换」（菜单底部，全站统一）
 * 2. 文案随当前语言本地化（英文站显示 Home/Menu/简体中文）
 * 3. 语言切换指向「当前页对应语言版本」，不存在(404)则回退该语言首页
 * 4. 下载页版本通道/下载源切换淡入过渡
 */
(function () {
  /* ---- 语言检测 ---- */
  var path = location.pathname;
  var isEn = /^\/en(\/|$)/.test(path);
  var CUR = isEn ? 'en' : 'zh';
  var TARGET = isEn ? 'zh' : 'en';

  function normalize(p) {
    p = ('' + p).replace(/index\.html$/, '').replace(/\.html$/, '');
    if (p.length > 1 && p.charAt(p.length - 1) === '/') p = p.slice(0, -1);
    return p;
  }
  function langHome(lang) { return lang === 'en' ? '/en/' : '/'; }

  /* 计算目标语言对应页；已在目标语言返回 null */
  function computeTarget(lang) {
    if (lang === CUR) return null;
    var p = normalize(path), t;
    if (lang === 'en') {
      t = isEn ? p : (p === '/' ? '/en' : '/en' + p);
      if (!/^\/en/.test(t)) t = '/en' + (t.charAt(0) === '/' ? t : '/' + t);
    } else {
      t = isEn ? (p.replace(/^\/en/, '') || '/') : p;
    }
    t = normalize(t);
    return t === '' ? langHome(lang) : t;
  }

  /* 跳转目标语言页；不存在(404)回退该语言首页 */
  function goTarget(lang) {
    var t = computeTarget(lang);
    if (t === null) return;
    fetch(t, { method: 'GET', cache: 'no-store' })
      .then(function (r) { location.href = (r && r.ok) ? t : langHome(lang); })
      .catch(function () { location.href = langHome(lang); });
  }
  function goHome() { location.href = langHome(CUR); }

  /* ---- 返回首页（本地化，修「英文页返回首页跳中文站」） ---- */
  function addHome() {
    var navs = document.querySelectorAll('.v-mobile-nav');
    for (var i = 0; i < navs.length; i++) {
      var nav = navs[i];
      if (!nav || nav.querySelector('.v-mobile-home')) continue;
      var a = document.createElement('a');
      a.href = isEn ? '/en/' : '/';
      a.className = 'v-mobile-home';
      a.textContent = isEn ? '⌂ Home' : '⌂ 返回首页';
      nav.insertBefore(a, nav.firstChild);
    }
  }

  /* ---- 中英文切换（菜单底部） ---- */
  function addLangSwitch() {
    var navs = document.querySelectorAll('.v-mobile-nav');
    for (var i = 0; i < navs.length; i++) {
      var nav = navs[i];
      if (!nav || nav.querySelector('.v-mobile-lang')) continue;
      var a = document.createElement('a');
      a.href = '#';
      a.className = 'v-mobile-lang';
      a.setAttribute('role', 'button');
      a.setAttribute('aria-label', isEn ? 'Switch to Simplified Chinese' : '切换到 English');
      a.innerHTML = '<span class="v-lang-badge">文</span><span>' + (isEn ? '简体中文' : 'English') + '</span>';
      a.addEventListener('click', function (e) { e.preventDefault(); goTarget(TARGET); });
      nav.appendChild(a); /* 菜单底部 */
    }
  }

  /* ---- 移动菜单统一修复 ---- */
  function injectMenuCss() {
    if (document.getElementById('aiv-menu-fix')) return;
    var style = document.createElement('style');
    style.id = 'aiv-menu-fix';
    style.textContent = [
      '.v-mobile-head{position:sticky;top:-8px;z-index:2;display:flex;align-items:center;justify-content:space-between;min-height:48px;padding:0 4px 0 8px;margin:0 -4px 6px;background:var(--vp-c-bg,#fff);border-bottom:1px solid var(--vp-c-divider,#e2e2e3);font-size:13px;font-weight:650;letter-spacing:.04em;color:var(--vp-c-text-2,#777);}',
      '.v-mobile-close{position:static!important;display:grid;place-items:center;width:36px;height:36px;border:1px solid var(--vp-c-divider,#ddd)!important;border-radius:10px!important;background:var(--vp-c-bg-alt,#f6f6f7)!important;font-size:23px!important;line-height:1!important;color:var(--vp-c-text-1,#333)!important;cursor:pointer;top:auto!important;right:auto!important;}',
      '.v-mobile-nav a{display:flex;align-items:center;min-height:46px;padding:10px 12px;border-radius:9px;color:var(--vp-c-text-1,#333);text-decoration:none;font-size:15px;font-weight:550;}',
      '.v-mobile-nav a:hover{background:var(--vp-c-brand-soft,rgba(10,60,255,.08));}',
      '.v-mobile-nav .v-mobile-home{margin-bottom:5px;background:var(--vp-c-brand-soft,rgba(10,60,255,.08));color:var(--vp-c-brand-1,#49695d);font-weight:650;}',
      /* 中英文切换：菜单底部，分隔线 + 居中 + 品牌色 */
      '.v-mobile-nav .v-mobile-lang{margin-top:12px;border-top:1px solid var(--vp-c-divider,#e2e2e3);border-radius:0;padding:14px 12px 2px;justify-content:center;gap:8px;color:var(--vp-c-brand-1,#49695d);font-weight:650;}',
      '.v-mobile-nav .v-lang-badge{display:inline-grid;place-items:center;width:20px;height:20px;border-radius:5px;background:var(--vp-c-brand-1,#49695d);color:#fff;font-size:12px;font-weight:700;}'
    ].join('\n');
    document.head.appendChild(style);
  }

  /* 确保菜单存在（缺失时补建完整菜单） */
  function ensureMenu() {
    var ham = document.querySelector('.VPNavBarHamburger');
    if (!ham) return null;
    var menu = document.querySelector('.v-mobile-menu');
    if (!menu) {
      menu = document.createElement('div');
      menu.className = 'v-mobile-menu';
      menu.innerHTML = '<div class="v-mobile-mask"></div><div class="v-mobile-panel">'
        + '<div class="v-mobile-head"><span>' + (isEn ? 'Menu' : '导航菜单') + '</span><button class="v-mobile-close" type="button" aria-label="' + (isEn ? 'Close menu' : '关闭菜单') + '">&times;</button></div>'
        + '<div class="v-mobile-nav"></div></div>';
      document.body.appendChild(menu);
      var mnav = menu.querySelector('.v-mobile-nav');
      var added = false;
      document.querySelectorAll('.VPNavBarMenuLink[href]').forEach(function (a) {
        var h = a.getAttribute('href');
        if (!h || h === '#') return;
        var p = document.createElement('a');
        p.href = h;
        p.textContent = (a.textContent || '').trim();
        mnav.appendChild(p);
        added = true;
      });
      if (!added) {
        document.querySelectorAll('.VPNavBarMenu a[href]').forEach(function (a) {
          var h = a.getAttribute('href');
          if (!h || h === '#') return;
          var p = document.createElement('a');
          p.href = h;
          p.textContent = (a.textContent || '').trim();
          mnav.appendChild(p);
        });
      }
      function closeMenu() { menu.classList.remove('open'); ham.setAttribute('aria-expanded', 'false'); }
      ham.addEventListener('click', function (e) {
        e.stopPropagation();
        menu.classList.add('open');
        ham.setAttribute('aria-expanded', 'true');
      });
      menu.querySelector('.v-mobile-mask').addEventListener('click', closeMenu);
      menu.querySelector('.v-mobile-close').addEventListener('click', closeMenu);
    }
    return menu;
  }

  /* 已有菜单但缺头栏 → 注入头栏（双语） */
  function fixMenuHead() {
    var menu = document.querySelector('.v-mobile-menu');
    if (!menu) return;
    var panel = menu.querySelector('.v-mobile-panel');
    if (!panel) return;
    var head = menu.querySelector('.v-mobile-head');
    var nav = menu.querySelector('.v-mobile-nav');
    var close = menu.querySelector('.v-mobile-close');
    if (!head) {
      head = document.createElement('div');
      head.className = 'v-mobile-head';
      var span = document.createElement('span');
      span.textContent = isEn ? 'Menu' : '导航菜单';
      head.appendChild(span);
      if (close) {
        close.setAttribute('aria-label', isEn ? 'Close menu' : '关闭菜单');
        head.appendChild(close);
      } else {
        var c = document.createElement('button');
        c.type = 'button';
        c.className = 'v-mobile-close';
        c.setAttribute('aria-label', isEn ? 'Close menu' : '关闭菜单');
        c.innerHTML = '&times;';
        c.addEventListener('click', function () { menu.classList.remove('open'); });
        head.appendChild(c);
      }
      panel.insertBefore(head, nav || panel.firstChild);
    }
  }

  /* 全量执行 */
  function run() {
    injectMenuCss();
    ensureMenu();
    fixMenuHead();
    addHome();
    addLangSwitch();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', run);
  } else {
    run();
  }

  /* 下载页：切换版本通道/下载源时内容淡入过渡 */
  function addSwitchFade() {
    document.querySelectorAll('.switch').forEach(function (s) {
      s.addEventListener('click', function (e) {
        if (!e.target.closest('.switch-btn')) return;
        ['.info-card', '.pkgs', '.ver-big', '.release-note'].forEach(function (sel) {
          var el = document.querySelector(sel);
          if (!el) return;
          el.classList.remove('aiv-fade');
          void el.offsetWidth;
          el.classList.add('aiv-fade');
        });
      });
    });
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', addSwitchFade);
  } else {
    addSwitchFade();
  }
})();
