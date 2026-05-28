package com.wisesmartchurch.screen;

import android.util.Log;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * Auto-découverte UDP de la régie sur le réseau local.
 *
 * Protocole (même port 9002) :
 *   TV Box  → broadcast  "WSC_DISCOVER"
 *   Régie   → unicast    "WSC_ANNOUNCE:<ip>:9000"
 *   Régie   → broadcast  "WSC_ANNOUNCE:<ip>:9000"  (toutes les 10s)
 *
 * La régie (APK ou Electron EXE) doit envoyer ce broadcast.
 * Pour le serveur WebSocket embarqué dans le Capacitor APK existant,
 * la classe WscUdpAnnounce côté régie gère l'annonce périodique.
 */
public class WscUdpDiscovery extends Thread {

    private static final String TAG     = "WscUdp";
    private static final String ANNOUNCE = "WSC_ANNOUNCE:";
    private static final String DISCOVER = "WSC_DISCOVER";

    public interface Listener {
        void onRegieFound(String ip);
    }

    private final int      port;
    private final Listener listener;
    private DatagramSocket socket;
    private volatile boolean running = true;

    public WscUdpDiscovery(int port, Listener listener) {
        this.port     = port;
        this.listener = listener;
        setDaemon(true);
        setName("WscUdpDiscovery");
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
            socket.setSoTimeout(6000); // timeout → re-send discover

            sendDiscover(); // premier envoi immédiat

            byte[] buf = new byte[512];
            while (running) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength(),
                                           StandardCharsets.UTF_8).trim();
                    Log.d(TAG, "UDP ← " + pkt.getAddress().getHostAddress() + ": " + msg);

                    if (msg.startsWith(ANNOUNCE)) {
                        // Format: WSC_ANNOUNCE:192.168.1.14:9000
                        String payload = msg.substring(ANNOUNCE.length());
                        String ip = payload.contains(":") ? payload.split(":")[0] : payload;
                        if (!ip.isEmpty() && listener != null) {
                            listener.onRegieFound(ip.trim());
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Pas de réponse dans les 6s → renvoyer DISCOVER
                    if (running) sendDiscover();
                }
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "UDP thread error", e);
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    /** Envoie un broadcast DISCOVER sur toutes les interfaces réseau */
    public void sendDiscover() {
        new Thread(() -> {
            try (DatagramSocket ds = new DatagramSocket()) {
                ds.setBroadcast(true);
                byte[] msg = DISCOVER.getBytes(StandardCharsets.UTF_8);

                // 255.255.255.255 (global broadcast)
                send(ds, msg, "255.255.255.255");

                // Broadcasts spécifiques par interface (plus fiable sur certains routeurs)
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                     en != null && en.hasMoreElements(); ) {
                    for (InterfaceAddress ia : en.nextElement().getInterfaceAddresses()) {
                        InetAddress bcast = ia.getBroadcast();
                        if (bcast != null) send(ds, msg, bcast.getHostAddress());
                    }
                }
                Log.d(TAG, "📡 DISCOVER broadcast envoyé");
            } catch (Exception e) {
                Log.w(TAG, "sendDiscover: " + e.getMessage());
            }
        }, "WscUdpSend").start();
    }

    private void send(DatagramSocket ds, byte[] data, String address) {
        try {
            InetAddress addr = InetAddress.getByName(address);
            ds.send(new DatagramPacket(data, data.length, addr, port));
        } catch (Exception e) { /* ignorer */ }
    }

    public void stopDiscovery() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }
}
