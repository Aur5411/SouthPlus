package net.northplus.app;

/**
 * 注入到网页里的脚本：夜间配色、无图模式、广告清理、点图调用原生查看器。
 *
 * <p>设计要点：
 * <ul>
 *   <li>脚本整体用 ES5 语法，兼容较老的 WebView 内核。</li>
 *   <li>所有改动都是幂等的（用 id 检查、data-np-src 标记），可反复注入。</li>
 *   <li>动态插入的节点（懒加载 / 后续页）由 MutationObserver 兜住。</li>
 * </ul>
 */
public final class Js {

    private static final String TEMPLATE = """
            (function () {
              var NP = window.NP;
              if (!NP) { return; }
              var CFG = __CFG__;

              var TRANSPARENT = 'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==';

              var NIGHT_CSS = 'html,body{background:#101216 !important;color:#c9cfd9 !important;}'
                + 'body *{border-color:#2a2f3a !important;}'
                + 'table,tr,td,th,tbody,thead,tfoot,div,ul,ol,li,dl,dt,dd,p,span,font,center,fieldset,'
                + 'form,h1,h2,h3,h4,h5,h6,blockquote,pre,code,section,article,header,footer,nav,main,'
                + 'aside,label,strong,b,i,em{background:transparent !important;'
                + 'background-color:transparent !important;color:#c9cfd9 !important;}'
                + 'a,a:visited{color:#7fb0ff !important;}a:hover{color:#a8c8ff !important;}'
                + 'input,textarea,select,button{background:#1b1f27 !important;color:#d7dde6 !important;'
                + 'border-color:#39404d !important;}'
                + 'hr{border-color:#2a2f3a !important;}'
                + 'img{opacity:0.92;}'
                + '.navbar,.card,.list-group,.list-group-item,.accordion,.inner,.modal-content,'
                + '.jumbotron,.table,td.f_one,td.t_one,td.r_one{background:#171a20 !important;'
                + 'color:#c9cfd9 !important;border-color:#2a2f3a !important;}'
                + '.text-muted,.muted,.gray,.gray a{color:#8b93a1 !important;}';

              var NOIMG_CSS = 'img{background:#e7eaf0 !important;min-height:14px;}';

              function head() { return document.head || document.documentElement; }

              function ensureStyle(id, css) {
                var e = document.getElementById(id);
                if (!css) {
                  if (e && e.parentNode) { e.parentNode.removeChild(e); }
                  return;
                }
                if (!e) {
                  e = document.createElement('style');
                  e.id = id;
                  head().appendChild(e);
                }
                if (e.textContent !== css) { e.textContent = css; }
              }

              function isJunk(src) {
                if (!src) { return true; }
                var s = src.toLowerCase();
                if (s.indexOf('data:image/gif;base64,r0lgod') === 0) { return true; }
                if (s.indexOf('mobileads') >= 0) { return true; }
                if (s.indexOf('segucrwj') >= 0) { return true; }
                // 站点模板资源目录：logo / 导航 / 表情 / 广告角标全在这里，正文图走外链
                if (s.indexOf('/images/') >= 0 || s.indexOf('images/') === 0) { return true; }
                if (s.indexOf('/statics/') >= 0 || s.indexOf('statics/') === 0) { return true; }
                if (s.indexOf('/templates/') >= 0) { return true; }
                if (s.indexOf('icon_') >= 0) { return true; }
                if (s.indexOf('emot') >= 0) { return true; }
                return false;
              }

              function realSrc(im) {
                var d = im.getAttribute('data-np-src');
                if (d) { return d; }
                return im.currentSrc || im.src || '';
              }

              /**
               * 是否正文内容图（用于「点开全屏查看」）。
               * 尺寸未知时不算内容图，避免把模板小图标当成图片打开。
               */
              function isContent(im) {
                var s = realSrc(im);
                if (isJunk(s)) { return false; }
                if (im.getAttribute('data-np-src')) { return true; }
                var w = im.naturalWidth, h = im.naturalHeight;
                if (!w || !h) { return false; }
                return w >= 120 && h >= 120;
              }

              function collect() {
                var list = [];
                var imgs = document.querySelectorAll('img');
                for (var i = 0; i < imgs.length; i++) {
                  var im = imgs[i];
                  if (!isContent(im)) { continue; }
                  var s = realSrc(im);
                  if (list.indexOf(s) < 0) { list.push(s); }
                }
                return list;
              }

              /**
               * 无图模式：这里不做尺寸判断。理由是要在图片真正下载之前就拦掉，
               * 此时 naturalWidth 还是 0；模板资源已由 isJunk 按 URL 排除。
               */
              function applyNoImage() {
                var imgs = document.querySelectorAll('img');
                for (var i = 0; i < imgs.length; i++) {
                  var im = imgs[i];
                  if (im.getAttribute('data-np-src')) { continue; }
                  var s = im.currentSrc || im.src || '';
                  if (!s || s.indexOf('data:') === 0 || isJunk(s)) { continue; }
                  im.setAttribute('data-np-src', s);
                  im.src = TRANSPARENT;
                }
              }

              /**
               * 只清站点自己的广告，不碰用户在帖子里发的链接。
               * 判据两条：① onclick 里有站点自己的 ga(...'ad') 打点；
               * ② 是「图片横幅 + 指向购物站」的组合。纯文字外链一律放过。
               */
              function isSiteAd(a) {
                var oc = (a.getAttribute('onclick') || '').toLowerCase();
                if (oc.indexOf('ga(') >= 0 && oc.indexOf("'ad'") >= 0) { return true; }
                if (!a.querySelector('img')) { return false; }
                var h = (a.getAttribute('href') || '').toLowerCase();
                if (!h || h.indexOf('javascript:') === 0) { return false; }
                return h.indexOf('tmall.com') >= 0 || h.indexOf('taobao.com') >= 0
                    || h.indexOf('tb.cn') >= 0 || h.indexOf('jd.com') >= 0;
              }

              function applyAdBlock() {
                // 移动模板的横幅图
                var imgs = document.querySelectorAll('img');
                for (var i = imgs.length - 1; i >= 0; i--) {
                  var im = imgs[i];
                  var s = (im.currentSrc || im.src || '').toLowerCase();
                  if (s.indexOf('mobileads') >= 0 && im.parentNode) {
                    im.parentNode.removeChild(im);
                  }
                }
                // 桌面模板顶部（header）里的联盟横幅
                var as = document.querySelectorAll('a');
                for (var j = as.length - 1; j >= 0; j--) {
                  var a = as[j];
                  if (isSiteAd(a) && a.parentNode) {
                    a.parentNode.removeChild(a);
                  }
                }
              }

              function linkIsResource(node) {
                var a = node;
                while (a && a.tagName !== 'A' && a.tagName !== 'BODY') { a = a.parentNode; }
                if (!a || a.tagName !== 'A') { return false; }
                var h = (a.getAttribute('href') || '').toLowerCase();
                if (!h) { return false; }
                if (h.indexOf('.zip') >= 0 || h.indexOf('.rar') >= 0 || h.indexOf('.7z') >= 0
                    || h.indexOf('.torrent') >= 0 || h.indexOf('.apk') >= 0
                    || h.indexOf('.mp4') >= 0 || h.indexOf('.mkv') >= 0
                    || h.indexOf('.mp3') >= 0 || h.indexOf('.flac') >= 0
                    || h.indexOf('.pdf') >= 0) { return true; }
                if (h.indexOf('pan.baidu.com') >= 0 || h.indexOf('aliyundrive') >= 0
                    || h.indexOf('alipan') >= 0 || h.indexOf('123pan') >= 0
                    || h.indexOf('lanzou') >= 0 || h.indexOf('mega.nz') >= 0
                    || h.indexOf('drive.google') >= 0 || h.indexOf('115.com') >= 0
                    || h.indexOf('quark') >= 0) { return true; }
                return false;
              }

              function onTap(ev) {
                var t = ev.target;
                if (!t || t.tagName !== 'IMG') { return; }
                if (linkIsResource(t)) { return; }
                if (!isContent(t)) { return; }
                var s = realSrc(t);
                if (!s) { return; }
                ev.preventDefault();
                ev.stopPropagation();
                var list = collect();
                var idx = list.indexOf(s);
                if (idx < 0) { list.unshift(s); idx = 0; }
                try { NP.openImage(JSON.stringify(list), idx); } catch (e) { }
              }

              var pending = false;
              function run() {
                ensureStyle('np-night-css', CFG.night ? NIGHT_CSS : '');
                ensureStyle('np-noimg-css', CFG.noImage ? NOIMG_CSS : '');
                if (CFG.adBlock) { applyAdBlock(); }
                if (CFG.noImage) { applyNoImage(); }
                if (CFG.tapImage && !window.__npTap) {
                  window.__npTap = true;
                  document.addEventListener('click', onTap, true);
                }
              }

              function schedule() {
                if (pending) { return; }
                pending = true;
                setTimeout(function () { pending = false; run(); }, 120);
              }

              run();

              if ((CFG.adBlock || CFG.noImage) && !window.__npMo) {
                window.__npMo = new MutationObserver(schedule);
                window.__npMo.observe(document.documentElement,
                    { childList: true, subtree: true });
              }

              window.__npRun = run;
            })();
            """;

    private Js() {
    }

    /** 组装带配置的脚本。 */
    public static String build(boolean night, boolean noImage, boolean adBlock, boolean tapImage) {
        String cfg = "{\"night\":" + night
                + ",\"noImage\":" + noImage
                + ",\"adBlock\":" + adBlock
                + ",\"tapImage\":" + tapImage + "}";
        return TEMPLATE.replace("__CFG__", cfg);
    }
}
