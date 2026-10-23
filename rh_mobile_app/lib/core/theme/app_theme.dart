import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';

/// Thème RH Connect — aligné sur design-system/sirh-rh-evenement/MASTER.md
/// (skill ui-ux-pro-max : « Minimalism & Swiss », bleu de confiance,
/// Plus Jakarta Sans). Mêmes tokens que l'admin web.
class AppTheme {
  AppTheme._();

  // ── Couleurs (contrastes vérifiés WCAG sur blanc) ─────────────────────────
  static const Color primary        = Color(0xFF2563EB); // 5.2:1 texte blanc
  static const Color primaryDark    = Color(0xFF1D4ED8);
  static const Color primaryLight   = Color(0xFF3B82F6);
  static const Color primarySurface = Color(0xFFEFF6FF);
  static const Color accent         = Color(0xFFEA580C);
  static const Color background     = Color(0xFFF8FAFC);
  static const Color surface        = Color(0xFFFFFFFF);
  static const Color surfaceMuted   = Color(0xFFF1F5F9);
  static const Color textPrimary    = Color(0xFF0F172A);
  static const Color textSecondary  = Color(0xFF475569); // 7.6:1
  static const Color textTertiary   = Color(0xFF64748B); // 4.8:1 — minimum pour du texte
  static const Color border         = Color(0xFFE2E8F0);
  static const Color inputBorder    = Color(0xFF8291A7); // 3.2:1 (contour de champ)
  static const Color success        = Color(0xFF15803D);
  static const Color successSurface = Color(0xFFDCFCE7);
  static const Color warning        = Color(0xFFB45309);
  static const Color warningSurface = Color(0xFFFEF3C7);
  static const Color error          = Color(0xFFDC2626);
  static const Color errorSurface   = Color(0xFFFEE2E2);

  // ── Espacements / rayons ─────────────────────────────────────────────────
  static const double spaceXs = 4;
  static const double spaceSm = 8;
  static const double spaceMd = 16;
  static const double spaceLg = 24;
  static const double spaceXl = 32;
  static const double radiusSm = 8;
  static const double radius = 12;
  static const double radiusLg = 16;

  /// Cible tactile minimale (iOS 44 pt / Android 48 dp).
  static const double minTouch = 48;

  static TextStyle _font({
    double? size,
    FontWeight? weight,
    Color? color,
    double? height,
    double? letterSpacing,
  }) =>
      GoogleFonts.plusJakartaSans(
        fontSize: size,
        fontWeight: weight,
        color: color,
        height: height,
        letterSpacing: letterSpacing,
      );

  static ThemeData light() {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: primary,
      brightness: Brightness.light,
      primary: primary,
      onPrimary: Colors.white,
      secondary: primaryDark,
      tertiary: accent,
      surface: surface,
      onSurface: textPrimary,
      error: error,
    ).copyWith(
      primaryContainer: primarySurface,
      onPrimaryContainer: primaryDark,
      surfaceContainerLowest: surface,
      surfaceContainerLow: background,
      surfaceContainer: surfaceMuted,
      surfaceContainerHighest: surfaceMuted,
      onSurfaceVariant: textSecondary,
      outline: inputBorder,
      outlineVariant: border,
    );

    final textTheme = GoogleFonts.plusJakartaSansTextTheme().copyWith(
      headlineMedium: _font(size: 26, weight: FontWeight.w800, color: textPrimary, letterSpacing: -0.4),
      headlineSmall:  _font(size: 22, weight: FontWeight.w700, color: textPrimary, letterSpacing: -0.3),
      titleLarge:     _font(size: 18, weight: FontWeight.w700, color: textPrimary, letterSpacing: -0.2),
      titleMedium:    _font(size: 16, weight: FontWeight.w600, color: textPrimary),
      titleSmall:     _font(size: 14, weight: FontWeight.w600, color: textPrimary),
      bodyLarge:      _font(size: 16, color: textPrimary, height: 1.5),
      bodyMedium:     _font(size: 14, color: textSecondary, height: 1.5),
      bodySmall:      _font(size: 12, color: textTertiary, height: 1.4),
      labelLarge:     _font(size: 15, weight: FontWeight.w600),
      labelMedium:    _font(size: 13, weight: FontWeight.w600),
      labelSmall:     _font(size: 12, weight: FontWeight.w600),
    );

    final roundedSm = RoundedRectangleBorder(borderRadius: BorderRadius.circular(radiusSm));
    final rounded = RoundedRectangleBorder(borderRadius: BorderRadius.circular(radius));

