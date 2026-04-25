import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/notification_action.dart';
import '../providers/collaborateur_notifier.dart';
import '../providers/auth_notifier.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userAsync = ref.watch(collaborateurNotifierProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: userAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: AppTheme.error),
              const SizedBox(height: 16),
              Text('Erreur: $e'),
              TextButton(
                onPressed: () =>
                    ref.read(collaborateurNotifierProvider.notifier).fetchMoi(),
                child: const Text('Réessayer'),
              ),
            ],
          ),
        ),
        data: (user) {
          if (user == null) {
            return const Center(child: Text('Aucune donnée utilisateur'));
          }

          final initials =
              '${user.prenom.isNotEmpty ? user.prenom[0] : ""}${user.nom.isNotEmpty ? user.nom[0] : ""}';

          return CustomScrollView(
            slivers: [
              // ── Header ──
              SliverToBoxAdapter(
                child: Container(
                  width: double.infinity,
                  decoration: const BoxDecoration(
                    color: AppTheme.primary,
                    borderRadius: BorderRadius.only(
                      bottomLeft: Radius.circular(28),
                      bottomRight: Radius.circular(28),
                    ),
                  ),
                  child: SafeArea(
                    bottom: false,
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 16, 32),
                      child: Column(
                        children: [
                          // AppBar row
                          Row(
                            children: [
                              Text(
                                'Mon Profil',
                                style: Theme.of(context)
                                    .textTheme
                                    .titleMedium
                                    ?.copyWith(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w700,
                                    ),
                              ),
                              const Spacer(),
                              const NotificationActionBadge(),
                            ],
                          ),
                          const SizedBox(height: 24),
                          // Avatar
                          Stack(
                            children: [
                              Container(
                                width: 80,
                                height: 80,
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  shape: BoxShape.circle,
                                  border: Border.all(
                                      color: Colors.white.withOpacity(0.3),
                                      width: 3),
                                ),
                                child: Center(
                                  child: Text(
                                    initials.toUpperCase(),
                                    style: const TextStyle(
                                      fontSize: 28,
                                      fontWeight: FontWeight.w700,
                                      color: AppTheme.primary,
                                    ),
                                  ),
                                ),
                              ),
                              Positioned(
                                bottom: 0,
                                right: 0,
                                child: Container(
                                  width: 26,
                                  height: 26,
                                  decoration: BoxDecoration(
                                    color: const Color(0xFF3B82F6),
                                    shape: BoxShape.circle,
                                    border: Border.all(
                                        color: Colors.white, width: 2),
                                  ),
                                  child: const Icon(Icons.edit_rounded,
                                      size: 13, color: Colors.white),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Text(
                            '${user.prenom} ${user.nom}',
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                              color: Colors.white,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            user.email ?? 'Email non renseigné',
                            style: TextStyle(
                              fontSize: 13,
                              color: Colors.white.withOpacity(0.65),
                            ),
                          ),
                          const SizedBox(height: 20),
                          // Stats row
                          Container(
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.12),
                              borderRadius: BorderRadius.circular(16),
                            ),
                            padding: const EdgeInsets.symmetric(
                                vertical: 14, horizontal: 20),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.spaceAround,
                              children: [
                                _StatItem(value: '12', label: 'Jours\ncongés'),
                                Container(
                                    width: 0.5,
                                    height: 36,
                                    color: Colors.white.withOpacity(0.3)),
                                _StatItem(value: '4', label: 'Demandes\nen cours'),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),

              // ── Menu ──
              SliverPadding(
                padding: const EdgeInsets.all(16),
                sliver: SliverList(
                  delegate: SliverChildListDelegate([
                    const SizedBox(height: 8),
                    _MenuSection(items: [
                      _MenuItem(
                          icon: Icons.person_outline_rounded,
                          label: 'Informations personnelles',
                          onTap: () {}),
                      _MenuItem(
                          icon: Icons.lock_outline_rounded,
                          label: 'Sécurité',
                          onTap: () {}),
                      _MenuItem(
                          icon: Icons.notifications_none_rounded,
                          label: 'Notifications',
                          onTap: () {}),
                    ]),
                    const SizedBox(height: 12),
                    _MenuSection(items: [
                      _MenuItem(
                          icon: Icons.help_outline_rounded,
                          label: 'Aide & Support',
                          onTap: () {}),
                    ]),
                    const SizedBox(height: 24),
                    // Logout
                    Material(
                      color: const Color(0xFFFFF1F2),
                      borderRadius: BorderRadius.circular(14),
                      child: InkWell(
                        onTap: () =>
                            ref.read(authNotifierProvider.notifier).signOut(),
                        borderRadius: BorderRadius.circular(14),
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          alignment: Alignment.center,
                          child: const Text(
                            'Se déconnecter',
                            style: TextStyle(
                              color: AppTheme.error,
                              fontWeight: FontWeight.w600,
                              fontSize: 15,
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),
                  ]),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _StatItem extends StatelessWidget {
  final String value;
  final String label;
  const _StatItem({required this.value, required this.label});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(value,
            style: const TextStyle(
                fontSize: 22, fontWeight: FontWeight.w800, color: Colors.white)),
        const SizedBox(height: 2),
        Text(label,
            textAlign: TextAlign.center,
            style:
                TextStyle(fontSize: 11, color: Colors.white.withOpacity(0.65))),
      ],
    );
  }
}

class _MenuSection extends StatelessWidget {
  final List<_MenuItem> items;
  const _MenuSection({required this.items});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.border, width: 0.5),
      ),
      child: Column(
        children: items.asMap().entries.map((e) {
          final isLast = e.key == items.length - 1;
          return Column(
            children: [
              e.value,
              if (!isLast)
                const Divider(height: 0, indent: 56),
            ],
          );
        }).toList(),
      ),
    );
  }
}

class _MenuItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _MenuItem({required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: AppTheme.primarySurface,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(icon, color: AppTheme.primary, size: 18),
            ),
            const SizedBox(width: 14),
            Expanded(
                child: Text(label,
                    style: Theme.of(context)
                        .textTheme
                        .bodyMedium
                        ?.copyWith(color: AppTheme.textPrimary, fontWeight: FontWeight.w500))),
            const Icon(Icons.chevron_right_rounded,
                color: AppTheme.textSecondary, size: 18),
          ],
        ),
      ),
    );
  }
}
