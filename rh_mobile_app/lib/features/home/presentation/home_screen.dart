import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../auth/presentation/profile_screen.dart';
import '../../facial/data/facial_models.dart';
import '../../facial/providers/facial_controller.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/notification_action.dart';
import '../../demandes_admin/data/demande_admin_models.dart';
import '../../demandes_admin/presentation/demandes_admin_list_screen.dart';
import '../../demandes_admin/presentation/ro_validation_screen.dart';
import '../../../features/auth/providers/collaborateur_notifier.dart';
import '../../../features/auth/providers/auth_notifier.dart';
import '../../evaluations/data/evaluation_repository.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  void initState() {
    super.initState();
    // Vérification faciale : GET /moi en arrière-plan, jamais bloquant pour l'accueil.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) ref.read(facialControllerProvider.notifier).demarrer();
    });
  }

  @override
  Widget build(BuildContext context) {
    // Consentement ou reconsentement imposé à la connexion (D-P07) : écran empilé sur l'accueil.
    ref.listen<FacialRoute>(
      facialControllerProvider.select((s) => s.decision.route),
      (prev, next) {
        if (next != FacialRoute.aucune && prev != next) {
          context.push('/facial/consentement');
        }
      },
    );
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        elevation: 0,
        title: Row(
          children: [
            Text(
              'RH Connect',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w700,
                color: AppTheme.textPrimary,
              ),
            ),
          ],
        ),
        actions: [
          const NotificationActionBadge(),
          // Profile menu
          PopupMenuButton<String>(
            offset: const Offset(0, 45),
            child: Container(
              padding: const EdgeInsets.all(2),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: AppTheme.border, width: 1.5),
              ),
              child: const CircleAvatar(
                radius: 18,
                backgroundColor: AppTheme.primarySurface,
                child: Text(
                  'U',
                  style: TextStyle(
                    color: AppTheme.primary,
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
            itemBuilder:
                (context) => [
                  PopupMenuItem(
                    value: 'profile',
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: AppTheme.primarySurface,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Icon(
                            Icons.person_outline,
                            size: 20,
                            color: AppTheme.primary,
                          ),
                        ),
                        const SizedBox(width: 12),
                        const Text(
                          'Mon profil',
                          style: TextStyle(fontWeight: FontWeight.w500),
                        ),
                      ],
                    ),
                  ),
                  PopupMenuItem(
                    value: 'settings',
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: AppTheme.surfaceMuted,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(
                            Icons.settings_outlined,
                            size: 20,
                            color: AppTheme.textTertiary,
                          ),
                        ),
                        const SizedBox(width: 12),
                        const Text(
                          'Paramètres',
                          style: TextStyle(fontWeight: FontWeight.w500),
                        ),
                      ],
                    ),
                  ),
                  const PopupMenuDivider(height: 1),
                  PopupMenuItem(
                    value: 'logout',
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: AppTheme.errorSurface,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(
                            Icons.logout,
                            size: 20,
                            color: AppTheme.error,
                          ),
                        ),
                        const SizedBox(width: 12),
                        const Text(
                          'Déconnexion',
                          style: TextStyle(
                            color: AppTheme.error,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
            onSelected: (value) {
              if (value == 'profile') {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const ProfileScreen()),
                );
              } else if (value == 'logout') {
                _handleLogout();
              }
            },
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: const _DashboardPage(),
    );
  }

  void _handleLogout() {
    ref.read(authNotifierProvider.notifier).signOut();
  }
}

// ── Bottom Navigation ────────────────────────────────────────────────────────

class _BottomNav extends ConsumerWidget {
  final int currentIndex;
  final ValueChanged<int> onTap;
  const _BottomNav({required this.currentIndex, required this.onTap});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // File M01 : JWT RO/RESPONSABLE (indice) OU demandes en attente (manager nœud).
    final userAsync = ref.watch(collaborateurNotifierProvider);
    final pendingCount = ref.watch(roDemandesEnAttenteProvider).maybeWhen(
          data: (l) => l.length,
          orElse: () => 0,
        );
    final peutValiderFile = userAsync.maybeWhen(
      data: (u) => u?.peutValiderFile == true,
      orElse: () => false,
    );
    final montreFile = peutValiderFile || pendingCount > 0;

