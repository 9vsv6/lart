package app.lart.mobile.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.PlayerPrefs
import app.lart.mobile.data.RankedStream
import app.lart.mobile.data.Ranking
import app.lart.mobile.data.Video
import app.lart.mobile.ui.LartColors
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

interface PlayerActions {
    fun back()
    fun togglePlay()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun setSpeed(speed: Float)
    fun setFit(fit: Fit)
    fun selectAudio(key: String)
    fun selectSubtitle(choice: SubChoice)
    fun setSubDelay(delayMs: Long)
    /** Positive plays the sound later than the picture. */
    fun setAudioDelay(delayMs: Long)
    /** mpv only: null turns Anime4K off. */
    fun setAnime4K(mode: String?, hq: Boolean)
    fun castTogglePlay()
    fun castSeekBy(deltaMs: Long)
    fun stopCasting()
    fun updatePrefs(change: (PlayerPrefs) -> PlayerPrefs)
    fun skipSegment()
    fun playNext()
    fun playEpisode(video: Video)
    fun loadSources()
    fun playSource(source: RankedStream)
    fun enterPip()
    /** Minutes until pause; 0 pauses at the end of this episode; null turns it off. */
    fun setSleep(minutes: Int?)
    fun keepWatching()
    fun startOver()
    /** Adds `delta` (-1..1) to the screen brightness and returns the new level. */
    fun changeBrightness(delta: Float): Float
    fun changeVolume(delta: Float): Float
    fun retry()
}

private val Scrim = Color(0xB3000000)

/** How far subtitles move up, as a fraction of the height, while the controls show. */
const val CONTROLS_LIFT = 0.16f
private val PanelColor = Color(0xF21A1C20)

@Composable
fun PlayerUi(state: PlayerState, prefs: PlayerPrefs, actions: PlayerActions, castButton: (@Composable () -> Unit)? = null) {
    // Controls fade out after a few seconds of playback without touches.
    var touchedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.controlsVisible, state.playing, state.panel, touchedAt) {
        if (state.controlsVisible && state.playing && state.panel == null) {
            delay(4_000)
            state.controlsVisible = false
        }
    }
    LaunchedEffect(state.hint) {
        if (state.hint != null) {
            delay(900)
            state.hint = null
        }
    }
    if (state.inPip) {
        SubtitleLayer(state, prefs)
        return
    }
    state.castingTo?.let { device ->
        CastingScreen(device, state, actions)
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state.locked) {
                detectTapGestures(
                    onTap = {
                        touchedAt = System.currentTimeMillis()
                        if (state.panel != null) state.panel = null
                        else state.controlsVisible = !state.controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (state.locked) return@detectTapGestures
                        val third = size.width / 3f
                        when {
                            offset.x < third -> {
                                actions.seekBy(-10_000)
                                state.hint = "-10s"
                            }
                            offset.x > third * 2 -> {
                                actions.seekBy(10_000)
                                state.hint = "+10s"
                            }
                            else -> actions.togglePlay()
                        }
                    },
                )
            }
            .pointerInput(state.locked) {
                if (state.locked) return@pointerInput
                // Harbor's phone gestures: left edge brightness, right edge volume, sideways to seek.
                var axis = 0 // 0 undecided, 1 horizontal, 2 vertical
                var left = false
                var seekFrom = 0L
                var seekDelta = 0L
                detectDragGestures(
                    onDragStart = { start ->
                        axis = 0
                        left = start.x < size.width / 2f
                        seekFrom = state.positionMs
                        seekDelta = 0
                    },
                    onDragEnd = {
                        if (axis == 1) actions.seekTo((seekFrom + seekDelta).coerceIn(0, state.durationMs))
                        axis = 0
                    },
                ) { change, drag ->
                    change.consume()
                    if (axis == 0) axis = if (abs(drag.x) > abs(drag.y)) 1 else 2
                    if (axis == 1) {
                        // A full swipe across the screen moves 90 seconds.
                        seekDelta += (drag.x / size.width * 90_000).toLong()
                        val target = (seekFrom + seekDelta).coerceIn(0, state.durationMs)
                        state.hint = "${if (seekDelta >= 0) "+" else "-"}${clock(abs(seekDelta))}  ·  ${clock(target)}"
                        state.hintIcon = 0
                    } else {
                        val delta = -drag.y / size.height
                        if (left) {
                            val level = actions.changeBrightness(delta)
                            state.hint = "Brightness ${(level * 100).toInt()}%"
                            state.hintIcon = 1
                        } else {
                            val level = actions.changeVolume(delta)
                            state.hint = "Volume ${(level * 100).toInt()}%"
                            state.hintIcon = 2
                        }
                    }
                }
            },
    ) {
        SubtitleLayer(state, prefs)

        if (state.buffering && !state.ended && state.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White, strokeWidth = 3.dp)
        }

        if (state.locked) {
            AnimatedVisibility(state.controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    RoundIcon(
                        Icons.Rounded.Lock,
                        "Unlock",
                        Modifier.align(Alignment.CenterStart).padding(start = 28.dp),
                    ) { state.locked = false }
                }
            }
        } else {
            AnimatedVisibility(state.controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Controls(state, prefs, actions, castButton) { touchedAt = System.currentTimeMillis() }
            }
        }

        Hint(state)
        if (!state.stillWatching) BottomPills(state, prefs, actions, Modifier.align(Alignment.BottomEnd))
        ResumePill(state, actions, Modifier.align(Alignment.BottomStart))

        state.error?.let { ErrorCard(it, state, actions, Modifier.align(Alignment.Center)) }

        AnimatedVisibility(
            visible = state.panel != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SidePanel(state, prefs, actions)
        }

        if (state.stillWatching) StillWatching(state, actions)
    }
}

