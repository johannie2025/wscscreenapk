package com.wisesmartchurch.screen;

import android.util.Log;
import okhttp3.*;
import okio.ByteString;
import java.util.concurrent.TimeUnit;

/** WebSocket OkHttp — reconnexion automatique toutes les 4s */
public class WscWsClient {

    private static final String TAG = "WscWsClient";

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onMessage(String json);
    }

    private final String   url;
    private final Listener listener;
    private final OkHttpClient httpClient;
    private WebSocket  ws;
    private boolean    active = true;
    private boolean    connected = false;

    public WscWsClient(String url, Listener listener) {
        this.url      = url;
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0,    TimeUnit.MILLISECONDS)   // pas de timeout en lecture
            .pingInterval(25,  TimeUnit.SECONDS)        // keep-alive ping
            .retryOnConnectionFailure(false)
            .build();
    }

    public void connect() {
        if (!active) return;
        Log.i(TAG, "⟳ Connexion: " + url);
        Request request = new Request.Builder().url(url).build();
        ws = httpClient.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                connected = true;
                Log.i(TAG, "✅ Connecté: " + url);
                if (listener != null) listener.onConnected();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (listener != null) listener.onMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                if (listener != null) listener.onMessage(bytes.utf8());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                connected = false;
                Log.i(TAG, "🔌 Fermé (" + code + "): " + reason);
                if (listener != null) listener.onDisconnected();
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connected = false;
                Log.w(TAG, "❌ Échec: " + t.getMessage());
                if (listener != null) listener.onDisconnected();
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (!active) return;
        new android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed(() -> { if (active) connect(); }, 4000);
    }

    public boolean isConnected() { return connected; }

    public void send(String json) {
        if (ws != null && connected) {
            ws.send(json);
        }
    }

    public void disconnect() {
        active    = false;
        connected = false;
        if (ws != null) { ws.close(1000, "bye"); ws = null; }
        httpClient.dispatcher().executorService().shutdown();
    }
}
