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
    private TextView tvShortUrl;
    private TextView tvUrlCount;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

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

        // Listeners
        btnShorten.setOnClickListener(v -> shortenUrl());
        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnOpen.setOnClickListener(v -> openInBrowser());
        btnLogout.setOnClickListener(v -> signOut());

        btnCopy.setVisibility(View.GONE);
        btnOpen.setVisibility(View.GONE);
    }

    private void shortenUrl() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userEmail = (user != null && user.getEmail() != null) ? user.getEmail() : "anonimo";

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

        UrlDatabase.getInstance(this).shortenUrl(originalUrl, userEmail, new UrlDatabase.UrlCallback() {
            @Override
            public void onSuccess(String shortUrl) {
                runOnUiThread(() -> {
                    btnShorten.setEnabled(true);
                    btnShorten.setText("Acortar URL");
                    tvShortUrl.setText(shortUrl);
                    tvShortUrl.setVisibility(View.VISIBLE);

                    // Mostrar botones adicionales
                    btnCopy.setVisibility(View.VISIBLE);
                    btnOpen.setVisibility(View.VISIBLE);

                    Toast.makeText(HomeActivity.this, "URL acortada creada", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnShorten.setEnabled(true);
                    btnShorten.setText("Acortar URL");
                    Toast.makeText(HomeActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "No se pudo abrir la URL", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void signOut() {
        // Cerrar sesión en Firebase
        mAuth.signOut();

        // Cerrar sesión en Google
        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                task -> {
                    // Redirigir al login
                    Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                });
    }
    private void updateUrlCount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            UrlDatabase.getInstance(this).getOrCreateUserId(user.getEmail(), new UrlDatabase.UserIdCallback() {
                @Override
                public void onSuccess(int userId) {
                    UrlDatabase.getInstance(HomeActivity.this).getRemainingAttempts(userId, new UrlDatabase.AttemptsCallback() {
                        @Override
                        public void onSuccess(int remainingAttempts) {
                            runOnUiThread(() -> {
                                String countText = "Intentos: " + remainingAttempts;
                                tvUrlCount.setText(countText);
                                tvUrlCount.setVisibility(View.VISIBLE);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                tvUrlCount.setText("Error al cargar intentos");
                                tvUrlCount.setVisibility(View.VISIBLE);
                            });
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Log.e("USER_ID_ERROR", message);
                        Toast.makeText(HomeActivity.this,
                                "Error al obtener ID de usuario: " + message,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }
}