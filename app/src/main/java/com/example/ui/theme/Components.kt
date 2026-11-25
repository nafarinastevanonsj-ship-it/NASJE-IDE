package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

val NasjeBlueVioletGradient = Brush.linearGradient(
    colors = listOf(NasjeBlue, NasjeIndigo, NasjeViolet)
)

val NasjeGlowBorderGradient = Brush.linearGradient(
    colors = listOf(
        NasjeCyan.copy(alpha = 0.65f),
        NasjeBlue.copy(alpha = 0.45f),
        NasjeViolet.copy(alpha = 0.65f)
    )
)

val NasjeBackdropBrush = Brush.verticalGradient(
    colors = listOf(NasjeBgDeep, NasjeBgPrimary, Color(0xFF080D1B))
)

@Composable
fun NasjeLogoMark(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = NasjeViolet, spotColor = NasjeBlue)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, NasjeGlowBorderGradient, RoundedCornerShape(12.dp))
            .background(Color(0xFF070E21)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_nasje_logo),
            contentDescription = "Logo NASJE IDE",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    glowColor: Brush = NasjeGlowBorderGradient,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    cornerRadius: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .clip(shape)
        .border(BorderStroke(1.dp, glowColor), shape)
        .background(backgroundColor, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(contentPadding)

    Column(modifier = baseModifier, content = content)
}

@Composable
fun GradientPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    testTag: String = "gradient_primary_button",
    gradient: Brush = NasjeBlueVioletGradient
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(54.dp)
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = NasjeViolet,
                spotColor = NasjeBlue
            )
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color(0xFF1E293B),
            contentColor = Color.White,
            disabledContentColor = NasjeTextMuted
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    brush = if (enabled) gradient else Brush.linearGradient(
                        listOf(Color(0xFF1E293B), Color(0xFF1E293B))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (enabled) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun LanguageBadge(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = accentColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
        }
    }
}
