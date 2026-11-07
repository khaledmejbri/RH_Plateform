import '../../../core/libelles/libelles.dart';

/// Catalogue fermé M01 — types de congé (story 7).
class TypeCongeOption {
  const TypeCongeOption({required this.code, required this.label});

  final String code;
  final String label;
}

const List<TypeCongeOption> kTypesConge = [
  TypeCongeOption(code: 'ANNUEL', label: 'Congé annuel'),
  TypeCongeOption(code: 'MALADIE', label: 'Congé maladie'),
  TypeCongeOption(code: 'MATERNITE', label: 'Congé maternité'),
  TypeCongeOption(code: 'SANS_SOLDE', label: 'Congé sans solde'),
  TypeCongeOption(code: 'AUTRE', label: 'Autre'),
];

bool typeCongeExigePieceJointe(String code) =>
    code == 'MALADIE' || code == 'MATERNITE';

String libelleTypeConge(String code) => Libelles.typeConge(code);
