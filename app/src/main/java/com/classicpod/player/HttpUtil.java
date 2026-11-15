package com.classicpod.player;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public final class HttpUtil {
    private static final int TIMEOUT_MS = 12000;
    private static boolean tlsReady;

    private HttpUtil() {
    }

    public static String get(String urlString) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "ClassicPod/4.1 (Android 5)");
            conn.setRequestProperty("Accept", "application/json");
            enableTls(conn);
            int code = conn.getResponseCode();
            in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                throw new IOExceptionLite("HTTP " + code);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            int total = 0;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
                if (total > 400000) {
                    break;
                }
            }
            if (code >= 400) {
                throw new IOExceptionLite("HTTP " + code);
            }
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void enableTls(HttpURLConnection conn) {
        if (!(conn instanceof HttpsURLConnection)) {
            return;
        }
        try {
            if (!tlsReady) {
                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                HttpsURLConnection.setDefaultSSLSocketFactory(
                        new Tls12SocketFactory(sc.getSocketFactory()));
                tlsReady = true;
            }
            ((HttpsURLConnection) conn).setSSLSocketFactory(
                    HttpsURLConnection.getDefaultSSLSocketFactory());
        } catch (Exception ignored) {
        }
    }

    public static final class IOExceptionLite extends Exception {
        IOExceptionLite(String message) {
            super(message);
        }
    }
}
