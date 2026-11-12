package com.aola.lab;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import com.aola.lab.Model.Comment;
import com.aola.lab.Model.Folder;
import com.aola.lab.Model.Friendship;
import com.aola.lab.Model.Kind;
import com.aola.lab.Model.LoginLog;
import com.aola.lab.Model.Post;
import com.aola.lab.Model.PostLike;
import com.aola.lab.Model.ResourceFile;
import com.aola.lab.Model.Tag;
import com.aola.lab.Model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public class Repositories {
    public interface Users extends JpaRepository<User, Long> {
        Optional<User> findByUsername(String username);

        boolean existsByUsername(String username);
    }

    public interface Logs extends JpaRepository<LoginLog, Long> {
        List<LoginLog> findTop5ByUserOrderByLoggedAtDescIdDesc(User user);
    }

    public interface Friendships extends JpaRepository<Friendship, Long> {
        Optional<Friendship> findByLowUserAndHighUser(User low, User high);

        List<Friendship> findByLowUserOrHighUser(User low, User high);
    }

    public interface Tags extends JpaRepository<Tag, Long> {
        boolean existsByName(String name);
    }

    public interface Posts extends JpaRepository<Post, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select p from Post p where p.id = :id")
        Optional<Post> findByIdForUpdate(@Param("id") Long id);

        List<Post> findAllByOrderByUpdatedAtDesc();

        List<Post> findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(
                LocalDateTime from, LocalDateTime until);

        List<Post> findByUpdatedAtGreaterThanEqualOrderByUpdatedAtDesc(LocalDateTime from);

        List<Post> findByUpdatedAtLessThanOrderByUpdatedAtDesc(LocalDateTime until);

        List<Post> findByAuthorAndKindOrderByUpdatedAtDesc(User user, Kind kind);

        long countByAuthorAndKind(User user, Kind kind);

    }

    public interface PostLikes extends JpaRepository<PostLike, Long> {
        @Modifying
        @Query("delete from PostLike l where l.post.id = :postId")
        void deleteForPost(@Param("postId") Long postId);

        boolean existsByPostAndUser(Post post, User user);

        long countByPost(Post post);

        long countByPostAuthor(User author);

        Optional<PostLike> findByPostAndUser(Post post, User user);
    }

    public interface Comments extends JpaRepository<Comment, Long> {
        @Modifying
        @Query("delete from Comment c where c.post.id = :postId and c.parent is not null")
        void deleteRepliesForPost(@Param("postId") Long postId);

        @Modifying
        @Query("delete from Comment c where c.post.id = :postId and c.parent is null")
        void deleteRootsForPost(@Param("postId") Long postId);

        List<Comment> findByPostAndParentIsNullOrderByCreatedAtDesc(Post post);

        List<Comment> findByParentOrderByCreatedAtAsc(Comment parent);

        List<Comment> findTop5ByPostAuthorOrderByCreatedAtDesc(User author);

        long countByPost(Post post);

        long countByPostAuthor(User author);
    }

    public interface Folders extends JpaRepository<Folder, Long> {
        boolean existsByName(String name);

        boolean existsByNameAndIdNot(String name, Long id);

        List<Folder> findByParentIsNullOrderByNameAsc();

        List<Folder> findByParentOrderByNameAsc(Folder parent);

        List<Folder> findByParentIsNullOrParentOrderByNameAsc(Folder parent);

        boolean existsByParent(Folder parent);
    }

    public interface Resources extends JpaRepository<ResourceFile, Long> {
        List<ResourceFile> findByVisibleTrue();

        boolean existsByFolder(Folder folder);
    }
}
