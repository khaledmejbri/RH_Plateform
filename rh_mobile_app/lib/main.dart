import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'app.dart';
import 'core/constants/api_constants.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Recette web (flutter run -d chrome) : expose l'arbre d'accessibilité dans le DOM pour que les outils
  // de test (lecteur d'écran, automatisation navigateur) voient boutons et champs. Jamais en release.
  if (kIsWeb && !kReleaseMode) {
    SemanticsBinding.instance.ensureSemantics();
  }
  await initializeDateFormatting('fr_FR', null);
  await ApiConstants.init();
  debugPrint('[API] Gateway = ${ApiConstants.baseUrl}');
  runApp(const ProviderScope(child: RhMobileApp()));
}
