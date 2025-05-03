package com.example.acortadorurl;

import androidx.appcompat.app.AppCompatActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

    // Componentes UI
    private EditText etUrl;
    private Button btnShorten, btnCopy, btnOpen, btnLogout;
    private TextView tvShortUrl, tvUrlCount;

    // Autenticación
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    // Datos usuario
    private String userId;
    private boolean isPremium = false; // Cambiar según suscripción

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            // Aquí deberías verificar si el usuario es premium
            // isPremium = checkPremiumStatus(userId);
        }

        // Configurar Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Vincular componentes UI
        etUrl = findViewById(R.id.etUrl);
        btnShorten = findViewById(R.id.btnShorten);
        btnCopy = findViewById(R.id.btnCopy);
        btnOpen = findViewById(R.id.btnOpen);
        btnLogout = findViewById(R.id.btnLogout);
        tvShortUrl = findViewById(R.id.tvShortUrl);
        tvUrlCount = findViewById(R.id.tvUrlCount);

        // Actualizar contador de URLs
        updateUrlCount();

        // Configurar listeners
        btnShorten.setOnClickListener(v -> shortenUrl());
        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnOpen.setOnClickListener(v -> openInBrowser());
        btnLogout.setOnClickListener(v -> signOut());
    }

    private void shortenUrl() {
        String originalUrl = etUrl.getText().toString().trim();

        // Validar URL
        if (originalUrl.isEmpty()) {
            etUrl.setError("Ingresa una URL");
            return;
        }

        // Asegurar protocolo http
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            originalUrl = "http://" + originalUrl;
        }

        // Acortar URL
        UrlDatabase urlDatabase = UrlDatabase.getInstance(this);
        String shortUrl = urlDatabase.shortenUrl(originalUrl, userId, isPremium);

        // Manejar resultado
        if (shortUrl.equals("LIMIT_REACHED")) {
            Toast.makeText(this, "Límite de 50 URLs alcanzado", Toast.LENGTH_LONG).show();
        } else {
            tvShortUrl.setText(shortUrl);
            tvShortUrl.setVisibility(View.VISIBLE);
            btnCopy.setVisibility(View.VISIBLE);
            btnOpen.setVisibility(View.VISIBLE);
            updateUrlCount();
            Toast.makeText(this, "URL acortada creada", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("URL acortada", tvShortUrl.getText().toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "URL copiada", Toast.LENGTH_SHORT).show();
    }

    private void openInBrowser() {
        String shortUrl = tvShortUrl.getText().toString();
        String originalUrl = UrlDatabase.getInstance(this).getOriginalUrl(
                shortUrl.replace("misapp://", "")
        );

        if (originalUrl != null) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateUrlCount() {
        int count = UrlDatabase.getInstance(this).getUserUrlCount(userId);
        String countText = "Usadas: " + count + "/" + (isPremium ? "∞" : "50");
        tvUrlCount.setText(countText);
    }

    private void signOut() {
        // Cerrar sesión en Firebase
        mAuth.signOut();

        // Cerrar sesión en Google
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Redirigir a Login y limpiar historial
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    // Método para manejar deep links (opcional)
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    private void handleDeepLink(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String shortUrl = intent.getData().toString();
            String originalUrl = UrlDatabase.getInstance(this).getOriginalUrl(
                    shortUrl.replace("misapp://", "")
            );

            if (originalUrl != null) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}