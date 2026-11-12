package com.gathin.wallpapermanager;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight local Wi-Fi web server for transferring and managing TV wallpapers from a phone or PC.
 */
public final class TransferServer {
    private static final String TAG = "TransferServer";
    public static final int PORT = 8888;

    public interface ChangeListener {
        void onWallpapersChanged();
    }

    private final File wallpapersDir;
    private final ChangeListener listener;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running;

    public TransferServer(File wallpapersDir, ChangeListener listener) {
        this.wallpapersDir = wallpapersDir;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "Server started on port " + PORT);
                while (running) {
                    Socket socket = serverSocket.accept();
                    executor.execute(() -> handleClient(socket));
                }
            } catch (Exception e) {
                if (running) Log.e(TAG, "Server error", e);
            }
        });
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
        }
        executor.shutdownNow();
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            // Read HTTP request line and headers
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int b;
            int consecutiveLf = 0;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if (b == '\n') {
                    consecutiveLf++;
                    if (consecutiveLf == 2 || (headerBuf.size() >= 4 &&
                            headerBuf.toString().endsWith("\r\n\r\n"))) {
                        break;
                    }
                } else if (b != '\r') {
                    consecutiveLf = 0;
                }
            }

            String headerText = headerBuf.toString(StandardCharsets.UTF_8.name());
            String[] lines = headerText.split("\r?\n");
            if (lines.length == 0 || lines[0].isEmpty()) return;

            String[] reqParts = lines[0].split(" ");
            if (reqParts.length < 2) return;
            String method = reqParts[0];
            String path = reqParts[1];

            int contentLength = 0;
            String contentType = "";
            for (String line : lines) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception ignored) {}
                } else if (lower.startsWith("content-type:")) {
                    contentType = line.substring(13).trim();
                }
            }

            if ("GET".equalsIgnoreCase(method)) {
                if (path.equals("/") || path.startsWith("/?")) {
                    serveHtml(out);
                } else if (path.startsWith("/thumb?name=")) {
                    serveImage(out, path.substring(12));
                } else if (path.startsWith("/delete?name=")) {
                    deleteImage(path.substring(13));
                    sendRedirect(out, "/");
                } else {
                    send404(out);
                }
            } else if ("POST".equalsIgnoreCase(method) && path.startsWith("/upload")) {
                handleUpload(in, contentLength, contentType);
                sendRedirect(out, "/");
            } else {
                send404(out);
            }
            out.flush();
        } catch (Exception e) {
            Log.w(TAG, "Client handle error", e);
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void serveHtml(OutputStream out) throws Exception {
        File[] files = wallpapersDir.listFiles((d, name) -> {
            String l = name.toLowerCase();
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
        });
        int count = files != null ? files.length : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>Realme TV — Wallpaper Manager</title>");
        sb.append("<style>");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0E1218; color: #E0E6ED; margin: 0; padding: 24px; }");
        sb.append(".container { max-width: 960px; margin: 0 auto; }");
        sb.append("h1 { color: #00E5FF; margin-bottom: 6px; font-size: 26px; }");
        sb.append(".subtitle { color: #8C9BAE; margin-bottom: 24px; font-size: 14px; }");
        sb.append(".card { background: #161D26; border-radius: 12px; padding: 20px; margin-bottom: 24px; border: 1px solid #232E3C; }");
        sb.append(".upload-zone { border: 2px dashed #00E5FF; border-radius: 10px; padding: 28px; text-align: center; background: rgba(0,229,255,0.04); cursor: pointer; }");
        sb.append(".btn { background: #00E5FF; color: #05101A; font-weight: bold; border: none; padding: 12px 24px; border-radius: 8px; cursor: pointer; font-size: 15px; margin-top: 14px; }");
        sb.append(".btn:hover { background: #33EBFF; }");
        sb.append(".grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; margin-top: 16px; }");
        sb.append(".item { background: #1C2430; border-radius: 8px; overflow: hidden; border: 1px solid #283547; display: flex; flex-direction: column; }");
        sb.append(".item img { width: 100%; height: 125px; object-fit: cover; background: #10141A; }");
        sb.append(".item-info { padding: 10px; flex: 1; display: flex; flex-direction: column; justify-content: space-between; }");
        sb.append(".item-name { font-size: 12px; word-break: break-all; color: #CFD8DC; margin-bottom: 8px; }");
        sb.append(".btn-del { background: #E53935; color: white; border: none; padding: 6px 12px; border-radius: 6px; cursor: pointer; font-size: 12px; text-decoration: none; text-align: center; }");
        sb.append(".btn-del:hover { background: #FF5252; }");
        sb.append("</style></head><body>");
        sb.append("<div class='container'>");
        sb.append("<h1>🖼️ Realme TV Wallpaper Manager</h1>");
        sb.append("<div class='subtitle'>Total ").append(count).append(" wallpapers active on TV (used for random dock backgrounds & screensavers)</div>");

        // Upload Form
        sb.append("<div class='card'>");
        sb.append("<h2 style='font-size:18px; margin-top:0;'>Upload New Wallpapers</h2>");
        sb.append("<form action='/upload' method='post' enctype='multipart/form-data'>");
        sb.append("<div class='upload-zone'>");
        sb.append("<p style='margin:0 0 10px 0;'>Select JPEG, PNG, or WebP images to send to your TV:</p>");
        sb.append("<input type='file' name='file' accept='image/*' multiple required style='color:#B0BEC5;'>");
        sb.append("<br><button type='submit' class='btn'>📤 Upload to TV</button>");
        sb.append("</div></form></div>");

        // Existing Wallpapers Grid
        sb.append("<div class='card'>");
        sb.append("<h2 style='font-size:18px; margin-top:0;'>Current TV Wallpapers (").append(count).append(")</h2>");
        sb.append("<div class='grid'>");
        if (files != null) {
            for (File f : files) {
                String encoded = java.net.URLEncoder.encode(f.getName(), "UTF-8");
                sb.append("<div class='item'>");
                sb.append("<img src='/thumb?name=").append(encoded).append("' loading='lazy'>");
                sb.append("<div class='item-info'>");
                sb.append("<div class='item-name'>").append(f.getName()).append("</div>");
                sb.append("<a class='btn-del' href='/delete?name=").append(encoded).append("' onclick=\"return confirm('Delete this wallpaper from TV?')\">🗑️ Delete</a>");
                sb.append("</div></div>");
            }
        }
        sb.append("</div></div></div></body></html>");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes());
        out.write(bytes);
    }

    private void serveImage(OutputStream out, String nameParam) throws Exception {
        String decoded = URLDecoder.decode(nameParam, "UTF-8");
        File file = new File(wallpapersDir, decoded);
        if (!file.exists() || !file.isFile()) {
            send404(out);
            return;
        }
        String contentType = decoded.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\nContent-Length: " + file.length() + "\r\nCache-Control: max-age=3600\r\nConnection: close\r\n\r\n").getBytes());
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    private void deleteImage(String nameParam) throws Exception {
        String decoded = URLDecoder.decode(nameParam, "UTF-8");
        File file = new File(wallpapersDir, decoded);
        if (file.exists() && file.isFile()) {
            boolean deleted = file.delete();
            Log.i(TAG, "Deleted wallpaper: " + decoded + ", success=" + deleted);
            if (listener != null) listener.onWallpapersChanged();
        }
    }

    private void handleUpload(InputStream in, int length, String contentType) {
        try {
            if (!contentType.contains("boundary=")) {
                // Raw binary upload
                File target = new File(wallpapersDir, "upload_" + System.currentTimeMillis() + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    byte[] buf = new byte[8192];
                    int remaining = length;
                    while (remaining > 0) {
                        int r = in.read(buf, 0, Math.min(buf.length, remaining));
                        if (r == -1) break;
                        fos.write(buf, 0, r);
                        remaining -= r;
                    }
                }
                if (listener != null) listener.onWallpapersChanged();
                return;
            }

            // Multipart parsing
            String boundary = "--" + contentType.substring(contentType.indexOf("boundary=") + 9).trim();
            byte[] boundaryBytes = boundary.getBytes(StandardCharsets.UTF_8);

            // Read the full body
            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int remaining = length;
            while (remaining > 0) {
                int r = in.read(buf, 0, Math.min(buf.length, remaining));
                if (r == -1) break;
                bodyStream.write(buf, 0, r);
                remaining -= r;
            }
            byte[] body = bodyStream.toByteArray();

            // Simple multipart extractor
            int index = 0;
            while (index < body.length) {
                int partStart = indexOf(body, boundaryBytes, index);
                if (partStart == -1) break;
                int partHeaderStart = partStart + boundaryBytes.length;
                int partHeaderEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), partHeaderStart);
                if (partHeaderEnd == -1) break;

                String partHeader = new String(body, partHeaderStart, partHeaderEnd - partHeaderStart, StandardCharsets.UTF_8);
                int nextPart = indexOf(body, boundaryBytes, partHeaderEnd + 4);
                int dataEnd = nextPart != -1 ? nextPart - 2 : body.length; // strip \r\n before next boundary

                if (partHeader.contains("filename=\"")) {
                    int fnStart = partHeader.indexOf("filename=\"") + 10;
                    int fnEnd = partHeader.indexOf("\"", fnStart);
                    String filename = partHeader.substring(fnStart, fnEnd);
                    if (!filename.isEmpty()) {
                        filename = new File(filename).getName(); // strip any paths
                        File target = new File(wallpapersDir, filename);
                        try (FileOutputStream fos = new FileOutputStream(target)) {
                            fos.write(body, partHeaderEnd + 4, dataEnd - (partHeaderEnd + 4));
                        }
                        Log.i(TAG, "Uploaded new wallpaper: " + filename);
                    }
                }
                index = nextPart;
            }
            if (listener != null) listener.onWallpapersChanged();
        } catch (Exception e) {
            Log.e(TAG, "Upload processing failed", e);
        }
    }

    private static int indexOf(byte[] src, byte[] target, int start) {
        if (target.length == 0 || start >= src.length) return -1;
        for (int i = start; i <= src.length - target.length; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (src[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private void sendRedirect(OutputStream out, String target) throws Exception {
        out.write(("HTTP/1.1 303 See Other\r\nLocation: " + target + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
    }

    private void send404(OutputStream out) throws Exception {
        byte[] bytes = "Not Found".getBytes();
        out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes());
        out.write(bytes);
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.1.7";
    }
}
