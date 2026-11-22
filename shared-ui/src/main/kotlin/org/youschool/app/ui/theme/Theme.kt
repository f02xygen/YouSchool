package org.youschool.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

private fun createScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    surfaceContainerLowest: Color,
    surfaceContainerLow: Color,
    surfaceContainer: Color,
    surfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    outline: Color,
    outlineVariant: Color,
    isDark: Boolean
): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = surfaceContainerLowest,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant
        )
    }
}

// 1. Blue
private val LightBlueScheme = createScheme(
    primary = Color(0xFF1565C0), onPrimary = Color.White, primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70), onSecondary = Color.White, secondaryContainer = Color(0xFFD7E3F8), onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF006874), onTertiary = Color.White, tertiaryContainer = Color(0xFF9EEFFD), onTertiaryContainer = Color(0xFF001F24),
    surface = Color(0xFFFDFBFF), onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F9FF),
    surfaceContainer = Color(0xFFF1F4FA), surfaceContainerHigh = Color(0xFFEBEFF5), surfaceContainerHighest = Color(0xFFE5E9F0),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C7D0), isDark = false
)
private val DarkBlueScheme = createScheme(
    primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258), primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB), onSecondary = Color(0xFF253140), secondaryContainer = Color(0xFF3B4858), onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFF82D3E0), onTertiary = Color(0xFF00363D), tertiaryContainer = Color(0xFF004F58), onTertiaryContainer = Color(0xFF9EEFFD),
    surface = Color(0xFF111418), onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C7D0),
    surfaceContainerLowest = Color(0xFF0C0E12), surfaceContainerLow = Color(0xFF14171D),
    surfaceContainer = Color(0xFF1A1F26), surfaceContainerHigh = Color(0xFF222830), surfaceContainerHighest = Color(0xFF2C323B),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E), isDark = true
)

// 2. Purple
private val LightPurpleScheme = createScheme(
    primary = Color(0xFF6750A4), onPrimary = Color.White, primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71), onSecondary = Color.White, secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFD8E4), onTertiaryContainer = Color(0xFF31111D),
    surface = Color(0xFFFFFBFE), onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9F5FD),
    surfaceContainer = Color(0xFFF7F2FA), surfaceContainerHigh = Color(0xFFF1ECF4), surfaceContainerHighest = Color(0xFFEBE6EE),
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0), isDark = false
)
private val DarkPurpleScheme = createScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72), primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41), secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF492532), tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD8E4),
    surface = Color(0xFF141218), onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0F0D13), surfaceContainerLow = Color(0xFF1C1A20),
    surfaceContainer = Color(0xFF211F26), surfaceContainerHigh = Color(0xFF2B2930), surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F), isDark = true
)

// 3. Green
private val LightGreenScheme = createScheme(
    primary = Color(0xFF2E6B38), onPrimary = Color.White, primaryContainer = Color(0xFFB0F4B2), onPrimaryContainer = Color(0xFF002206),
    secondary = Color(0xFF526350), onSecondary = Color.White, secondaryContainer = Color(0xFFD5E8D0), onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF386567), onTertiary = Color.White, tertiaryContainer = Color(0xFFBCEBEB), onTertiaryContainer = Color(0xFF002022),
    surface = Color(0xFFFCFDF6), onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D9), onSurfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7FAF2),
    surfaceContainer = Color(0xFFF1F5EE), surfaceContainerHigh = Color(0xFFEBF0E8), surfaceContainerHighest = Color(0xFFE5EAE2),
    outline = Color(0xFF72796F), outlineVariant = Color(0xFFC2C9BD), isDark = false
)
private val DarkGreenScheme = createScheme(
    primary = Color(0xFF95D797), onPrimary = Color(0xFF003910), primaryContainer = Color(0xFF125222), onPrimaryContainer = Color(0xFFB0F4B2),
    secondary = Color(0xFFB9CCB4), onSecondary = Color(0xFF243424), secondaryContainer = Color(0xFF3A4B39), onSecondaryContainer = Color(0xFFD5E8D0),
    tertiary = Color(0xFFA1CED0), onTertiary = Color(0xFF003739), tertiaryContainer = Color(0xFF1F4E50), onTertiaryContainer = Color(0xFFBCEBEB),
    surface = Color(0xFF121511), onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940), onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceContainerLowest = Color(0xFF0C0E0B), surfaceContainerLow = Color(0xFF151914),
    surfaceContainer = Color(0xFF1B201A), surfaceContainerHigh = Color(0xFF242922), surfaceContainerHighest = Color(0xFF2F342D),
    outline = Color(0xFF8C9388), outlineVariant = Color(0xFF424940), isDark = true
)

