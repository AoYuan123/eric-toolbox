package com.eric.toolbox.modules.read

/**
 * 阅读器排版设置，以及把章节 HTML 包装成阅读页（注入样式和翻页脚本）。纯函数，可单测。
 *
 * 翻页原理：左右翻页模式用 CSS 多栏把一章排成横向的一页页，脚本按屏宽滚动；上下滚动模式就是普通网页滚动。
 * 书里自带的 <script> 一律删掉，只运行我们自己的脚本。
 */
data class ReadSettings(
    val fontSize: Int = 19,
    val lineHeight: Double = 1.7,
    val margin: Int = 22,
    val font: String = "serif", // serif / sans / system
    val theme: String = "paper", // paper / green / white / night
    val paged: Boolean = true,
    val volumeKeys: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoNight: Boolean = false,
    val tapSides: Boolean = true,
) {
    data class Colors(val bg: String, val fg: String, val muted: String, val link: String, val mark: String)

    fun effectiveTheme(hour: Int): String = if (autoNight && (hour >= 22 || hour < 6)) "night" else theme

    companion object {
        val themes = linkedMapOf(
            "paper" to ("纸张" to Colors("#F4ECD8", "#3B3226", "#6B604F", "#8A5A1E", "#E9D49A")),
            "green" to ("护眼" to Colors("#CFE3CC", "#24321F", "#56664F", "#2F5D2A", "#B5D09A")),
            "white" to ("白色" to Colors("#FFFFFF", "#222222", "#666666", "#1F5FAD", "#FFE58A")),
            "night" to ("夜间" to Colors("#111214", "#B9BCC2", "#7C8089", "#C9A55A", "#4A3F22")),
        )
        fun colors(theme: String) = (themes[theme] ?: themes.getValue("paper")).second
    }
}

object ReaderHtml {
    private val scriptTag = Regex("""<script[\s\S]*?</script>|<script[^>]*/>""", RegexOption.IGNORE_CASE)
    private val headOpen = Regex("""<head[^>]*>""", RegexOption.IGNORE_CASE)
    private val bodyOpen = Regex("""<body[^>]*>""", RegexOption.IGNORE_CASE)
    private val htmlOpen = Regex("""<html[^>]*>""", RegexOption.IGNORE_CASE)
    private val inlineHandlers = Regex("""\s+on[a-z]+\s*=\s*("[^"]*"|'[^']*')""", RegexOption.IGNORE_CASE)

    fun fontStack(font: String) = when (font) {
        "sans" -> "'Noto Sans CJK SC','Source Han Sans SC',Roboto,'Helvetica Neue',sans-serif"
        "system" -> "inherit"
        else -> "Georgia,'Noto Serif CJK SC','Source Han Serif SC','Songti SC',serif"
    }

    fun css(s: ReadSettings, theme: String): String {
        val c = ReadSettings.colors(theme)
        val font = if (s.font == "system") "" else "font-family:${fontStack(s.font)} !important;"
        val common = """
            html{-webkit-text-size-adjust:100%;font-size:${s.fontSize}px !important;background:${c.bg} !important;}
            body{color:${c.fg} !important;background:${c.bg} !important;$font line-height:${s.lineHeight} !important;
                 word-wrap:break-word;overflow-wrap:break-word;-webkit-hyphens:auto;hyphens:auto;text-align:justify;}
            body *{color:inherit !important;background-color:transparent !important;max-width:100%;}
            p,li,blockquote,dd,div{line-height:${s.lineHeight} !important;}
            p{margin-top:0;margin-bottom:0.6em;}
            h1,h2,h3,h4{line-height:1.35 !important;text-align:left;}
            a{color:${c.link} !important;}
            img,svg{max-width:100% !important;height:auto;}
            pre,code{white-space:pre-wrap;font-size:0.85em;}
            mark.__hit{background:${c.mark} !important;color:inherit !important;border-radius:2px;}
            ::selection{background:${c.mark};}
        """.trimIndent()
        val layout = if (s.paged) """
            html{height:100vh;overflow:hidden !important;margin:0 !important;padding:0 !important;}
            body{box-sizing:border-box;height:100vh;margin:0 !important;padding:0 ${s.margin}px !important;
                 column-width:calc(100vw - ${2 * s.margin}px);column-gap:${2 * s.margin}px;column-fill:auto;overflow:visible !important;}
            img,svg{max-height:calc(100vh - 8px) !important;object-fit:contain;break-inside:avoid;}
            h1,h2,h3{break-after:avoid;}
        """.trimIndent() else """
            html{margin:0 !important;padding:0 !important;}
            body{margin:0 !important;padding:8px ${s.margin}px 48px ${s.margin}px !important;}
        """.trimIndent()
        return common + "\n" + layout
    }

