package com.example.acortadorurl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistorialAdapter extends RecyclerView.Adapter<HistorialAdapter.ViewHolder> {
    private List<Map<String, String>> urls;

    public HistorialAdapter(List<Map<String, String>> urls) {
        this.urls = urls;
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

        // Formatear fecha
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = inputFormat.parse(item.get("fecha"));
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            holder.tvFecha.setText(outputFormat.format(date));
        } catch (Exception e) {
            holder.tvFecha.setText(item.get("fecha"));
        }

        // Click para copiar
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

        public ViewHolder(View view) {
            super(view);
            tvUrlOriginal = view.findViewById(R.id.tvUrlOriginal);
            tvUrlCorta = view.findViewById(R.id.tvUrlCorta);
            tvFecha = view.findViewById(R.id.tvFecha);
        }
    }
}