// 4. Orange
private val LightOrangeScheme = createScheme(
    primary = Color(0xFF8B5000), onPrimary = Color.White, primaryContainer = Color(0xFFFFDCBE), onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF725A42), onSecondary = Color.White, secondaryContainer = Color(0xFFFEDDBE), onSecondaryContainer = Color(0xFF291806),
    tertiary = Color(0xFF5B6323), onTertiary = Color.White, tertiaryContainer = Color(0xFFDFEA9B), onTertiaryContainer = Color(0xFF171E00),
    surface = Color(0xFFFFFBFF), onSurface = Color(0xFF201A15),
    surfaceVariant = Color(0xFFEFE0D5), onSurfaceVariant = Color(0xFF4F453E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF7F2),
    surfaceContainer = Color(0xFFFBF2EC), surfaceContainerHigh = Color(0xFFF5EBE5), surfaceContainerHighest = Color(0xFFEFE5DF),
    outline = Color(0xFF81756D), outlineVariant = Color(0xFFD2C4BB), isDark = false
)
private val DarkOrangeScheme = createScheme(
    primary = Color(0xFFFFB870), onPrimary = Color(0xFF4A2800), primaryContainer = Color(0xFF6B3B00), onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFDEC2A2), onSecondary = Color(0xFF402C17), secondaryContainer = Color(0xFF58422B), onSecondaryContainer = Color(0xFFFEDDBE),
    tertiary = Color(0xFFC3CD82), onTertiary = Color(0xFF2C3400), tertiaryContainer = Color(0xFF434B0D), onTertiaryContainer = Color(0xFFDFEA9B),
    surface = Color(0xFF17130E), onSurface = Color(0xFFECE0DA),
    surfaceVariant = Color(0xFF4F453E), onSurfaceVariant = Color(0xFFD2C4BB),
    surfaceContainerLowest = Color(0xFF100D09), surfaceContainerLow = Color(0xFF1D1813),
    surfaceContainer = Color(0xFF241E19), surfaceContainerHigh = Color(0xFF2E2721), surfaceContainerHighest = Color(0xFF3A322B),
    outline = Color(0xFF9C8F84), outlineVariant = Color(0xFF4F453E), isDark = true
)

