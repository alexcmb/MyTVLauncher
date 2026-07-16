package alexcmb.mytvlauncher.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

private val Panel = Color(0xF0202020)
private val Accent = Color(0xFF3D5AFE)

/** A single-field prompt matching [TvMenu]; the keyboard comes up with it. */
@Composable
fun TvTextPrompt(title: String, hint: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    var text by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp)
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(24.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
                    .border(2.dp, Accent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (text.isEmpty()) {
                    Text(hint, color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    // A blank name would create a nameless row; just close instead.
                    keyboardActions = KeyboardActions(onDone = {
                        val name = text.trim()
                        onDismiss()
                        if (name.isNotEmpty()) onSubmit(name)
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                )
            }
        }
    }
}
