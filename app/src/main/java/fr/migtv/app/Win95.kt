package fr.migtv.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private fun DrawScope.frame(topLeft: Color, bottomRight: Color, inset: Float, thickness: Float) {
    val w = size.width
    val h = size.height
    drawRect(topLeft, Offset(inset, inset), Size(w - 2 * inset, thickness))
    drawRect(topLeft, Offset(inset, inset), Size(thickness, h - 2 * inset))
    drawRect(bottomRight, Offset(inset, h - inset - thickness), Size(w - 2 * inset, thickness))
    drawRect(bottomRight, Offset(w - inset - thickness, inset), Size(thickness, h - 2 * inset))
}

/** Bordure en relief (bouton, fenêtre) ou en creux (champ, liste), à la Windows 95. */
fun Modifier.bevel(raised: Boolean = true, fill: Color = Face): Modifier = drawBehind {
    drawRect(fill)
    val t = 1.dp.toPx()
    if (raised) {
        frame(Hilite, DarkShadow, 0f, t)
        frame(Light, Shadow, t, t)
    } else {
        frame(Shadow, Hilite, 0f, t)
        frame(DarkShadow, Light, t, t)
    }
}

/** Liseré de séparation d'une liste. */
@Composable
fun WinSeparator() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Shadow))
}

@Composable
fun WinButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    latched: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val down = pressed || latched
    Box(
        modifier
            .defaultMinSize(minHeight = if (compact) 28.dp else 34.dp)
            .bevel(raised = !down)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 3.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Ink else Shadow,
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = if (latched) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.offset(x = if (down) 1.dp else 0.dp, y = if (down) 1.dp else 0.dp)
        )
    }
}

/** Fenêtre avec barre de titre bleue ; [fill] fait occuper tout l'espace restant au contenu. */
@Composable
fun WinWindow(
    title: String,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.bevel().padding(3.dp)) {
        Row(
            Modifier.fillMaxWidth().background(TitleBlue).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = Hilite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke(this)
        }
        Column((if (fill) Modifier.weight(1f) else Modifier).padding(8.dp), content = content)
    }
}

@Composable
fun WinDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        WinWindow(title, Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun WinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column(modifier) {
        Text(label, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 15.sp, fontFamily = FontFamily.SansSerif),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth().bevel(raised = false, fill = Hilite).padding(horizontal = 8.dp, vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = Shadow, fontSize = 15.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
fun WinCheck(checked: Boolean, label: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier.clickable(interactionSource = source, indication = null) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(18.dp).bevel(raised = false, fill = Hilite), contentAlignment = Alignment.Center) {
            if (checked) Text("X", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
fun WinTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f)
                    .bevel(raised = !on)
                    .clickable(interactionSource = source, indication = null) { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/** Barre de progression indéterminée : un bloc bleu qui traverse un creux blanc. */
@Composable
fun WinProgress(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "progress")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "progress"
    )
    Box(
        modifier.fillMaxWidth().height(16.dp).bevel(raised = false, fill = Hilite).drawBehind {
            // Lu dans la phase de dessin : l'animation ne recompose rien.
            val inset = 2.dp.toPx()
            val block = size.width * 0.25f
            val left = ((size.width + block) * p - block).coerceAtLeast(inset)
            val right = ((size.width + block) * p).coerceAtMost(size.width - inset)
            if (right > left) drawRect(TitleBlue, Offset(left, inset), Size(right - left, size.height - 2 * inset))
        }
    )
}