    OutlineInputBorder inputBorderOf(Color c, [double w = 1]) => OutlineInputBorder(
          borderRadius: BorderRadius.circular(radius),
          borderSide: BorderSide(color: c, width: w),
        );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: background,
      textTheme: textTheme,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      visualDensity: VisualDensity.standard,
      splashFactory: InkSparkle.splashFactory,
      appBarTheme: AppBarTheme(
        centerTitle: false,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        surfaceTintColor: Colors.transparent,
        shadowColor: border,
        backgroundColor: surface,
        foregroundColor: textPrimary,
        systemOverlayStyle: SystemUiOverlayStyle.dark,
        titleTextStyle: _font(size: 18, weight: FontWeight.w700, color: textPrimary, letterSpacing: -0.2),
        iconTheme: const IconThemeData(color: textPrimary, size: 24),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radius),
          side: const BorderSide(color: border),
        ),
        color: surface,
        surfaceTintColor: Colors.transparent,
        margin: EdgeInsets.zero,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: Colors.white,
          disabledBackgroundColor: border,
          disabledForegroundColor: textTertiary,
          elevation: 0,
          minimumSize: const Size.fromHeight(minTouch),
          padding: const EdgeInsets.symmetric(horizontal: spaceLg, vertical: 14),
          shape: rounded,
          textStyle: _font(size: 15, weight: FontWeight.w600),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: Colors.white,
          elevation: 0,
          minimumSize: const Size(64, minTouch),
          shape: rounded,
          textStyle: _font(size: 15, weight: FontWeight.w600),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: primaryDark,
          minimumSize: const Size.fromHeight(minTouch),
          side: const BorderSide(color: border),
          padding: const EdgeInsets.symmetric(horizontal: spaceLg, vertical: 14),
          shape: rounded,
          textStyle: _font(size: 15, weight: FontWeight.w600),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: primaryDark,
          minimumSize: const Size(48, 44),
          shape: roundedSm,
          textStyle: _font(size: 14, weight: FontWeight.w600),
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(minimumSize: const Size(minTouch, minTouch)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surface,
        border: inputBorderOf(inputBorder),
        enabledBorder: inputBorderOf(inputBorder),
        focusedBorder: inputBorderOf(primary, 2),
        errorBorder: inputBorderOf(error),
        focusedErrorBorder: inputBorderOf(error, 2),
        disabledBorder: inputBorderOf(border),
        contentPadding: const EdgeInsets.symmetric(horizontal: spaceMd, vertical: 14),
        hintStyle: _font(color: textTertiary, size: 14),
        labelStyle: _font(color: textSecondary, size: 14),
        floatingLabelStyle: _font(color: primaryDark, size: 13, weight: FontWeight.w600),
        helperStyle: _font(color: textTertiary, size: 12),
        errorStyle: _font(color: error, size: 12, weight: FontWeight.w500),
        prefixIconColor: textTertiary,
        suffixIconColor: textTertiary,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: surfaceMuted,
        selectedColor: primarySurface,
        side: const BorderSide(color: border),
        labelStyle: _font(size: 13, weight: FontWeight.w600, color: textSecondary),
        secondaryLabelStyle: _font(size: 13, weight: FontWeight.w600, color: primaryDark),
        shape: StadiumBorder(side: const BorderSide(color: border)),
        padding: const EdgeInsets.symmetric(horizontal: spaceSm, vertical: spaceXs),
      ),
      listTileTheme: ListTileThemeData(
        minVerticalPadding: 12,
        iconColor: textSecondary,
        titleTextStyle: _font(size: 15, weight: FontWeight.w600, color: textPrimary),
        subtitleTextStyle: _font(size: 13, color: textSecondary),
        shape: rounded,
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: surface,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(radiusLg)),
        titleTextStyle: _font(size: 18, weight: FontWeight.w700, color: textPrimary),
        contentTextStyle: _font(size: 14, color: textSecondary, height: 1.5),
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: surface,
        surfaceTintColor: Colors.transparent,
        showDragHandle: true,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(radiusLg)),
        ),
      ),
      dividerTheme: const DividerThemeData(color: border, thickness: 1, space: 1),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: primary,
        linearTrackColor: primarySurface,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: textPrimary,
        contentTextStyle: _font(color: Colors.white, size: 14),
        actionTextColor: const Color(0xFF93C5FD),
        shape: rounded,
        elevation: 0,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: surface,
        surfaceTintColor: Colors.transparent,
        indicatorColor: primarySurface,
        height: 68,
        labelTextStyle: WidgetStateProperty.resolveWith(
          (s) => _font(
            size: 12,
            weight: s.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
            color: s.contains(WidgetState.selected) ? primaryDark : textTertiary,
          ),
        ),
        iconTheme: WidgetStateProperty.resolveWith(
          (s) => IconThemeData(
            size: 24,
            color: s.contains(WidgetState.selected) ? primaryDark : textTertiary,
          ),
        ),
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: surface,
        selectedItemColor: primaryDark,
        unselectedItemColor: textTertiary,
        selectedLabelStyle: _font(weight: FontWeight.w700, size: 12),
        unselectedLabelStyle: _font(weight: FontWeight.w500, size: 12),
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: primary,
        foregroundColor: Colors.white,
        elevation: 2,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(radiusLg)),
      ),
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.android: CupertinoPageTransitionsBuilder(),
          TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
        },
      ),
    );
  }
}