@Composable
private fun Controls(state: PlayerState, prefs: PlayerPrefs, actions: PlayerActions, castButton: (@Composable () -> Unit)?, onTouch: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent))),
        )
        Box(
            Modifier.fillMaxWidth().height(170.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim))),
        )

        // Top: back, title, and the picture tools.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions::back) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(state.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.episodeLine?.let { Text(it, color = Color(0xCCFFFFFF), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            castButton?.let { Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { it() } }
            ToolButton(Icons.Rounded.AspectRatio, state.fit.label) {
                onTouch()
                actions.setFit(Fit.entries[(state.fit.ordinal + 1) % Fit.entries.size])
                state.hint = state.fit.label
                state.hintIcon = 0
            }
            ToolButton(Icons.Rounded.LockOpen, "Lock") {
                state.locked = true
                state.controlsVisible = true
            }
            ToolButton(Icons.Rounded.PictureInPictureAlt, "Picture in picture") { actions.enterPip() }
        }

        // Centre: rewind, play/pause, forward.
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.Rounded.Replay10, "Back 10 seconds") { onTouch(); actions.seekBy(-10_000) }
            if (!state.buffering || state.ended) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { onTouch(); actions.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.playing) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp),
                    )
                }
            } else Spacer(Modifier.size(76.dp))
            RoundIcon(Icons.Rounded.Forward10, "Forward 10 seconds") { onTouch(); actions.seekBy(10_000) }
        }

        // Bottom: seek bar and the menus.
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 28.dp).padding(bottom = 10.dp),
        ) {
            SeekBar(state, actions, onTouch)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${clock(state.positionMs)} / ${clock(state.durationMs)}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (state.isSeries) MenuButton(Icons.Rounded.VideoLibrary, "Episodes") { state.panel = Panel.Episodes }
                MenuButton(Icons.Rounded.SwapHoriz, "Sources") {
                    state.panel = Panel.Sources
                    actions.loadSources()
                }
                MenuButton(Icons.Rounded.Subtitles, "Subtitles") { state.panel = Panel.Subtitles }
                MenuButton(Icons.Rounded.Audiotrack, "Audio") { state.panel = Panel.Audio }
                MenuButton(Icons.Rounded.Speed, if (state.speed == 1f) "Speed" else speedLabel(state.speed)) { state.panel = Panel.Speed }
                if (state.anime4kAvailable) {
                    MenuButton(Icons.Rounded.AutoAwesome, if (prefs.anime4kMode.isNotEmpty()) "4K ${prefs.anime4kMode}" else "Anime4K") { state.panel = Panel.Anime4K }
                }
                MenuButton(Icons.Rounded.Bedtime, if (state.sleepAt != null) "Sleep on" else "Sleep") { state.panel = Panel.Sleep }
                if (state.next != null) MenuButton(Icons.Rounded.SkipNext, "Next") { actions.playNext() }
            }
        }
    }
}

