import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/constants/api_constants.dart';
import '../../../core/theme/app_theme.dart';
import '../data/auth_repository.dart';
import '../providers/auth_notifier.dart';

/// Vrai uniquement si le build est lancé avec `--dart-define=DEMO_MODE=true` (absent par défaut, donc de Codemagic).
const bool modeDemoActif = bool.fromEnvironment('DEMO_MODE', defaultValue: false);

/// Adresse du support RH, fournie à la compilation (`--dart-define=SUPPORT_RH_EMAIL=...`), jamais en dur.
const String contactSupportRh = String.fromEnvironment('SUPPORT_RH_EMAIL', defaultValue: '');

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _form     = GlobalKey<FormState>();
  final _email    = TextEditingController();
  final _password = TextEditingController();
  var _loading = false;
  var _obscure = true;
  bool _serveurVisible = serveurEditable;

  /// Mot de passe oublié : consigne en français avec l'adresse du support RH si elle est configurée.
  Future<void> _motDePasseOublie() async {
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Mot de passe oublié'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Contactez votre service RH pour réinitialiser votre mot de passe.'),
            if (contactSupportRh.isNotEmpty) ...[
              const SizedBox(height: 12),
              SelectableText(
                contactSupportRh,
                style: const TextStyle(fontWeight: FontWeight.w700, color: AppTheme.primary),
              ),
            ],
          ],
        ),
        actions: [
          if (contactSupportRh.isNotEmpty)
            TextButton(
              onPressed: () async {
                await Clipboard.setData(const ClipboardData(text: contactSupportRh));
                if (ctx.mounted) Navigator.pop(ctx);
              },
              child: const Text('Copier l’adresse'),
            ),
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Fermer')),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _loading = true);
    try {
      await ref.read(authNotifierProvider.notifier).signIn(
            _email.text.trim(),
            _password.text,
          );
      if (mounted) context.go('/home');
    } on AuthException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Connexion impossible. Vérifiez le réseau.')),
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _demo() async {
    if (!modeDemoActif) return;
    setState(() => _loading = true);
    await ref.read(authNotifierProvider.notifier).demo();
    if (mounted) {
      setState(() => _loading = false);
      context.go('/home');
    }
  }

  Future<void> _editServer() async {
    final ctrl = TextEditingController(text: ApiConstants.baseUrl);
    final url = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Adresse du serveur'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: TextInputType.url,
          autocorrect: false,
          decoration: const InputDecoration(
            hintText: 'https://xxxx.trycloudflare.com',
            helperText: 'Vide = valeur par défaut',
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Annuler')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, ctrl.text),
            child: const Text('Enregistrer'),
          ),
        ],
      ),
    );
    ctrl.dispose();
    if (url == null) return;
    await ApiConstants.setBaseUrl(url);
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SingleChildScrollView(
        child: Column(
          children: [
            // ── Top hero ──────────────────────────────────────
            Container(
              width: double.infinity,
              height: size.height * 0.32,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [AppTheme.primary, AppTheme.primaryDark],
                ),
                borderRadius: BorderRadius.only(
                  bottomLeft:  Radius.circular(28),
                  bottomRight: Radius.circular(28),
                ),
              ),
              child: SafeArea(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    GestureDetector(
                      // Option cachée : en débogage seulement, un appui long affiche le champ « Serveur ».
                      onLongPress: (kDebugMode || serveurEditable)
                          ? () => setState(() => _serveurVisible = !_serveurVisible)
                          : null,
                      child: Container(
                        width: 72,
                        height: 72,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: const Icon(Icons.groups_2_rounded,
                            size: 40, color: Colors.white, semanticLabel: 'RH Connect'),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'RH Connect',
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                            fontSize: 22,
                          ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Votre espace RH',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: Colors.white.withValues(alpha: 0.88),
                            fontSize: 14,
                          ),
                    ),
                  ],
                ),
              ),
            ),

            // ── Form card ─────────────────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 28, 20, 24),
              child: Container(
                decoration: BoxDecoration(
                  color: AppTheme.surface,
                  borderRadius: BorderRadius.circular(AppTheme.radiusLg),
                  border: Border.all(color: AppTheme.border),
                  boxShadow: const [
                    BoxShadow(color: Color(0x0F0F172A), blurRadius: 24, offset: Offset(0, 8)),
                  ],
                ),
                padding: const EdgeInsets.all(24),
                child: Form(
                  key: _form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Connexion',
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Accédez à votre espace RH',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                      if (_serveurVisible) ...[
                      const SizedBox(height: 4),
                      InkWell(
                        onTap: _loading ? null : _editServer,
                        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(minHeight: 44),
                          child: Row(
                          children: [
                            Expanded(
                              child: Text(
                                'Serveur ${ApiConstants.baseUrl}',
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ),
                            const Icon(Icons.edit_outlined,
                                size: 18, color: AppTheme.textTertiary, semanticLabel: 'Modifier le serveur'),
                          ],
                        ),
                        ),
                      ),
                      ],
                      const SizedBox(height: 24),

                      // Email
                      TextFormField(
                        controller: _email,
                        keyboardType: TextInputType.emailAddress,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: 'E-mail ou matricule',
                          prefixIcon: Icon(Icons.person_outline_rounded, size: 20),
                        ),
                        validator: (v) =>
                            (v == null || v.trim().isEmpty) ? 'Champ requis' : null,
                      ),
                      const SizedBox(height: 14),

                      // Password
                      TextFormField(
                        controller: _password,
                        obscureText: _obscure,
                        textInputAction: TextInputAction.done,
                        onFieldSubmitted: (_) => _submit(),
                        decoration: InputDecoration(
                          labelText: 'Mot de passe',
                          prefixIcon:
                              const Icon(Icons.lock_outline_rounded, size: 20),
                          suffixIcon: IconButton(
                            tooltip: _obscure ? 'Afficher le mot de passe' : 'Masquer le mot de passe',
                            onPressed: () =>
                                setState(() => _obscure = !_obscure),
                            icon: Icon(
                              _obscure
                                  ? Icons.visibility_outlined
                                  : Icons.visibility_off_outlined,
                              size: 20,
                            ),
                          ),
                        ),
                        validator: (v) =>
                            (v == null || v.isEmpty) ? 'Champ requis' : null,
                      ),

                      // Forgot
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton(
                          onPressed: _motDePasseOublie,
                          style: TextButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 4),
                          ),
                          child: const Text('Mot de passe oublié ?',
                              style: TextStyle(fontSize: 13)),
                        ),
                      ),
                      const SizedBox(height: 8),

                      // Login button
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: _loading ? null : _submit,
                          child: _loading
                              ? const SizedBox(
                                  height: 20,
                                  width: 20,
                                  child: CircularProgressIndicator(
                                      strokeWidth: 2, color: Colors.white),
                                )
                              : const Text('Se connecter'),
                        ),
                      ),
                      // D-T02.c : démo seulement dans un build `--dart-define=DEMO_MODE=true`.
                      if (modeDemoActif) ...[
                        const SizedBox(height: 12),

                        // Divider
                        Row(
                          children: [
                            const Expanded(child: Divider()),
                            Padding(
                              padding:
                                  const EdgeInsets.symmetric(horizontal: 12),
                              child: Text('ou',
                                  style: Theme.of(context).textTheme.bodySmall),
                            ),
                            const Expanded(child: Divider()),
                          ],
                        ),
                        const SizedBox(height: 12),

                        // Demo button
                        SizedBox(
                          width: double.infinity,
                          child: OutlinedButton(
                            onPressed: _loading ? null : _demo,
                            child: const Text('Continuer en démo'),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),

            // Footer note
            Padding(
              padding: const EdgeInsets.only(bottom: 24),
              child: Text(
                'Les comptes sont créés par les RH.',
                style: Theme.of(context)
                    .textTheme
                    .bodySmall
                    ?.copyWith(color: AppTheme.textSecondary),
                textAlign: TextAlign.center,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
