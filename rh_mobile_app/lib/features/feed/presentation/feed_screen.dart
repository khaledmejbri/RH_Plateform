import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/notification_action.dart';
import '../providers/feed_provider.dart';
import '../data/post_model.dart';

class FeedScreen extends ConsumerWidget {
  const FeedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(feedProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Actualités RH'),
        actions: [
          IconButton(
            icon: const Icon(Icons.search_rounded),
            onPressed: () {},
          ),
          const NotificationActionBadge(),
        ],
      ),
      body: state.isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: () async {},
              color: AppTheme.primary,
              child: ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: state.posts.length,
                separatorBuilder: (_, __) => const SizedBox(height: 12),
                itemBuilder: (_, i) => PostCard(post: state.posts[i]),
              ),
            ),
    );
  }
}

class PostCard extends ConsumerWidget {
  final Post post;
  const PostCard({super.key, required this.post});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.border, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: const BoxDecoration(
                    color: AppTheme.primarySurface,
                    shape: BoxShape.circle,
                  ),
                  child: Center(
                    child: Text(
                      post.authorName.isNotEmpty ? post.authorName[0] : '?',
                      style: const TextStyle(
                          color: AppTheme.primary,
                          fontWeight: FontWeight.w700,
                          fontSize: 16),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(post.authorName,
                          style: const TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                              color: AppTheme.textPrimary)),
                      Text(
                        '${post.authorRole} · ${post.timeAgo}',
                        style: const TextStyle(
                            fontSize: 12, color: AppTheme.textSecondary),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.more_horiz_rounded,
                    color: AppTheme.textSecondary),
              ],
            ),
          ),

          // Content
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Text(
              post.content,
              style: const TextStyle(
                  fontSize: 14,
                  height: 1.5,
                  color: AppTheme.textPrimary),
            ),
          ),

          // Image
          if (post.imageUrl != null)
            ClipRRect(
              borderRadius:
                  const BorderRadius.vertical(bottom: Radius.circular(16)),
              child: Image.network(
                post.imageUrl!,
                width: double.infinity,
                height: 200,
                fit: BoxFit.cover,
              ),
            ),

          // Actions
          if (post.imageUrl == null)
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 0, 8, 8),
              child: Row(
                children: [
                  _ActionBtn(
                    icon: post.isLiked
                        ? Icons.favorite_rounded
                        : Icons.favorite_outline_rounded,
                    label: '${post.likes}',
                    color:
                        post.isLiked ? Colors.red : AppTheme.textSecondary,
                    onTap: () =>
                        ref.read(feedProvider.notifier).toggleLike(post.id),
                  ),
                  _ActionBtn(
                    icon: Icons.chat_bubble_outline_rounded,
                    label: '${post.commentCount}',
                    color: AppTheme.textSecondary,
                    onTap: () {},
                  ),
                  const Spacer(),
                  _ActionBtn(
                    icon: Icons.share_outlined,
                    label: 'Partager',
                    color: AppTheme.textSecondary,
                    onTap: () {},
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _ActionBtn extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _ActionBtn(
      {required this.icon,
      required this.label,
      required this.color,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        child: Row(
          children: [
            Icon(icon, size: 18, color: color),
            const SizedBox(width: 5),
            Text(label,
                style: TextStyle(
                    color: color,
                    fontWeight: FontWeight.w500,
                    fontSize: 13)),
          ],
        ),
      ),
    );
  }
}
