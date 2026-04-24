import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/post_model.dart';

class FeedState {
  final List<Post> posts;
  final bool isLoading;

  FeedState({this.posts = const [], this.isLoading = false});

  FeedState copyWith({List<Post>? posts, bool? isLoading}) {
    return FeedState(
      posts: posts ?? this.posts,
      isLoading: isLoading ?? this.isLoading,
    );
  }
}

class FeedNotifier extends StateNotifier<FeedState> {
  FeedNotifier() : super(FeedState()) {
    _loadInitialPosts();
  }

  void _loadInitialPosts() {
    state = state.copyWith(isLoading: true);
    // Mock data
    final mockPosts = [
      Post(
        id: '1',
        authorName: 'Direction RH',
        authorRole: 'Ressources Humaines',
        content: 'Nous sommes ravis d\'annoncer le lancement de notre nouveau programme de bien-être au travail ! 🌿 Profitez de séances de yoga tous les mardis à 17h.',
        createdAt: DateTime.now().subtract(const Duration(hours: 2)),
        likes: 24,
        commentCount: 5,
        isLiked: true,
      ),
      Post(
        id: '2',
        authorName: 'Jean Dupont',
        authorRole: 'Responsable Opérationnel',
        content: 'Félicitations à l\'équipe IT pour la réussite de la migration du week-end dernier. Beau travail collectif ! 🚀',
        imageUrl: 'https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=2070&auto=format&fit=crop',
        createdAt: DateTime.now().subtract(const Duration(hours: 5)),
        likes: 45,
        commentCount: 12,
      ),
      Post(
        id: '3',
        authorName: 'Équipe Communication',
        authorRole: 'Interne',
        content: 'N\'oubliez pas notre Afterwork ce jeudi à partir de 18h30. On vous attend nombreux ! 🍕🥤',
        createdAt: DateTime.now().subtract(const Duration(days: 1)),
        likes: 18,
        commentCount: 2,
      ),
    ];
    state = state.copyWith(posts: mockPosts, isLoading: false);
  }

  void toggleLike(String postId) {
    final updatedPosts = state.posts.map((post) {
      if (post.id == postId) {
        return Post(
          id: post.id,
          authorName: post.authorName,
          authorRole: post.authorRole,
          content: post.content,
          imageUrl: post.imageUrl,
          createdAt: post.createdAt,
          likes: post.isLiked ? post.likes - 1 : post.likes + 1,
          commentCount: post.commentCount,
          isLiked: !post.isLiked,
        );
      }
      return post;
    }).toList();
    state = state.copyWith(posts: updatedPosts);
  }
}

final feedProvider = StateNotifierProvider<FeedNotifier, FeedState>((ref) {
  return FeedNotifier();
});
