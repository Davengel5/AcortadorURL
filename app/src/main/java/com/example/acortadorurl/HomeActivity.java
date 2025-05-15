package com.example.acortadorurl;

import androidx.appcompat.app.AppCompatActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {
    private EditText etUrl;
    private Button btnShorten, btnCopy, btnOpen, btnLogout;
    private TextView tvShortUrl, tvUrlCount;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private boolean isPremium = false;
    private Button btnUpgrade;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();

        // Configurar Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Vincular vistas
        etUrl = findViewById(R.id.etUrl);
        btnShorten = findViewById(R.id.btnShorten);
        btnCopy = findViewById(R.id.btnCopy);
        btnOpen = findViewById(R.id.btnOpen);
        btnLogout = findViewById(R.id.btnLogout);
        tvShortUrl = findViewById(R.id.tvShortUrl);
        tvUrlCount = findViewById(R.id.tvUrlCount);
        btnUpgrade = findViewById(R.id.btnUpgrade);

        // Configurar listeners
        btnShorten.setOnClickListener(v -> shortenUrl());
        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnOpen.setOnClickListener(v -> openInBrowser());
        btnLogout.setOnClickListener(v -> signOut());
        btnUpgrade.setOnClickListener(v -> showPremiumUpgrade());

        // Ocultar botones inicialmente
        btnCopy.setVisibility(View.GONE);
        btnOpen.setVisibility(View.GONE);
        btnUpgrade.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkUserStatus();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Actualizar estado premium
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                updateUrlCount(user.getEmail());
                Toast.makeText(this, "¡Felicidades! Ahora eres Premium", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkUserStatus() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Usuario autenticado - actualizar intentos
            updateUrlCount(user.getEmail());
        } else {
            // Usuario no autenticado
            tvUrlCount.setText("Inicia sesión para ver intentos");
            btnShorten.setEnabled(true); // Permitir acortar como anónimo
        }
    }

    private void updateUrlCount(String userEmail) {
        UrlDatabase.getInstance(this).getRemainingAttempts(userEmail, new UrlDatabase.AttemptsCallback() {
            @Override
            public void onSuccess(int remainingAttempts, boolean premiumStatus) {
                runOnUiThread(() -> {
                    isPremium = premiumStatus;
                    String countText = isPremium ? "Intentos: ∞ (Premium)" : "Intentos: " + remainingAttempts;
                    tvUrlCount.setText(countText);

                    // Debug: Verificar valores
                    Log.d("PREMIUM_DEBUG", "Premium status: " + premiumStatus);
                    Log.d("PREMIUM_DEBUG", "Botón upgrade visible: " + !premiumStatus);

                    btnUpgrade.setVisibility(premiumStatus ? View.GONE : View.VISIBLE);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    // Mostrar botón por si acaso (podría ser error temporal)
                    btnUpgrade.setVisibility(View.VISIBLE);
                    Log.e("PREMIUM_ERROR", message);
                });
            }
        });
    }

    private void shortenUrl() {
        String originalUrl = etUrl.getText().toString().trim();

        if (originalUrl.isEmpty()) {
            etUrl.setError("Ingresa una URL");
            return;
        }

        // Asegurar protocolo HTTP
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            originalUrl = "http://" + originalUrl;
        }

        btnShorten.setEnabled(false);
        btnShorten.setText("Acortando...");

        FirebaseUser user = mAuth.getCurrentUser();
        String userEmail = (user != null && user.getEmail() != null) ? user.getEmail() : "anonimo";

        UrlDatabase.getInstance(this).shortenUrl(originalUrl, userEmail, new UrlDatabase.UrlCallback() {
            @Override
            public void onSuccess(String shortUrl, int remainingAttempts, boolean premiumStatus) {
                runOnUiThread(() -> {
                    btnShorten.setEnabled(true);
                    btnShorten.setText("Acortar URL");
                    tvShortUrl.setText(shortUrl);
                    tvShortUrl.setVisibility(View.VISIBLE);
                    btnCopy.setVisibility(View.VISIBLE);
                    btnOpen.setVisibility(View.VISIBLE);

                    isPremium = premiumStatus;
                    String countText = isPremium ? "Intentos: ∞ (Premium)" : "Intentos: " + remainingAttempts;
                    tvUrlCount.setText(countText);

                    Toast.makeText(HomeActivity.this, "URL acortada creada", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnShorten.setEnabled(true);
                    btnShorten.setText("Acortar URL");

                    if (message.contains("Límite de intentos")) {
                        tvUrlCount.setText("Límite alcanzado");
                        btnShorten.setEnabled(false);
                    }

                    Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void copyToClipboard() {
        String shortUrl = tvShortUrl.getText().toString();
        if (!shortUrl.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("URL acortada", shortUrl);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "URL copiada al portapapeles", Toast.LENGTH_SHORT).show();
        }
    }

    private void openInBrowser() {
        String shortUrl = tvShortUrl.getText().toString();
        if (!shortUrl.isEmpty()) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(shortUrl));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void signOut() {
        mAuth.signOut();
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
    private void showPremiumUpgrade() {
        Intent intent = new Intent(this, PremiumActivity.class);
        startActivityForResult(intent, 1); // Usamos código 1 para identificar esta actividad
    }
}