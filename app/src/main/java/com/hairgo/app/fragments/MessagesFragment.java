package com.hairgo.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.hairgo.app.R;
import com.hairgo.app.activities.ChatActivity;
import com.hairgo.app.adapters.ConversationAdapter;
import com.hairgo.app.firebase.ChatManager;
import com.hairgo.app.models.Conversation;

import java.util.ArrayList;
import java.util.List;

/**
 * The Messages inbox, shared by the client and owner dashboards. Which side
 * the viewer is on only changes the name shown for each conversation, so the
 * role is passed in as an argument rather than duplicating the fragment.
 */
public class MessagesFragment extends Fragment {

    private static final String ARG_VIEWER_IS_OWNER = "viewer_is_owner";

    public static MessagesFragment newInstance(boolean viewerIsOwner) {
        MessagesFragment fragment = new MessagesFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_VIEWER_IS_OWNER, viewerIsOwner);
        fragment.setArguments(args);
        return fragment;
    }

    private final List<Conversation> conversations = new ArrayList<>();
    private ConversationAdapter adapter;
    private TextView tvEmpty;
    private ChatManager chatManager;
    private ListenerRegistration conversationsListener;
    private boolean viewerIsOwner = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        chatManager = new ChatManager();

        if (getArguments() != null) {
            viewerIsOwner = getArguments().getBoolean(ARG_VIEWER_IS_OWNER, false);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        final String uid = user == null ? "" : user.getUid();

        RecyclerView rv = view.findViewById(R.id.rvConversations);
        tvEmpty = view.findViewById(R.id.tvMessagesEmpty);

        adapter = new ConversationAdapter(conversations, uid, viewerIsOwner,
                conversation -> openConversation(conversation));
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        startListening();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopListening();
    }

    /** Live inbox: a new message bumps its conversation to the top on its own. */
    private void startListening() {
        stopListening();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        conversationsListener = chatManager.listenToConversations(user.getUid(),
                new ChatManager.ConversationsCallback() {
                    @Override
                    public void onSuccess(List<Conversation> updated) {
                        if (!isAdded()) return;
                        conversations.clear();
                        conversations.addAll(updated);
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(conversations.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Could not load messages",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void stopListening() {
        if (conversationsListener != null) {
            conversationsListener.remove();
            conversationsListener = null;
        }
    }

    /**
     * Opens the chat for the tapped conversation. The conversation document id
     * is the booking id, which is what ChatActivity expects.
     */
    private void openConversation(Conversation conversation) {
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra("bookingId", conversation.getBookingId());
        startActivity(intent);
    }
}
