/*
 * host.js（用户脚本宿主）的离线测试。
 *
 * 用 jsdom 造一个假 NP_SCRIPT 桥，验证：
 *   @run-at 调度时序 / GM 存储往返 / 通知与开页 / 菜单注册与回调 /
 *   样式注入 / 脚本抛错时上报并回落原生注入 / 无桥时静默
 *
 * 用法：node tools/host_test.js
 */
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const HERE = __dirname;
const HOST_JS = fs.readFileSync(path.join(HERE, '..', 'app', 'src', 'main', 'assets',
    'userscripts', 'host.js'), 'utf8');

let pass = 0;
let fail = 0;

function ok(cond, label, detail) {
  if (cond) {
    pass++;
    console.log('  PASS  ' + label);
  } else {
    fail++;
    console.log('  FAIL  ' + label + (detail ? '  ->  ' + detail : ''));
  }
}

function makeBridge(w, store, calls) {
  return {
    listFor: function () { return JSON.stringify(w.__scripts); },
    gmGet: function (id, key) {
      const k = id + '|' + key;
      return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : '';
    },
    gmSet: function (id, key, json) { store[id + '|' + key] = json; },
    gmDel: function (id, key) { delete store[id + '|' + key]; },
    gmKeys: function (id) {
      return JSON.stringify(Object.keys(store)
          .filter(function (k) { return k.indexOf(id + '|') === 0; })
          .map(function (k) { return k.substring(id.length + 1); }));
    },
    log: function (id, m) { calls.logs.push(id + ':' + m); },
    error: function (id, m) { calls.errors.push(id + ':' + m); },
    notify: function (t, x) { calls.notify.push([t, x]); },
    openTab: function (u) { calls.openTab.push(u); },
    menu: function (id, cid, cap) { calls.menu.push([id, cid, cap]); },
    runFallback: function (id, r) { calls.fallback.push([id, r]); }
  };
}

function phase(label, withBridge, scriptsOverride) {
  console.log('\n[' + label + ']');

  const dom = new JSDOM('<!doctype html><html><head></head><body></body></html>', {
    runScripts: 'outside-only',
    url: 'https://north-plus.net/simple/index.php?t1.html'
  });
  const w = dom.window;
  const d = w.document;

  // 固定文档状态，让 @run-at 的调度可确定性断言
  let state = 'loading';
  Object.defineProperty(d, 'readyState', { configurable: true, get: function () { return state; } });

  const store = {};
  const calls = { logs: [], errors: [], notify: [], openTab: [], menu: [], fallback: [] };

  w.__scripts = scriptsOverride || [
    { id: 's1', name: 'A', version: '1', description: '', runAt: 'document-start',
      code: "window.__log=(window.__log||[]);window.__log.push('start');", matches: [] },
    { id: 's2', name: 'B', version: '1', description: '', runAt: 'document-end',
      code: "window.__log=(window.__log||[]);window.__log.push('end');", matches: [] },
    { id: 's3', name: 'C', version: '1', description: '', runAt: 'document-idle',
      code: "window.__log=(window.__log||[]);window.__log.push('idle');", matches: [] },
    { id: 's4', name: 'GM', version: '1', description: '', runAt: 'document-start',
      code: [
        "GM.setValue('cfg',{a:1,b:'x'});",
        "window.__gmRead=GM.getValue('cfg',null);",
        "window.__gmDef=GM.getValue('missing',{d:true});",
        "window.__gmKeys=GM.listValues();",
        "GM.notification('hello','title');",
        "GM.openInTab('https://example.com/');",
        "GM.registerMenuCommand('do-it',function(){window.__menuRan=true;});",
        "GM_addStyle('body{color:red}');",
        "window.__legacy=(typeof GM_getValue==='function')&&(typeof GM_setValue==='function');"
      ].join('\n'), matches: [] },
    { id: 's5', name: 'Bad', version: '1', description: '', runAt: 'document-start',
      code: "throw new Error('boom');", matches: [] }
  ];

  if (withBridge) {
    w.NP_SCRIPT = makeBridge(w, store, calls);
  }

  let threw = null;
  try { w.eval(HOST_JS); } catch (e) { threw = e; }

  return { w: w, d: d, store: store, calls: calls, threw: threw,
           setState: function (s) { state = s; },
           dom: dom };
}

// ---------------------------------------------------------------------- 主流程

const r = phase('有桥：完整能力', true);

