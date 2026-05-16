import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../auth/presentation/profile_screen.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/notification_action.dart';
import '../../demandes_admin/presentation/ro_validation_screen.dart';
import '../../../features/auth/providers/collaborateur_notifier.dart';
import '../../../features/auth/providers/auth_notifier.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  Widget build(BuildContext context) {
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
                            color: const Color(0xFFF3F4F6),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(
                            Icons.settings_outlined,
                            size: 20,
                            color: Color(0xFF6B7280),
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
                            color: const Color(0xFFFFF1F2),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(
                            Icons.logout,
                            size: 20,
                            color: Colors.red,
                          ),
                        ),
                        const SizedBox(width: 12),
                        const Text(
                          'Déconnexion',
                          style: TextStyle(
                            color: Colors.red,
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
    // Show pending badge on Demandes tab for RO users
    final userAsync = ref.watch(collaborateurNotifierProvider);
    final isRo = userAsync.maybeWhen(
      data: (u) => u?.isRo == true || u?.isChefDept == true,
      orElse: () => false,
    );
    int pendingCount = 0;
    if (isRo) {
      pendingCount = ref
          .watch(roDemandesEnAttenteProvider)
          .maybeWhen(data: (l) => l.length, orElse: () => 0);
    }

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
                badge: pendingCount,
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
                            : AppTheme.primary.withOpacity(0.9),
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: AppTheme.primary.withOpacity(0.35),
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
                        isActive ? AppTheme.primary : const Color(0xFFADB5BD),
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
                          color: Color(0xFF16A34A),
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
                color: isActive ? AppTheme.primary : const Color(0xFFADB5BD),
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
                                      ? '${user.prenom} ${user.nom}'
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
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                child: Container(
                  constraints: const BoxConstraints(minHeight: 100),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF1E40AF), Color(0xFF3B82F6)],
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
                                color: Colors.white.withOpacity(0.7),
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '0 absence',
                              style: Theme.of(
                                context,
                              ).textTheme.titleMedium?.copyWith(
                                color: Colors.white,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Prochaine paie: fin du mois',
                              style: Theme.of(
                                context,
                              ).textTheme.bodySmall?.copyWith(
                                color: Colors.white.withOpacity(0.65),
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
                          color: Colors.white.withOpacity(0.15),
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
              ),
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

            // ── RO Banner (visible uniquement si profil RO ou RESPONSABLE) ──
            SliverToBoxAdapter(
              child: userAsync.when(
                loading: () => const SizedBox.shrink(),
                error: (_, __) => const SizedBox.shrink(),
                data: (user) {
                  final isResponsable = user != null && user.isResponsable;
                  if (!isResponsable) return const SizedBox.shrink();
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                    child: _RoBanner(profil: user!.profilAcces),
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
                childAspectRatio: 1.1,
                children: [
                  _ServiceCard(
                    title: 'Pointage',
                    subtitle: 'Scanner QR',
                    icon: Icons.qr_code_scanner_rounded,
                    iconBg: const Color(0xFFEEF2FF),
                    iconColor: AppTheme.primary,
                    onTap: () => context.push('/pointage'),
                  ),
                  _ServiceCard(
                    title: 'Congés',
                    subtitle: 'Demander',
                    icon: Icons.calendar_month_rounded,
                    iconBg: const Color(0xFFFFF7ED),
                    iconColor: const Color(0xFFEA580C),
                    onTap: () => context.push('/demandes-admin'),
                  ),
                  _ServiceCard(
                    title: 'Documents',
                    subtitle: 'Attestations',
                    icon: Icons.description_rounded,
                    iconBg: const Color(0xFFF0FDF4),
                    iconColor: const Color(0xFF16A34A),
                    onTap: () => context.push('/documents'),
                  ),
                  _ServiceCard(
                    title: 'Formations',
                    subtitle: 'Plan annuel',
                    icon: Icons.school_rounded,
                    iconBg: const Color(0xFFEFF6FF),
                    iconColor: const Color(0xFF2563EB),
                    onTap: () => context.push('/formations'),
                  ),
                  _ServiceCard(
                    title: 'Plaintes',
                    subtitle: 'Déclarer',
                    icon: Icons.report_problem_rounded,
                    iconBg: const Color(0xFFFFF1F2),
                    iconColor: const Color(0xFFE11D48),
                    onTap: () => context.push('/plaintes'),
                  ),
                  _ServiceCard(
                    title: 'Autorisation',
                    subtitle: 'Sortie',
                    icon: Icons.vpn_key_rounded,
                    iconBg: const Color(0xFFEFF6FF),
                    iconColor: const Color(0xFF3B82F6),
                    onTap:
                        () => context.push(
                          '/demandes-admin/autorisation/nouveau',
                        ),
                  ),
                  _ServiceCard(
                    title: 'Actualités',
                    subtitle: 'Lire',
                    icon: Icons.newspaper_rounded,
                    iconBg: const Color(0xFFF5F3FF),
                    iconColor: const Color(0xFF7C3AED),
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

  const _ServiceCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.iconBg,
    required this.iconColor,
    required this.onTap,
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
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: iconBg,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: iconColor, size: 22),
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
    final isResponsable = userAsync.maybeWhen(
      data: (u) => u?.isResponsable ?? false,
      orElse: () => false,
    );

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
          // ── Section RO : validation des demandes de l'unité ──────────────
          if (isResponsable) ...[
            _SectionTitle(title: 'En tant que responsable'),
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
            iconBg: const Color(0xFFFFF7ED),
            iconColor: const Color(0xFFEA580C),
            onTap: () => context.push('/demandes-admin'),
          ),
          const SizedBox(height: 12),
          _DemandeChoiceCard(
            title: 'Autorisations de sortie',
            subtitle: 'Demander une permission (max 4h)',
            icon: Icons.vpn_key_rounded,
            iconBg: const Color(0xFFEFF6FF),
            iconColor: const Color(0xFF3B82F6),
            onTap: () => context.push('/demandes-admin/autorisation/nouveau'),
          ),
          const SizedBox(height: 12),
          _DemandeChoiceCard(
            title: 'Documents',
            subtitle: 'Attestations, bulletins de paie…',
            icon: Icons.description_rounded,
            iconBg: const Color(0xFFF0FDF4),
            iconColor: const Color(0xFF16A34A),
            onTap: () => context.push('/documents'),
          ),
          const SizedBox(height: 12),
          if (isResponsable) ...[
            _DemandeChoiceCard(
              title: 'Formations',
              subtitle: 'Unite ou collaborateurs cibles',
              icon: Icons.school_rounded,
              iconBg: const Color(0xFFEFF6FF),
              iconColor: const Color(0xFF2563EB),
              onTap: () => context.push('/formations'),
            ),
            const SizedBox(height: 12),
          ],
          _DemandeChoiceCard(
            title: 'Plaintes',
            subtitle: 'Déclarer une réclamation',
            icon: Icons.report_problem_rounded,
            iconBg: const Color(0xFFFFF1F2),
            iconColor: const Color(0xFFE11D48),
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
        color: Color(0xFF64748B),
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
              color: const Color(0xFF16A34A).withOpacity(0.06),
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
                color: const Color(0xFF16A34A).withOpacity(0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.how_to_reg_rounded,
                color: Color(0xFF16A34A),
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
                      color: Color(0xFF1E293B),
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
                              ? const Color(0xFF16A34A)
                              : const Color(0xFF94A3B8),
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
                  color: const Color(0xFF16A34A),
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
              color: Color(0xFF94A3B8),
              size: 20,
            ),
          ],
        ),
      ),
    );
  }
}

class _RoBanner extends StatelessWidget {
  final String profil;
  const _RoBanner({required this.profil});

  @override
  Widget build(BuildContext context) {
    final isRo = profil == 'RO';
    final label = isRo ? 'Responsable Opérationnel' : 'Chef de Département';
    final color = isRo ? const Color(0xFF0D9488) : const Color(0xFF2563EB);

    return InkWell(
      onTap: () => GoRouter.of(context).push('/demandes-admin/ro/validation'),
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [color.withOpacity(0.08), color.withOpacity(0.04)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: color.withOpacity(0.25)),
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: color.withOpacity(0.12),
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
                    'Demandes à valider',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                      color: color,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Votre unité a des demandes en attente de votre validation',
                    style: const TextStyle(
                      fontSize: 12,
                      color: Color(0xFF64748B),
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