// 5. Red
private val LightRedScheme = createScheme(
    primary = Color(0xFFB3261E), onPrimary = Color.White, primaryContainer = Color(0xFFF9DEDC), onPrimaryContainer = Color(0xFF410E0B),
    secondary = Color(0xFF775656), onSecondary = Color.White, secondaryContainer = Color(0xFFFFDAD9), onSecondaryContainer = Color(0xFF2C1516),
    tertiary = Color(0xFF755A2F), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFDDB0), onTertiaryContainer = Color(0xFF291800),
    surface = Color(0xFFFFFBFB), onSurface = Color(0xFF201A1A),
    surfaceVariant = Color(0xFFF4DDDC), onSurfaceVariant = Color(0xFF534343),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF5F5),
    surfaceContainer = Color(0xFFFBF1F1), surfaceContainerHigh = Color(0xFFF5EBEB), surfaceContainerHighest = Color(0xFFEFE4E4),
    outline = Color(0xFF857373), outlineVariant = Color(0xFFD7C1C1), isDark = false
)
private val DarkRedScheme = createScheme(
    primary = Color(0xFFF2B8B5), onPrimary = Color(0xFF601410), primaryContainer = Color(0xFF8C1D18), onPrimaryContainer = Color(0xFFF9DEDC),
    secondary = Color(0xFFE6BDBC), onSecondary = Color(0xFF442929), secondaryContainer = Color(0xFF5D3F3F), onSecondaryContainer = Color(0xFFFFDAD9),
    tertiary = Color(0xFFE5C18D), onTertiary = Color(0xFF422C05), tertiaryContainer = Color(0xFF5B4219), onTertiaryContainer = Color(0xFFFFDDB0),
    surface = Color(0xFF181212), onSurface = Color(0xFFECE0DF),
    surfaceVariant = Color(0xFF534343), onSurfaceVariant = Color(0xFFD7C1C1),
    surfaceContainerLowest = Color(0xFF110B0B), surfaceContainerLow = Color(0xFF1D1616),
    surfaceContainer = Color(0xFF241D1D), surfaceContainerHigh = Color(0xFF2E2626), surfaceContainerHighest = Color(0xFF3A3030),
    outline = Color(0xFFA08C8C), outlineVariant = Color(0xFF534343), isDark = true
)

// 6. Teal
private val LightTealScheme = createScheme(
    primary = Color(0xFF006A60), onPrimary = Color.White, primaryContainer = Color(0xFF74F8E5), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F), onSecondary = Color.White, secondaryContainer = Color(0xFFCCE8E2), onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF456179), onTertiary = Color.White, tertiaryContainer = Color(0xFFCCE5FF), onTertiaryContainer = Color(0xFF001E31),
    surface = Color(0xFFFAFDFB), onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5FAF8),
    surfaceContainer = Color(0xFFEFF5F3), surfaceContainerHigh = Color(0xFFE9F0EE), surfaceContainerHighest = Color(0xFFE3EAE8),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C5), isDark = false
)
private val DarkTealScheme = createScheme(
    primary = Color(0xFF53DBC9), onPrimary = Color(0xFF003731), primaryContainer = Color(0xFF005048), onPrimaryContainer = Color(0xFF74F8E5),
    secondary = Color(0xFFB1CCC6), onSecondary = Color(0xFF1C3531), secondaryContainer = Color(0xFF324B47), onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFADCAE6), onTertiary = Color(0xFF153349), tertiaryContainer = Color(0xFF2D4961), onTertiaryContainer = Color(0xFFCCE5FF),
    surface = Color(0xFF101413), onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color(0xFF0A0F0E), surfaceContainerLow = Color(0xFF141A19),
    surfaceContainer = Color(0xFF1A2220), surfaceContainerHigh = Color(0xFF232A29), surfaceContainerHighest = Color(0xFF2E3533),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4947), isDark = true
)

