package org.youschool.app.ui.screens.profile

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.youschool.app.core.update.AppRelease
import org.youschool.app.core.update.AppUpdateChecker
import org.youschool.app.ui.navigation.Screen
import org.youschool.app.ui.components.BottomBarItemsEditor
import org.youschool.app.ui.navigation.BottomBarConfig
import org.youschool.app.domain.repository.SchoolRepository
import org.youschool.app.ui.screens.schedule.GradeBadge
import org.youschool.app.ui.theme.*
import org.youschool.app.ui.util.rememberHaptic

private enum class ProfileSettingsSection(val title: String, val icon: ImageVector) {
    APPEARANCE("Оформление", Icons.Rounded.Palette),
    NAVIGATION("Навигация и отклик", Icons.Rounded.Tune),
    NOTIFICATIONS("Уведомления", Icons.Rounded.NotificationsActive),
    GRADES("Оценки", Icons.Rounded.Grade),
    ABOUT("О приложении", Icons.Rounded.Info)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    repository: SchoolRepository,
    themeManager: ThemeManager,
    onLogout: () -> Unit,
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile = repository.getUserProfile()
    val themeSettings by themeManager.themeSettings.collectAsState()
    var selectedSection by rememberSaveable { mutableStateOf<ProfileSettingsSection?>(null) }
    val pageState = rememberSaveableStateHolder()
    BackHandler(enabled = selectedSection != null) { selectedSection = null }
    val haptic = rememberHaptic()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            haptic.confirm()
            themeManager.setNotificationsEnabled(true)
        } else {
            haptic.tick()
            themeManager.setNotificationsEnabled(false)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateFoundDialog by remember { mutableStateOf<AppRelease?>(null) }
    val currentAppVersion = remember { AppUpdateChecker.getAppVersion(context) }

    if (updateFoundDialog != null) {
        val rel = updateFoundDialog!!
        AlertDialog(
            onDismissRequest = { updateFoundDialog = null },
            icon = {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text(text = "Доступно обновление ${rel.versionName}")
            },
            text = {
                Text(
                    text = if (rel.releaseNotes.isNotBlank()) {
                        "На GitHub опубликован новый релиз YouSchool!\n\n${rel.releaseNotes.take(300)}"
                    } else {
                        "На GitHub доступна новая версия YouSchool. Хотите перейти к скачиванию?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetUrl = rel.apkDownloadUrl ?: rel.releaseUrl
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(rel.releaseUrl))
                                context.startActivity(fallback)
                            } catch (_: Exception) {}
                        }
                        updateFoundDialog = null
                    }
                ) {
                    Text("Скачать")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateFoundDialog = null }) {
                    Text("Позже")
                }
            }
        )
    }

    // State for color picker dialog
    var colorPickerTarget by remember { mutableStateOf<String?>(null) } // "5", "4", "3", "2", "1", "start", "end"

    if (colorPickerTarget != null) {
        val target = colorPickerTarget!!
        val title = when (target) {
            "5" -> "Цвет для оценки 5"
            "4" -> "Цвет для оценки 4"
            "3" -> "Цвет для оценки 3"
            "2" -> "Цвет для оценки 2"
            "1" -> "Цвет для оценки 1"
            "start" -> "Начальный цвет градиента (вес 0.2)"
            "end" -> "Конечный цвет градиента (вес 3.0)"
            else -> "Выбор цвета"
        }
        val initialColor = when (target) {
            "5" -> themeSettings.gradeCustomColors.color5
            "4" -> themeSettings.gradeCustomColors.color4
            "3" -> themeSettings.gradeCustomColors.color3
            "2" -> themeSettings.gradeCustomColors.color2
            "1" -> themeSettings.gradeCustomColors.color1
            "start" -> themeSettings.weightGradientSettings.startColor
            "end" -> themeSettings.weightGradientSettings.endColor
            else -> MaterialTheme.colorScheme.primary
        }

        ColorPickerDialog(
            title = title,
            initialColor = initialColor,
            onColorSelected = { selected ->
                haptic.tick()
                when (target) {
                    "start" -> themeManager.setWeightGradientColors(selected, themeSettings.weightGradientSettings.endColor)
                    "end" -> themeManager.setWeightGradientColors(themeSettings.weightGradientSettings.startColor, selected)
                    else -> themeManager.setGradeCustomColor(target, selected)
                }
            },
            onResetDefault = {
                haptic.tick()
                when (target) {
                    "5" -> themeManager.setGradeCustomColor("5", Grade5Color)
                    "4" -> themeManager.setGradeCustomColor("4", Grade4Color)
                    "3" -> themeManager.setGradeCustomColor("3", Grade3Color)
                    "2" -> themeManager.setGradeCustomColor("2", Grade2Color)
                    "1" -> themeManager.setGradeCustomColor("1", Grade1Color)
                    "start" -> themeManager.setWeightGradientColors(Color(0xFF00ACC1), themeSettings.weightGradientSettings.endColor)
                    "end" -> themeManager.setWeightGradientColors(themeSettings.weightGradientSettings.startColor, Color(0xFF6A1B9A))
                }
            },
            onDismiss = { colorPickerTarget = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selectedSection != null) {
                        IconButton(onClick = { selectedSection = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                title = {
                    Text(
                        text = selectedSection?.title ?: "Профиль",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        pageState.SaveableStateProvider(selectedSection?.name ?: "profile") {
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedSection == null) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = profile?.fullName?.ifBlank { "Ученик" } ?: "Пользователь",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = profile?.schoolName ?: "eSchool Center",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

                ProfileSettingsSection.entries.forEach { section ->
                    val summary = when (section) {
                        ProfileSettingsSection.APPEARANCE -> "${themeSettings.themeMode.title} · ${if (themeSettings.isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Адаптивная" else themeSettings.colorPalette.title}"
                        ProfileSettingsSection.NAVIGATION -> "Пунктов: ${themeSettings.bottomBarRoutes.size} · ${themeSettings.navBarStyle.title}"
                        ProfileSettingsSection.NOTIFICATIONS -> if (!themeSettings.areNotificationsEnabled) "Выключены" else
                            listOfNotNull(
                                "Новые оценки".takeIf { themeSettings.gradeNotificationsEnabled },
                                "Напоминания о ДЗ".takeIf { themeSettings.homeworkNotificationsEnabled }
                            ).joinToString(", ").ifEmpty { "Все типы выключены" }
                        ProfileSettingsSection.GRADES -> "Цвета, оформление и вес оценок"
                        ProfileSettingsSection.ABOUT -> "Версия $currentAppVersion, обновления"
                    }
                    ListItem(
                        headlineContent = { Text(section.title) },
                        supportingContent = { Text(summary) },
                        leadingContent = { Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            haptic.tick()
                            selectedSection = section
                        }
                    )
                }
                val hiddenScreens = Screen.allMainScreens.filter { it.route !in themeSettings.bottomBarRoutes }
                if (hiddenScreens.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Text("Разделы приложения", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp))
                    hiddenScreens.forEach { screen ->
                        ListItem(
                            headlineContent = { Text(screen.title) },
                            leadingContent = { screen.icon?.let { Icon(it, contentDescription = null) } },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onNavigate(screen.route) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (selectedSection == ProfileSettingsSection.APPEARANCE) {
            // 1. Оформление и тема
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Оформление и тема", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(text = "Режим отображения", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))

                    val modeScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(modeScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.values().forEach { mode ->
                            val selected = themeSettings.themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptic.tick()
                                    themeManager.setThemeMode(mode)
                                },
                                label = {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    haptic.click()
                                    themeManager.setDynamicColorEnabled(!themeSettings.isDynamicColor)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Адаптивная тема (Material You)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Использовать цвета обоев устройства",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = themeSettings.isDynamicColor,
                                onCheckedChange = { enabled ->
                                    haptic.click()
                                    themeManager.setDynamicColorEnabled(enabled)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(text = "Цветовая палитра", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppColorPalette.values().forEach { palette ->
                            val isSelected = !themeSettings.isDynamicColor && themeSettings.colorPalette == palette
                            val borderColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                label = "border_color"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        haptic.tick()
                                        themeManager.setColorPalette(palette)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .border(2.dp, borderColor, CircleShape)
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(palette.primaryColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = palette.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            }

            if (selectedSection == ProfileSettingsSection.NAVIGATION) {
            // 2. Навигация и отклик
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Навигация и отклик", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Haptic Feedback Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Виброотклик", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Тактильный отклик при переключении вкладок, расчете баллов и отправке заданий",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = themeSettings.isHapticFeedbackEnabled,
                            onCheckedChange = {
                                themeManager.setHapticFeedbackEnabled(it)
                                if (it) haptic.click()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    BottomBarItemsEditor(
                        themeManager = themeManager,
                        settings = themeSettings,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { themeManager.setBottomBarRoutes(BottomBarConfig.defaults) }) { Text("Сбросить панель") }
                    Spacer(Modifier.height(16.dp))

                    // Navigation Bar Style
                    Text(text = "Стиль нижней панели навигации", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NavigationBarStyle.values().forEach { style ->
                            val isSelected = themeSettings.navBarStyle == style
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.tick()
                                    themeManager.setNavigationBarStyle(style)
                                },
                                label = { Text(style.title) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (themeSettings.navBarStyle == NavigationBarStyle.FLOATING_PILL) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Вибрация при перетаскивании",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Тактильный отклик при прохождении каждого пункта панели",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = themeSettings.isNavBarDragHapticEnabled,
                                enabled = themeSettings.isHapticFeedbackEnabled,
                                onCheckedChange = {
                                    themeManager.setNavBarDragHapticEnabled(it)
                                    if (it) haptic.click()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Отступ панели снизу",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Предотвращает случайное срабатывание системных жестов переключения окон",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Text(
                                    text = "${themeSettings.navBarBottomOffsetDp} dp",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = themeSettings.navBarBottomOffsetDp.toFloat(),
                            onValueChange = {
                                themeManager.setNavBarBottomOffset(it.toInt())
                            },
                            valueRange = 4f..48f,
                            steps = 10,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(8 to "8 dp", 16 to "16 dp", 28 to "28 dp", 40 to "40 dp").forEach { (dpVal, label) ->
                                TextButton(
                                    onClick = {
                                        haptic.tick()
                                        themeManager.setNavBarBottomOffset(dpVal)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (themeSettings.navBarBottomOffsetDp == dpVal) FontWeight.Bold else FontWeight.Normal,
                                        color = if (themeSettings.navBarBottomOffsetDp == dpVal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            }

            if (selectedSection == ProfileSettingsSection.NOTIFICATIONS) {
            // 3. Уведомления
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Уведомления", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Разрешить уведомления",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Выберите типы уведомлений ниже. Android может задерживать фоновую проверку.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            modifier = Modifier.semantics { contentDescription = "Разрешить уведомления" },
                            checked = themeSettings.areNotificationsEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            haptic.click()
                                            themeManager.setNotificationsEnabled(true)
                                        } else {
                                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        haptic.click()
                                        themeManager.setNotificationsEnabled(true)
                                    }
                                } else {
                                    haptic.click()
                                    themeManager.setNotificationsEnabled(false)
                                }
                            }
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Новые оценки", Modifier.weight(1f))
                        Switch(modifier = Modifier.semantics { contentDescription = "Новые оценки" },
                            checked = themeSettings.gradeNotificationsEnabled,
                            enabled = themeSettings.areNotificationsEnabled,
                            onCheckedChange = { themeManager.setGradeNotificationsEnabled(it) })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Напоминания о ДЗ")
                            Text("Один раз в день сдачи, без звука и вибрации", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(modifier = Modifier.semantics { contentDescription = "Напоминания о ДЗ" },
                            checked = themeSettings.homeworkNotificationsEnabled,
                            enabled = themeSettings.areNotificationsEnabled,
                            onCheckedChange = { themeManager.setHomeworkNotificationsEnabled(it) })
                    }
                    val minute = themeSettings.homeworkReminderMinute
                    TextButton(enabled = themeSettings.areNotificationsEnabled && themeSettings.homeworkNotificationsEnabled, onClick = {
                        android.app.TimePickerDialog(context, { _, hour, min -> themeManager.setHomeworkReminderMinute(hour * 60 + min) },
                            minute / 60, minute % 60, true).show()
                    }) { Text("Напоминать около %02d:%02d".format(minute / 60, minute % 60)) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            }

            if (selectedSection == ProfileSettingsSection.GRADES) {
            // 4. Окрашивание оценок
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Grade, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Окрашивание оценок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live preview row
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Превью в текущем режиме:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GradeBadge(mark = "5", weight = 1.0)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("5 • в.1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GradeBadge(mark = "4", weight = 1.5)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("4 • в.1.5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GradeBadge(mark = "5", weight = 3.0)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("5 • в.3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GradeBadge(mark = "3", weight = 0.5)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("3 • в.0.5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GradeBadge(mark = "2", weight = 1.0)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("2 • в.1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mode selection chips
                    GradeColorMode.values().forEach { mode ->
                        val isSelected = themeSettings.gradeColorMode == mode
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.tick()
                                    themeManager.setGradeColorMode(mode)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.tick()
                                        themeManager.setGradeColorMode(mode)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = mode.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(text = mode.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode-specific customizable settings
                    when (themeSettings.gradeColorMode) {
                        GradeColorMode.BY_VALUE -> {
                            Text(
                                text = "Нажмите на оценку, чтобы изменить её цвет:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("5", "4", "3", "2", "1").forEach { gradeNum ->
                                    val col = themeSettings.gradeCustomColors.getColorForMark(gradeNum)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable {
                                                haptic.tick()
                                                colorPickerTarget = gradeNum
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = col,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(text = gradeNum, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    haptic.tick()
                                    themeManager.resetGradeCustomColors()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Сбросить цвета на дефолт")
                            }
                        }
                        GradeColorMode.BY_VALUE_AND_WEIGHT -> {
                            Text(
                                text = "Цвета оценок (базовые):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                listOf("5", "4", "3", "2", "1").forEach { gradeNum ->
                                    val col = themeSettings.gradeCustomColors.getColorForMark(gradeNum)
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = col,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable {
                                                haptic.tick()
                                                colorPickerTarget = gradeNum
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(text = gradeNum, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Яркость и насыщенность плавно нарастают от веса 0.2 (светлее) до 3.0 (максимально глубокий цвет)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        GradeColorMode.BY_WEIGHT_GRADIENT -> {
                            Text(
                                text = "Цвета градиента веса (от мин 0.2 к макс 3.0):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Start Color Button
                                OutlinedButton(
                                    onClick = {
                                        haptic.tick()
                                        colorPickerTarget = "start"
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(themeSettings.weightGradientSettings.startColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Мин (0.2)")
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // End Color Button
                                OutlinedButton(
                                    onClick = {
                                        haptic.tick()
                                        colorPickerTarget = "end"
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(themeSettings.weightGradientSettings.endColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Макс (3.0)")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Visual Gradient Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                themeSettings.weightGradientSettings.startColor,
                                                themeSettings.weightGradientSettings.endColor
                                            )
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("0.2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("0.5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("2.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("3.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Вес оценок
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Numbers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Вес оценок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live preview row
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Превью в расписании и дневнике:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GradeBadge(mark = "5", weight = 1.0)
                                GradeBadge(mark = "5", weight = 1.5)
                                GradeBadge(mark = "4", weight = 2.0)
                                GradeBadge(mark = "5", weight = 3.0)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Switch showGradeWeights
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Показывать вес у оценок",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Отображать вес рядом с баллом (например: 5² или 5₂)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = themeSettings.showGradeWeights,
                            onCheckedChange = { show ->
                                haptic.click()
                                themeManager.setShowGradeWeights(show)
                            }
                        )
                    }

                    if (themeSettings.showGradeWeights) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Положение индекса веса",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GradeWeightPosition.values().forEach { pos ->
                                val isSelected = themeSettings.gradeWeightPosition == pos
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.tick()
                                        themeManager.setGradeWeightPosition(pos)
                                    },
                                    label = { Text("${pos.title} (${pos.example})") },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            }

            if (selectedSection == ProfileSettingsSection.ABOUT) {
            // 6. Обновления приложения
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Обновления приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Проверять обновления при запуске",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Автоматически проверять наличие свежих версий YouSchool на GitHub",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = themeSettings.checkForUpdates,
                            onCheckedChange = { enable ->
                                haptic.click()
                                themeManager.setCheckForUpdates(enable)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Текущая версия",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "v$currentAppVersion",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (isCheckingUpdates) return@OutlinedButton
                                haptic.click()
                                isCheckingUpdates = true
                                coroutineScope.launch {
                                    val rel = AppUpdateChecker.checkForUpdate(context)
                                    isCheckingUpdates = false
                                    if (rel != null) {
                                        updateFoundDialog = rel
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "У вас установлена последняя версия (v$currentAppVersion)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Проверка...")
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Проверить сейчас")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Повторить онбординг
            OutlinedButton(
                onClick = {
                    haptic.confirm()
                    themeManager.setOnboardingCompleted(false)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Пройти настройку заново (Онбординг)",
                    maxLines = 2,
                    softWrap = true,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            }

            if (selectedSection == null) {
            // 5. Выход из аккаунта
            OutlinedButton(
                onClick = {
                    haptic.click()
                    repository.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Выйти из аккаунта", fontWeight = FontWeight.SemiBold)
            }

            }

            Spacer(modifier = Modifier.height(28.dp + LocalBottomBarPadding.current).navigationBarsPadding())
        }
        }
    }
}

@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onResetDefault: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    text = "Выберите оттенок из палитры:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                val rows = CuratedPaletteColors.chunked(4)
                rows.forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        rowColors.forEach { col ->
                            val isSelected = col == initialColor
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onColorSelected(col)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            if (onResetDefault != null) {
                TextButton(
                    onClick = {
                        onResetDefault()
                        onDismiss()
                    }
                ) {
                    Text("Сбросить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
