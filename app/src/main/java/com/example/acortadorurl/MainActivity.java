package com.example.acortadorurl;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 123;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private Button btnGoogleLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        // Configurar Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // Usa el ID de Firebase
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Error en Google Sign-In: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            insertUserInApi(user); // ¡Aquí llamamos al método!
                        }

                        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "Error en autenticación", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    // Ya comiteate nmms
    private void insertUserInApi(FirebaseUser user) {
        if (user == null || user.getEmail() == null) return;

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                // Primero verificar si el usuario ya existe
                JSONObject checkUser = new JSONObject();
                checkUser.put("email", user.getEmail());

                Request checkRequest = new Request.Builder()
                        .url("https://apiurl.up.railway.app/check_user.php")
                        .post(RequestBody.create(
                                checkUser.toString(),
                                MediaType.parse("application/json")
                        ))
                        .build();

                Response checkResponse = client.newCall(checkRequest).execute();
                String checkData = checkResponse.body().string();
                JSONObject checkJson = new JSONObject(checkData);

                // Si el usuario ya existe, no hacer nada
                if (checkJson.getBoolean("exists")) {
                    Log.d("API_RESPONSE", "Usuario ya registrado");
                    return;
                }

                // Si no existe, crear nuevo usuario
                JSONObject json = new JSONObject();
                json.put("nombre", user.getDisplayName() != null ? user.getDisplayName() : "Usuario");
                json.put("email", user.getEmail());
                json.put("tipo", "Free");
                json.put("intentos", 5);

                Request request = new Request.Builder()
                        .url("https://apiurl.up.railway.app/insert_user.php")
                        .post(RequestBody.create(
                                json.toString(),
                                MediaType.parse("application/json")
                        ))
                        .build();

                Response response = client.newCall(request).execute();
                String responseData = response.body().string();

                runOnUiThread(() -> {
                    Log.d("API_RESPONSE", "Usuario registrado: " + responseData);
                });

            } catch (Exception e) {
                Log.e("API_ERROR", "Error insertando usuario", e);
            }
        }).start();
    }

}