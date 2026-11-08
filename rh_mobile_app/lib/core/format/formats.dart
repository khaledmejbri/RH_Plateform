/// Module unique de formatage des dates et nombres de RH Connect (D-L01.c, règle R4).
///
/// Écrit à la main (sans `DateFormat`) : aucun besoin d'initialiser une locale, donc utilisable
/// tel quel dans les tests unitaires. Les dates sont `jj/mm/aaaa`, les date-heure
/// `jj/mm/aaaa hh:mm`, les décimaux à virgule.
library;

const String kValeurAbsente = '—';

String _deux(int n) => n.toString().padLeft(2, '0');

/// Analyse une date ISO (`2026-11-25`, `2026-11-25T10:30:00`, `2026-11-25T10:30:00Z`) ; null si illisible.
/// Une date avec fuseau (Z, +01:00) est convertie à l'heure locale de l'appareil.
DateTime? analyserDate(Object? brut) {
  if (brut == null) return null;
  if (brut is DateTime) return brut;
  final s = brut.toString().trim();
  if (s.isEmpty) return null;
  final d = DateTime.tryParse(s);
  if (d == null) return null;
  return d.isUtc ? d.toLocal() : d;
}

/// `06/11/2026`.
String formatDate(DateTime d) => '${_deux(d.day)}/${_deux(d.month)}/${d.year}';

/// `06/11/2026 14:05`.
String formatDateHeure(DateTime d) => '${formatDate(d)} ${formatHeure(d)}';

/// `14:05`.
String formatHeure(DateTime d) => '${_deux(d.hour)}:${_deux(d.minute)}';

/// Date ISO (texte ou DateTime) vers `jj/mm/aaaa` ; [kValeurAbsente] si absente ou illisible.
String formatDateIso(Object? brut) {
  final d = analyserDate(brut);
  return d == null ? kValeurAbsente : formatDate(d);
}

/// Date-heure ISO vers `jj/mm/aaaa hh:mm` ; [kValeurAbsente] si absente ou illisible.
String formatDateHeureIso(Object? brut) {
  final d = analyserDate(brut);
  return d == null ? kValeurAbsente : formatDateHeure(d);
}

/// Nombre de jours calendaires d'une période, bornes comprises (>= 1).
int nombreJoursPeriode(DateTime debut, DateTime fin) {
  final a = DateTime.utc(debut.year, debut.month, debut.day);
  final b = DateTime.utc(fin.year, fin.month, fin.day);
  return b.difference(a).inDays + 1;
}

/// Période : un seul jour « 06/11/2026 (1 jour) », sinon « du 06/11/2026 au 08/11/2026 (3 jours) ».
/// Si une borne est illisible, renvoie la seule borne lisible, ou [kValeurAbsente].
String formatPeriode(Object? debut, Object? fin) {
  final d = analyserDate(debut);
  final f = analyserDate(fin);
  if (d == null && f == null) return kValeurAbsente;
  if (d == null) return formatDate(f!);
  if (f == null) return formatDate(d);
  final n = nombreJoursPeriode(d, f);
  if (n <= 1) return '${formatDate(d)} (1 jour)';
  return 'du ${formatDate(d)} au ${formatDate(f)} ($n jours)';
}

/// Libellé court de durée : « 1 jour », « 3 jours ».
String formatNombreJours(int n) => n <= 1 ? '$n jour' : '$n jours';

/// Décimal à virgule : `13,5` ; entier sans décimale (`13`).
String formatDecimal(num valeur, {int decimales = 1}) {
  if (valeur == valeur.roundToDouble()) return valeur.toInt().toString();
  return valeur.toStringAsFixed(decimales).replaceAll('.', ',');
}

/// Note sur [sur] : « 13,5 / 20 » ; [kValeurAbsente] si [valeur] est absente (R8 : jamais « 0 / 20 » factice).
String formatNote(num? valeur, num sur, {int decimales = 1}) {
  if (valeur == null) return kValeurAbsente;
  return '${formatDecimal(valeur, decimales: decimales)} / ${formatDecimal(sur)}';
}

/// Initiale en majuscule d'un nom ; « ? » à défaut.
String initialeDe(String? nom) {
  final t = nom?.trim() ?? '';
  return t.isEmpty ? '?' : t.substring(0, 1).toUpperCase();
}

/// Date au format attendu par les API (`2026-11-05`).
String formatDateApi(DateTime d) => '${d.year.toString().padLeft(4, '0')}-${_deux(d.month)}-${_deux(d.day)}';

/// Durée en minutes : « 8 h 02 », « 45 min », « 2 h » ; « 0 min » pour zéro ou une valeur négative.
String formatDuree(int minutes) {
  if (minutes <= 0) return '0 min';
  final h = minutes ~/ 60;
  final m = minutes % 60;
  if (h == 0) return '$m min';
  if (m == 0) return '$h h';
  return '$h h ${_deux(m)}';
}

/// Retard d'une journée (D-P19.g) : [kValeurAbsente] si non calculable (`null`), « À l'heure » à 0,
/// sinon « 12 min de retard ».
String formatRetard(int? minutes) {
  if (minutes == null) return kValeurAbsente;
  if (minutes <= 0) return 'À l’heure';
  return '$minutes min de retard';
}

/// Compteur de badge : masqué (`null`) à 0, exact jusqu'à 99, « 99+ » au-delà.
String? libelleBadge(int nombre) {
  if (nombre <= 0) return null;
  return nombre > 99 ? '99+' : '$nombre';
}
