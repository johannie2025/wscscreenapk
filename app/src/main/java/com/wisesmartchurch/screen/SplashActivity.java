package com.wisesmartchurch.screen;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/** ══════════════════════════════════════════
 *  WISE SMART CHURCH — Splash Screen
 *  Animation d'intro avec crédits
 * ══════════════════════════════════════════ */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* Plein écran immersif dès le départ */
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        setContentView(R.layout.activity_splash);

        /* Police Cinzel (assets/fonts/Cinzel-Bold.ttf) */
        Typeface cinzel = Utils.loadCinzel(this);
        Typeface cinzelReg = Utils.loadCinzelRegular(this);

        TextView tvSystem  = findViewById(R.id.splash_system);
        TextView tvTitle   = findViewById(R.id.splash_title);
        TextView tvBy      = findViewById(R.id.splash_by);
        TextView tvAuthor  = findViewById(R.id.splash_author);
        TextView tvContact = findViewById(R.id.splash_contact);
        View     divider   = findViewById(R.id.splash_divider);

        if (cinzel != null) {
            tvTitle.setTypeface(cinzel);
            tvSystem.setTypeface(cinzel);
            tvAuthor.setTypeface(cinzel);
        }
        if (cinzelReg != null) {
            tvBy.setTypeface(cinzelReg);
            tvContact.setTypeface(cinzelReg);
        }

        /* Séquence d'animation : fade-in + slide-up en cascade */
        View[] seq = { tvSystem, tvTitle, divider, tvBy, tvAuthor, tvContact };
        long   delay = 200;
        for (View v : seq) {
            v.setAlpha(0f);
            v.setTranslationY(50f);
            ObjectAnimator alpha  = ObjectAnimator.ofFloat(v, "alpha",        0f,  1f);
            ObjectAnimator slideY = ObjectAnimator.ofFloat(v, "translationY", 50f, 0f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(alpha, slideY);
            set.setDuration(750);
            set.setStartDelay(delay);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.start();
            delay += 380;
        }

        /* Naviguer vers ScreenActivity après 4,5 s */
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, ScreenActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 4500);
    }
}