// 7. Cyan
private val LightCyanScheme = createScheme(
    primary = Color(0xFF00658E), onPrimary = Color.White, primaryContainer = Color(0xFFC7E7FF), onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4F616E), onSecondary = Color.White, secondaryContainer = Color(0xFFD2E5F5), onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF63597C), onTertiary = Color.White, tertiaryContainer = Color(0xFFE9DDFF), onTertiaryContainer = Color(0xFF1F1635),
    surface = Color(0xFFF7FAFD), onSurface = Color(0xFF181C1F),
    surfaceVariant = Color(0xFFDCE3E9), onSurfaceVariant = Color(0xFF40484D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F8FC),
    surfaceContainer = Color(0xFFF0F5F9), surfaceContainerHigh = Color(0xFFEAEEF3), surfaceContainerHighest = Color(0xFFE4E9ED),
    outline = Color(0xFF71787E), outlineVariant = Color(0xFFC0C7CD), isDark = false
)
private val DarkCyanScheme = createScheme(
    primary = Color(0xFF84CFFF), onPrimary = Color(0xFF00344C), primaryContainer = Color(0xFF004C6C), onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB6C9D8), onSecondary = Color(0xFF21323E), secondaryContainer = Color(0xFF374955), onSecondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFFCDC0E9), onTertiary = Color(0xFF342B4C), tertiaryContainer = Color(0xFF4B4163), onTertiaryContainer = Color(0xFFE9DDFF),
    surface = Color(0xFF0F1417), onSurface = Color(0xFFE0E3E7),
    surfaceVariant = Color(0xFF40484D), onSurfaceVariant = Color(0xFFC0C7CD),
    surfaceContainerLowest = Color(0xFF0A0F12), surfaceContainerLow = Color(0xFF141A1E),
    surfaceContainer = Color(0xFF1A2126), surfaceContainerHigh = Color(0xFF22282D), surfaceContainerHighest = Color(0xFF2D3338),
    outline = Color(0xFF8A9298), outlineVariant = Color(0xFF40484D), isDark = true
)

// 8. Indigo
private val LightIndigoScheme = createScheme(
    primary = Color(0xFF3F51B5), onPrimary = Color.White, primaryContainer = Color(0xFFDFE0FF), onPrimaryContainer = Color(0xFF000B61),
    secondary = Color(0xFF5B5D72), onSecondary = Color.White, secondaryContainer = Color(0xFFE0E1F9), onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFD7F3), onTertiaryContainer = Color(0xFF2E1128),
    surface = Color(0xFFFDF8FD), onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC), onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F6FE),
    surfaceContainer = Color(0xFFF3F3FA), surfaceContainerHigh = Color(0xFFEDECF4), surfaceContainerHighest = Color(0xFFE7E6EE),
    outline = Color(0xFF777680), outlineVariant = Color(0xFFC7C5D0), isDark = false
)
private val DarkIndigoScheme = createScheme(
    primary = Color(0xFFBCC2FF), onPrimary = Color(0xFF031E85), primaryContainer = Color(0xFF26389C), onPrimaryContainer = Color(0xFFDFE0FF),
    secondary = Color(0xFFC4C5DD), onSecondary = Color(0xFF2D2F42), secondaryContainer = Color(0xFF434559), onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7), onTertiary = Color(0xFF44263E), tertiaryContainer = Color(0xFF5D3C55), onTertiaryContainer = Color(0xFFFFD7F3),
    surface = Color(0xFF131318), onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0E0E14), surfaceContainerLow = Color(0xFF191A20),
    surfaceContainer = Color(0xFF1F2028), surfaceContainerHigh = Color(0xFF292A32), surfaceContainerHighest = Color(0xFF34353E),
    outline = Color(0xFF918F9A), outlineVariant = Color(0xFF46464F), isDark = true
)

// 9. Pink
private val LightPinkScheme = createScheme(
    primary = Color(0xFF984062), onPrimary = Color.White, primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E001E),
    secondary = Color(0xFF74565F), onSecondary = Color.White, secondaryContainer = Color(0xFFFFD9E2), onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7C5635), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFDCC2), onTertiaryContainer = Color(0xFF2E1500),
    surface = Color(0xFFFFF8F8), onSurface = Color(0xFF201A1C),
    surfaceVariant = Color(0xFFF2DDE2), onSurfaceVariant = Color(0xFF514347),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF2F4),
    surfaceContainer = Color(0xFFFBF1F3), surfaceContainerHigh = Color(0xFFF5EBED), surfaceContainerHighest = Color(0xFFEFE5E7),
    outline = Color(0xFF837377), outlineVariant = Color(0xFFD5C2C6), isDark = false
)
private val DarkPinkScheme = createScheme(
    primary = Color(0xFFFFAFC9), onPrimary = Color(0xFF5D1134), primaryContainer = Color(0xFF7A294A), onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE3BDC7), onSecondary = Color(0xFF422931), secondaryContainer = Color(0xFF5A3F48), onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFBD94), onTertiary = Color(0xFF472A0C), tertiaryContainer = Color(0xFF613F1F), onTertiaryContainer = Color(0xFFFFDCC2),
    surface = Color(0xFF181114), onSurface = Color(0xFFECE0E2),
    surfaceVariant = Color(0xFF514347), onSurfaceVariant = Color(0xFFD5C2C6),
    surfaceContainerLowest = Color(0xFF120B0E), surfaceContainerLow = Color(0xFF1E1619),
    surfaceContainer = Color(0xFF251D20), surfaceContainerHigh = Color(0xFF2F2629), surfaceContainerHighest = Color(0xFF3A3134),
    outline = Color(0xFF9E8C91), outlineVariant = Color(0xFF514347), isDark = true
)

