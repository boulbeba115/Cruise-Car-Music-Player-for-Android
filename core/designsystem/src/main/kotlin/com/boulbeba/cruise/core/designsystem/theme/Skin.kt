package com.boulbeba.cruise.core.designsystem.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.boulbeba.cruise.core.designsystem.R

/** How the cover is presented on Now Playing. */
enum class CoverStyle {
    /** Record sleeve with holographic foil; the disc slides out and spins while playing. */
    Sleeve,
    /** The cover inside a living blob whose edge ripples with the spectrum. */
    Liquid,
    /** White die-cut sticker that tilts in 3D, foil sweep and a peel corner. */
    Sticker,
    /** Cassette: the art is the label, tape moves from reel to reel with the song. */
    Cassette,
    Vinyl, Neon, Gauge, Minimal,
}

/** The animated backdrop behind every screen. */
enum class BackgroundStyle { CoverGlow, NeonCity, Carbon, Felt, Aurora, Stripes, Plain }

/** The animated music (progress) bar. */
enum class BarStyle {
    Waveform,
    /** A sine wave that follows the music (Aurora). */
    Wavy,
    /** A glass tube filling with light (Neon City). */
    NeonTube,
    LedSegments,
    Groove,
    Hairline,
    /** Two reels and the tape between them, with a tape counter (Tape Deck). */
    Tape,
}

/** How the navigation marks the current destination. */
enum class NavStyle { Pill, Glow, Block, Underline }

/**
 * A skin changes the whole look (owner's request 2026-10-23): colours, typefaces, shapes, backdrop, cover
 * presentation, music bar, navigation and controls.
 */
@Immutable
data class Skin(
    val id: String,
    @StringRes val name: Int,
    @StringRes val tagline: Int,
    val colors: CruiseColors,
    val display: FontFamily,
    val body: FontFamily,
    val displayWeight: FontWeight = FontWeight.Bold,
    val uppercaseTitles: Boolean = false,
    /** Multiplies every corner radius: 0 = square, 1 = default, 1.5 = very round. */
    val corners: Float = 1f,
    val roundButtons: Boolean = true,
    val cover: CoverStyle = CoverStyle.Sleeve,
    val background: BackgroundStyle = BackgroundStyle.CoverGlow,
    val bar: BarStyle = BarStyle.Waveform,
    val nav: NavStyle = NavStyle.Pill,
    /** Accent follows the current album cover. */
    val dynamicAccent: Boolean = false,
    /** Soft light around accents (neon). */
    val glow: Boolean = false,
    /** Title and artist drawn as lit neon signs (Neon City). */
    val neonSigns: Boolean = false,
)

object Skins {
    // Static instances cut from the variable fonts (tools: fontTools instancer): variable fonts would render at their
    // default axis value — ExtraLight for Manrope — on Android 8.1 and older.
    private fun family(regular: Int, bold: Int, extraBold: Int = bold) = FontFamily(
        Font(regular, FontWeight.Normal), Font(regular, FontWeight.Medium),
        Font(bold, FontWeight.SemiBold), Font(bold, FontWeight.Bold), Font(extraBold, FontWeight.ExtraBold),
    )
    private val manrope = family(R.font.manrope_regular, R.font.manrope_bold, R.font.manrope_extrabold)
    private val orbitron = family(R.font.orbitron_regular, R.font.orbitron_bold)
    private val audiowide = FontFamily(Font(R.font.audiowide))
    private val tiltNeon = FontFamily(Font(R.font.tilt_neon_regular, FontWeight.Normal), Font(R.font.tilt_neon_regular, FontWeight.Bold))
    private val shareTech = FontFamily(Font(R.font.share_tech_mono))
    private val playfair = family(R.font.playfair_display_regular, R.font.playfair_display_bold)
    private val spaceGrotesk = family(R.font.space_grotesk_regular, R.font.space_grotesk_bold)

    val Midnight = Skin(
        id = "midnight", name = R.string.skin_midnight, tagline = R.string.skin_midnight_tagline,
        colors = MidnightColors, display = manrope, body = manrope,
        cover = CoverStyle.Sleeve, background = BackgroundStyle.CoverGlow, bar = BarStyle.Waveform, nav = NavStyle.Pill,
        dynamicAccent = true,
    )
    val NeonDrive = Skin(
        id = "neon", name = R.string.skin_neon, tagline = R.string.skin_neon_tagline,
        colors = NeonColors, display = tiltNeon, body = audiowide, displayWeight = FontWeight.Normal,
        corners = 0.6f, cover = CoverStyle.Neon, background = BackgroundStyle.NeonCity, bar = BarStyle.NeonTube,
        nav = NavStyle.Glow, glow = true, neonSigns = true,
    )
    val AmberCockpit = Skin(
        id = "amber", name = R.string.skin_amber, tagline = R.string.skin_amber_tagline,
        colors = AmberColors, display = shareTech, body = shareTech, displayWeight = FontWeight.Normal, uppercaseTitles = true,
        corners = 0.2f, roundButtons = false, cover = CoverStyle.Gauge, background = BackgroundStyle.Carbon,
        bar = BarStyle.LedSegments, nav = NavStyle.Block,
    )
    val VinylLounge = Skin(
        id = "vinyl", name = R.string.skin_vinyl, tagline = R.string.skin_vinyl_tagline,
        colors = VinylColors, display = playfair, body = manrope, corners = 1.15f,
        cover = CoverStyle.Vinyl, background = BackgroundStyle.Felt, bar = BarStyle.Groove, nav = NavStyle.Underline,
    )
    val Aurora = Skin(
        id = "aurora", name = R.string.skin_aurora, tagline = R.string.skin_aurora_tagline,
        colors = AuroraColors, display = spaceGrotesk, body = spaceGrotesk, corners = 1.5f,
        cover = CoverStyle.Liquid, background = BackgroundStyle.Aurora, bar = BarStyle.Wavy, nav = NavStyle.Pill,
    )
    val OledPure = Skin(
        id = "oled", name = R.string.skin_oled, tagline = R.string.skin_oled_tagline,
        colors = OledColors, display = manrope, body = manrope, displayWeight = FontWeight.ExtraBold, corners = 0.7f,
        cover = CoverStyle.Minimal, background = BackgroundStyle.Plain, bar = BarStyle.Hairline, nav = NavStyle.Underline,
    )
    val Daylight = Skin(
        id = "daylight", name = R.string.skin_daylight, tagline = R.string.skin_daylight_tagline,
        colors = DaylightColors, display = manrope, body = manrope,
        cover = CoverStyle.Sticker, background = BackgroundStyle.Plain, bar = BarStyle.Waveform, nav = NavStyle.Pill,
    )

    val TapeDeck = Skin(
        id = "tape", name = R.string.skin_tape, tagline = R.string.skin_tape_tagline,
        colors = TapeColors, display = spaceGrotesk, body = manrope, corners = 0.8f,
        cover = CoverStyle.Cassette, background = BackgroundStyle.Stripes, bar = BarStyle.Tape, nav = NavStyle.Block,
    )

    val all: List<Skin> = listOf(Midnight, NeonDrive, AmberCockpit, VinylLounge, TapeDeck, Aurora, OledPure, Daylight)

    fun byId(id: String?): Skin = all.firstOrNull { it.id == id } ?: Midnight
}

val LocalSkin = staticCompositionLocalOf { Skins.Midnight }
