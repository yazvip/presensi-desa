package id.desa.presensi.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cip monogram emas berlapis — identitas visual mewah Presensi Desa. */
@Composable
fun Monogram(size: Int = 58, label: String = "PD") {
    val accents = LocalAccents.current
    val goldBrush = Brush.linearGradient(
        listOf(accents.goldLight, accents.gold, accents.goldDeep),
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(accents.emeraldDeep, accents.emeraldSoft),
                ),
            )
            .border(1.5.dp, goldBrush, CircleShape)
            .padding(3.dp)
            .border(0.75.dp, accents.gold.copy(alpha = 0.40f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = accents.goldLight,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = (size * 0.34f).sp,
                letterSpacing = 1.sp,
            ),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun BrandHeader(
    title: String,
    subtitle: String,
    eyebrow: String = "Sistem Presensi Resmi",
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(size = 56)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.gold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = accents.muted,
        )
    }
}

/** Kartu permukaan mewah dengan garis rambut halus. */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    val accents = LocalAccents.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = if (highlighted) 1.2.dp else 1.dp,
            color = if (highlighted) accents.gold.copy(alpha = 0.55f) else accents.hairline,
        ),
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

/** Kartu hero bernuansa emerald gelap dengan aksen garis emas. */
@Composable
fun HeroEmeraldCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accents = LocalAccents.current
    val bgBrush = Brush.linearGradient(
        colors = listOf(accents.emeraldDeep, accents.emeraldSoft.copy(alpha = 0.92f)),
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(accents.gold.copy(alpha = 0.65f), accents.goldDeep.copy(alpha = 0.25f)),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(bgBrush)
            .border(1.2.dp, borderBrush, RoundedCornerShape(26.dp)),
    ) {
        // Aksen geometris halus di sudut kanan atas
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = accents.goldLight.copy(alpha = 0.08f),
                radius = size.width * 0.36f,
                center = Offset(size.width * 0.92f, size.height * 0.10f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = size.width * 0.22f,
                center = Offset(size.width * 0.88f, size.height * 0.88f),
            )
        }
        Box(Modifier.padding(22.dp)) {
            content()
        }
    }
}

@Composable
fun GoldRule(modifier: Modifier = Modifier, width: Int = 52) {
    val accents = LocalAccents.current
    Box(
        modifier = modifier
            .width(width.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accents.goldLight, accents.gold, accents.goldDeep),
                ),
            ),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val accents = LocalAccents.current
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        border = if (enabled && !loading) {
            BorderStroke(1.dp, accents.gold.copy(alpha = 0.45f))
        } else {
            null
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = accents.hairline,
            disabledContentColor = accents.muted,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accents = LocalAccents.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accents.hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LuxuryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val accents = LocalAccents.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = accents.muted.copy(alpha = 0.65f)) } },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accents.gold,
            unfocusedBorderColor = accents.hairline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = accents.muted,
            focusedContainerColor = accents.surfaceElevated,
            unfocusedContainerColor = accents.surfaceElevated.copy(alpha = 0.55f),
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
) {
    val accents = LocalAccents.current
    val (bg, fg) = when (tone) {
        PillTone.Success -> accents.success.copy(alpha = 0.13f) to accents.success
        PillTone.Warning -> accents.gold.copy(alpha = 0.18f) to accents.goldDeep
        PillTone.Danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.13f) to MaterialTheme.colorScheme.error
        PillTone.Neutral -> accents.muted.copy(alpha = 0.12f) to accents.muted
        PillTone.GoldDarkBg -> Color.White.copy(alpha = 0.12f) to accents.goldLight
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(0.75.dp, fg.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp),
            color = fg,
            fontWeight = FontWeight.Bold,
        )
    }
}

enum class PillTone { Success, Warning, Danger, Neutral, GoldDarkBg }

@Composable
fun NoticeBanner(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccents.current
    val color = if (isError) MaterialTheme.colorScheme.error else accents.success
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalAccents.current.muted)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = LocalAccents.current.muted,
        textAlign = TextAlign.Start,
    )
}