// 10. Lime
private val LightLimeScheme = createScheme(
    primary = Color(0xFF4D6700), onPrimary = Color.White, primaryContainer = Color(0xFFC9F06A), onPrimaryContainer = Color(0xFF141F00),
    secondary = Color(0xFF5A6148), onSecondary = Color.White, secondaryContainer = Color(0xFFDFE6C6), onSecondaryContainer = Color(0xFF181E0A),
    tertiary = Color(0xFF396660), onTertiary = Color.White, tertiaryContainer = Color(0xFFBCECE4), onTertiaryContainer = Color(0xFF00201D),
    surface = Color(0xFFFDFDF4), onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE2E4D4), onSurfaceVariant = Color(0xFF45483C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAF0),
    surfaceContainer = Color(0xFFF3F5EA), surfaceContainerHigh = Color(0xFFEDF0E4), surfaceContainerHighest = Color(0xFFE7EADE),
    outline = Color(0xFF76796B), outlineVariant = Color(0xFFC6C8B8), isDark = false
)
private val DarkLimeScheme = createScheme(
    primary = Color(0xFFAED351), onPrimary = Color(0xFF263500), primaryContainer = Color(0xFF394D00), onPrimaryContainer = Color(0xFFC9F06A),
    secondary = Color(0xFFC3CAAB), onSecondary = Color(0xFF2D331D), secondaryContainer = Color(0xFF434A31), onSecondaryContainer = Color(0xFFDFE6C6),
    tertiary = Color(0xFFA0D0C8), onTertiary = Color(0xFF003732), tertiaryContainer = Color(0xFF1F4E49), onTertiaryContainer = Color(0xFFBCECE4),
    surface = Color(0xFF12140E), onSurface = Color(0xFFE3E3DA),
    surfaceVariant = Color(0xFF45483C), onSurfaceVariant = Color(0xFFC6C8B8),
    surfaceContainerLowest = Color(0xFF0D0F0A), surfaceContainerLow = Color(0xFF171B12),
    surfaceContainer = Color(0xFF1D2118), surfaceContainerHigh = Color(0xFF262920), surfaceContainerHighest = Color(0xFF31352A),
    outline = Color(0xFF909282), outlineVariant = Color(0xFF45483C), isDark = true
)

