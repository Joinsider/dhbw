package de.fampopprol.dhbwhorb.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.roboto_flex
import org.jetbrains.compose.resources.Font

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun myTypography(): Typography {

    // Roboto Flex font family - will fallback to system font automatically if font fails to load
    val customFont = FontFamily(
        Font(Res.font.roboto_flex, FontWeight.Normal),
        Font(Res.font.roboto_flex, FontWeight.Medium),
        Font(Res.font.roboto_flex, FontWeight.Bold)
    )

    // Stretched/expanded font for expressive titles
    val customFontExpanded = FontFamily(
        Font(
            Res.font.roboto_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(700),
                FontVariation.grade(-100),
                //FontVariation.Setting("xtra", 603f),
                //FontVariation.Setting("YTLC", 416f),
                //FontVariation.Setting("YTUC", 528f),
                //FontVariation.Setting("YTAS", 649f),
                FontVariation.width(75f) // 100 = normal, 125 = expanded
            )
        )
    )

    // Condensed/squished font for compact text
    val customFontConsensed = FontFamily(
        Font(
            Res.font.roboto_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.width(25f),
                FontVariation.Setting("opsz", 20f),
                FontVariation.Setting("YTLC", 440f),
                FontVariation.Setting("YTUC", 580f),
                FontVariation.Setting("YTAS", 700f),
            )
        )
    )

    return Typography(
        // Display styles
        displayLarge = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp
        ),
        displayMedium = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp
        ),
        // Headline styles
        headlineLarge = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Normal,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        // Title styles
        titleLarge = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        // Body styles
        bodyLarge = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        // Label styles
        labelLarge = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = customFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = customFontConsensed,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        // Expressive styles
        headlineLargeEmphasized = TextStyle(
            fontFamily = customFontExpanded,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 72.sp,
            letterSpacing = 0.sp
        )
    )
}
