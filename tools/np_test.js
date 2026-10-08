const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const BASE = __dirname;
const FIXTURES = path.join(BASE, 'fixtures');

// 模拟真实 WebView 的图片尺寸：模板小图标 24x24、广告横幅 729x90、外链内容图 800x600
function patchImageSize(w) {
  const sizeOf = function (el) {
    const s = (el.getAttribute('data-np-src') || el.getAttribute('src') || '').toLowerCase();
    if (s.indexOf('mobileads') >= 0) return [729, 90];
    if (s.indexOf('/images/') >= 0) return [24, 24];
    if (!s) return [0, 0];
    return [800, 600];
  };
  Object.defineProperty(w.HTMLImageElement.prototype, 'naturalWidth', {
    configurable: true,
    get: function () { return sizeOf(this)[0]; }
  });
  Object.defineProperty(w.HTMLImageElement.prototype, 'naturalHeight', {
    configurable: true,
    get: function () { return sizeOf(this)[1]; }
  });
}

function run(pageFile, cfg, label) {
  const html = fs.readFileSync(path.join(FIXTURES, pageFile), 'utf8');
  const dom = new JSDOM(html, {
    runScripts: 'outside-only',
    url: 'https://north-plus.net/simple/index.php'
  });
  const w = dom.window;
  const d = w.document;
  patchImageSize(w);

  const calls = { openImage: [] };
  w.NP = {
    openImage: function (json, idx) { calls.openImage.push({ json: json, idx: idx }); },
    openUrl: function () {},
    toast: function () {}
  };

  const imgs0 = Array.prototype.slice.call(d.querySelectorAll('img'));
  const adBefore = imgs0.filter(function (i) {
    return (i.src || '').toLowerCase().indexOf('mobileads') >= 0;
  }).length;

  let js = fs.readFileSync(path.join(BASE, 'np_inject.js'), 'utf8');
  js = js.replace('"night":true', '"night":' + cfg.night)
         .replace('"noImage":true', '"noImage":' + cfg.noImage)
         .replace('"adBlock":true', '"adBlock":' + cfg.adBlock)
         .replace('"tapImage":true', '"tapImage":' + cfg.tapImage);

  let err = null;
  try { w.eval(js); } catch (e) { err = e; }

  const after = Array.prototype.slice.call(d.querySelectorAll('img'));
  const adAfter = after.filter(function (i) {
    return (i.src || '').toLowerCase().indexOf('mobileads') >= 0;
  }).length;
  const swapped = after.filter(function (i) { return !!i.getAttribute('data-np-src'); });
  const nightCss = !!d.getElementById('np-night-css');
  const noImgCss = !!d.getElementById('np-noimg-css');

  console.log('');
  console.log('[' + label + '] 注入异常: ' + (err ? err.message : '无'));
  console.log('  img ' + imgs0.length + ' -> ' + after.length
      + ' | 广告图 ' + adBefore + ' -> ' + adAfter
      + ' | 夜间样式 ' + nightCss + ' | 无图样式 ' + noImgCss
      + ' | 已换占位图 ' + swapped.length);
  if (swapped.length) {
    console.log('    换掉的首项: ' + String(swapped[0].getAttribute('data-np-src')).slice(0, 100));
  }

  // 点第一张「外链内容图」
  let target = null;
  after.forEach(function (im) {
    if (target) return;
    const s = im.getAttribute('data-np-src') || im.src || '';
    if (s && s.indexOf('/images/') < 0 && s.indexOf('data:') !== 0) target = im;
  });

  let parsed = null;
  let clickedTemplateIcon = null;
  if (target) {
    target.dispatchEvent(new w.MouseEvent('click', { bubbles: true, cancelable: true }));
    const got = calls.openImage[0];
    try { parsed = got ? JSON.parse(got.json) : null; } catch (e) { parsed = null; }
    console.log('  点内容图: ' + (got ? '触发' : '未触发')
        + ' | index=' + (got ? got.idx : '-')
        + ' | 列表长度=' + (parsed ? parsed.length : '-'));
    if (parsed) console.log('    首项: ' + String(parsed[0]).slice(0, 100));

    // 点模板小图标，不应该触发
    let icon = null;
    after.forEach(function (im) {
      if (icon) return;
      const s = im.getAttribute('src') || '';
      if (s.indexOf('/images/') >= 0 && s.indexOf('mobileads') < 0) icon = im;
    });
    if (icon) {
      const n0 = calls.openImage.length;
      icon.dispatchEvent(new w.MouseEvent('click', { bubbles: true, cancelable: true }));
      clickedTemplateIcon = calls.openImage.length === n0 ? '已忽略' : '被误开';
    }
  }

  // 资源链接图片放行
  let resourceClick = 'n/a';
  if (target) {
    const a = d.createElement('a');
    a.setAttribute('href', 'https://pan.baidu.com/s/abc');
    target.parentNode.insertBefore(a, target);
    a.appendChild(target);
    const n0 = calls.openImage.length;
    target.dispatchEvent(new w.MouseEvent('click', { bubbles: true, cancelable: true }));
    resourceClick = calls.openImage.length === n0 ? '已正确放行' : '被误拦';
  }

  return {
    err: err, adBefore: adBefore, adAfter: adAfter, nightCss: nightCss,
    noImgCss: noImgCss, swapped: swapped.length, parsed: parsed,
    resourceClick: resourceClick, clickedTemplateIcon: clickedTemplateIcon,
    total: after.length
  };
}

