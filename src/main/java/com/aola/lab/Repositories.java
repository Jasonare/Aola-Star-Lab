package com.aola.lab;

import java.util.List;
import java.util.Optional;

import com.aola.lab.Model.Comment;
import com.aola.lab.Model.Folder;
import com.aola.lab.Model.Friendship;
import com.aola.lab.Model.Kind;
import com.aola.lab.Model.LoginLog;
import com.aola.lab.Model.Post;
import com.aola.lab.Model.ResourceFile;
import com.aola.lab.Model.Tag;
import com.aola.lab.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
        List<Post> findAllByOrderByCreatedAtDesc();

        long countByAuthorAndKind(User user, Kind kind);
    }

    public interface Comments extends JpaRepository<Comment, Long> {
        List<Comment> findByPostOrderByCreatedAtDesc(Post post);

        List<Comment> findTop5ByPostAuthorOrderByCreatedAtDesc(User author);
    }

    public interface Folders extends JpaRepository<Folder, Long> {
        boolean existsByName(String name);
    }

    public interface Resources extends JpaRepository<ResourceFile, Long> {
        List<ResourceFile> findByVisibleTrue();
    }
}
