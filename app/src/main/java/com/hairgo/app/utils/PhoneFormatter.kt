package com.hairgo.app.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

object PhoneFormatter {

    private val GROUPS = intArrayOf(2, 3, 4)

    /**
     * Formats SA subscriber digits for a field that already shows a fixed "+27 "
     * prefix: strips a leading 0, caps at 9 digits and groups them 2-3-4, e.g.
     * "839637850" becomes "83 963 7850".
     */
    fun format(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("0")) digits = digits.substring(1)
        if (digits.length > 9 && digits.startsWith("27")) digits = digits.substring(2)
        digits = digits.take(9)

        val sb = StringBuilder()
        var index = 0
        for (size in GROUPS) {
            if (index >= digits.length) break
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(digits, index, minOf(index + size, digits.length))
            index += size
        }
        return sb.toString()
    }

    /** Keeps [editText] formatted while typing, without moving the cursor. */
    fun attach(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            private var formatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (formatting) return
                formatting = true

                val text = s?.toString().orEmpty()
                val formatted = format(text)
                if (formatted != text) {
                    val digitsBefore = text.take(editText.selectionStart).count { it.isDigit() }
                    editText.setText(formatted)
                    editText.setSelection(cursorFor(formatted, digitsBefore))
                }

                formatting = false
            }
        })
    }

    private fun cursorFor(text: String, digits: Int): Int {
        var seen = 0
        for (i in text.indices) {
            if (seen == digits) return i
            if (text[i].isDigit()) seen++
        }
        return text.length
    }
}
