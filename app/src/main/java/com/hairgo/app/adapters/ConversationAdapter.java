package com.hairgo.app.adapters;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.Conversation;

import java.util.List;

public class ConversationAdapter
        extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    private final List<Conversation> conversations;
    private final String viewerUid;
    private final boolean viewerIsOwner;
    private final OnConversationClickListener listener;

    public ConversationAdapter(List<Conversation> conversations, String viewerUid,
                               boolean viewerIsOwner, OnConversationClickListener listener) {
        this.conversations = conversations;
        this.viewerUid = viewerUid;
        this.viewerIsOwner = viewerIsOwner;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);

        holder.tvName.setText(conversation.displayNameFor(viewerIsOwner));
        holder.tvLastMessage.setText(conversation.getLastMessage());

        if (conversation.getLastMessageAt() == null) {
            holder.tvTime.setText("");
        } else {
            holder.tvTime.setText(DateUtils.getRelativeTimeSpanString(
                    conversation.getLastMessageAt().getTime(), System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS));
        }

        holder.dotUnread.setVisibility(
                conversation.isUnreadFor(viewerUid) ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConversationClick(conversation);
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public static class ConversationViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvLastMessage, tvTime;
        final View dotUnread;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            dotUnread = itemView.findViewById(R.id.dotUnread);
        }
    }
}
