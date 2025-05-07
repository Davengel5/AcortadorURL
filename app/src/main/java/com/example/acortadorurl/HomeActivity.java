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
    private EditText etUrl;
    private Button btnShorten, btnCopy, btnOpen, btnLogout;
    private TextView tvShortUrl, tvUrlCount;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private String userId;
    private boolean isPremium = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
        }

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

        // Actualizar contador
        updateUrlCount();

        // Listeners
        btnShorten.setOnClickListener(v -> shortenUrl());
        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnOpen.setOnClickListener(v -> openInBrowser());
        btnLogout.setOnClickListener(v -> signOut());
    }

    private void shortenUrl() {
        String originalUrl = etUrl.getText().toString().trim();

        if (originalUrl.isEmpty()) {
            etUrl.setError("Ingresa una URL");
            return;
        }

        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            originalUrl = "http://" + originalUrl;
        }

        btnShorten.setEnabled(false);
        btnShorten.setText("Acortando...");

        UrlDatabase.getInstance(this).shortenUrl(originalUrl, userId, isPremium, new UrlDatabase.UrlCallback() {
            @Override
            public void onSuccess(String shortUrl) {
                runOnUiThread(() -> {
                    btnShorten.setEnabled(true);
                    btnShorten.setText("Acortar URL");
                    tvShortUrl.setText(shortUrl);
                    tvShortUrl.setVisibility(View.VISIBLE);
                    btnCopy.setVisibility(View.VISIBLE);
                    btnOpen.setVisibility(View.VISIBLE);
                    updateUrlCount();
                    Toast.makeText(HomeActivity.this, "URL acortada creada", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnShorten.setEnabled(true);
                    btnShorten.setText("Acortar URL");
                    if (message.equals("LIMIT_REACHED")) {
                        Toast.makeText(HomeActivity.this, "Límite de 5 URLs alcanzado", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(HomeActivity.this, "Error: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("URL acortada", tvShortUrl.getText().toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "URL copiada", Toast.LENGTH_SHORT).show();
    }

    private void openInBrowser() {
        String shortUrl = tvShortUrl.getText().toString();

        if (shortUrl.isEmpty()) {
            Toast.makeText(this, "No hay URL para abrir", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extraer el código corto (ej: "http://192.168.1.239/AcortadorURL/abc123" -> "abc123")
        String[] parts = shortUrl.split("/");
        String shortCode = parts[parts.length - 1];

        // Construir URL de redirección
        String redirectUrl = "http://192.168.1.239/AcortadorURL/" + shortCode;

        // Abrir en navegador
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUrlCount() {
        if (userId != null) {
            UrlDatabase urlDatabase = UrlDatabase.getInstance(this);
            int count = urlDatabase.getUrlCount(userId);
            String countText = "Usadas: " + count + "/" + (isPremium ? "∞" : "5");
            tvUrlCount.setText(countText);
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
}