package com.makd.afinity.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.makd.afinity.R

private val googleFontProvider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.google_fonts_certs,
    )

private val googleSansFlexFont = GoogleFont("Google Sans Flex")
private val quicksandFont = GoogleFont("Quicksand")
private val ibmPlexSansFont = GoogleFont("IBM Plex Sans")
private val ibmPlexSansCondensedFont = GoogleFont("IBM Plex Sans Condensed")

val GoogleSansFlex =
    FontFamily(
        Font(googleSansFlexFont, googleFontProvider, FontWeight.Thin),
        Font(googleSansFlexFont, googleFontProvider, FontWeight.ExtraLight),
        Font(googleSansFlexFont, googleFontProvider, FontWeight.Light),
        Font(googleSansFlexFont, googleFontProvider, FontWeight.Normal),
        Font(googleSansFlexFont, googleFontProvider, FontWeight.Medium),
        Font(googleSansFlexFont, googleFontProvider, FontWeight.SemiBold),
        Font(googleSansFlexFont, googleFontProvider, FontWeight.Bold),
        Font(googleSansFlexFont, googleFontProvider, FontWeight.ExtraBold),
        Font(googleSansFlexFont, googleFontProvider, FontWeight.Black),
    )

val Quicksand =
    FontFamily(
        Font(quicksandFont, googleFontProvider, FontWeight.Light),
        Font(quicksandFont, googleFontProvider, FontWeight.Normal),
        Font(quicksandFont, googleFontProvider, FontWeight.Medium),
        Font(quicksandFont, googleFontProvider, FontWeight.SemiBold),
        Font(quicksandFont, googleFontProvider, FontWeight.Bold),
    )

val IBMPlexSans =
    FontFamily(
        Font(ibmPlexSansFont, googleFontProvider, FontWeight.Normal),
        Font(ibmPlexSansFont, googleFontProvider, FontWeight.Medium),
        Font(ibmPlexSansFont, googleFontProvider, FontWeight.Bold),
    )

val IBMPlexSansCondensed =
    FontFamily(
        Font(ibmPlexSansCondensedFont, googleFontProvider, FontWeight.Normal),
        Font(ibmPlexSansCondensedFont, googleFontProvider, FontWeight.Medium),
        Font(ibmPlexSansCondensedFont, googleFontProvider, FontWeight.Bold),
    )

val CenterLineHeightStyle =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private val NoPaddingPlatformTextStyle = PlatformTextStyle(includeFontPadding = false)

fun getAppTypography(appFont: AppFont): Typography {
    val selectedFontFamily =
        when (appFont) {
            AppFont.DEFAULT -> FontFamily.Default
            AppFont.GOOGLE_SANS -> GoogleSansFlex
            AppFont.QUICKSAND -> Quicksand
            AppFont.IBM_PLEX_SANS -> IBMPlexSans
            AppFont.IBM_PLEX_SANS_CONDENSED -> IBMPlexSansCondensed
        }

    return Typography(
        displayLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        displayMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        displaySmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        titleLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        titleMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        titleSmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        bodySmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        labelLarge =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        labelMedium =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
        labelSmall =
            TextStyle(
                fontFamily = selectedFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
                platformStyle = NoPaddingPlatformTextStyle,
                lineHeightStyle = CenterLineHeightStyle,
            ),
    )
}
