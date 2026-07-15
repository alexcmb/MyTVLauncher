package alexcmb.mytvlauncher.browse

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import alexcmb.mytvlauncher.R

/** A dark, TV-styled single-field prompt, matching [MenuDialog]. */
class TextInputDialog(context: Context) {
    private val dialog = Dialog(context)
    private val view = LayoutInflater.from(context).inflate(R.layout.dialog_text_input, null)
    private val titleView: TextView = view.findViewById(R.id.input_title)
    private val field: EditText = view.findViewById(R.id.input_field)

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        // Bring the keyboard up with the dialog; typing on a remote is tedious enough.
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    fun setTitle(title: CharSequence): TextInputDialog {
        titleView.text = title
        return this
    }

    /** Fires with the trimmed text once the user confirms; a blank entry just closes. */
    fun onSubmit(action: (String) -> Unit): TextInputDialog {
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            val text = field.text.toString().trim()
            dialog.dismiss()
            if (text.isNotEmpty()) action(text)
            true
        }
        return this
    }

    fun show() {
        dialog.show()
        field.requestFocus()
    }
}