    return Container(
      decoration: const BoxDecoration(
        color: AppTheme.surface,
        border: Border(top: BorderSide(color: AppTheme.border, width: 0.5)),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _NavItem(
                icon: Icons.home_outlined,
                activeIcon: Icons.home_rounded,
                label: 'Accueil',
                index: 0,
                current: currentIndex,
                onTap: onTap,
              ),
              _NavItem(
                icon: Icons.assignment_outlined,
                activeIcon: Icons.assignment_rounded,
                label: 'Demandes',
                index: 1,
                current: currentIndex,
                onTap: onTap,
                badge: montreFile ? pendingCount : 0,
              ),
              // QR FAB center
              GestureDetector(
                onTap: () => onTap(2),
                child: Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color:
                        currentIndex == 2
                            ? AppTheme.primary
                            : AppTheme.primary.withValues(alpha: 0.9),
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: AppTheme.primary.withValues(alpha: 0.35),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: const Icon(
                    Icons.qr_code_scanner_rounded,
                    color: Colors.white,
                    size: 24,
                  ),
                ),
              ),
              _NavItem(
                icon: Icons.article_outlined,
                activeIcon: Icons.article_rounded,
                label: 'Actu',
                index: 3,
                current: currentIndex,
                onTap: onTap,
              ),
              _NavItem(
                icon: Icons.person_outline,
                activeIcon: Icons.person_rounded,
                label: 'Moi',
                index: 4,
                current: currentIndex,
                onTap: onTap,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  final IconData icon;
  final IconData activeIcon;
  final String label;
  final int index;
  final int current;
  final ValueChanged<int> onTap;
  final int badge;

  const _NavItem({
    required this.icon,
    required this.activeIcon,
    required this.label,
    required this.index,
    required this.current,
    required this.onTap,
    this.badge = 0,
  });

  @override
  Widget build(BuildContext context) {
    final isActive = index == current;
    return GestureDetector(
      onTap: () => onTap(index),
      behavior: HitTestBehavior.opaque,
      child: SizedBox(
        width: 56,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              decoration: BoxDecoration(
                color: isActive ? AppTheme.primarySurface : Colors.transparent,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Icon(
                    isActive ? activeIcon : icon,
                    size: 22,
                    color:
                        isActive ? AppTheme.primary : AppTheme.textTertiary,
                  ),
                  if (badge > 0)
                    Positioned(
                      top: -6,
                      right: -8,
                      child: Container(
                        padding: const EdgeInsets.all(3),
                        constraints: const BoxConstraints(
                          minWidth: 16,
                          minHeight: 16,
                        ),
                        decoration: const BoxDecoration(
                          color: AppTheme.success,
                          shape: BoxShape.circle,
                        ),
                        child: Text(
                          badge > 9 ? '9+' : '$badge',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: isActive ? FontWeight.w600 : FontWeight.w400,
                color: isActive ? AppTheme.primary : AppTheme.textTertiary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Dashboard Page ────────────────────────────────────────────────────────────

class _DashboardPage extends ConsumerWidget {
  const _DashboardPage();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userAsync = ref.watch(collaborateurNotifierProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: CustomScrollView(
          slivers: [
            // ── App Bar ──
            SliverToBoxAdapter(
              child: Container(
                color: AppTheme.surface,
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 16),
                child: Row(
                  children: [
                    Expanded(
                      child: userAsync.when(
                        loading: () => const SizedBox.shrink(),
                        error: (_, __) => const SizedBox.shrink(),
                        data:
                            (user) => Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Bonjour 👋',
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  user != null
                                      ? '${user.prenom} ${user.name}'
                                      : 'Bienvenue',
                                  style: Theme.of(context).textTheme.titleLarge
                                      ?.copyWith(fontWeight: FontWeight.w700),
                                ),
                              ],
                            ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // ── Hero Banner ──
            SliverToBoxAdapter(
              child: Builder(builder: (context) {
              // SCRUM-21 : chiffres réels du collaborateur (demandes M01), plus de valeurs figées
              // ni de mention de paie (§ 1 / § 19.6 : la paie est hors périmètre du SIRH).
              final resume = ref.watch(demandesAdminListProvider).maybeWhen(
                    data: _ResumeMois.depuis,
                    orElse: () => const _ResumeMois('—', 'Chargement de vos demandes…'),
                  );
              return Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                child: Container(
                  constraints: const BoxConstraints(minHeight: 100),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [AppTheme.primaryDark, AppTheme.primaryLight],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  padding: const EdgeInsets.all(20),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              'Ce mois-ci',
                              style: Theme.of(
                                context,
                              ).textTheme.bodySmall?.copyWith(
                                color: Colors.white.withValues(alpha: 0.7),
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              resume.titre,
                              style: Theme.of(
                                context,
                              ).textTheme.titleMedium?.copyWith(
                                color: Colors.white,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              resume.detail,
                              style: Theme.of(
                                context,
                              ).textTheme.bodySmall?.copyWith(
                                color: Colors.white.withValues(alpha: 0.65),
                                fontSize: 11,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        width: 52,
                        height: 52,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: const Icon(
                          Icons.insert_chart_outlined_rounded,
                          color: Colors.white,
                          size: 28,
                        ),
                      ),
                    ],
                  ),
                ),
              );
              }),
            ),

            // ── Section title ──
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
                child: Text(
                  'Services',
                  style: Theme.of(
                    context,
                  ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                ),
              ),
            ),

            // ── File validation : JWT RO/RESPONSABLE ou file non vide (manager nœud) ──
            SliverToBoxAdapter(
              child: userAsync.when(
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
                data: (user) {
                  final pending = ref.watch(roDemandesEnAttenteProvider).maybeWhen(
                        data: (l) => l.length,
                        orElse: () => 0,
                      );
                  final montre = (user?.peutValiderFile == true) || pending > 0;
                  if (user == null || !montre) {
                    return const SizedBox.shrink();
                  }
                  final libelle = user.jwtRoles.libelleHabilitation ??
                      (pending > 0
                          ? 'Responsable d’unité'
                          : 'Responsable opérationnel');
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                    child: _RoBanner(
                      libelle: libelle,
                      isRo: user.isRo || (!user.isChefDept && pending > 0),
                    ),
                  );
                },
              ),
            ),

            // ── Services Grid ──
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              sliver: SliverGrid.count(
                crossAxisCount: 2,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                childAspectRatio: 1.45,
                children: [
                  _ServiceCard(
                    title: 'Pointage',
                    subtitle: 'Scanner QR',
                    icon: Icons.qr_code_scanner_rounded,
                    iconBg: AppTheme.primarySurface,
                    iconColor: AppTheme.primary,
                    onTap: () => context.push('/pointage'),
                  ),
                  ...userAsync.when(
                    data: (user) {
                      if (user?.peutConfigurerPresence != true) {
                        return const <Widget>[];
                      }
                      return [
                        _ServiceCard(
                          title: 'Sites pointage',
                          subtitle: 'GPS site',
                          icon: Icons.place_rounded,
                          iconBg: const Color(0xFFECFEFF),
                          iconColor: const Color(0xFF0891B2),
                          onTap: () => context.push('/presence/sites'),
                        ),
                      ];
                    },
                    loading: () => const <Widget>[],
                    error: (_, __) => const <Widget>[],
                  ),
                  _ServiceCard(
                    title: 'Congés',
                    subtitle: 'Demander',
                    icon: Icons.calendar_month_rounded,
                    iconBg: AppTheme.accentSurface,
                    iconColor: AppTheme.accent,
                    onTap: () => context.push('/demandes-admin'),
                  ),
                  _ServiceCard(
                    title: 'Documents',
                    subtitle: 'Attestations',
                    icon: Icons.description_rounded,
                    iconBg: AppTheme.successSurface,
                    iconColor: AppTheme.success,
                    onTap: () => context.push('/documents'),
                  ),
                  _ServiceCard(
                    title: 'Formations',
                    subtitle: 'Plan annuel',
                    icon: Icons.school_rounded,
                    iconBg: AppTheme.primarySurface,
                    iconColor: AppTheme.primary,
                    onTap: () => context.push('/formations'),
                  ),
                  _ServiceCard(
                    title: 'Plaintes',
                    subtitle: 'Déclarer',
                    icon: Icons.report_problem_rounded,
                    iconBg: AppTheme.errorSurface,
                    iconColor: AppTheme.error,
                    onTap: () => context.push('/plaintes'),
                  ),
                  // SCRUM-20 : carte masquée sans évaluation non archivée ; affichée en cas d'erreur
                  // réseau pour ne pas cacher une évaluation en cours.
                  if (ref.watch(evaluationsVisiblesProvider).maybeWhen(
                        data: (visible) => visible,
                        error: (_, __) => true,
                        orElse: () => false,
                      ))
                  _ServiceCard(
                    title: 'Évaluations',
                    subtitle: 'Performance',
                    icon: Icons.star_outline_rounded,
                    iconBg: AppTheme.warningSurface,
                    iconColor: AppTheme.warning,
                    badge: ref.watch(evaluationsBadgeCountProvider).maybeWhen(
                          data: (n) => n,
                          orElse: () => 0,
                        ),
                    onTap: () => context.push('/evaluations'),
                  ),
                  _ServiceCard(
                    title: 'Organigramme',
                    subtitle: 'Hiérarchie',
                    icon: Icons.account_tree_outlined,
                    iconBg: AppTheme.successSurface,
                    iconColor: AppTheme.success,
                    onTap: () => context.push('/organigramme'),
                  ),
                  _ServiceCard(
                    title: 'Autorisation',
                    subtitle: 'Sortie',
                    icon: Icons.vpn_key_rounded,
                    iconBg: AppTheme.primarySurface,
                    iconColor: AppTheme.primaryLight,
                    onTap:
                        () => context.push(
                          '/demandes-admin/autorisation/nouveau',
                        ),
                  ),
                  _ServiceCard(
                    title: 'Actualités',
                    subtitle: 'Lire',
                    icon: Icons.newspaper_rounded,
                    iconBg: AppTheme.primarySurface,
                    iconColor: AppTheme.primaryDark,
                    onTap: () => context.push('/feed'),
                  ),
                ],
              ),
            ),

            const SliverToBoxAdapter(child: SizedBox(height: 24)),
          ],
        ),
      ),
    );
  }
}

class _ServiceCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color iconBg;
  final Color iconColor;
  final VoidCallback onTap;
  final int badge;

  const _ServiceCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.iconBg,
    required this.iconColor,
    required this.onTap,
    this.badge = 0,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.border, width: 0.5),
          ),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: iconBg,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(icon, color: iconColor, size: 22),
                  ),
                  const Spacer(),
                  if (badge > 0)
                    Container(
                      constraints: const BoxConstraints(minWidth: 22, minHeight: 22),
                      padding: const EdgeInsets.symmetric(horizontal: 6),
                      decoration: BoxDecoration(
                        color: AppTheme.error,
                        borderRadius: BorderRadius.circular(11),
                      ),
                      alignment: Alignment.center,
                      child: Text(
                        badge > 9 ? '9+' : '$badge',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: Theme.of(context).textTheme.labelMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                      color: AppTheme.textPrimary,
                    ),
                  ),
                  Text(
                    subtitle,
                    style: Theme.of(
                      context,
                    ).textTheme.bodySmall?.copyWith(fontSize: 11),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Demandes Page ─────────────────────────────────────────────────────────────

class _DemandesPage extends ConsumerWidget {
  const _DemandesPage();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userAsync = ref.watch(collaborateurNotifierProvider);
    final pendingCount = ref.watch(roDemandesEnAttenteProvider).maybeWhen(
          data: (l) => l.length,
          orElse: () => 0,
        );
    final peutValiderFile = userAsync.maybeWhen(
      data: (u) => u?.peutValiderFile ?? false,
      orElse: () => false,
    );
    final montreFileValidation = peutValiderFile || pendingCount > 0;

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Mes Demandes'),
        backgroundColor: AppTheme.surface,
        actions: const [NotificationActionBadge()],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Section manager nœud : validation 1er niveau ────────
          if (montreFileValidation) ...[
            _SectionTitle(title: 'En tant que responsable d’unité'),
            const SizedBox(height: 8),
            const _RoValidationCard(),
            const SizedBox(height: 20),
            _SectionTitle(title: 'Mes demandes'),
            const SizedBox(height: 8),
          ],
          // ── Section collaborateur ─────────────────────────────────────────
          _DemandeChoiceCard(
            title: 'Congés',
            subtitle: 'Gérer vos absences et congés',
            icon: Icons.calendar_month_rounded,
            iconBg: AppTheme.accentSurface,
            iconColor: AppTheme.accent,
            onTap: () => context.push('/demandes-admin'),
          ),
          const SizedBox(height: 12),
          _DemandeChoiceCard(
            title: 'Autorisations de sortie',
            subtitle: 'Demander une permission (max 4h)',
            icon: Icons.vpn_key_rounded,
            iconBg: AppTheme.primarySurface,
            iconColor: AppTheme.primaryLight,
            onTap: () => context.push('/demandes-admin/autorisation/nouveau'),
          ),
          const SizedBox(height: 12),
          _DemandeChoiceCard(
            title: 'Documents',
            subtitle: 'Attestations, bulletins de paie…',
            icon: Icons.description_rounded,
            iconBg: AppTheme.successSurface,
            iconColor: AppTheme.success,
            onTap: () => context.push('/documents'),
          ),
          const SizedBox(height: 12),
          if (peutValiderFile) ...[
            _DemandeChoiceCard(
              title: 'Formations',
              subtitle: 'Unité ou collaborateurs cibles',
              icon: Icons.school_rounded,
              iconBg: AppTheme.primarySurface,
              iconColor: AppTheme.primary,
              onTap: () => context.push('/formations'),
            ),
            const SizedBox(height: 12),
          ],
          _DemandeChoiceCard(
            title: 'Plaintes',
            subtitle: 'Déclarer une réclamation',
            icon: Icons.report_problem_rounded,
            iconBg: AppTheme.errorSurface,
            iconColor: AppTheme.error,
            onTap: () => context.push('/plaintes'),
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String title;
  const _SectionTitle({required this.title});

  @override
  Widget build(BuildContext context) {
    return Text(
      title,
      style: const TextStyle(
        fontSize: 13,
        fontWeight: FontWeight.w700,
        color: AppTheme.textTertiary,
        letterSpacing: 0.3,
      ),
    );
  }
}

// ── RO Banner ────────────────────────────────────────────────────────────────

// ── RO Validation Card (in Demandes tab, with live badge count) ───────────────

class _RoValidationCard extends ConsumerWidget {
  const _RoValidationCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pendingAsync = ref.watch(roDemandesEnAttenteProvider);
    final count = pendingAsync.maybeWhen(
      data: (l) => l.length,
      orElse: () => 0,
    );

    return InkWell(
      onTap: () => context.push('/demandes-admin/ro/validation'),
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: const Color(0xFFBBF7D0)),
          boxShadow: [
            BoxShadow(
              color: AppTheme.success.withValues(alpha: 0.06),
              blurRadius: 10,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppTheme.success.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.how_to_reg_rounded,
                color: AppTheme.success,
                size: 22,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Demandes à valider',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 15,
                      color: AppTheme.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    count == 0
                        ? 'Aucune demande en attente'
                        : '$count demande${count > 1 ? 's' : ''} en attente de validation',
                    style: TextStyle(
                      fontSize: 12,
                      color:
                          count > 0
                              ? AppTheme.success
                              : AppTheme.textTertiary,
                      fontWeight:
                          count > 0 ? FontWeight.w600 : FontWeight.normal,
                    ),
                  ),
                ],
              ),
            ),
            if (count > 0)
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: AppTheme.success,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  '$count',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            const SizedBox(width: 8),
            const Icon(
              Icons.chevron_right_rounded,
              color: AppTheme.textTertiary,
              size: 20,
            ),
          ],
        ),
      ),
    );
  }
}

