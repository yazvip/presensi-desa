package id.desa.presensi.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Cip monogram emas — identitas visual aplikasi. */
@Composable
fun Monogram(size: Int = 56, label: String = "PD") {
    val accents = LocalAccents.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(accents.emerald, accents.emerald.copy(alpha = 0.82f)),
                ),
            )
            .border(1.5.dp, accents.gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = accents.gold,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun BrandHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Monogram()
        Spacer(Modifier.height(20.dp))
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAccents.current.muted,
        )
    }
}

/** Kartu permukaan dengan garis rambut tipis — bukan bayangan tebal. */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accents = LocalAccents.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, accents.hairline),
    ) {
        Box(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun GoldRule(modifier: Modifier = Modifier, width: Int = 44) {
    Box(
        modifier = modifier
            .width(width.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LocalAccents.current.gold),
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
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
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
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val accents = LocalAccents.current
    val (bg, fg) = when (tone) {
        PillTone.Success -> accents.success.copy(alpha = 0.14f) to accents.success
        PillTone.Warning -> accents.gold.copy(alpha = 0.20f) to accents.gold
        PillTone.Danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f) to MaterialTheme.colorScheme.error
        PillTone.Neutral -> accents.muted.copy(alpha = 0.14f) to accents.muted
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

enum class PillTone { Success, Warning, Danger, Neutral }

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
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.30f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
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