@Composable
private fun SeekBar(state: PlayerState, actions: PlayerActions, onTouch: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val value = (if (dragging) dragValue else state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (state.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    // Harbor's thin seek bar: track, buffered range, played range and a round thumb.
    Canvas(
        Modifier.fillMaxWidth().height(36.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    onTouch()
                    actions.seekTo(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onTouch()
                        dragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        actions.seekTo((dragValue * duration).toLong())
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    onTouch()
                    dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val y = size.height / 2
        val stroke = (if (dragging) 6.dp else 4.dp).toPx()
        drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        drawLine(Color(0x66FFFFFF), Offset(0f, y), Offset(size.width * buffered, y), stroke, StrokeCap.Round)
        drawLine(LartColors.Accent, Offset(0f, y), Offset(size.width * value, y), stroke, StrokeCap.Round)
        drawCircle(Color.White, (if (dragging) 9.dp else 7.dp).toPx(), Offset(size.width * value, y))
    }
    if (dragging) {
        Text(clock((dragValue * duration).toLong()), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Every subtitle, from the video or an addon, drawn by Lart in the chosen style. */
@Composable
private fun SubtitleLayer(state: PlayerState, prefs: PlayerPrefs) {
    val cues = state.cues
    if (cues.isEmpty()) return
    val (top, bottom) = cues.partition { it.top }
    if (bottom.isNotEmpty()) {
        // Lifted above the controls while they show, as Harbor does.
        BoxWithFraction(prefs.subBottom + if (state.controlsVisible && !state.inPip) CONTROLS_LIFT else 0f) {
            CueColumn(bottom, prefs)
        }
    }
    if (top.isNotEmpty()) {
        Box(Modifier.fillMaxSize().padding(top = 24.dp, start = 48.dp, end = 48.dp), contentAlignment = Alignment.TopCenter) {
            CueColumn(top, prefs)
        }
    }
}

@Composable
private fun CueColumn(cues: List<UiCue>, prefs: PlayerPrefs) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cues.forEach { cue ->
            val image = cue.image
            when {
                image != null -> androidx.compose.foundation.Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = if (cue.width > 0f) Modifier.fillMaxWidth(cue.width.coerceIn(0.1f, 1f)) else Modifier,
                )
                cue.text != null -> SubtitleText(cue.text, prefs)
            }
        }
    }
}

@Composable
private fun BoxWithFraction(bottom: Float, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = maxHeight * bottom, start = 48.dp, end = 48.dp),
            contentAlignment = Alignment.BottomCenter,
        ) { content() }
    }
}

@Composable
fun SubtitleText(text: String, prefs: PlayerPrefs, modifier: Modifier = Modifier) {
    val base = TextStyle(
        color = Color(prefs.subColor.toInt()),
        fontSize = (22 * prefs.subScale).sp,
        lineHeight = (28 * prefs.subScale).sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    when (prefs.subBackground) {
        "box" -> Text(
            text,
            style = base,
            modifier = modifier.background(Color(0xB3000000), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        "shadow" -> Text(text, style = base.copy(shadow = Shadow(Color.Black, Offset(2f, 2f), 6f)), modifier = modifier)
        else -> Box(modifier) {
            // A drawn outline: the text in black, nudged around, under the coloured text.
            val outline = base.copy(color = Color.Black)
            for ((dx, dy) in listOf(-1.5f to 0f, 1.5f to 0f, 0f to -1.5f, 0f to 1.5f, -1f to -1f, 1f to 1f, -1f to 1f, 1f to -1f)) {
                Text(text, style = outline, modifier = Modifier.offset(x = dx.dp, y = dy.dp))
            }
            Text(text, style = base)
        }
    }
}

@Composable
private fun Hint(state: PlayerState) {
    val text = state.hint ?: return
    Box(Modifier.fillMaxSize().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.background(Color(0xCC000000), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.hintIcon) {
                1 -> Icon(Icons.Rounded.LightMode, null, tint = Color.White, modifier = Modifier.size(18.dp))
                2 -> Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Skip intro/recap/credits, and Up next near the end, like Harbor's skip pill. */
@Composable
private fun BottomPills(state: PlayerState, prefs: PlayerPrefs, actions: PlayerActions, modifier: Modifier) {
    val bottom = if (state.controlsVisible) 120.dp else 36.dp
    Column(modifier.padding(end = 32.dp, bottom = bottom), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val skip = state.skip
        if (skip != null && !prefs.autoSkipIntro) {
            Pill(skip.kind.label, primary = false) { actions.skipSegment() }
        }
        val next = state.next
        val remaining = state.durationMs - state.positionMs
        if (next != null && state.durationMs > 0 && (remaining <= 45_000 || state.ended) && skip == null) {
            UpNext(next, state, actions)
        }
    }
}

@Composable
private fun UpNext(next: Video, state: PlayerState, actions: PlayerActions) {
    Row(
        Modifier.widthIn(max = 380.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xE6000000))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .clickable(enabled = !state.nextLoading) { actions.playNext() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(LartColors.Elevated)) {
            next.thumbnail?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text("UP NEXT", color = LartColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(episodeCode(next), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(next.label, color = Color(0xCCFFFFFF), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (state.nextLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Icon(Icons.Rounded.SkipNext, "Play next episode", tint = Color.White)
    }
}

@Composable
private fun ResumePill(state: PlayerState, actions: PlayerActions, modifier: Modifier) {
    val from = state.resumedFromMs
    if (from <= 0) return
    LaunchedEffect(from) {
        delay(7_000)
        state.resumedFromMs = 0
    }
    val bottom = if (state.controlsVisible) 120.dp else 36.dp
    Row(
        modifier.padding(start = 32.dp, bottom = bottom).background(Color(0xE6000000), RoundedCornerShape(50))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Resumed at ${clock(from)}", color = Color.White, fontSize = 13.sp)
        Pill("Start over", primary = true) {
            actions.startOver()
            state.resumedFromMs = 0
        }
    }
}

@Composable
private fun ErrorCard(message: String, state: PlayerState, actions: PlayerActions, modifier: Modifier) {
    Column(
        modifier.widthIn(max = 420.dp).background(Color(0xF0181A1E), RoundedCornerShape(20.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This source didn't play", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = Color(0xCCFFFFFF), fontSize = 13.5.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Try again", primary = false) { actions.retry() }
            Pill("Other sources", primary = true) {
                state.error = null
                state.panel = Panel.Sources
                actions.loadSources()
            }
        }
    }
}

@Composable
private fun StillWatching(state: PlayerState, actions: PlayerActions) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Are you still watching?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            state.episodeLine?.let { Text(it, color = Color(0xCCFFFFFF), fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Keep watching", primary = true) { actions.keepWatching() }
                Pill("Exit", primary = false) { actions.back() }
            }
        }
    }
}

// ---- Side panels ----

@Composable
private fun SidePanel(state: PlayerState, prefs: PlayerPrefs, actions: PlayerActions) {
    val panel = state.panel ?: return
    Column(
        Modifier.fillMaxHeight().width(380.dp).background(PanelColor)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(top = 20.dp, start = 20.dp, end = 20.dp),
    ) {
        Text(
            when (panel) {
                Panel.Subtitles -> "Subtitles"
                Panel.SubStyle -> "Subtitle style"
                Panel.Audio -> "Audio"
                Panel.Speed -> "Playback speed"
                Panel.Sources -> "Sources"
                Panel.Episodes -> "Episodes"
                Panel.Sleep -> "Sleep timer"
                Panel.Anime4K -> "Anime4K"
            },
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        when (panel) {
            Panel.Subtitles -> SubtitlesPanel(state, actions)
            Panel.SubStyle -> SubStylePanel(prefs, actions)
            Panel.Audio -> LazyColumn {
                item {
                    DelayRow("Delay", state.audioDelayMs, "Sound later", "Sound earlier", 50, 250) { actions.setAudioDelay(it) }
                }
                if (state.audioTracks.isEmpty()) item { Muted("This video has one audio track.") }
                items(state.audioTracks, key = { it.key }) { track ->
                    OptionRow(track.label, track.detail, track.selected) { actions.selectAudio(track.key) }
                }
            }
            Panel.Speed -> LazyColumn {
                items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { speed ->
                    OptionRow(if (speed == 1f) "Normal" else speedLabel(speed), null, state.speed == speed) { actions.setSpeed(speed) }
                }
            }
            Panel.Sleep -> LazyColumn {
                val at = state.sleepAt
                if (at != null && at > 0) {
                    item { Muted("Pauses at ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(at)}") }
                }
                item { OptionRow("Off", null, at == null) { actions.setSleep(null) } }
                item { OptionRow("End of this episode", null, at == -1L) { actions.setSleep(0) } }
                items(listOf(15, 30, 45, 60, 90)) { minutes ->
                    OptionRow("$minutes minutes", null, false) { actions.setSleep(minutes) }
                }
            }
            Panel.Sources -> SourcesPanel(state, actions)
            Panel.Anime4K -> Anime4KPanel(state, prefs, actions)
            Panel.Episodes -> EpisodesPanel(state, actions)
        }
    }
}

@Composable
private fun SubtitlesPanel(state: PlayerState, actions: PlayerActions) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Pill("Style", primary = false) { state.panel = Panel.SubStyle }
                Spacer(Modifier.width(10.dp))
            }
        }
        if (state.subChoice != SubChoice.Off) {
            item {
                // Sync, like Harbor's sub-sync bar: works for subtitles in the video and from addons.
                DelayRow("Sync", state.subDelayMs, "Later", "Earlier", 100, 500) { actions.setSubDelay(it) }
            }
        }
        item { OptionRow("Off", null, state.subChoice == SubChoice.Off) { actions.selectSubtitle(SubChoice.Off) } }
        if (state.embeddedSubs.isNotEmpty()) {
            item { GroupLabel("In the video") }
            items(state.embeddedSubs, key = { "e" + it.key }) { track ->
                OptionRow(track.label, track.detail, (state.subChoice as? SubChoice.Embedded)?.key == track.key) {
                    actions.selectSubtitle(SubChoice.Embedded(track.key))
                }
            }
        }
        val addon = state.addonSubs
        item { GroupLabel("From your addons") }
        when {
            addon == null -> item { Muted("Searching your subtitle addons…") }
            addon.isEmpty() -> item { Muted("Your addons found no subtitles for this.") }
            else -> {
                // Grouped by language, the device language first.
                val device = Locale.getDefault().language
                val sorted = addon.sortedWith(compareBy({ languageCode(it.lang) != device }, { languageName(it.lang) }))
                items(sorted, key = { "a" + it.url }) { sub ->
                    val selected = (state.subChoice as? SubChoice.Addon)?.subtitle?.url == sub.url
                    OptionRow(languageName(sub.lang), sub.id?.takeIf { it.length < 40 }, selected) {
                        actions.selectSubtitle(SubChoice.Addon(sub))
                    }
                }
            }
        }
        if (state.subLoading) item { Muted("Loading subtitle…") }
    }
}

@Composable
private fun SubStylePanel(prefs: PlayerPrefs, actions: PlayerActions) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A4048)), contentAlignment = Alignment.Center) {
                SubtitleText("The quick brown fox", prefs)
            }
        }
        item {
            GroupLabel("Size")
            Slider(
                value = prefs.subScale,
                onValueChange = { v -> actions.updatePrefs { it.copy(subScale = v) } },
                valueRange = 0.6f..1.8f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LartColors.Accent),
            )
        }
        item {
            GroupLabel("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(0xFFFFFFFF, 0xFFFFE66D, 0xFF8BE9FD, 0xFFB8F5A0).forEach { argb ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(argb.toInt()))
                            .border(if (prefs.subColor == argb) 3.dp else 0.dp, LartColors.Accent, CircleShape)
                            .clickable { actions.updatePrefs { it.copy(subColor = argb) } },
                    )
                }
            }
        }
        item {
            GroupLabel("Background")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("outline" to "Outline", "shadow" to "Shadow", "box" to "Box").forEach { (key, label) ->
                    Pill(label, primary = prefs.subBackground == key) { actions.updatePrefs { it.copy(subBackground = key) } }
                }
            }
        }
        item {
            GroupLabel("Position")
            Slider(
                value = prefs.subBottom,
                onValueChange = { v -> actions.updatePrefs { it.copy(subBottom = v) } },
                valueRange = 0.02f..0.4f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LartColors.Accent),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skip intros automatically", color = Color.White, fontSize = 15.sp)
                    Text("When an intro database knows the episode.", color = Color(0x99FFFFFF), fontSize = 12.sp)
                }
                Switch(checked = prefs.autoSkipIntro, onCheckedChange = { v -> actions.updatePrefs { it.copy(autoSkipIntro = v) } })
            }
        }
        item { Muted("Style applies to every text subtitle, in the video or from an addon. Image subtitles (PGS, VobSub) keep their own look.") }
    }
}

