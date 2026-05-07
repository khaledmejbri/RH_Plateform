import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../feed/presentation/feed_screen.dart';
import '../../pointage/presentation/pointage_screen.dart';
import '../../notifications/notifications_screen.dart';
import 'package:rh_mobile_app/features/demandes_admin/presentation/demandes_admin_list_screen.dart';
import 'package:rh_mobile_app/features/documents/presentation/documents_list_screen.dart';
import 'package:rh_mobile_app/features/auth/presentation/profile_screen.dart';
import '../../../core/widgets/notification_action.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  int _currentIndex = 0;
  
  // Dashboard is first instead of Feed
  final List<Widget> _pages = [
    const _DashboardScreen(),
    const _DemandesChoiceScreen(),
    const PointageScreen(),
    const FeedScreen(),
    const ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _pages[_currentIndex],
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.05),
              blurRadius: 10,
              offset: const Offset(0, -5),
            ),
          ],
        ),
        child: BottomNavigationBar(
          currentIndex: _currentIndex,
          onTap: (index) => setState(() => _currentIndex = index),
          type: BottomNavigationBarType.fixed,
          backgroundColor: Colors.white,
          selectedItemColor: const Color(0xFF2563EB),
          unselectedItemColor: Colors.grey.shade400,
          selectedLabelStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12),
          unselectedLabelStyle: const TextStyle(fontWeight: FontWeight.w500, fontSize: 12),
          items: const [
            BottomNavigationBarItem(
              icon: Icon(Icons.dashboard_outlined),
              activeIcon: Icon(Icons.dashboard_rounded),
              label: 'Accueil',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.assignment_outlined),
              activeIcon: Icon(Icons.assignment_rounded),
              label: 'Demandes',
            ),
            BottomNavigationBarItem(
              icon: CircleAvatar(
                radius: 24,
                backgroundColor: Color(0xFF2563EB),
                child: Icon(Icons.qr_code_scanner, color: Colors.white),
              ),
              label: '',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.article_outlined),
              activeIcon: Icon(Icons.article_rounded),
              label: 'Actualités',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.person_outline),
              activeIcon: Icon(Icons.person_rounded),
              label: 'Moi',
            ),
          ],
        ),
      ),
    );
  }
}

class _DashboardScreen extends ConsumerWidget {
  const _DashboardScreen();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Tableau de bord', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        centerTitle: true,
        actions: const [NotificationActionBadge()],
      ),
      body: GridView.count(
        padding: const EdgeInsets.all(24),
        crossAxisCount: 2,
        mainAxisSpacing: 16,
        crossAxisSpacing: 16,
        children: [
          _ServiceCard(
            title: 'Pointage',
            icon: Icons.qr_code_scanner_rounded,
            color: const Color(0xFF2563EB),
            onTap: () {
              // Get the parent HomeScreen to switch to Pointage tab
              final state = context.findAncestorStateOfType<_HomeScreenState>();
              state?.setState(() => state._currentIndex = 2);
            },
          ),
          _ServiceCard(
            title: 'Congés',
            icon: Icons.calendar_month_rounded,
            color: Colors.orange,
            onTap: () => context.push('/demandes-admin'),
          ),
          _ServiceCard(
            title: 'Autorisations',
            icon: Icons.access_time_rounded,
            color: Colors.teal,
            onTap: () => context.push('/demandes-admin/autorisation/nouveau'),
          ),
          _ServiceCard(
            title: 'Documents',
            icon: Icons.description_rounded,
            color: Colors.blue,
            onTap: () => context.push('/documents'),
          ),
          _ServiceCard(
            title: 'Plaintes',
            icon: Icons.report_problem_rounded,
            color: Colors.red,
            onTap: () => context.push('/plaintes'),
          ),
          _ServiceCard(
            title: 'Actualités',
            icon: Icons.article_rounded,
            color: Colors.purple,
            onTap: () {
              // Switch to Actualités tab
              final state = context.findAncestorStateOfType<_HomeScreenState>();
              state?.setState(() => state._currentIndex = 3);
            },
          ),
        ],
      ),
    );
  }
}

class _DemandesChoiceScreen extends StatelessWidget {
  const _DemandesChoiceScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Mes Demandes', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        centerTitle: true,
        actions: const [NotificationActionBadge()],
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          _ChoiceCard(
            title: 'Congés',
            subtitle: 'Gérer vos absences et congés',
            icon: Icons.calendar_month_rounded,
            color: Colors.orange,
            onTap: () => context.push('/demandes-admin'),
          ),
          const SizedBox(height: 16),
          _ChoiceCard(
            title: 'Autorisations',
            subtitle: 'Demander des autorisations de sortie',
            icon: Icons.access_time_rounded,
            color: Colors.teal,
            onTap: () => context.push('/demandes-admin/autorisation/nouveau'),
          ),
          const SizedBox(height: 16),
          _ChoiceCard(
            title: 'Documents',
            subtitle: 'Attestations, bulletins de paie...',
            icon: Icons.description_rounded,
            color: Colors.blue,
            onTap: () => context.push('/documents'),
          ),
          const SizedBox(height: 16),
          _ChoiceCard(
            title: 'Plaintes',
            subtitle: 'Déclarer une plainte ou réclamation',
            icon: Icons.report_problem_rounded,
            color: Colors.red,
            onTap: () => context.push('/plaintes'),
          ),
        ],
      ),
    );
  }
}

class _ChoiceCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  const _ChoiceCard({required this.title, required this.subtitle, required this.icon, required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: color, size: 28),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
                  Text(subtitle, style: const TextStyle(color: Colors.grey, fontSize: 13)),
                ],
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: Colors.grey.shade300),
          ],
        ),
      ),
    );
  }
}

class _ServiceCard extends StatelessWidget {
  final String title;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  const _ServiceCard({required this.title, required this.icon, required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(24),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: color, size: 32),
            ),
            const SizedBox(height: 12),
            Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
          ],
        ),
      ),
    );
  }
}