ok(!r.threw, '注入 host.js 不抛异常', r.threw && r.threw.message);

const log0 = (r.w.__log || []).slice();
ok(log0.indexOf('start') >= 0, 'document-start 脚本已执行', JSON.stringify(log0));
ok(log0.indexOf('end') < 0, 'document-end 脚本尚未执行（文档还在 loading）', JSON.stringify(log0));
ok(log0.indexOf('idle') < 0, 'document-idle 脚本尚未执行', JSON.stringify(log0));

// GM 存储
ok(JSON.stringify(r.w.__gmRead) === '{"a":1,"b":"x"}', 'GM 往返读写（对象）',
    JSON.stringify(r.w.__gmRead));
ok(JSON.stringify(r.w.__gmDef) === '{"d":true}', 'GM 缺省值返回', JSON.stringify(r.w.__gmDef));
ok(Array.isArray(r.w.__gmKeys) && r.w.__gmKeys.indexOf('cfg') >= 0, 'GM.listValues 列出键',
    JSON.stringify(r.w.__gmKeys));
ok(r.w.__legacy === true, '同时提供旧式 GM_* 全局函数');

// 原生能力
ok(r.calls.notify.length === 1 && r.calls.notify[0][0] === 'title'
    && r.calls.notify[0][1] === 'hello', 'GM.notification 透传到原生',
    JSON.stringify(r.calls.notify));
ok(r.calls.openTab.length === 1 && r.calls.openTab[0] === 'https://example.com/',
    'GM.openInTab 透传到原生', JSON.stringify(r.calls.openTab));

// 样式
let styleOk = false;
Array.prototype.forEach.call(r.d.querySelectorAll('style'), function (s) {
  if (s.textContent.indexOf('color:red') >= 0) styleOk = true;
});
ok(styleOk, 'GM_addStyle 注入样式到 head');

// 菜单
ok(r.calls.menu.length === 1 && r.calls.menu[0][2] === 'do-it',
    'GM_registerMenuCommand 注册到原生', JSON.stringify(r.calls.menu));
const cmdId = r.calls.menu.length ? r.calls.menu[0][1] : null;
if (cmdId) {
  r.w.__npRunMenu(cmdId);
}
ok(r.w.__menuRan === true, '原生菜单点击能回调到脚本注册的函数');

// 抛错脚本
ok(r.calls.errors.length === 1 && r.calls.errors[0].indexOf('boom') >= 0,
    '脚本抛错会回报错误信息', JSON.stringify(r.calls.errors));
ok(r.calls.fallback.length === 1 && r.calls.fallback[0][0] === 's5',
    '抛错后请求原生兜底注入（绕过 CSP）', JSON.stringify(r.calls.fallback));

// 推进文档状态
r.setState('interactive');
r.d.dispatchEvent(new r.w.Event('DOMContentLoaded', { bubbles: true }));
const log1 = (r.w.__log || []).slice();
ok(log1.indexOf('end') >= 0, 'DOMContentLoaded 后 document-end 脚本执行', JSON.stringify(log1));

r.setState('complete');
r.w.dispatchEvent(new r.w.Event('load'));
const log2 = (r.w.__log || []).slice();
ok(log2.indexOf('idle') >= 0, 'load 后 document-idle 脚本执行', JSON.stringify(log2));

// ---------------------------------------------------------------------- 无桥

const n = phase('无桥：应静默不报错', false);
ok(!n.threw, '没有 NP_SCRIPT 时 host.js 静默返回', n.threw && n.threw.message);
ok(!n.w.__npUserscriptHost, '没有桥时不设置已注入标记（下次可重试）',
    String(n.w.__npUserscriptHost));

// ---------------------------------------------------------------------- 空列表

const e = phase('有桥但无匹配脚本', true, []);
ok(!e.threw, '空脚本列表不抛异常');
ok(e.calls.errors.length === 0, '空脚本列表不产生错误', JSON.stringify(e.calls.errors));
ok(e.calls.logs.length === 0, '空脚本列表不产生任何执行记录', JSON.stringify(e.calls.logs));

// ---------------------------------------------------------------------- 语法

try {
  new Function(HOST_JS);
  ok(true, 'host.js 是可解析的合法 JavaScript');
} catch (err) {
  ok(false, 'host.js 可被解析', err.message);
}

console.log('');
console.log('结果: PASS=' + pass + '  FAIL=' + fail);
process.exit(fail === 0 ? 0 : 1);
