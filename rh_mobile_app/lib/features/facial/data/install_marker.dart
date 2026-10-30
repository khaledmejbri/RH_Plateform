export 'install_marker_stub.dart' if (dart.library.io) 'install_marker_io.dart';

/// Marqueur d'installation : fichier dans le dossier de l'appli, supprimé à la désinstallation
/// (contrairement au Keychain iOS, qui survit). Absent au démarrage = nouvelle installation.
abstract class InstallMarker {
  Future<bool> existe();
  Future<void> creer();
}
