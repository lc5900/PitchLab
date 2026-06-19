package com.lc5900.pitchlab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val AppBackground = Color(0xFF080A0D)
private val Panel = Color(0xFF15181E)
private val PanelLight = Color(0xFF1E222B)
private val Border = Color(0xFF2B303A)
private val Purple = Color(0xFF6657F3)
private val Green = Color(0xFF68D957)
private val Yellow = Color(0xFFFFC44F)
private val Red = Color(0xFFFF6666)
private val Muted = Color(0xFF9AA0AC)

@Composable
@Preview
fun App() {
    val dependencies = rememberPitchLabDependencies()
    val scope = rememberCoroutineScope()
    val controller = remember(dependencies) { PitchLabController(dependencies, scope) }
    val state by controller.state.collectAsState()
    val homeExitHandler = rememberHomeExitHandler()
    val strings = pitchStrings(state.language)
    var pendingDiscardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    PlatformBackHandler {
        if (state.screen == PitchScreen.Home) {
            homeExitHandler.requestExit(strings.backAgainToExit)
        } else if (state.hasUnsavedPractice()) {
            pendingDiscardAction = { controller.backHome() }
        } else {
            controller.backHome()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = AppBackground, surface = Panel)) {
        CompositionLocalProvider(LocalPitchStrings provides strings) {
            pendingDiscardAction?.let { action ->
                AlertDialog(
                    onDismissRequest = { pendingDiscardAction = null },
                    title = { Text(strings.discardPracticeTitle) },
                    text = { Text(strings.discardPracticeBody) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingDiscardAction = null
                                action()
                            },
                        ) {
                            Text(strings.discardPracticeConfirm)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDiscardAction = null }) {
                            Text(strings.cancel)
                        }
                    },
                )
            }
            Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
                when (state.screen) {
                    PitchScreen.Home -> HomeScreen(state, controller)
                    PitchScreen.Tuner -> TunerScreen(
                        state = state,
                        controller = controller,
                        requestDiscardingAction = { action ->
                            if (state.hasUnsavedPractice()) pendingDiscardAction = action else action()
                        },
                    )
                    PitchScreen.Session -> SessionScreen(
                        state = state,
                        controller = controller,
                        requestDiscardingAction = { action ->
                            if (state.hasUnsavedPractice()) pendingDiscardAction = action else action()
                        },
                    )
                    PitchScreen.History -> HistoryScreen(state, controller)
                    PitchScreen.Settings -> SettingsScreen(state, controller)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(state: PitchLabUiState, controller: PitchLabController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(showBack = false, onBack = {})
            InfoCard()
            val s = LocalPitchStrings.current
            ModeCard(
                title = s.freeMode,
                subtitle = s.freeSubtitle,
                mode = PracticeMode.Free,
                color = Green,
                onStart = { controller.beginSession(PracticeMode.Free) },
            )
            ModeCard(
                title = s.targetMode,
                subtitle = s.targetSubtitle,
                mode = PracticeMode.Target,
                color = Purple,
                onStart = { controller.beginSession(PracticeMode.Target) },
            )
            TunerEntryCard(controller::openTuner)
            RecentPracticeCard(state.recentSummaries, controller::openHistory)
            SensitivityCard(state.sensitivity, controller::setSensitivity)
            Spacer(Modifier.height(4.dp))
        }
        BottomNav(PitchScreen.Home, controller)
    }
}

