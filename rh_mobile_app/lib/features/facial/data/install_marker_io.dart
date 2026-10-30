import 'dart:io';

import 'package:path_provider/path_provider.dart';

import 'install_marker.dart';

/// iOS uniquement : le Keychain survit à la désinstallation, pas le dossier de l'appli.
/// Android : `allowBackup=false` suffit, pas de marqueur.
InstallMarker? creerInstallMarker() => Platform.isIOS ? _FichierInstallMarker() : null;

class _FichierInstallMarker implements InstallMarker {
  Future<File> _fichier() async {
    final dir = await getApplicationSupportDirectory();
    return File('${dir.path}${Platform.pathSeparator}.rh_installation');
  }

  @override
  Future<bool> existe() async => (await _fichier()).exists();

  @override
  Future<void> creer() async {
    final f = await _fichier();
    await f.create(recursive: true);
    await f.writeAsString(DateTime.now().toUtc().toIso8601String());
  }
}
