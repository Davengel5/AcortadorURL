package com.example.acortadorurl;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PremiumActivity extends AppCompatActivity {

    private EditText etCardNumber, etExpiry, etCvv;
    private Button btnPay;
    private boolean isFormatting = false;
    private TextWatcher expiryTextWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_premium);

        // Inicializar vistas
        TextView tvBenefits = findViewById(R.id.tvBenefits);
        etCardNumber = findViewById(R.id.etCardNumber);
        etExpiry = findViewById(R.id.etExpiry);
        etCvv = findViewById(R.id.etCvv);
        btnPay = findViewById(R.id.btnPay);

        // Configurar beneficios
        String benefits = "★ Beneficios Premium ★\n\n" +
                "• Intentos ilimitados para acortar URLs\n" +
                "• Sin Anuncios\n" +
                "• Y ya we\n" +
                "• ¿Acaso querías más?";
        tvBenefits.setText(benefits);

        // Configurar listeners
        setupCardNumberFormatting();
        setupExpiryFormatting();
        setupValidationListener();

        // Botón de pago
        btnPay.setOnClickListener(v -> upgradeToPremium());
    }

    private void setupCardNumberFormatting() {
        etCardNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;

                isFormatting = true;

                String original = s.toString().replaceAll("\\s", "");
                StringBuilder formatted = new StringBuilder();

                for (int i = 0; i < original.length(); i++) {
                    if (i > 0 && i % 4 == 0) {
                        formatted.append(" ");
                    }
                    formatted.append(original.charAt(i));
                }

                if (!s.toString().equals(formatted.toString())) {
                    etCardNumber.removeTextChangedListener(this);
                    etCardNumber.setText(formatted.toString());
                    etCardNumber.setSelection(formatted.length());
                    etCardNumber.addTextChangedListener(this);
                }

                isFormatting = false;
                validateFields();
            }
        });
    }

    private void setupExpiryFormatting() {
        expiryTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 2 && before == 0 && !s.toString().contains("/")) {
                    etExpiry.removeTextChangedListener(expiryTextWatcher);
                    etExpiry.setText(s + "/");
                    etExpiry.setSelection(3);
                    etExpiry.addTextChangedListener(expiryTextWatcher);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateFields();
            }
        };

        etExpiry.addTextChangedListener(expiryTextWatcher);
    }

    private void setupValidationListener() {
        TextWatcher validationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateFields();
            }
        };

        etCvv.addTextChangedListener(validationWatcher);
    }

    private void validateFields() {
        String cardNumber = etCardNumber.getText().toString().replaceAll("\\s", "");
        String expiry = etExpiry.getText().toString();
        String cvv = etCvv.getText().toString();

        boolean isCardValid = cardNumber.matches("^\\d{16}$");
        boolean isExpiryValid = expiry.matches("^\\d{2}/\\d{2}$");
        boolean isCvvValid = cvv.matches("^\\d{3,4}$");

        btnPay.setEnabled(isCardValid && isExpiryValid && isCvvValid);

        Log.d("VALIDATION", String.format(
                "Card: %b, Expiry: %b, CVV: %b",
                isCardValid, isExpiryValid, isCvvValid
        ));
    }

    private void upgradeToPremium() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "Error: No se pudo identificar tu cuenta", Toast.LENGTH_SHORT).show();
            return;
        }

        btnPay.setEnabled(false);
        btnPay.setText("Procesando...");

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                JSONObject json = new JSONObject();
                json.put("email", user.getEmail());

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                        .url("https://apiurl.up.railway.app/update_user.php")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                String responseData = response.body().string();

                runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        if (jsonResponse.getBoolean("success")) {
                            setResult(RESULT_OK);
                            finish();
                            Toast.makeText(PremiumActivity.this, "¡Ahora eres Premium!", Toast.LENGTH_SHORT).show();
                        } else {
                            btnPay.setEnabled(true);
                            btnPay.setText("Actualizar a Premium");
                            Toast.makeText(PremiumActivity.this,
                                    "Error: " + jsonResponse.optString("message", "Error desconocido"),
                                    Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        handleUpgradeError("Error procesando la respuesta");
                    }
                });
            } catch (Exception e) {
                handleUpgradeError("Error de conexión: " + e.getMessage());
            }
        }).start();
    }

    private void handleUpgradeError(String message) {
        runOnUiThread(() -> {
            btnPay.setEnabled(true);
            btnPay.setText("Actualizar a Premium");
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            Log.e("UPGRADE_ERROR", message);
        });
    }
}