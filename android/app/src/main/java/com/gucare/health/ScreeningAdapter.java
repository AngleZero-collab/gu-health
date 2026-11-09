package com.gucare.health;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Presentation only: eligibility comes from the existing, tested policy engine. */
final class ScreeningAdapter extends RecyclerView.Adapter<ScreeningAdapter.Holder> {
    private final List<PolicyEngine.Policy> policies;
    private final Ui ui;
    private final Function<PolicyEngine.Policy,String> status;
    private final Consumer<PolicyEngine.Policy> details,dial,maps;
    ScreeningAdapter(List<PolicyEngine.Policy> policies,Ui ui,Function<PolicyEngine.Policy,String> status,
                     Consumer<PolicyEngine.Policy> details,Consumer<PolicyEngine.Policy> dial,Consumer<PolicyEngine.Policy> maps){
        this.policies=policies;this.ui=ui;this.status=status;this.details=details;this.dial=dial;this.maps=maps;
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_screening,parent,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){
        PolicyEngine.Policy policy=policies.get(position);
        ((TextView)holder.itemView.findViewById(R.id.screening_title)).setText(policy.title);
        ((TextView)holder.itemView.findViewById(R.id.screening_status)).setText(status.apply(policy));
        holder.itemView.setContentDescription(policy.title+"，點選查看說明");
        holder.itemView.setOnClickListener(v->details.accept(policy));
        holder.itemView.findViewById(R.id.btn_dial).setOnClickListener(v->dial.accept(policy));
        holder.itemView.findViewById(R.id.btn_maps).setOnClickListener(v->maps.accept(policy));
        ui.enforce(holder.itemView);
    }
    @Override public int getItemCount(){return policies.size();}
    static final class Holder extends RecyclerView.ViewHolder { Holder(View v){super(v);} }
}
