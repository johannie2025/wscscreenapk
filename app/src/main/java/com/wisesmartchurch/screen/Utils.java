package com.wisesmartchurch.screen;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class Utils {

    private Utils() {}

    /** Charge Cinzel-Bold depuis assets/fonts/Cinzel-Bold.ttf */
    public static Typeface loadCinzel(Context ctx) {
        try { return Typeface.createFromAsset(ctx.getAssets(), "fonts/Cinzel-Bold.ttf"); }
        catch (Exception e) { Log.w("Utils", "Cinzel-Bold.ttf manquant — fallback système"); return null; }
    }

    /** Charge Cinzel-Regular depuis assets/fonts/Cinzel-Regular.ttf */
    public static Typeface loadCinzelRegular(Context ctx) {
        try { return Typeface.createFromAsset(ctx.getAssets(), "fonts/Cinzel-Regular.ttf"); }
        catch (Exception e) { return null; }
    }

    /** Obtenir l'IP locale IPv4 (Ethernet ou WiFi) */
    public static String getLocalIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en != null && en.hasMoreElements(); ) {
                for (Enumeration<InetAddress> addrs = en.nextElement().getInetAddresses();
                     addrs.hasMoreElements(); ) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address)
                        return addr.getHostAddress();
                }
            }
        } catch (Exception e) { Log.e("Utils", "getLocalIp", e); }
        return "?.?.?.?";
    }
}
