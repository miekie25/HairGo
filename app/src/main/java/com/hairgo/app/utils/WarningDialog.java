package com.hairgo.app.utils;

import android.app.Activity;
import android.text.TextUtils;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hairgo.app.R;
import com.hairgo.app.firebase.AuthManager;

/**
 * Shows the one-time warning an admin left on the signed-in user's profile.
 * The note is marked as seen only after the user dismisses the dialog.
 */
public final class WarningDialog {

    private WarningDialog() {
    }

    public static void showIfUnseen(Activity activity, Runnable onDone) {
        if (activity == null || activity.isFinishing()) {
            run(onDone);
            return;
        }

        new AuthManager().loadUnseenWarning(note -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                run(onDone);
                return;
            }

            if (TextUtils.isEmpty(note)) {
                run(onDone);
                return;
            }

            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.warning_dialog_title)
                    .setMessage(note)
                    .setCancelable(false)
                    .setPositiveButton(R.string.warning_dialog_ok, (dialog, which) -> {
                        new AuthManager().markWarningSeen();
                        run(onDone);
                    })
                    .show();
        });
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }
}