const a = run('s_img.html', { night: true, noImage: true, adBlock: true, tapImage: true }, '带图帖子 全开');
const b = run('s_f9.html', { night: false, noImage: false, adBlock: true, tapImage: true }, '版块页 仅广告过滤');
const c = run('simple.html', { night: false, noImage: false, adBlock: false, tapImage: false }, '首页 全关');

console.log('');
console.log('===== 断言 =====');
const cases = [];
cases.push(['注入无异常', !a.err && !b.err && !c.err]);
cases.push(['广告图被清除', a.adBefore > 0 && a.adAfter === 0 && b.adBefore > 0 && b.adAfter === 0]);
cases.push(['夜间样式按开关出现', a.nightCss === true && b.nightCss === false]);
cases.push(['无图样式按开关出现', a.noImgCss === true && b.noImgCss === false]);
cases.push(['无图模式换掉外链内容图', a.swapped >= 1 && b.swapped === 0]);
cases.push(['点图返回合法 JSON 数组', a.parsed instanceof Array && a.parsed.length >= 1]);
cases.push(['列表只含外链内容图', !!a.parsed && a.parsed.every(function (u) {
  return u.indexOf('/images/') < 0;
})]);
cases.push(['模板小图标点不开查看器', a.clickedTemplateIcon === '已忽略']);
cases.push(['网盘链接图片不被误拦', a.resourceClick === '已正确放行']);
cases.push(['全关时不注入任何样式', c.nightCss === false && c.noImgCss === false]);

let allOk = true;
for (const p of cases) {
  console.log('  ' + (p[1] ? 'PASS' : 'FAIL') + '  ' + p[0]);
  if (!p[1]) allOk = false;
}

// --------------------------------------------------------------------- 桌面版广告清理

// 站点自己的顶部联盟横幅（应清除） vs 用户在帖子里发的购物链接（应保留）
const DESKTOP_HTML = `<!doctype html><html><head></head><body>
<div id="header"><table><tr><td class="banner">
  <a href="https://segucrwj.taobao.com/" target="_blank"
     onclick="ga('send', 'event', 'moe80', 'clicked', 'ad');">
     <img src="/images/segucrwj29.jpg"/></a>
</td><td class="banner" id="banner" align="right"></td></tr></table></div>
<div id="post">
  <a href="https://item.taobao.com/item.htm?id=123">这个资源是在淘宝买的</a>
  <a href="https://www.tmall.com/banner"><img src="https://cdn.example.com/b.png"/></a>
  <a href="https://pan.baidu.com/s/abc">网盘链接</a>
</div>
</body></html>`;

function desktopAdCase(cfg, label) {
  const dom = new JSDOM(DESKTOP_HTML, {
    runScripts: 'outside-only',
    url: 'https://north-plus.net/index.php'
  });
  const w = dom.window;
  const d = w.document;
  patchImageSize(w);
  w.NP = { openImage: function () {}, openUrl: function () {}, toast: function () {} };

  let js = fs.readFileSync(path.join(BASE, 'np_inject.js'), 'utf8');
  js = js.replace('"night":true', '"night":' + cfg.night)
         .replace('"noImage":true', '"noImage":' + cfg.noImage)
         .replace('"adBlock":true', '"adBlock":' + cfg.adBlock)
         .replace('"tapImage":true', '"tapImage":' + cfg.tapImage);

  let err = null;
  try { w.eval(js); } catch (e) { err = e; }

  const hrefs = Array.prototype.map.call(d.querySelectorAll('a'), function (a) {
    return a.getAttribute('href') || '';
  });
  const headerAd = hrefs.some(function (h) { return h.indexOf('segucrwj') >= 0; });
  const userText = hrefs.some(function (h) { return h.indexOf('item.taobao.com') >= 0; });
  const imgBanner = hrefs.some(function (h) { return h.indexOf('tmall.com') >= 0; });
  const pan = hrefs.some(function (h) { return h.indexOf('pan.baidu.com') >= 0; });

  console.log('');
  console.log('[' + label + '] 注入异常: ' + (err ? err.message : '无'));
  console.log('  顶部联盟横幅还在: ' + headerAd + ' | 帖内文字外链还在: ' + userText
      + ' | 图片横幅广告还在: ' + imgBanner + ' | 网盘链接还在: ' + pan);

  return { err: err, headerAd: headerAd, userText: userText, imgBanner: imgBanner, pan: pan };
}

const adOn = desktopAdCase({ night: false, noImage: false, adBlock: true, tapImage: true },
    '桌面版 开启广告过滤');
const adOff = desktopAdCase({ night: false, noImage: false, adBlock: false, tapImage: true },
    '桌面版 关闭广告过滤');

console.log('');
console.log('===== 桌面版广告断言 =====');
const adCases = [];
adCases.push(['注入无异常', !adOn.err && !adOff.err]);
adCases.push(['站点顶部联盟横幅被清除', adOn.headerAd === false]);
adCases.push(['图片横幅广告被清除', adOn.imgBanner === false]);
adCases.push(['帖内纯文字淘宝链接保留（不误伤用户内容）', adOn.userText === true]);
adCases.push(['网盘链接保留', adOn.pan === true]);
adCases.push(['关闭广告过滤时横幅保留', adOff.headerAd === true]);
let adOk = true;
for (const p of adCases) {
  console.log('  ' + (p[1] ? 'PASS' : 'FAIL') + '  ' + p[0]);
  if (!p[1]) adOk = false;
}
if (!adOk || !allOk) { console.log('\n存在失败项'); process.exit(1); }
console.log('\n全部通过');