// 11. Amber
private val LightAmberScheme = createScheme(
    primary = Color(0xFF7D5700), onPrimary = Color.White, primaryContainer = Color(0xFFFFDF9E), onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6C5D3F), onSecondary = Color.White, secondaryContainer = Color(0xFFF6E1BB), onSecondaryContainer = Color(0xFF251A04),
    tertiary = Color(0xFF496547), onTertiary = Color.White, tertiaryContainer = Color(0xFFCBEBC4), onTertiaryContainer = Color(0xFF062109),
    surface = Color(0xFFFFFBFF), onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFECE1CF), onSurfaceVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF6EE),
    surfaceContainer = Color(0xFFFAF3E8), surfaceContainerHigh = Color(0xFFF4ECE1), surfaceContainerHighest = Color(0xFFEEE6DA),
    outline = Color(0xFF7F7667), outlineVariant = Color(0xFFD0C5B4), isDark = false
)
private val DarkAmberScheme = createScheme(
    primary = Color(0xFFFABD46), onPrimary = Color(0xFF422C00), primaryContainer = Color(0xFF5F4100), onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFD9C5A0), onSecondary = Color(0xFF3B2F15), secondaryContainer = Color(0xFF53452A), onSecondaryContainer = Color(0xFFF6E1BB),
    tertiary = Color(0xFFAFCFA9), onTertiary = Color(0xFF1B361C), tertiaryContainer = Color(0xFF324D31), onTertiaryContainer = Color(0xFFCBEBC4),
    surface = Color(0xFF17130B), onSurface = Color(0xFFE9E1D8),
    surfaceVariant = Color(0xFF4D4639), onSurfaceVariant = Color(0xFFD0C5B4),
    surfaceContainerLowest = Color(0xFF110D07), surfaceContainerLow = Color(0xFF1E1911),
    surfaceContainer = Color(0xFF241F18), surfaceContainerHigh = Color(0xFF2D271F), surfaceContainerHighest = Color(0xFF383229),
    outline = Color(0xFF999080), outlineVariant = Color(0xFF4D4639), isDark = true
)

// 12. Brown
private val LightBrownScheme = createScheme(
    primary = Color(0xFF83543A), onPrimary = Color.White, primaryContainer = Color(0xFFFFDBCE), onPrimaryContainer = Color(0xFF331302),
    secondary = Color(0xFF74594E), onSecondary = Color.White, secondaryContainer = Color(0xFFFFDBD0), onSecondaryContainer = Color(0xFF2B170F),
    tertiary = Color(0xFF655F31), onTertiary = Color.White, tertiaryContainer = Color(0xFFECE3A8), onTertiaryContainer = Color(0xFF201C00),
    surface = Color(0xFFFFFBFF), onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFF2DFD7), onSurfaceVariant = Color(0xFF51443D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF6F3),
    surfaceContainer = Color(0xFFFAF2EF), surfaceContainerHigh = Color(0xFFF4EBE7), surfaceContainerHighest = Color(0xFFEEE5E1),
    outline = Color(0xFF83746D), outlineVariant = Color(0xFFD5C3BB), isDark = false
)
private val DarkBrownScheme = createScheme(
    primary = Color(0xFFF7B99B), onPrimary = Color(0xFF4D2711), primaryContainer = Color(0xFF673D25), onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFFE4BEB1), onSecondary = Color(0xFF422B22), secondaryContainer = Color(0xFF5B4137), onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFFCFC78F), onTertiary = Color(0xFF363107), tertiaryContainer = Color(0xFF4D471C), onTertiaryContainer = Color(0xFFECE3A8),
    surface = Color(0xFF181210), onSurface = Color(0xFFECE0DB),
    surfaceVariant = Color(0xFF51443D), onSurfaceVariant = Color(0xFFD5C3BB),
    surfaceContainerLowest = Color(0xFF120D0B), surfaceContainerLow = Color(0xFF1E1715),
    surfaceContainer = Color(0xFF231D1B), surfaceContainerHigh = Color(0xFF2E2623), surfaceContainerHighest = Color(0xFF39312E),
    outline = Color(0xFF9E8D85), outlineVariant = Color(0xFF51443D), isDark = true
)