    /** 阅读页脚本。原生端通过 R（JavascriptInterface）接收：ready(总页数, 当前页)、page(当前页, 总页数)、tap(区域)、edge(方向)。 */
    fun js(paged: Boolean, tapSides: Boolean): String = """
        (function(){
          var paged=$paged, sides=$tapSides;
          var W=function(){return window.innerWidth;};
          function pages(){ if(!paged){return 1;} var sw=document.scrollingElement.scrollWidth; return Math.max(1, Math.round(sw / W())); }
          function cur(){ return paged ? Math.round(window.scrollX / W()) : 0; }
          function frac(){ if(paged){ var p=pages(); return p<=1?0:cur()/(p-1);} var h=document.scrollingElement.scrollHeight-window.innerHeight; return h<=0?0:Math.min(1,window.scrollY/h); }
          function report(){ R.page(cur(), pages(), frac()); }
          window.__go=function(n, smooth){ if(!paged) return; var p=pages(); n=Math.max(0,Math.min(p-1,n)); window.scrollTo({left:n*W(),top:0,behavior:smooth?'smooth':'auto'}); setTimeout(report, smooth?260:0); };
          window.__goFrac=function(f){ if(paged){ __go(Math.round(f*(pages()-1)), false);} else { var h=document.scrollingElement.scrollHeight-window.innerHeight; window.scrollTo(0, Math.max(0,f*h)); setTimeout(report,0);} };
          window.__next=function(){ if(paged){ if(cur()>=pages()-1){R.edge(1);} else {__go(cur()+1,true);} } else { var y=window.scrollY, h=document.scrollingElement.scrollHeight-window.innerHeight; if(y>=h-2){R.edge(1);} else {window.scrollBy({top:window.innerHeight-40,behavior:'smooth'}); setTimeout(report,300);} } };
          window.__prev=function(){ if(paged){ if(cur()<=0){R.edge(-1);} else {__go(cur()-1,true);} } else { if(window.scrollY<=2){R.edge(-1);} else {window.scrollBy({top:-(window.innerHeight-40),behavior:'smooth'}); setTimeout(report,300);} } };
          window.__find=function(q, nth){
            q=(q||'').toLowerCase(); if(!q) return false;
            var w=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null), n, seen=0;
            while((n=w.nextNode())){
              var t=n.nodeValue.toLowerCase(), k=-1, from=0;
              while((k=t.indexOf(q,from))>=0){
                if(seen===nth){
                  var r=document.createRange(); r.setStart(n,k); r.setEnd(n,k+q.length);
                  var m=document.createElement('mark'); m.className='__hit'; try{ r.surroundContents(m);}catch(e){}
                  var rect=m.getBoundingClientRect();
                  if(paged){ __go(Math.floor((rect.left+window.scrollX)/W()), false);} else { window.scrollTo(0, rect.top+window.scrollY-80); setTimeout(report,0);}
                  return true;
                }
                seen++; from=k+q.length;
              }
            }
            return false;
          };
          var sx=0, sy=0, st=0, moved=false;
          document.addEventListener('touchstart', function(e){ var t=e.touches[0]; sx=t.clientX; sy=t.clientY; st=Date.now(); moved=false; }, {passive:true});
          document.addEventListener('touchmove', function(e){ var t=e.touches[0]; if(Math.abs(t.clientX-sx)>10||Math.abs(t.clientY-sy)>10) moved=true; if(paged) e.preventDefault(); }, {passive:false});
          document.addEventListener('touchend', function(e){
            var t=e.changedTouches[0], dx=t.clientX-sx, dy=t.clientY-sy, dt=Date.now()-st;
            var sel=window.getSelection && String(window.getSelection());
            if(sel && sel.length>0) return;
            if(paged && Math.abs(dx)>40 && Math.abs(dx)>Math.abs(dy)){ if(dx<0) __next(); else __prev(); return; }
            if(!moved && dt<350){
              var x=t.clientX/W();
              if(sides && x<0.3){ __prev(); } else if(sides && x>0.7){ __next(); } else { R.tap(1); }
            } else if(!paged && moved){ setTimeout(report,120); }
          }, {passive:true});
          if(!paged){ var tm=null; window.addEventListener('scroll', function(){ clearTimeout(tm); tm=setTimeout(report,150); }); }
          function start(){ R.ready(pages()); }
          if(document.readyState==='complete'){ setTimeout(start,30); } else { window.addEventListener('load', function(){ setTimeout(start,30); }); }
          window.addEventListener('resize', function(){ setTimeout(report,50); });
        })();
    """.trimIndent()

    /** 把章节 HTML 包装成阅读页。 */
    fun wrap(chapter: String, s: ReadSettings, theme: String): String {
        var h = scriptTag.replace(chapter, "")
        h = inlineHandlers.replace(h, "")
        val head = "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">" +
            "<style id=\"__reader\">${css(s, theme)}</style>"
        val tail = "<script>${js(s.paged, s.tapSides)}</script>"
        val hm = headOpen.find(h)
        val htm = htmlOpen.find(h)
        h = when {
            hm != null -> h.substring(0, hm.range.last + 1) + head + h.substring(hm.range.last + 1)
            htm != null -> h.substring(0, htm.range.last + 1) + "<head>$head</head>" + h.substring(htm.range.last + 1)
            else -> "<html><head>$head</head><body>$h</body></html>"
        }
        // 样式放在 head 最前面会被书自带样式覆盖，所以在 body 末尾再放一次（!important 保证生效）
        h = if (h.contains("</body>", ignoreCase = true)) {
            val i = h.lastIndexOf("</body>", ignoreCase = true)
            h.substring(0, i) + "<style>${css(s, theme)}</style>" + tail + h.substring(i)
        } else h + "<style>${css(s, theme)}</style>" + tail
        if (!bodyOpen.containsMatchIn(h)) h = h.replace("</head>", "</head><body>") + "</body>"
        return h
    }
}