class _RoBanner extends StatelessWidget {
  final String libelle;
  final bool isRo;
  const _RoBanner({required this.libelle, required this.isRo});

  @override
  Widget build(BuildContext context) {
    final color = isRo ? const Color(0xFF0D9488) : AppTheme.primary;

    return InkWell(
      onTap: () => GoRouter.of(context).push('/demandes-admin/ro/validation'),
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [color.withValues(alpha: 0.08), color.withValues(alpha: 0.04)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: color.withValues(alpha: 0.25)),
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(Icons.how_to_reg_rounded, color: color, size: 20),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'File de validation',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                      color: color,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '$libelle — demandes de votre nœud à valider',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppTheme.textTertiary,
                    ),
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: color, size: 20),
          ],
        ),
      ),
    );
  }
}

class _DemandeChoiceCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color iconBg;
  final Color iconColor;
  final VoidCallback onTap;
  final String? badge;
  final Color? badgeColor;

  const _DemandeChoiceCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.iconBg,
    required this.iconColor,
    required this.onTap,
    this.badge,
    this.badgeColor,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.border, width: 0.5),
          ),
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: iconBg,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(icon, color: iconColor, size: 24),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const Icon(
                Icons.chevron_right_rounded,
                color: AppTheme.textSecondary,
                size: 20,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Encart « Ce mois-ci » de l'accueil, calculé depuis les demandes M01 du collaborateur (SCRUM-21).
class _ResumeMois {
  const _ResumeMois(this.titre, this.detail);

  final String titre;
  final String detail;

  static _ResumeMois depuis(List<DemandeAdminItem> demandes) {
    final now = DateTime.now();
    final debutMois = DateTime(now.year, now.month, 1);
    final finMois = DateTime(now.year, now.month + 1, 0);
    bool couvreLeMois(DemandeAdminItem d) {
      final debut = DateTime.tryParse(d.periodeDebut ?? '');
      final fin = DateTime.tryParse(d.periodeFin ?? d.periodeDebut ?? '');
      return debut != null && fin != null && !debut.isAfter(finMois) && !fin.isBefore(debutMois);
    }

    final absences = demandes
        .where((d) => d.statut == 'APPROUVEE' && d.typeDemande != 'ORDRE_MISSION' && couvreLeMois(d))
        .length;
    final enCours = demandes
        .where((d) => d.statut == 'EN_VALIDATION_SUPERIEUR' || d.statut == 'EN_VALIDATION_RRH' || d.statut == 'SOUMISE')
        .length;
    return _ResumeMois(
      absences == 0 ? 'Aucune absence approuvée' : '$absences absence${absences > 1 ? 's' : ''} approuvée${absences > 1 ? 's' : ''}',
      enCours == 0 ? 'Aucune demande en attente' : '$enCours demande${enCours > 1 ? 's' : ''} en cours de validation',
    );
  }
}
