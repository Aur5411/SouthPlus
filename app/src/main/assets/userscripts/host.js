/*
 * 用户脚本宿主（GM API 垫片 + @run-at 调度）
 *
 * 由原生在 document-start 注册（androidx.webkit 的 addDocumentStartJavaScript），
 * 若设备不支持该特性，则由 onPageStarted 兜底注入。
 *
 * 设计要点：
 *  - 脚本列表与源码通过 NP_SCRIPT 桥同步取回，宿主本身不需要知道有哪些脚本。
 *  - 优先用 new Function 在页面上下文里执行（时序最准）；
 *    若被页面 CSP 拦住，回落到原生 evaluateJavascript（不受页面 CSP 限制）。
 *  - GM 存储落在原生 SharedPreferences，卸载 App 前一直有效，行为与油猴一致。
 */
(function () {
  // 先确认桥在，再打「已注入」标记。
  // 否则一旦宿主被早于桥注入，标记会把当前页面上的脚本永久关掉。
  var B = window.NP_SCRIPT;
  if (!B) {
    return;
  }
  if (window.__npUserscriptHost) {
    return;
  }
  window.__npUserscriptHost = true;

  function parse(text, fallback) {
    try {
      return JSON.parse(text);
    } catch (e) {
      return fallback;
    }
  }

  var menuRegistry = {};
  var menuSeq = 0;

  function makeGM(id) {
    var gm = {};

    gm.getValue = function (key, def) {
      var raw = '';
      try {
        raw = B.gmGet(id, String(key));
      } catch (e) {
        raw = '';
      }
      if (raw === null || raw === undefined || raw === '') {
        return def;
      }
      var parsed = parse(raw, undefined);
      return parsed === undefined ? def : parsed;
    };

    gm.setValue = function (key, value) {
      try {
        B.gmSet(id, String(key), JSON.stringify(value === undefined ? null : value));
      } catch (e) { /* 存储失败不影响脚本继续跑 */ }
    };

    gm.deleteValue = function (key) {
      try {
        B.gmDel(id, String(key));
      } catch (e) { }
    };

    gm.listValues = function () {
      try {
        return parse(B.gmKeys(id), []);
      } catch (e) {
        return [];
      }
    };

    gm.notification = function (text, title) {
      try {
        B.notify(String(title === null || title === undefined ? '' : title),
                 String(text === null || text === undefined ? '' : text));
      } catch (e) { }
    };

    gm.openInTab = function (url) {
      try {
        B.openTab(String(url));
      } catch (e) { }
    };

    gm.registerMenuCommand = function (caption, fn) {
      var commandId = id + '#' + (menuSeq++);
      menuRegistry[commandId] = fn;
      try {
        B.menu(id, commandId, String(caption));
      } catch (e) { }
    };

    gm.log = function () { };

    return gm;
  }

  function addStyle(css) {
    var st = document.createElement('style');
    st.textContent = String(css);
    var root = document.head || document.documentElement;
    if (root) {
      root.appendChild(st);
    }
    return st;
  }

  // 供原生兜底注入使用：原生拼好包装函数后直接 evaluateJavascript，
  // 不经过 new Function，因此不受页面 CSP 限制。
  window.__npMakeGM = makeGM;
  window.__npAddStyle = addStyle;

  // 原生菜单点击后回调到这里
  window.__npRunMenu = function (commandId) {
    var fn = menuRegistry[commandId];
    if (typeof fn === 'function') {
      try {
        fn();
      } catch (e) {
        try {
          B.error(commandId.split('#')[0], String((e && e.message) || e));
        } catch (x) { }
      }
    }
  };

  function execute(entry) {
    var gm = makeGM(entry.id);
    var info = {
      script: {
        name: entry.name,
        version: entry.version,
        description: entry.description,
        matches: entry.matches
      },
      scriptHandler: 'NorthPlus',
      version: '1.0.0'
    };

    try {
      var fn = new Function(
        'GM', 'GM_getValue', 'GM_setValue', 'GM_deleteValue', 'GM_listValues',
        'GM_notification', 'GM_openInTab', 'GM_registerMenuCommand', 'GM_addStyle',
        'GM_info', 'unsafeWindow',
        entry.code
      );
      fn(gm, gm.getValue, gm.setValue, gm.deleteValue, gm.listValues,
         gm.notification, gm.openInTab, gm.registerMenuCommand, addStyle,
         info, window);
      try {
        B.log(entry.id, 'loaded');
      } catch (e) { }
    } catch (err) {
      var msg = String((err && err.message) || err);
      try {
        B.error(entry.id, msg);
      } catch (e) { }
      // 兜底：交给原生注入，绕过页面 CSP
      try {
        B.runFallback(entry.id, msg);
      } catch (e) { }
    }
  }

  function runGroup(items) {
    for (var i = 0; i < items.length; i++) {
      execute(items[i]);
    }
  }

  function boot() {
    var list;
    try {
      list = parse(B.listFor(String(location.href)), []);
    } catch (e) {
      return;
    }
    if (!list || !list.length) {
      return;
    }

    var start = [];
    var end = [];
    var idle = [];
    for (var i = 0; i < list.length; i++) {
      var at = list[i].runAt || 'document-end';
      if (at === 'document-start') {
        start.push(list[i]);
      } else if (at === 'document-idle') {
        idle.push(list[i]);
      } else {
        end.push(list[i]);
      }
    }

    if (start.length) {
      runGroup(start);
    }

    var fireEnd = function () {
      if (end.length) {
        runGroup(end);
      }
    };
    var fireIdle = function () {
      if (idle.length) {
        runGroup(idle);
      }
    };

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fireEnd, false);
    } else {
      fireEnd();
    }
    if (document.readyState === 'complete') {
      fireIdle();
    } else {
      window.addEventListener('load', fireIdle, false);
    }
  }

  boot();
})();