@Composable
private fun SourcesPanel(state: PlayerState, actions: PlayerActions) {
    val sources = state.sources
    if (sources == null) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LartColors.Accent) }
        return
    }
    if (sources.isEmpty()) {
        Muted("No other sources found.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sources) { source ->
            val stream = source.stream
            val current = stream.url != null && stream.url == state.currentUrl
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (current) Color(0x33F0A35E) else Color(0x14FFFFFF))
                    .clickable(enabled = !current) { actions.playSource(source) }.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    (stream.name ?: source.addon.manifest.name).replace('\n', ' '),
                    color = LartColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Ranking.badges(stream).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString("  ·  "), color = Color(0xCCFFFFFF), fontSize = 12.sp)
                }
                Text(stream.text, color = Color(0x99FFFFFF), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (current) Text("Playing", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EpisodesPanel(state: PlayerState, actions: PlayerActions) {
    val current = state.episodes.firstOrNull { it.id == state.videoId }
    var season by remember(current?.season) { mutableStateOf(current?.season) }
    val seasons = remember(state.episodes) { state.episodes.mapNotNull { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it })) }
    val shown = state.episodes.filter { it.season == season }.sortedBy { it.episode ?: 0 }
    val listState = rememberLazyListState()
    LaunchedEffect(season) {
        val index = shown.indexOfFirst { it.id == state.videoId }
        if (index > 0) listState.scrollToItem(index)
    }
    if (seasons.size > 1) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            items(seasons) { s -> Pill(if (s == 0) "Specials" else "Season $s", primary = s == season) { season = s } }
        }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shown, key = { it.id }) { video ->
            val playing = video.id == state.videoId
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (playing) Color(0x33F0A35E) else Color.Transparent)
                    .clickable(enabled = !playing) { actions.playEpisode(video) }.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(LartColors.Elevated)) {
                    video.thumbnail?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Column(Modifier.weight(1f)) {
                    Text(episodeCode(video), color = LartColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(video.label, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (playing) Text("Playing", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                }
            }
        }
    }
}

