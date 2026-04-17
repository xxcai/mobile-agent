package com.hh.agent.android.web;

public final class WebDomScriptFactory {

    private static final int MAX_DEPTH = 6;
    private static final int MAX_NODES = 200;
    private static final int MAX_SERIALIZED_CHARS = 24000;

    private WebDomScriptFactory() {
    }

    public static String buildSnapshotScript() {
        return "(function(){"
                + "var MAX_DEPTH=" + MAX_DEPTH + ";"
                + "var MAX_NODES=" + MAX_NODES + ";"
                + "var MAX_SERIALIZED_CHARS=" + MAX_SERIALIZED_CHARS + ";"
                + "var nodeCount=0;var maxDepthReached=0;var truncated=false;"
                + "function limit(v,max){v=(v||'').trim();return v.length>max?v.substring(0,max):v;}"
                + "function walk(el,depth){"
                + " if(!el||el.nodeType!==1)return null;"
                + " if(depth>MAX_DEPTH||nodeCount>=MAX_NODES){truncated=true;return null;}"
                + " var tag=(el.tagName||'').toLowerCase();"
                + " if(tag==='script'||tag==='style'||tag==='meta')return null;"
                + " maxDepthReached=Math.max(maxDepthReached,depth);"
                + " var ref='node-'+nodeCount;el.setAttribute('data-agent-ref',ref);nodeCount++;"
                + " var classes=(el.className||'').toString().trim().split(/\\s+/).filter(Boolean).slice(0,6);"
                + " var rect=el.getBoundingClientRect?el.getBoundingClientRect():null;"
                + " var node={ref:ref,tag:tag,id:limit(el.id,60),classes:classes,selector:limit(tag+(el.id?('#'+el.id):''),160),text:limit(el.textContent,80),ariaLabel:limit(el.getAttribute('aria-label'),80),clickable:!!(el.onclick||tag==='button'||tag==='a'),inputable:tag==='input'||tag==='textarea',value:limit(el.value,60),bounds:rect?{x:Math.round(rect.left),y:Math.round(rect.top),width:Math.round(rect.width),height:Math.round(rect.height)}:null,children:[]};"
                + " for(var i=0;i<el.children.length;i++){var child=walk(el.children[i],depth+1);if(child){node.children.push(child);}}"
                + " return node;"
                + "}"
                + " var payload={format:'json_tree',pageUrl:window.location.href,pageTitle:document.title,capturedAtEpochMs:Date.now(),nodeCount:0,maxDepthReached:0,truncated:false,tree:walk(document.body,0)};"
                + " payload.nodeCount=nodeCount;payload.maxDepthReached=maxDepthReached;payload.truncated=truncated;"
                + " var json=JSON.stringify(payload);"
                + " if(json.length>MAX_SERIALIZED_CHARS){payload.truncated=true;payload.tree={tag:'body',children:[]};json=JSON.stringify(payload);}"
                + " return json;"
                + "})()";
    }

    public static String buildResolveClickTargetScript(String ref, String selector) {
        return "(function(){"
                + "function limit(v,max){v=(v||'').trim();return v.length>max?v.substring(0,max):v;}"
                + "var el=null;"
                + selectorLookup(ref, selector)
                + "if(!el){return JSON.stringify({ok:false,error:'element_not_found'});}"
                + "var rect=el.getBoundingClientRect?el.getBoundingClientRect():null;"
                + "if(!rect||rect.width<=0||rect.height<=0){return JSON.stringify({ok:false,error:'element_has_no_bounds'});}"
                + "var viewportWidth=Math.max(window.innerWidth||0,document.documentElement.clientWidth||0,1);"
                + "var viewportHeight=Math.max(window.innerHeight||0,document.documentElement.clientHeight||0,1);"
                + "var centerX=rect.left+(rect.width/2);"
                + "var centerY=rect.top+(rect.height/2);"
                + "var normalizedX=Math.max(0,Math.min(1,centerX/viewportWidth));"
                + "var normalizedY=Math.max(0,Math.min(1,centerY/viewportHeight));"
                + "return JSON.stringify({ok:true,ref:el.getAttribute('data-agent-ref'),tag:el.tagName,text:limit(el.textContent,80),viewportWidth:viewportWidth,viewportHeight:viewportHeight,bounds:{left:Math.round(rect.left),top:Math.round(rect.top),width:Math.round(rect.width),height:Math.round(rect.height)},normalizedX:normalizedX,normalizedY:normalizedY});"
                + "})()";
    }

    public static String buildClickScript(String ref, String selector) {
        return buildResolveClickTargetScript(ref, selector);
    }

    public static String buildInputScript(String ref, String selector, String text) {
        return "(function(){"
                + "var el=null;"
                + selectorLookup(ref, selector)
                + "if(!el){return JSON.stringify({ok:false,error:'element_not_found'});}"
                + "el.value=" + jsString(text) + ";"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "return JSON.stringify({ok:true,ref:el.getAttribute('data-agent-ref'),tag:el.tagName,value:el.value});"
                + "})()";
    }

    public static String buildScrollToBottomScript() {
        return "(function(){"
                + "var before=Math.max(window.scrollY||0,document.documentElement.scrollTop||0,document.body.scrollTop||0);"
                + "var height=Math.max(document.documentElement.scrollHeight||0,document.body.scrollHeight||0);"
                + "window.scrollTo(0,height);"
                + "var after=Math.max(window.scrollY||0,document.documentElement.scrollTop||0,document.body.scrollTop||0);"
                + "return JSON.stringify({ok:true,before:before,after:after,height:height});"
                + "})()";
    }

    private static String selectorLookup(String ref, String selector) {
        StringBuilder builder = new StringBuilder();
        if (ref != null && !ref.isEmpty()) {
            builder.append("el=document.querySelector('[data-agent-ref=\\\"")
                    .append(escape(ref))
                    .append("\\\"]');");
        }
        if (selector != null && !selector.isEmpty()) {
            builder.append("if(!el){el=document.querySelector(")
                    .append(jsString(selector))
                    .append(");}");
        }
        return builder.toString();
    }

    private static String jsString(String value) {
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

