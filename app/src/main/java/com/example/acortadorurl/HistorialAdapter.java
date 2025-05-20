package com.example.acortadorurl;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HistorialAdapter extends RecyclerView.Adapter<HistorialAdapter.ViewHolder> {
    private List<Map<String, String>> urls;
    private OnItemDeletedListener onItemDeletedListener;
    public HistorialAdapter(List<Map<String, String>> urls) {
        this.urls = urls;
    }

    public interface OnItemDeletedListener {
        void onItemDeleted();
    }

    public void setOnItemDeletedListener(OnItemDeletedListener listener) {
        this.onItemDeletedListener = listener;
    }
    public void actualizarDatos(List<Map<String, String>> nuevosDatos) {
        this.urls = nuevosDatos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_url, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, String> item = urls.get(position);

        holder.tvUrlOriginal.setText(item.get("url"));
        holder.tvUrlCorta.setText("https://apiurl.up.railway.app/" + item.get("slug"));

        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = inputFormat.parse(item.get("fecha"));
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            holder.tvFecha.setText(outputFormat.format(date));
        } catch (Exception e) {
            holder.tvFecha.setText(item.get("fecha"));
        }

        holder.btnDelete.setOnClickListener(v -> {
            String slug = item.get("slug");
            deleteUrlFromHistory(slug, holder.itemView.getContext(), holder.getAdapterPosition());
        });

        holder.itemView.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) v.getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("URL corta", holder.tvUrlCorta.getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "URL copiada", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvUrlOriginal, tvUrlCorta, tvFecha;
        public Button btnDelete;

        public ViewHolder(View view) {
            super(view);
            tvUrlOriginal = view.findViewById(R.id.tvUrlOriginal);
            tvUrlCorta = view.findViewById(R.id.tvUrlCorta);
            tvFecha = view.findViewById(R.id.tvFecha);
            btnDelete = view.findViewById(R.id.btnDelete);
        }
    }
    private void deleteUrlFromHistory(String slug, Context context, int position) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("slug", slug);
                json.put("email", user.getEmail());

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                        .url("https://apiurl.up.railway.app/historial.php")
                        .delete(body)
                        .build();

                Response response = client.newCall(request).execute();
                String responseData = response.body().string();

                ((Activity) context).runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        if (jsonResponse.getBoolean("success")) {
                            // Usamos la posición pasada como parámetro
                            urls.remove(position);
                            notifyItemRemoved(position);
                            Toast.makeText(context, "URL eliminada", Toast.LENGTH_SHORT).show();
                            if (onItemDeletedListener != null) {
                                onItemDeletedListener.onItemDeleted();
                            }
                        } else {
                            Toast.makeText(context, "Error: " + jsonResponse.optString("error"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(context, "Error procesando respuesta", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}