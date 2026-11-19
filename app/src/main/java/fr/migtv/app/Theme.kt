package fr.migtv.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Palette « années 90 » sobre : gris de fenêtre, biseaux, barre de titre bleu marine.
internal val Face = Color(0xFFC0C0C0)
internal val Hilite = Color(0xFFFFFFFF)
internal val Light = Color(0xFFDFDFDF)
internal val Shadow = Color(0xFF808080)
internal val DarkShadow = Color(0xFF000000)
internal val TitleBlue = Color(0xFF000080)
internal val Ink = Color(0xFF000000)
internal val InkSoft = Color(0xFF404040)
internal val ErrorRed = Color(0xFF800000)

/** Thème de l'application : texte noir sans empattement sur fond gris, aucun arrondi. */
@Composable
fun MigTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TitleBlue, onPrimary = Hilite,
            background = Face, onBackground = Ink,
            surface = Face, onSurface = Ink
        )
    ) {
        Surface(color = Face, contentColor = Ink) {
            ProvideTextStyle(TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp), content)
        }
    }
}
