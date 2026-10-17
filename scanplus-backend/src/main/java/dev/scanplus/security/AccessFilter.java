package dev.scanplus.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
final class AccessFilter extends OncePerRequestFilter {
    private final String adminToken;
    private final boolean tls;
    private final SecurityStore store;
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    private record Window(long minute,int requests) {}
    AccessFilter(@Value("${scanplus.admin-token}") String adminToken,
                 @Value("${scanplus.require-tls}") boolean tls,SecurityStore store) {
        if(adminToken.length()<32) throw new IllegalStateException("SCANPLUS_ADMIN_TOKEN must contain at least 32 random characters.");
        this.adminToken=adminToken;this.tls=tls;this.store=store;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        res.setHeader("Cache-Control","no-store");res.setHeader("X-Content-Type-Options","nosniff");
        if(tls && !req.isSecure()) { res.sendError(426);return; }
        String path=req.getRequestURI();
        long limit=path.equals("/api/admin/apks")?AppControlStore.MAX_APK:path.equals("/api/device/apps/inventory")?2*1024*1024:path.matches("/api/device/app-commands/[a-fA-F0-9-]{36}/ack")?65536:16384;
        if(req.getContentLengthLong()>limit) {res.sendError(413);return;}
        try{String length=req.getHeader("Content-Length");if(length!=null&&(Long.parseLong(length)>limit||Long.parseLong(length)<0)){res.sendError(413);return;}}catch(NumberFormatException e){res.sendError(400);return;}
        if(path.equals("/api/health") && req.getMethod().equals("GET")) {chain.doFilter(req,res);return;}
        if(path.equals("/api/enroll") && req.getMethod().equals("POST")) {
            long minute=System.currentTimeMillis()/60000;
            if(windows.size()>4096) windows.entrySet().removeIf(e->e.getValue().minute<minute);
            if(windows.size()>8192){res.sendError(429);return;}
            Window w=windows.compute(req.getRemoteAddr(),(key,old)->old==null||old.minute!=minute?new Window(minute,1):new Window(minute,old.requests+1));
            if(w.requests>10) {res.sendError(429);return;}
            forward(req,res,chain,limit);return;
        }
        String header=req.getHeader("Authorization");
        if(header==null||!header.startsWith("Bearer ")||header.length()>200) {res.sendError(401);return;}
        String token=header.substring(7);
        if(path.startsWith("/api/admin/")) {
            if(!Tokens.same(token,adminToken)) {res.sendError(401);return;}
        } else if(path.startsWith("/api/device/")) {
            try {
                UUID id=UUID.fromString(req.getHeader("X-Device-Id"));
                if(!store.authenticate(id,token)) {res.sendError(401);return;}
                req.setAttribute("deviceId",id);
            } catch(IllegalArgumentException|NullPointerException error) {res.sendError(401);return;}
        } else {res.sendError(404);return;}
        forward(req,res,chain,limit);
    }
    private void forward(HttpServletRequest req,HttpServletResponse res,FilterChain chain,long limit)throws IOException,ServletException{
        if(!req.getMethod().equals("POST")||req.getRequestURI().equals("/api/admin/apks")){chain.doFilter(req,res);return;}
        byte[] bytes=req.getInputStream().readNBytes((int)limit+1);if(bytes.length>limit){res.sendError(413);return;}
        chain.doFilter(new HttpServletRequestWrapper(req){
            @Override public int getContentLength(){return bytes.length;}
            @Override public long getContentLengthLong(){return bytes.length;}
            @Override public ServletInputStream getInputStream(){ByteArrayInputStream input=new ByteArrayInputStream(bytes);return new ServletInputStream(){@Override public int read(){return input.read();}@Override public int read(byte[] b,int off,int len){return input.read(b,off,len);}@Override public boolean isFinished(){return input.available()==0;}@Override public boolean isReady(){return true;}@Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous API");}};}
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
        },res);
    }
}
