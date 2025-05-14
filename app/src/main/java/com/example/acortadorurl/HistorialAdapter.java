package com.example.acortadorurl;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
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
        holder.tvUrlCorta.setText(item.get("slug"));
        holder.tvFecha.setText(item.get("fecha"));
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