@Composable
private fun HistoryScreen(state: PitchLabUiState, controller: PitchLabController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(showBack = true, onBack = controller::backHome)
            val s = LocalPitchStrings.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.practiceHistory, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = controller::clearHistory,
                    enabled = state.recentSummaries.isNotEmpty(),
                ) {
                    Text(s.clearHistory, color = if (state.recentSummaries.isNotEmpty()) Red else Muted, fontSize = 12.sp)
                }
            }
            if (state.recentSummaries.isEmpty()) {
                PitchPanel(contentPadding = 12.dp) {
                    Text(s.noHistory, color = Muted, fontSize = 12.sp)
                }
            } else {
                state.recentSummaries.forEach { item ->
                    key(item.startedAtMillis) {
                        HistoryItem(item)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        BottomNav(PitchScreen.History, controller)
    }
}

@Composable
private fun SettingsScreen(state: PitchLabUiState, controller: PitchLabController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(showBack = true, onBack = controller::backHome)
            val s = LocalPitchStrings.current
            Text(s.settings, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LanguageCard(state.language, controller::setLanguage)
            SensitivityCard(state.sensitivity, controller::setSensitivity)
            ReferencePitchCard(state.referencePitchHz, controller::setReferencePitchHz)
            ChartWindowCard(state.chartWindowSeconds, controller::setChartWindowSeconds)
            Spacer(Modifier.height(4.dp))
        }
        BottomNav(PitchScreen.Settings, controller)
    }
}

