package com.hairgo.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;
import com.hairgo.app.R;
import com.hairgo.app.adapters.MessageAdapter;
import com.hairgo.app.firebase.ChatManager;
import com.hairgo.app.models.ChatMessage;
import com.hairgo.app.utils.Snackbars;

import java.util.ArrayList;
import java.util.List;

/**
 * The one screen both sides of a booking use to chat. It reads the raw
 * booking to work out who is who, then listens to the messages subcollection
 * live. The same screen serves client and owner - the viewer's identity from
 * ChatContext decides which side the bubbles appear on.
 */
public class ChatActivity extends AppCompatActivity {

    private RecyclerView recyclerMessages;
    private View emptyState;
    private EditText etMessage;
    private ImageButton btnSend;
    private ProgressBar sendProgress;
    private ProgressBar progress;
    private View inputBar;

    private ChatManager chatManager;
    private ChatManager.ChatContext chatContext;
    private MessageAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private ListenerRegistration messagesListener;

    /**
     * True when the messages listener failed and was cancelled (e.g. the very
     * first listen on a brand-new chat). The next successful send re-attaches
     * the listener so the new message appears live.
     */
    private boolean listenerNeedsReattach = false;

    /**
     * Tracks whether the conversation document already exists. It is derived
     * from the message stream because the conversation document is created in
     * the same batch as the first message: an empty stream means nothing has
     * been sent yet, so the next send must use the create path.
     */
    private boolean conversationExists = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        String bookingId = getIntent().getStringExtra("bookingId");

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        recyclerMessages = findViewById(R.id.recyclerMessages);
        emptyState = findViewById(R.id.emptyState);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        sendProgress = findViewById(R.id.sendProgress);
        progress = findViewById(R.id.progress);
        inputBar = findViewById(R.id.inputBar);

        adapter = new MessageAdapter(messages, "");
        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> onSendClicked());

        if (bookingId == null || bookingId.isEmpty()) {
            Snackbars.showThen(findViewById(android.R.id.content),
                    R.string.chat_unavailable, this::finish);
            return;
        }

        chatManager = new ChatManager();
        loadContext(bookingId);
    }

    /**
     * Resolves the participants (client + owner) before anything is shown, so
     * a user who is not part of the booking never sees the input bar or the
     * message stream.
     */
    private void loadContext(String bookingId) {
        progress.setVisibility(View.VISIBLE);
        inputBar.setVisibility(View.GONE);

        chatManager.loadContext(bookingId, new ChatManager.ContextCallback() {
            @Override
            public void onSuccess(ChatManager.ChatContext context) {
                chatContext = context;
                progress.setVisibility(View.GONE);
                inputBar.setVisibility(View.VISIBLE);

                TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
                headerTitle.setText(context.viewerIsOwner ? context.clientName : context.salonName);

                // Rebuild the adapter with the viewer's uid so the bubble side
                // and the "who sent this" check use the real identity.
                adapter = new MessageAdapter(messages, context.viewerUid);
                recyclerMessages.setAdapter(adapter);

                listenToMessages();
            }

            @Override
            public void onFailure(String errorMessage) {
                progress.setVisibility(View.GONE);
                Snackbars.showThen(findViewById(android.R.id.content),
                        errorMessage, ChatActivity.this::finish);
            }
        });
    }

    /**
     * Listens to the messages live so both people see new messages without
     * leaving the screen. Also drives markRead whenever an incoming message
     * (one not sent by the viewer) arrives while the screen is open.
     */
    private void listenToMessages() {
        messagesListener = chatManager.listenToMessages(chatContext.bookingId,
                new ChatManager.MessagesCallback() {
                    @Override
                    public void onSuccess(List<ChatMessage> updated) {
                        // A successful snapshot clears any earlier failure: the
                        // stream is healthy again, so stop tracking a re-attach.
                        listenerNeedsReattach = false;

                        messages.clear();
                        messages.addAll(updated);
                        adapter.notifyDataSetChanged();
                        updateEmptyState();

                        conversationExists = !messages.isEmpty();
                        if (conversationExists) {
                            scrollToBottom();
                            maybeMarkRead();
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // The listener is cancelled by Firestore and will not
                        // recover. For a brand-new chat the conversation and its
                        // messages subcollection do not exist yet, so treat this
                        // as the empty state instead of showing a scary error.
                        // A later successful send re-attaches the listener.
                        stopListening();
                        listenerNeedsReattach = true;
                        updateEmptyState();
                    }
                });
    }

    /**
     * Re-attaches the messages listener after a send. Needed when the first
     * listen failed on a brand-new chat (a listener that errors is cancelled by
     * Firestore and does not recover), so the just-sent message shows up live
     * without leaving the screen.
     */
    private void reattachMessagesIfNeeded() {
        if (listenerNeedsReattach && messagesListener == null && chatContext != null) {
            listenerNeedsReattach = false;
            listenToMessages();
        }
    }

    private void stopListening() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }

    private void updateEmptyState() {
        emptyState.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerMessages.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Clears the viewer's unread dot only when the newest message was not sent
     * by the viewer - there is nothing to clear when you sent the last message.
     */
    private void maybeMarkRead() {
        ChatMessage last = messages.get(messages.size() - 1);
        if (!chatContext.viewerUid.equals(last.getSenderId()) && chatManager != null) {
            chatManager.markRead(chatContext);
        }
    }

    private void scrollToBottom() {
        recyclerMessages.post(() ->
                recyclerMessages.scrollToPosition(messages.size() - 1));
    }

    private void onSendClicked() {
        String text = etMessage.getText().toString();
        if (text.trim().isEmpty()) {
            return;
        }
        if (text.trim().length() > ChatManager.MAX_MESSAGE_LENGTH) {
            Snackbars.show(findViewById(android.R.id.content), R.string.chat_message_too_long);
            return;
        }

        setSending(true);
        chatManager.sendMessage(chatContext, text, conversationExists,
                new ChatManager.SendCallback() {
                    @Override
                    public void onSuccess() {
                        setSending(false);
                        etMessage.setText("");
                        // The conversation now exists; make sure the live stream
                        // is attached so this (and future) messages appear.
                        reattachMessagesIfNeeded();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        setSending(false);
                        Snackbars.show(findViewById(android.R.id.content), errorMessage);
                    }
                });
    }

    private void setSending(boolean sending) {
        btnSend.setEnabled(!sending);
        btnSend.setVisibility(sending ? View.GONE : View.VISIBLE);
        sendProgress.setVisibility(sending ? View.VISIBLE : View.GONE);
        etMessage.setEnabled(!sending);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
    }
}
