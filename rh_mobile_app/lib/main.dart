import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'app.dart';
import 'core/constants/api_constants.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('fr_FR', null);
  await ApiConstants.init();
  debugPrint('[API] Gateway = ${ApiConstants.baseUrl}');
  runApp(const ProviderScope(child: RhMobileApp()));
}