// ---- Small pieces ----

/** A nudge control: fine and coarse steps either way, the value, and a reset. */
@Composable
private fun DelayRow(label: String, valueMs: Long, plusHint: String, minusHint: String, fine: Long, coarse: Long, onChange: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (valueMs == 0L) "In sync" else "%+.2fs".format(Locale.US, valueMs / 1000f),
                color = if (valueMs == 0L) Color(0x99FFFFFF) else LartColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (valueMs != 0L) {
                Spacer(Modifier.width(8.dp))
                SmallButton("Reset") { onChange(0) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("−${coarse / 1000f}s") { onChange(valueMs - coarse) }
            SmallButton("−${fine / 1000f}s") { onChange(valueMs - fine) }
            SmallButton("+${fine / 1000f}s") { onChange(valueMs + fine) }
            SmallButton("+${coarse / 1000f}s") { onChange(valueMs + coarse) }
        }
        Text("+ $plusHint · − $minusHint", color = Color(0x80FFFFFF), fontSize = 11.sp)
    }
}

@Composable
private fun OptionRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (selected) Color.White else Color(0xDDFFFFFF), fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            detail?.let { Text(it, color = Color(0x88FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = LartColors.Accent)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), color = Color(0x88FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 8.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
}

@Composable
private fun Pill(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (primary) Color.White else Color(0x26FFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (primary) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x26FFFFFF)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    ) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(56.dp).clip(CircleShape).background(Color(0x26FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(30.dp)) }
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = Color.White) }
}

