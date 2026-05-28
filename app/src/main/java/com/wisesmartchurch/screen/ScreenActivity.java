package com.wisesmartchurch.screen;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

/** ══════════════════════════════════════════
 *  WISE SMART CHURCH — Screen Display Activity
 *  Affichage plein écran natif pour Android TV Box
 *  • OkHttp WebSocket (auto-reconnect)
 *  • UDP auto-découverte de la régie
 *  • Mode Kiosk (mot de passe : Jesus@2026)
 *  • Anti-veille permanente
 *  • Télécommande 100% native (DPAD)
 * ══════════════════════════════════════════ */
public class ScreenActivity extends AppCompatActivity
        implements WscWsClient.Listener, WscUdpDiscovery.Listener {

    private static final String TAG      = "WscScreen";
    private static final String KIOSK_PW = "Jesus@2026";
    private static final int    WS_PORT  = 9000;
    private static final int    UDP_PORT = 9002;
    private static final String PREF_IP  = "regie_ip";
    private static final String PREF_SID = "screen_id";

    /* ── UI ── */
    private View        rootView;
    private TextView    tvVerse, tvRef, tvScreenInfo, tvConnStatus;
    private LinearLayout ltLayout;
    private TextView    ltName, ltTitle;
    private View        ltBar;

    /* ── Réseau ── */
    private WscWsClient     wsClient;
    private WscUdpDiscovery udpDiscovery;
    private String          regieIp = "";

    /* ── Utilitaires ── */
    private final Handler   ui = new Handler(Looper.getMainLooper());
    private Typeface        cinzel;

    /* ════════════════════════════════════════ onCreate */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Anti-veille + plein écran */
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        enableImmersive();
        startKiosk();
        setContentView(R.layout.activity_screen);

        /* Références vues */
        rootView     = findViewById(R.id.screen_root);
        tvVerse      = findViewById(R.id.tv_verse);
        tvRef        = findViewById(R.id.tv_ref);
        tvScreenInfo = findViewById(R.id.tv_screen_info);
        tvConnStatus = findViewById(R.id.tv_conn_status);
        ltLayout     = findViewById(R.id.lt_layout);
        ltName       = findViewById(R.id.lt_name);
        ltTitle      = findViewById(R.id.lt_title);
        ltBar        = findViewById(R.id.lt_bar);

        /* Police Cinzel */
        cinzel = Utils.loadCinzel(this);
        if (cinzel != null) {
            tvVerse.setTypeface(cinzel);
            tvRef.setTypeface(cinzel);
            ltName.setTypeface(cinzel);
            tvScreenInfo.setTypeface(cinzel);
        }

        /* Afficher l'IP locale brièvement */
        String localIp  = Utils.getLocalIp();
        String screenId = getPrefs().getString(PREF_SID, "TV-1");
        tvScreenInfo.setText("📺 " + screenId + "  ·  " + localIp);
        tvScreenInfo.setVisibility(View.VISIBLE);
        ui.postDelayed(() -> {
            tvScreenInfo.animate().alpha(0f).setDuration(800)
                .withEndAction(() -> tvScreenInfo.setVisibility(View.GONE)).start();
        }, 5000);

        /* Long-press → menu kiosk */
        rootView.setOnLongClickListener(v -> { showKioskExit(); return true; });

        /* Charger IP sauvegardée */
        regieIp = getPrefs().getString(PREF_IP, "");
        if (!regieIp.isEmpty()) {
            connectTo(regieIp);
        } else {
            ui.postDelayed(this::showIpDialog, 1800);
        }

        /* Démarrer la découverte UDP en arrière-plan */
        udpDiscovery = new WscUdpDiscovery(UDP_PORT, this);
        udpDiscovery.start();
    }

    /* ════════════════════════════════════════ Connexion WS */
    private void connectTo(String ip) {
        if (wsClient != null) wsClient.disconnect();
        regieIp = ip.trim();
        getPrefs().edit().putString(PREF_IP, regieIp).apply();
        setStatus("⟳  " + regieIp + ":" + WS_PORT, 0xFFD97706, true);
        wsClient = new WscWsClient("ws://" + regieIp + ":" + WS_PORT, this);
        wsClient.connect();
    }

    /* ════════════════════════════════════════ WscWsClient.Listener */
    @Override
    public void onConnected() {
        ui.post(() -> {
            setStatus("●  " + regieIp, 0xFF22C55E, true);
            ui.postDelayed(() -> setStatus(null, 0, false), 3500);
        });
    }

    @Override
    public void onDisconnected() {
        ui.post(() -> setStatus("○  Déconnecté  —  " + regieIp, 0xFFEF4444, true));
    }

    @Override
    public void onMessage(String json) {
        ui.post(() -> {
            try {
                JSONObject p = new JSONObject(json);
                switch (p.optString("type", "")) {
                    case "project": renderVerse(p);      break;
                    case "clear":   clearScreen();       break;
                    case "lt":      renderLt(p);         break;
                    case "bg":      applyBg(p.optJSONObject("bg")); break;
                    case "tag":     showTag(p.optString("label")); break;
                    case "server_info": /* info serveur, ignorer */ break;
                }
            } catch (Exception e) { Log.w(TAG, "parse: " + e.getMessage()); }
        });
    }

    /* ════════════════════════════════════════ WscUdpDiscovery.Listener */
    @Override
    public void onRegieFound(String ip) {
        ui.post(() -> {
            Log.i(TAG, "📡 Régie auto-découverte UDP: " + ip);
            if (!ip.equals(regieIp)) connectTo(ip);
        });
    }

    /* ════════════════════════════════════════ Rendu */
    private void renderVerse(JSONObject p) {
        try {
            String text  = p.optString("text",  "");
            String ref   = p.optString("ref",   "");
            String color = p.optString("color", "#FFFFFF");

            tvVerse.setText(text);
            tvRef.setText(ref);
            try { tvVerse.setTextColor(android.graphics.Color.parseColor(color)); }
            catch (Exception e) { tvVerse.setTextColor(0xFFFFFFFF); }

            tvVerse.setVisibility(text.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            tvRef.setVisibility(ref.isEmpty()   ? View.GONE       : View.VISIBLE);

            /* Taille de police adaptative (vw % → sp) */
            String fsStr = p.optString("fontSize", "5vw")
                             .replaceAll("[^0-9.]", "");
            try {
                float vw = Float.parseFloat(fsStr);
                float displayWidthDp = getResources().getDisplayMetrics().widthPixels
                                       / getResources().getDisplayMetrics().density;
                float sp = (displayWidthDp * vw / 100f) * 0.72f;
                sp = Math.min(Math.max(sp, 18f), 120f);
                tvVerse.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
            } catch (Exception ignored) {}

            applyBg(p.optJSONObject("bg"));
        } catch (Exception e) { Log.e(TAG, "renderVerse", e); }
    }

    private void clearScreen() {
        tvVerse.setVisibility(View.INVISIBLE);
        tvRef.setVisibility(View.INVISIBLE);
        rootView.setBackgroundColor(0xFF000000);
    }

    private void renderLt(JSONObject p) {
        boolean show = p.optBoolean("show", false);
        if (show) {
            ltName.setText(p.optString("name",  ""));
            ltTitle.setText(p.optString("role", p.optString("title", "")));
            ltLayout.setVisibility(View.VISIBLE);
            ltLayout.animate().alpha(1f).setDuration(400).start();
        } else {
            ltLayout.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> ltLayout.setVisibility(View.GONE)).start();
        }
    }

    private void applyBg(JSONObject bg) {
        if (bg == null) { rootView.setBackgroundColor(0xFF000000); return; }
        String mode = bg.optString("mode", "black");
        switch (mode) {
            case "gradient":
                GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{ 0xFF0a0c1a, 0xFF0d1f3c });
                rootView.setBackground(gd);
                break;
            case "color":
                try {
                    String c = bg.optString("color", "#000000");
                    rootView.setBackgroundColor(android.graphics.Color.parseColor(c));
                } catch (Exception e) { rootView.setBackgroundColor(0xFF000000); }
                break;
            default: // "black"
                rootView.setBackgroundColor(0xFF000000);
                break;
        }
    }

    private void showTag(String label) {
        if (label == null || label.isEmpty()) return;
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
    }

    /* ════════════════════════════════════════ Dialogues */
    private void showIpDialog() {
        EditText et = new EditText(this);
        et.setHint("ex : 192.168.1.10");
        et.setText(regieIp);
        et.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        et.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
            .setTitle("📡  Connecter à la régie")
            .setMessage("Entrez l'IP de la régie (visible dans ⚙ de la régie)\n\nOu appuyez sur « Scanner » pour la trouver automatiquement.")
            .setView(et)
            .setPositiveButton("CONNECTER", (d, w) -> {
                String ip = et.getText().toString().trim();
                if (!ip.isEmpty()) connectTo(ip);
            })
            .setNeutralButton("🔍  Scanner", (d, w) -> {
                setStatus("🔍  Scan UDP…", 0xFFF59E0B, true);
                if (udpDiscovery != null) udpDiscovery.sendDiscover();
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void showKioskExit() {
        EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setHint("Mot de passe administrateur");
        et.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
            .setTitle("🔒  Mode Kiosk actif")
            .setMessage("Entrez le mot de passe pour accéder aux options :")
            .setView(et)
            .setPositiveButton("VALIDER", (d, w) -> {
                if (KIOSK_PW.equals(et.getText().toString())) {
                    stopLockTask();
                    showAdminMenu();
                } else {
                    Toast.makeText(this, "❌ Mot de passe incorrect", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void showAdminMenu() {
        String screenId = getPrefs().getString(PREF_SID, "TV-1");
        new AlertDialog.Builder(this)
            .setTitle("⚙  Administration")
            .setItems(new String[]{
                "📡  Configurer IP régie (" + regieIp + ")",
                "🏷   Changer ID écran (" + screenId + ")",
                "🔄  Redémarrer connexion WS",
                "🔒  Réactiver kiosk",
                "❌  Quitter l'application"
            }, (d, which) -> {
                switch (which) {
                    case 0: showIpDialog(); break;
                    case 1: showScreenIdDialog(); break;
                    case 2: if (!regieIp.isEmpty()) connectTo(regieIp); break;
                    case 3: startKiosk(); break;
                    case 4: finish(); break;
                }
            })
            .setNegativeButton("Fermer", null)
            .show();
    }

    private void showScreenIdDialog() {
        EditText et = new EditText(this);
        et.setText(getPrefs().getString(PREF_SID, "TV-1"));
        et.setHint("ex: podium, retour, chorale");
        et.setPadding(48, 32, 48, 32);
        new AlertDialog.Builder(this)
            .setTitle("🏷  Identifiant de cet écran")
            .setView(et)
            .setPositiveButton("Sauvegarder", (d, w) -> {
                String id = et.getText().toString().trim();
                if (!id.isEmpty()) getPrefs().edit().putString(PREF_SID, id).apply();
            })
            .setNegativeButton("Annuler", null)
            .show();
    }

    /* ════════════════════════════════════════ Télécommande DPAD */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                /* OK / Entrée → dialog IP si déconnecté */
                if (wsClient == null || !wsClient.isConnected()) showIpDialog();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                showKioskExit();
                return true;
            case KeyEvent.KEYCODE_BACK:
                /* BACK bloqué en kiosk */
                return true;
            case KeyEvent.KEYCODE_HOME:
                return true; // bloqué en kiosk
        }
        return super.onKeyDown(keyCode, event);
    }

    /* ════════════════════════════════════════ Kiosk + Immersif */
    private void startKiosk() {
        DevicePolicyManager dpm =
            (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isLockTaskPermitted(getPackageName())) {
            startLockTask();
            Log.i(TAG, "🔒 Kiosk Lock Task actif");
        } else {
            Log.w(TAG, "⚠ Lock Task non permis — kiosk partiel");
        }
    }

    private void enableImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.statusBars()
                     | android.view.WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN       |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION  |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersive();
    }

    /* ════════════════════════════════════════ Helpers */
    private void setStatus(String msg, int color, boolean visible) {
        if (msg != null) tvConnStatus.setText(msg);
        tvConnStatus.setTextColor(color);
        tvConnStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private android.content.SharedPreferences getPrefs() {
        return getPreferences(Context.MODE_PRIVATE);
    }

    /* ════════════════════════════════════════ Lifecycle */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wsClient    != null) wsClient.disconnect();
        if (udpDiscovery != null) udpDiscovery.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableImmersive();
    }
}
