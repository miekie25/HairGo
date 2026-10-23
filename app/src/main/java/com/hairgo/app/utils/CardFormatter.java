package com.hairgo.app.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public class CardFormatter {

    /**
     * Formats a card number: groups digits 4-4-4-4 with spaces.
     * E.g., "1234567890123456" -> "1234 5678 9012 3456"
     */
    public static String formatCardNumber(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() > 16) digits = digits.substring(0, 16);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /**
     * Formats expiry as MM/YY. Inserts '/' after 2 digits.
     * E.g., "1225" -> "12/25"
     */
    public static String formatExpiry(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() > 4) digits = digits.substring(0, 4);

        if (digits.length() >= 3) {
            return digits.substring(0, 2) + "/" + digits.substring(2);
        }
        return digits;
    }

    /**
     * Validates month part (01-12).
     */
    public static boolean isValidMonth(String month) {
        if (month.length() != 2) return false;
        try {
            int m = Integer.parseInt(month);
            return m >= 1 && m <= 12;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates full expiry (MM/YY not expired).
     */
    public static boolean isValidExpiry(String expiry) {
        if (!expiry.matches("^(0[1-9]|1[0-2])/\\d{2}$")) return false;

        String[] parts = expiry.split("/");
        int month = Integer.parseInt(parts[0]);
        int year = 2000 + Integer.parseInt(parts[1]);

        java.util.Calendar now = java.util.Calendar.getInstance();
        int currentYear = now.get(java.util.Calendar.YEAR);
        int currentMonth = now.get(java.util.Calendar.MONTH) + 1;

        return year > currentYear || (year == currentYear && month >= currentMonth);
    }

    /**
     * Attaches a TextWatcher that formats card number while typing,
     * preserving cursor position.
     */
    public static void attachCardNumberFormatter(EditText editText, ValidationListener listener) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean formatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (formatting) return;
                formatting = true;

                String text = s.toString();
                int cursor = editText.getSelectionStart();
                int digitsBeforeCursor = countDigits(text, cursor);

                String formatted = formatCardNumber(text);
                if (!formatted.equals(text)) {
                    editText.setText(formatted);
                    editText.setSelection(cursorFor(formatted, digitsBeforeCursor));
                }

                // Validate only when complete (16 digits) or on blur - handled by listener
                boolean valid = formatted.replace(" ", "").length() == 16;
                listener.onValidationChanged(true, valid ? null : "incomplete");

                formatting = false;
            }
        });
    }

    /**
     * Attaches a TextWatcher that formats expiry while typing,
     * preserving cursor position and handling backspace cleanly.
     */
    public static void attachExpiryFormatter(EditText editText, ValidationListener listener) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean formatting = false;
            private String previousText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                previousText = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (formatting) return;
                formatting = true;

                String text = s.toString();
                int cursor = editText.getSelectionStart();
                int digitsBeforeCursor = countDigits(text, cursor);

                String formatted = formatExpiry(text);
                if (!formatted.equals(text)) {
                    editText.setText(formatted);
                    editText.setSelection(cursorFor(formatted, digitsBeforeCursor));
                }

                // Validate month once 2 digits entered
                String digits = formatted.replace("/", "");
                if (digits.length() >= 2) {
                    String month = digits.substring(0, 2);
                    boolean monthValid = isValidMonth(month);
                    if (!monthValid) {
                        listener.onValidationChanged(false, "month");
                    } else if (digits.length() == 4) {
                        boolean expiryValid = isValidExpiry(formatted);
                        listener.onValidationChanged(false, expiryValid ? null : "expired");
                    } else {
                        listener.onValidationChanged(false, null);
                    }
                } else {
                    listener.onValidationChanged(false, null);
                }

                formatting = false;
            }
        });
    }

    /**
     * Attaches a TextWatcher for CVV (just validates length).
     */
    public static void attachCvvFormatter(EditText editText, ValidationListener listener) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                boolean valid = text.length() == 3;
                listener.onValidationChanged(true, valid ? null : "incomplete");
            }
        });
    }

    /**
     * Attaches a TextWatcher for cardholder name (filters, capitalizes).
     */
    public static void attachNameFormatter(EditText editText, ValidationListener listener) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean formatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (formatting) return;
                formatting = true;

                String text = s.toString();
                // Keep only letters, spaces, hyphens, apostrophes
                String filtered = text.replaceAll("[^A-Za-z\\s'-]", "");
                // Capitalize
                String upper = filtered.toUpperCase();

                if (!upper.equals(text)) {
                    int cursor = editText.getSelectionStart();
                    int charsBeforeCursor = countChars(text, cursor);
                    editText.setText(upper);
                    editText.setSelection(cursorForChars(upper, charsBeforeCursor));
                }

                boolean valid = upper.trim().length() >= 2;
                listener.onValidationChanged(true, valid ? null : "invalid");

                formatting = false;
            }
        });
    }

    // --- Helpers ---

    private static int countDigits(String text, int cursor) {
        int count = 0;
        for (int i = 0; i < Math.min(cursor, text.length()); i++) {
            if (Character.isDigit(text.charAt(i))) count++;
        }
        return count;
    }

    private static int countChars(String text, int cursor) {
        int count = 0;
        for (int i = 0; i < Math.min(cursor, text.length()); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c) || c == ' ' || c == '-' || c == '\'') count++;
        }
        return count;
    }

    private static int cursorFor(String formatted, int targetDigits) {
        int seen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (seen == targetDigits) return i;
            if (Character.isDigit(formatted.charAt(i))) seen++;
        }
        return formatted.length();
    }

    private static int cursorForChars(String formatted, int targetChars) {
        int seen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (seen == targetChars) return i;
            char c = formatted.charAt(i);
            if (Character.isLetter(c) || c == ' ' || c == '-' || c == '\'') seen++;
        }
        return formatted.length();
    }

    public interface ValidationListener {
        /**
         * @param isCompleteField true for card number, CVV, name (show error on blur/submit if incomplete)
         *                        false for expiry (show month/expiry error immediately)
         * @param errorKey null if valid, "incomplete", "month", "expired", "invalid"
         */
        void onValidationChanged(boolean isCompleteField, String errorKey);
    }
}