// 13. Slate
private val LightSlateScheme = createScheme(
    primary = Color(0xFF3D6176), onPrimary = Color.White, primaryContainer = Color(0xFFC1E7FF), onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF50606B), onSecondary = Color.White, secondaryContainer = Color(0xFFD3E5F2), onSecondaryContainer = Color(0xFF0D1D26),
    tertiary = Color(0xFF615A75), onTertiary = Color.White, tertiaryContainer = Color(0xFFE7DEFF), onTertiaryContainer = Color(0xFF1D1730),
    surface = Color(0xFFF8F9FD), onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDEE3E8), onSurfaceVariant = Color(0xFF41474D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F7FA),
    surfaceContainer = Color(0xFFF1F4F7), surfaceContainerHigh = Color(0xFFEBF0F3), surfaceContainerHighest = Color(0xFFE5EAEE),
    outline = Color(0xFF72787E), outlineVariant = Color(0xFFC1C7CD), isDark = false
)
private val DarkSlateScheme = createScheme(
    primary = Color(0xFFA5CBE2), onPrimary = Color(0xFF063346), primaryContainer = Color(0xFF244A5E), onPrimaryContainer = Color(0xFFC1E7FF),
    secondary = Color(0xFFB7C8D5), onSecondary = Color(0xFF22323C), secondaryContainer = Color(0xFF384953), onSecondaryContainer = Color(0xFFD3E5F2),
    tertiary = Color(0xFFCBC1E4), onTertiary = Color(0xFF332C45), tertiaryContainer = Color(0xFF4A425D), onTertiaryContainer = Color(0xFFE7DEFF),
    surface = Color(0xFF101417), onSurface = Color(0xFFE1E3E6),
    surfaceVariant = Color(0xFF41474D), onSurfaceVariant = Color(0xFFC1C7CD),
    surfaceContainerLowest = Color(0xFF0B0F12), surfaceContainerLow = Color(0xFF15191C),
    surfaceContainer = Color(0xFF1B2024), surfaceContainerHigh = Color(0xFF23282C), surfaceContainerHighest = Color(0xFF2E3338),
    outline = Color(0xFF8B9297), outlineVariant = Color(0xFF41474D), isDark = true
)

internal fun getStaticColorScheme(palette: AppColorPalette, isDark: Boolean): ColorScheme {
    return when (palette) {
        AppColorPalette.BLUE -> if (isDark) DarkBlueScheme else LightBlueScheme
        AppColorPalette.PURPLE -> if (isDark) DarkPurpleScheme else LightPurpleScheme
        AppColorPalette.GREEN -> if (isDark) DarkGreenScheme else LightGreenScheme
        AppColorPalette.ORANGE -> if (isDark) DarkOrangeScheme else LightOrangeScheme
        AppColorPalette.RED -> if (isDark) DarkRedScheme else LightRedScheme
        AppColorPalette.TEAL -> if (isDark) DarkTealScheme else LightTealScheme
        AppColorPalette.CYAN -> if (isDark) DarkCyanScheme else LightCyanScheme
        AppColorPalette.INDIGO -> if (isDark) DarkIndigoScheme else LightIndigoScheme
        AppColorPalette.PINK -> if (isDark) DarkPinkScheme else LightPinkScheme
        AppColorPalette.LIME -> if (isDark) DarkLimeScheme else LightLimeScheme
        AppColorPalette.AMBER -> if (isDark) DarkAmberScheme else LightAmberScheme
        AppColorPalette.BROWN -> if (isDark) DarkBrownScheme else LightBrownScheme
        AppColorPalette.SLATE -> if (isDark) DarkSlateScheme else LightSlateScheme
    }
}

val LocalThemeSettings = compositionLocalOf { ThemeSettings() }

@Composable
fun YouSchoolTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (settings.themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }
    val isAmoled = settings.themeMode == AppThemeMode.AMOLED

    val baseScheme = platformColorScheme(settings, isDark) ?: getStaticColorScheme(settings.colorPalette, isDark)

    val finalScheme = if (isAmoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainer = Color(0xFF0F0F0F),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1C1C1C),
            surfaceVariant = Color(0xFF121212)
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = Typography,
        content = { CompositionLocalProvider(LocalThemeSettings provides settings) { content() } }
    )
}
