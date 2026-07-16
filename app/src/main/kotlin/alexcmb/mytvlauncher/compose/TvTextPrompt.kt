package alexcmb.mytvlauncher.compose

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text

private val Panel = Color(0xF0202020)
private val Muted = Color(0xFF9AA0B4)
private const val ACCENT = 0xFF3D5AFE.toInt()

/**
 * A single-field prompt matching [TvMenu]. The field is a real EditText hosted with
 * AndroidView: on a TV the platform IME drives it reliably, which a BasicTextField didn't.
 */
@Composable
fun TvTextPrompt(title: String, hint: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp)
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(vertical = 20.dp, horizontal = 12.dp),
        ) {
            Text(
                text = title,
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                factory = { context ->
                    val density = context.resources.displayMetrics.density
                    fun px(dp: Int) = (dp * density).toInt()
                    EditText(context).apply {
                        isSingleLine = true
                        setHint(hint)
                        setTextColor(AndroidColor.WHITE)
                        setHintTextColor(0x80FFFFFF.toInt())
                        textSize = 18f
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(px(16), px(14), px(16), px(14))
                        background = GradientDrawable().apply {
                            cornerRadius = px(6).toFloat()
                            setColor(0x22FFFFFF)
                            setStroke(px(2), ACCENT)
                        }
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId != EditorInfo.IME_ACTION_DONE) {
                                return@setOnEditorActionListener false
                            }
                            val name = text.toString().trim()
                            onDismiss()
                            if (name.isNotEmpty()) onSubmit(name)
                            true
                        }
                        requestFocus()
                        val ime = context.getSystemService(InputMethodManager::class.java)
                        ime?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                },
            )
        }
    }
}