@Composable
private fun TunerScreen(
    state: PitchLabUiState,
    controller: PitchLabController,
    requestDiscardingAction: (() -> Unit) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(showBack = true, onBack = { requestDiscardingAction(controller::backHome) })
            InstrumentSegment(state.tunerInstrument, controller::setTunerInstrument)
            AudioInputErrorBanner(state.audioInputError)
            TunerStatusCard(state)
            TunerStringsCard(state, controller)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SessionScreen(
    state: PitchLabUiState,
    controller: PitchLabController,
    requestDiscardingAction: (() -> Unit) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(showBack = true, onBack = { requestDiscardingAction(controller::backHome) })
            SegmentedMode(state.selectedMode) { mode ->
                requestDiscardingAction {
                    controller.stop(saveSummary = false)
                    controller.beginSession(mode)
                }
            }
            AudioInputErrorBanner(state.audioInputError)
            if (state.selectedMode == PracticeMode.Target) {
                TargetSelector(state.target, state.availableTargets, controller::selectTarget)
                TargetStatusCards(state)
            } else {
                FreeStatusCard(state)
            }
            PitchChart(state)
            Legend(state.selectedMode)
            if (state.lastSummary != null || state.selectedMode == PracticeMode.Target) {
                ResultCard(state.lastSummary, state)
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        TransportControls(
            state = state,
            controller = controller,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Header(showBack: Boolean, onBack: () -> Unit) {
    val s = LocalPitchStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Text(
                "<",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = s.back }
                    .clickable(onClick = onBack),
                color = Color.White,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            WaveIcon(Purple)
            Spacer(Modifier.width(8.dp))
        }
        Text("PitchLab", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text(s.labName, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("🎙", fontSize = 18.sp)
    }
}

@Composable
private fun BottomNav(selected: PitchScreen, controller: PitchLabController) {
    val s = LocalPitchStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, Border.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItem(
            icon = "⌂",
            label = s.home,
            selected = selected == PitchScreen.Home,
            modifier = Modifier.weight(1f),
            onClick = controller::backHome,
        )
        BottomNavItem(
            icon = "◷",
            label = s.history,
            selected = selected == PitchScreen.History,
            modifier = Modifier.weight(1f),
            onClick = controller::openHistory,
        )
        BottomNavItem(
            icon = "⚙",
            label = s.settings,
            selected = selected == PitchScreen.Settings,
            modifier = Modifier.weight(1f),
            onClick = controller::openSettings,
        )
    }
}

@Composable
private fun BottomNavItem(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = if (selected) Purple else Muted
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, color = color, fontSize = 18.sp, lineHeight = 18.sp)
        Text(label, color = color, fontSize = 10.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun SegmentedMode(selected: PracticeMode, onSelect: (PracticeMode) -> Unit) {
    val s = LocalPitchStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelLight)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        SegmentButton(s.freeMode, selected == PracticeMode.Free, Modifier.weight(1f)) {
            onSelect(PracticeMode.Free)
        }
        SegmentButton(s.targetMode, selected == PracticeMode.Target, Modifier.weight(1f)) {
            onSelect(PracticeMode.Target)
        }
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Purple else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun InstrumentSegment(selected: TunerInstrument, onSelect: (TunerInstrument) -> Unit) {
    val s = LocalPitchStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelLight)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        SegmentButton(s.guitar, selected == TunerInstrument.Guitar, Modifier.weight(1f)) {
            onSelect(TunerInstrument.Guitar)
        }
        SegmentButton(s.ukulele, selected == TunerInstrument.Ukulele, Modifier.weight(1f)) {
            onSelect(TunerInstrument.Ukulele)
        }
    }
}

@Composable
private fun TunerStatusCard(state: PitchLabUiState) {
    val s = LocalPitchStrings.current
    val current = state.currentSample
    val strings = TuningPresets.strings(state.tunerInstrument, state.referencePitchHz)
    val closest = current?.let { sample ->
        strings.minByOrNull { string -> kotlin.math.abs(PitchMath.centsBetween(sample.frequencyHz, string.target.frequencyHz)) }
    }
    val cents = current?.let { sample -> closest?.let { PitchMath.centsBetween(sample.frequencyHz, it.target.frequencyHz) } }
    val color = cents?.let { PitchClassifier.targetTone(it).color() } ?: Muted
    PitchPanel(contentPadding = 10.dp) {
        Text(
            if (state.isPaused) s.paused else if (state.isRunning) s.recording else s.waiting,
            color = if (state.isRunning && !state.isPaused) Green else Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(s.detected, color = Muted, fontSize = 11.sp)
                Text(current?.let { "${it.note}${it.octave}" } ?: "--", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(current?.let { "${it.frequencyHz.roundToInt()} Hz" } ?: s.waitingPitch, color = Muted, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(s.closestString, color = Muted, fontSize = 11.sp)
                Text(closest?.label ?: "--", color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(cents?.let { tunerDirection(it, s) } ?: s.waiting, color = color, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AudioInputErrorBanner(error: AudioInputError?) {
    if (error == null) return
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 10.dp) {
        Text(
            when (error) {
                AudioInputError.PermissionDenied -> s.microphonePermissionDenied
                AudioInputError.Unavailable -> s.audioInputUnavailable
            },
            color = Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TunerStringsCard(state: PitchLabUiState, controller: PitchLabController) {
    val s = LocalPitchStrings.current
    val current = state.currentSample
    val strings = TuningPresets.strings(state.tunerInstrument, state.referencePitchHz)
    val closest = current?.let { sample ->
        strings.minByOrNull { string -> kotlin.math.abs(PitchMath.centsBetween(sample.frequencyHz, string.target.frequencyHz)) }
    }
    PitchPanel(contentPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.strings} · ${if (state.tunerInstrument == TunerInstrument.Guitar) s.guitar else s.ukulele}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(s.referenceTone, color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            strings.forEach { string ->
                val cents = current?.let { PitchMath.centsBetween(it.frequencyHz, string.target.frequencyHz) }
                key(string.position, string.label) {
                    TunerStringRow(
                        string = string,
                        cents = cents,
                        isClosest = closest == string,
                        onPlay = { controller.playReferenceTone(string.target) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferencePitchCard(referencePitchHz: Int, onReferencePitchChange: (Int) -> Unit) {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 12.dp) {
        Text(s.standardPitch, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(s.referencePitchRange, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepperButton("-", enabled = referencePitchHz > 432) {
                onReferencePitchChange(referencePitchHz - 1)
            }
            Text(
                "A4 = $referencePitchHz Hz",
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            StepperButton("+", enabled = referencePitchHz < 446) {
                onReferencePitchChange(referencePitchHz + 1)
            }
        }
    }
}

@Composable
private fun ChartWindowCard(chartWindowSeconds: Int, onChartWindowChange: (Int) -> Unit) {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 12.dp) {
        Text(s.chartWindow, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(10, 15, 30).forEach { seconds ->
                LanguageButton(
                    text = "${seconds}s",
                    selected = chartWindowSeconds == seconds,
                    modifier = Modifier.weight(1f),
                    onClick = { onChartWindowChange(seconds) },
                )
            }
        }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) PanelLight else PanelLight.copy(alpha = 0.45f))
            .border(1.dp, Border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TunerStringRow(
    string: TunerString,
    cents: Double?,
    isClosest: Boolean,
    onPlay: () -> Unit,
) {
    val s = LocalPitchStrings.current
    val activeColor = if (isClosest && cents != null) PitchClassifier.targetTone(cents).color() else Muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isClosest) activeColor.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f))
            .border(1.dp, if (isClosest) activeColor.copy(alpha = 0.7f) else Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(activeColor.copy(alpha = 0.16f))
                .border(1.dp, activeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(string.position.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(string.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${string.target.frequencyHz.roundToInt()} Hz", color = Muted, fontSize = 10.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (isClosest) cents?.let { signedCents(it) } ?: "--" else "--", color = activeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(if (isClosest) cents?.let { tunerDirection(it, s) } ?: s.waiting else s.playTone, color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onPlay,
            modifier = Modifier.height(48.dp),
        ) {
            Text(s.playTone, color = Purple, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InfoCard() {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 12.dp) {
        Text(s.twoModesTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(s.freeModeDescription, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
        Text(s.targetModeDescription, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, mode: PracticeMode, color: Color, onStart: () -> Unit) {
    PitchPanel(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f))
                    .border(1.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (mode == PracticeMode.Free) WaveIcon(color) else TargetIcon(color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            Text(">", color = Color.White, fontSize = 22.sp)
        }
        Spacer(Modifier.height(8.dp))
        MiniChart(mode)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(LocalPitchStrings.current.startTest, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TunerEntryCard(onStart: () -> Unit) {
    val s = LocalPitchStrings.current
    PitchPanel(
        modifier = Modifier.clickable(onClick = onStart),
        contentPadding = 10.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Yellow.copy(alpha = 0.16f))
                    .border(1.dp, Yellow, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("♪", color = Yellow, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.tuner, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${s.guitar} / ${s.ukulele} · ${s.referenceTone}", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
            Text(">", color = Color.White, fontSize = 22.sp)
        }
    }
}

@Composable
private fun FreeStatusCard(state: PitchLabUiState) {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 10.dp) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(s.currentPitch, color = Muted, fontSize = 11.sp)
                val sample = state.currentSample
                Text(sample?.let { "${it.note}${it.octave}" } ?: "--", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(sample?.let { "${it.frequencyHz.roundToInt()} Hz" } ?: s.waitingPitch, color = Muted, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(s.stability, color = Muted, fontSize = 11.sp)
                Text("${state.stabilityPercent}%", color = state.stabilityPercent.stabilityColor(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if (state.stabilityPercent >= 70) s.stable else s.needsControl, color = state.stabilityPercent.stabilityColor(), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TargetSelector(selected: TargetNote, targets: List<TargetNote>, onSelect: (TargetNote) -> Unit) {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.targetNote}: ${selected.label} / ${selected.frequencyHz.roundToInt()} Hz", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${s.referencePitch}: 440 Hz", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        val center = targets.indexOfFirst { it.label == selected.label }.coerceAtLeast(0)
        val visible = (center - 2..center + 2).mapNotNull { targets.getOrNull(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            visible.forEach { target ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (target == selected) Purple else PanelLight)
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                        .clickable { onSelect(target) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(target.label, color = Color.White, fontSize = 12.sp, fontWeight = if (target == selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun TargetStatusCards(state: PitchLabUiState) {
    val s = LocalPitchStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatusTile(s.pitchDeviation, state.currentSample?.centsFromTarget?.let { signedCents(it) } ?: "--", state.currentSample?.tone?.color() ?: Muted, Modifier.weight(1f))
        StatusTile(s.status, state.currentSample?.centsFromTarget?.let { if (it > 0) s.sharp else s.flat } ?: s.waiting, state.currentSample?.tone?.color() ?: Muted, Modifier.weight(1f))
    }
}

@Composable
private fun StatusTile(title: String, value: String, color: Color, modifier: Modifier) {
    PitchPanel(modifier = modifier, contentPadding = 10.dp) {
        Text(title, color = Muted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PitchChart(state: PitchLabUiState) {
    val s = LocalPitchStrings.current
    val chartData = remember(state.samples, state.selectedMode, state.target, state.referencePitchHz) {
        pitchChartData(state)
    }
    Column {
        Text(s.pitchSemitone, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(210.dp).semantics { contentDescription = s.pitchChartDescription }) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.width(30.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    dynamicYAxisLabels(chartData.minMidi, chartData.maxMidi).forEach {
                        Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                    }
                }
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    val grid = Color.White.copy(alpha = 0.08f)
                    repeat(6) { index ->
                        val x = size.width * index / 5f
                        drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                    repeat(9) { index ->
                        val y = size.height * index / 8f
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    if (chartData.targetPitch != null) {
                        val y = midiToY(chartData.targetPitch, chartData.minMidi, chartData.maxMidi, size.height)
                        drawLine(Color.White.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.3f)
                    }
                    chartData.visibleSamples.zipWithNext().forEach { (from, to) ->
                        val x1 = ((from.elapsedSeconds - chartData.windowStart) / state.chartWindowSeconds * size.width).toFloat()
                        val x2 = ((to.elapsedSeconds - chartData.windowStart) / state.chartWindowSeconds * size.width).toFloat()
                        if (x2 >= x1 && from.segmentIndex == to.segmentIndex) {
                            drawLine(
                                color = to.tone.color(),
                                start = Offset(x1, midiToY(from.midi + from.centsFromNearest / 100.0, chartData.minMidi, chartData.maxMidi, size.height)),
                                end = Offset(x2, midiToY(to.midi + to.centsFromNearest / 100.0, chartData.minMidi, chartData.maxMidi, size.height)),
                                strokeWidth = 3.2f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..5).forEach { index ->
                val second = chartData.windowStart + index * state.chartWindowSeconds / 5.0
                Text(second.roundToInt().toString(), color = Muted, fontSize = 10.sp)
            }
        }
        Text(s.timeSeconds, modifier = Modifier.fillMaxWidth(), color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Legend(mode: PracticeMode) {
    val s = LocalPitchStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (mode == PracticeMode.Target) {
            LegendItem(Green, s.targetGreenLegend)
            LegendItem(Yellow, s.targetYellowLegend)
            LegendItem(Red, s.targetRedLegend)
        } else {
            LegendItem(Green, s.freeGreenLegend)
            LegendItem(Yellow, s.freeYellowLegend)
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp)
    }
}

@Composable
private fun ResultCard(summary: PracticeSummary?, state: PitchLabUiState) {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 10.dp) {
        Text(s.resultTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            ResultMetric(s.averageDeviation, summary?.averageCents?.let { signedCents(it) } ?: state.currentSample?.centsFromTarget?.let { signedCents(it) } ?: "--", Red)
            ResultMetric(s.stableDuration, summary?.durationSeconds?.formatDuration() ?: state.elapsedSeconds.formatDuration(), Color.White)
            ResultMetric(s.passDecision, summary?.let { if (it.passed) s.passed else s.failed } ?: "--", if (summary?.passed == true) Green else Red)
        }
    }
}

@Composable
private fun ResultMetric(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Muted, fontSize = 10.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TransportControls(state: PitchLabUiState, controller: PitchLabController, modifier: Modifier = Modifier) {
    val s = LocalPitchStrings.current
    PitchPanel(modifier = modifier, contentPadding = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Purple)
                    .border(3.dp, Purple.copy(alpha = 0.24f), CircleShape)
                    .clickable { controller.startOrResume() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (!state.isRunning || state.isPaused) "▶" else "Ⅱ", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            CircleControl(s.stop, "■") { controller.stop() }
        }
        Spacer(Modifier.height(5.dp))
        Text(state.elapsedSeconds.formatDuration(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun CircleControl(label: String, glyph: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(PanelLight)
                .border(1.dp, Border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = Color.White, fontSize = 14.sp)
        }
        Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp)
    }
}

@Composable
private fun RecentPracticeCard(items: List<PracticeSummary>, onViewAll: () -> Unit) {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.recentPractice, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                s.viewAll,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                color = Purple,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(s.noHistory, color = Muted, fontSize = 11.sp)
        } else {
            items.take(3).forEach { item ->
                key(item.startedAtMillis) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WaveIcon(if (item.passed) Green else Yellow)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(practiceTitle(item, s), color = Color.White, fontSize = 12.sp)
                            Text("${item.durationSeconds.formatDuration()} · ${s.stability} ${item.stabilityPercent}%", color = Muted, fontSize = 10.sp)
                        }
                        Text("${item.stabilityPercent}%", color = item.stabilityPercent.stabilityColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(item: PracticeSummary) {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaveIcon(if (item.passed) Green else Yellow)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    practiceTitle(item, s),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${item.durationSeconds.formatDuration()} · ${s.stability} ${item.stabilityPercent}%",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (item.passed) s.passed else s.failed, color = if (item.passed) Green else Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(item.averageCents?.let { signedCents(it) } ?: "--", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SensitivityCard(sensitivity: Float, onSensitivityChange: (Float) -> Unit) {
    val s = LocalPitchStrings.current
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(s.sensitivityInfoTitle) },
            text = {
                Text(
                    s.sensitivityInfoBody,
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(s.ok)
                }
            },
        )
    }
    PitchPanel(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.sensitivitySettings, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.dp, Muted, CircleShape)
                    .clickable { showInfo = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("i", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.low, color = Color.White, fontSize = 11.sp)
            Slider(
                value = sensitivity,
                onValueChange = onSensitivityChange,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Purple,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
            Text(s.high, color = Color.White, fontSize = 11.sp)
        }
        Text(
            "${s.current}: ${sensitivityLabel(sensitivity, s)}",
            color = Muted,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LanguageCard(language: AppLanguage, onLanguageChange: (AppLanguage) -> Unit) {
    val s = LocalPitchStrings.current
    PitchPanel(contentPadding = 12.dp) {
        Text(s.language, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LanguageButton(
                text = s.simplifiedChinese,
                selected = language == AppLanguage.ZhHans,
                modifier = Modifier.weight(1f),
                onClick = { onLanguageChange(AppLanguage.ZhHans) },
            )
            LanguageButton(
                text = s.english,
                selected = language == AppLanguage.En,
                modifier = Modifier.weight(1f),
                onClick = { onLanguageChange(AppLanguage.En) },
            )
        }
    }
}

@Composable
private fun LanguageButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Purple else PanelLight)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PitchPanel(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(PanelLight, Panel)))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(contentPadding),
        content = content,
    )
}

@Composable
private fun MiniChart(mode: PracticeMode) {
    val s = LocalPitchStrings.current
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = s.miniChartDescription }
            .background(Color.Black.copy(alpha = 0.16f)),
    ) {
        val points = List(42) { index ->
            val drift = if (mode == PracticeMode.Target && index in 0..12 || mode == PracticeMode.Target && index > 31) 24f else 0f
            val wave = ((index * 37) % 17 - 8) * 1.7f + drift
            Offset(index * size.width / 41f, size.height / 2f + wave)
        }
        points.zipWithNext().forEachIndexed { index, (from, to) ->
            val color = if (mode == PracticeMode.Target && (index < 13 || index > 31)) Red else if (index in 24..30) Yellow else Green
            drawLine(color, from, to, strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun WaveIcon(color: Color) {
    Canvas(Modifier.size(34.dp).semantics { contentDescription = "Pitch wave" }) {
        val mid = size.height / 2f
        val step = size.width / 6f
        drawLine(color, Offset(0f, mid), Offset(step, mid - 5f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color, Offset(step, mid - 5f), Offset(step * 2, mid + 8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color, Offset(step * 2, mid + 8f), Offset(step * 3, mid - 13f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color, Offset(step * 3, mid - 13f), Offset(step * 4, mid + 10f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color, Offset(step * 4, mid + 10f), Offset(step * 5, mid - 4f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(color, Offset(step * 5, mid - 4f), Offset(size.width, mid), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

@Composable
private fun TargetIcon(color: Color) {
    Canvas(Modifier.size(34.dp).semantics { contentDescription = "Pitch target" }) {
        drawCircle(color.copy(alpha = 0.35f), radius = size.minDimension / 2f, style = Stroke(width = 3f))
        drawCircle(color, radius = size.minDimension / 5f)
        drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 2f)
        drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f)
    }
}

private fun dynamicYAxisLabels(minMidi: Double, maxMidi: Double): List<String> {
    val steps = 10
    return (0..steps).map { index ->
        val midi = maxMidi - (maxMidi - minMidi) * index / steps
        midiLabel(midi.roundToInt())
    }
}

private data class PitchChartData(
    val targetPitch: Double?,
    val minMidi: Double,
    val maxMidi: Double,
    val windowStart: Double,
    val visibleSamples: List<PitchSample>,
)

private fun pitchChartData(state: PitchLabUiState): PitchChartData {
    val targetPitch = if (state.selectedMode == PracticeMode.Target) {
        PitchMath.analyze(state.target.frequencyHz, state.referencePitchHz).midi.toDouble()
    } else {
        null
    }
    val plottedPitches = state.samples.map { it.midi + it.centsFromNearest / 100.0 }
    val centerPitch = plottedPitches.lastOrNull() ?: targetPitch ?: PitchMath.analyze(state.referencePitchHz.toDouble(), state.referencePitchHz).midi.toDouble()
    val dynamicPitches = plottedPitches.takeLast(120) + listOfNotNull(targetPitch, centerPitch)
    val rawMin = dynamicPitches.minOrNull() ?: centerPitch
    val rawMax = dynamicPitches.maxOrNull() ?: centerPitch
    val paddedMin = rawMin - 3.0
    val paddedMax = rawMax + 3.0
    val minSpan = 14.0
    val rangeCenter = (paddedMin + paddedMax) / 2.0
    val minMidi = if (paddedMax - paddedMin < minSpan) rangeCenter - minSpan / 2.0 else paddedMin
    val maxMidi = if (paddedMax - paddedMin < minSpan) rangeCenter + minSpan / 2.0 else paddedMax
    val latestSecond = state.samples.lastOrNull()?.elapsedSeconds ?: 0.0
    val windowStart = (latestSecond - state.chartWindowSeconds).coerceAtLeast(0.0)
    val windowEnd = windowStart + state.chartWindowSeconds
    return PitchChartData(
        targetPitch = targetPitch,
        minMidi = minMidi,
        maxMidi = maxMidi,
        windowStart = windowStart,
        visibleSamples = state.samples.filter { it.elapsedSeconds in windowStart..windowEnd },
    )
}

private fun midiLabel(midi: Int): String {
    val analysis = PitchMath.analyze(PitchMath.midiToFrequency(midi))
    return "${analysis.note}${analysis.octave}"
}

private fun midiToY(midi: Double, minMidi: Double, maxMidi: Double, height: Float): Float {
    val ratio = ((midi - minMidi) / (maxMidi - minMidi)).coerceIn(0.0, 1.0)
    return (height * (1.0 - ratio)).toFloat()
}

private fun PitchTone.color(): Color = when (this) {
    PitchTone.Green -> Green
    PitchTone.Yellow -> Yellow
    PitchTone.Red -> Red
}

private fun Int.stabilityColor(): Color = if (this >= 70) Green else Yellow

private fun signedCents(value: Double): String {
    val rounded = value.roundToInt()
    return if (rounded > 0) "+$rounded cents" else "$rounded cents"
}

private fun tunerDirection(cents: Double, strings: PitchStrings): String = when {
    cents > 10.0 -> strings.tuneDown
    cents < -10.0 -> strings.tuneUp
    else -> strings.inTune
}

private fun practiceTitle(item: PracticeSummary, strings: PitchStrings): String =
    if (item.mode == PracticeMode.Free) {
        strings.freePractice
    } else {
        "${strings.targetPracticePrefix} ${item.targetLabel}"
    }

private fun PitchLabUiState.hasUnsavedPractice(): Boolean =
    screen in setOf(PitchScreen.Session, PitchScreen.Tuner) && isRunning && samples.isNotEmpty()

private fun sensitivityLabel(value: Float, strings: PitchStrings): String = when {
    value < 0.33f -> strings.sensitivityLow
    value < 0.66f -> strings.sensitivityDefault
    else -> strings.sensitivityHigh
}

private fun Int.formatDuration(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "00:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