@Composable
private fun MenuButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s) else "%d:%02d".format(Locale.US, m, s)
}

private fun speedLabel(speed: Float) = speed.toString().removeSuffix(".0") + "×"

fun episodeCode(video: Video): String =
    if (video.season != null && video.episode != null) "S${video.season} · E${video.episode}" else video.label

private fun languageCode(lang: String): String = runCatching { Locale(lang).language }.getOrDefault(lang)

fun languageName(lang: String): String {
    val locale = Locale.forLanguageTag(lang).takeIf { it.language.isNotEmpty() } ?: Locale(lang)
    return locale.getDisplayLanguage(Locale.getDefault()).takeIf { it.isNotBlank() && it != lang }?.replaceFirstChar { it.titlecase() }
        ?: lang.uppercase()
}

/** While casting: what's on the TV, with play/pause, ±10 s and Stop casting. */
@Composable
private fun CastingScreen(device: String, state: PlayerState, actions: PlayerActions) {
    Box(Modifier.fillMaxSize().background(Color(0xF0101114)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Casting to $device", color = LartColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(state.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            state.episodeLine?.let { Text(it, color = Color(0xCCFFFFFF), fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Rounded.Replay10, "Back 10 seconds") { actions.castSeekBy(-10_000) }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { actions.castTogglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.castPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.castPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp),
                    )
                }
                RoundIcon(Icons.Rounded.Forward10, "Forward 10 seconds") { actions.castSeekBy(10_000) }
            }
            Pill("Stop casting", primary = true) { actions.stopCasting() }
        }
        IconButton(onClick = actions::back, modifier = Modifier.align(Alignment.TopStart).padding(20.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

/** Harbor's Anime4K menu: the six modes, Fast or HQ networks, and Off (mpv only). */
@Composable
private fun Anime4KPanel(state: PlayerState, prefs: PlayerPrefs, actions: PlayerActions) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Real-time upscaling and line restoration for anime, run on the GPU. Shaders download once (about 1 MB).",
                color = Color(0x99FFFFFF),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
        item {
            Row(Modifier.padding(start = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Fast", primary = !prefs.anime4kHq) { actions.setAnime4K(prefs.anime4kMode.ifEmpty { null }, false) }
                Pill("HQ", primary = prefs.anime4kHq) { actions.setAnime4K(prefs.anime4kMode.ifEmpty { null }, true) }
            }
        }
        if (state.anime4kLoading) item { Muted("Downloading shaders…") }
        item { OptionRow("Off", null, prefs.anime4kMode.isEmpty()) { actions.setAnime4K(null, prefs.anime4kHq) } }
        items(Anime4K.Mode.entries) { mode ->
            OptionRow(mode.label, mode.sub, prefs.anime4kMode == mode.name) { actions.setAnime4K(mode.name, prefs.anime4kHq) }
        }
        item { Muted("HQ is for fast phones and tablets; Fast suits most phones.") }
    }
}
