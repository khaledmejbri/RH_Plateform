import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../feed/presentation/feed_screen.dart';
import '../../pointage/presentation/pointage_screen.dart';
import '../../auth/presentation/profile_screen.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/notification_action.dart';
import '../../../features/auth/providers/collaborateur_notifier.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  int _currentIndex = 0;

  final List<Widget> _pages = [
    const _DashboardPage(),
    const _DemandesPage(),
    const PointageScreen(),
    const FeedScreen(),
    const ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _pages[_currentIndex],
      bottomNavigationBar: _BottomNav(
        currentIndex: _currentIndex,
        onTap: (i) => setState(() => _currentIndex = i),
      ),
    );
  }
}

// ── Bottom Navigation ────────────────────────────────────────────────────────

class _BottomNav extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onTap;
  const _BottomNav({required this.currentIndex, required this.onTap});

  @override
  Widget build(BuildContext context) {
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
              _NavItem(icon: Icons.home_outlined,      activeIcon: Icons.home_rounded,           label: 'Accueil',   index: 0, current: currentIndex, onTap: onTap),
              _NavItem(icon: Icons.assignment_outlined, activeIcon: Icons.assignment_rounded,      label: 'Demandes',  index: 1, current: currentIndex, onTap: onTap),
              // QR FAB center
              GestureDetector(
                onTap: () => onTap(2),
                child: Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color: currentIndex == 2 ? AppTheme.primary : AppTheme.primary.withOpacity(0.9),
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: AppTheme.primary.withOpacity(0.35),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: const Icon(Icons.qr_code_scanner_rounded, color: Colors.white, size: 24),
                ),
              ),
              _NavItem(icon: Icons.article_outlined,   activeIcon: Icons.article_rounded,         label: 'Actu',      index: 3, current: currentIndex, onTap: onTap),
              _NavItem(icon: Icons.person_outline,     activeIcon: Icons.person_rounded,          label: 'Moi',       index: 4, current: currentIndex, onTap: onTap),
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

  const _NavItem({
    required this.icon, required this.activeIcon, required this.label,
    required this.index, required this.current, required this.onTap,
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
              child: Icon(
                isActive ? activeIcon : icon,
                size: 22,
                color: isActive ? AppTheme.primary : const Color(0xFFADB5BD),
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
                padding: const EdgeInsets.fromLTRB(20, 16, 16, 16),
                child: Row(
                  children: [
                    Expanded(
                      child: userAsync.when(
                        loading: () => const SizedBox.shrink(),
                        error: (_, __) => const SizedBox.shrink(),
                        data: (user) => Column(
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
                              style: Theme.of(context)
                                  .textTheme
                                  .titleLarge
                                  ?.copyWith(fontWeight: FontWeight.w700),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const NotificationActionBadge(),
                  ],
                ),
              ),
            ),

            // ── Hero Banner ──
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                child: Container(
                  height: 100,
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
                              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                    color: Colors.white.withOpacity(0.7),
                                  ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '0 absence',
                              style: Theme.of(context)
                                  .textTheme
                                  .titleMedium
                                  ?.copyWith(
                                    color: Colors.white,
                                    fontWeight: FontWeight.w700,
                                  ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Prochaine paie: fin du mois',
                              style: Theme.of(context).textTheme.bodySmall?.copyWith(
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
                        child: const Icon(Icons.insert_chart_outlined_rounded,
                            color: Colors.white, size: 28),
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
                  style: Theme.of(context)
                      .textTheme
                      .titleSmall
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
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
                    onTap: () {
                      final state = context.findAncestorStateOfType<_HomeScreenState>();
                      state?.setState(() => state._currentIndex = 2);
                    },
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
                    onTap: () => context.push('/demandes-admin/autorisation/nouveau'),
                  ),
                  _ServiceCard(
                    title: 'Actualités',
                    subtitle: 'Lire',
                    icon: Icons.newspaper_rounded,
                    iconBg: const Color(0xFFF5F3FF),
                    iconColor: const Color(0xFF7C3AED),
                    onTap: () {
                      final state = context.findAncestorStateOfType<_HomeScreenState>();
                      state?.setState(() => state._currentIndex = 3);
                    },
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
    required this.title, required this.subtitle, required this.icon,
    required this.iconBg, required this.iconColor, required this.onTap,
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
                  Text(title,
                      style: Theme.of(context).textTheme.labelMedium?.copyWith(
                            fontWeight: FontWeight.w600,
                            color: AppTheme.textPrimary,
                          )),
                  Text(subtitle,
                      style: Theme.of(context)
                          .textTheme
                          .bodySmall
                          ?.copyWith(fontSize: 11)),
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

class _DemandesPage extends StatelessWidget {
  const _DemandesPage();

  @override
  Widget build(BuildContext context) {
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
            subtitle: 'Demander une permission',
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

class _DemandeChoiceCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color iconBg;
  final Color iconColor;
  final VoidCallback onTap;

  const _DemandeChoiceCard({
    required this.title, required this.subtitle, required this.icon,
    required this.iconBg, required this.iconColor, required this.onTap,
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
                    Text(title,
                        style: Theme.of(context)
                            .textTheme
                            .titleSmall
                            ?.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 2),
                    Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded,
                  color: AppTheme.textSecondary, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}
