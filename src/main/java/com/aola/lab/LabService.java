package com.aola.lab;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import com.aola.lab.Model.Comment;
import com.aola.lab.Model.Friendship;
import com.aola.lab.Model.FriendshipStatus;
import com.aola.lab.Model.Kind;
import com.aola.lab.Model.Post;
import com.aola.lab.Model.PostLike;
import com.aola.lab.Model.Review;
import com.aola.lab.Model.Tag;
import com.aola.lab.Model.User;
import com.aola.lab.Model.Visibility;
import com.aola.lab.Repositories.Comments;
import com.aola.lab.Repositories.Friendships;
import com.aola.lab.Repositories.PostLikes;
import com.aola.lab.Repositories.Posts;
import com.aola.lab.Repositories.Tags;
import com.aola.lab.Repositories.Users;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LabService {
    private final Users users;
    private final Friendships friendships;
    private final Posts posts;
    private final Tags tags;
    private final Comments comments;
    private final PostLikes postLikes;
    private final PasswordEncoder encoder;
    private final FileStore files;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LabService.class);

    public LabService(Users users, Friendships friendships, Posts posts, Tags tags, Comments comments,
            PostLikes postLikes, PasswordEncoder encoder, FileStore files) {
        this.users = users;
        this.friendships = friendships;
        this.posts = posts;
        this.tags = tags;
        this.comments = comments;
        this.postLikes = postLikes;
        this.encoder = encoder;
        this.files = files;
    }

    public User user(String name) {
        return users.findByUsername(name).orElseThrow(() -> new AccessDeniedException("用户不存在"));
    }

    public void register(String username, String password) {
        if (SecurityConfig.ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("该用户名为内置管理员账号，不能公开注册");
        }
        if (username == null || !username.matches("[A-Za-z0-9_\\u4e00-\\u9fff]{3,40}") || password == null
                || password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("用户名需为3至40位汉字、字母、数字或下划线，密码至少8位且不超过72字节");
        }
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setAdmin(false);
        users.saveAndFlush(user);
    }

    public void updateProfile(User user, String username, String signature) {
        String normalized = username == null ? "" : username.trim();
        if (!normalized.matches("[A-Za-z0-9_\\u4e00-\\u9fff]{3,40}")) {
            throw new IllegalArgumentException("用户名需为3至40位汉字、字母、数字或下划线");
        }
        if (user.isAdmin() && SecurityConfig.ADMIN_USERNAME.equalsIgnoreCase(user.getUsername())
                && !SecurityConfig.ADMIN_USERNAME.equals(normalized)) {
            throw new IllegalArgumentException("内置管理员账号不能更改用户名");
        }
        if (users.findByUsername(normalized).filter(existing -> !existing.getId().equals(user.getId())).isPresent()
                || SecurityConfig.ADMIN_USERNAME.equalsIgnoreCase(normalized)
                        && !SecurityConfig.ADMIN_USERNAME.equals(user.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (signature != null && signature.length() > 200) {
            throw new IllegalArgumentException("个人签名不能超过200字");
        }
        if (!user.getUsername().equals(normalized)) {
            user.setSecurityVersion(user.getSecurityVersion() + 1);
        }
        user.setUsername(normalized);
        user.setSignature(signature == null || signature.isBlank() ? null : signature.trim());
        users.saveAndFlush(user);
    }

    public void changePassword(User user, String oldPassword, String newPassword) {
        if (oldPassword == null || !encoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码不正确");
        }
        if (newPassword == null || newPassword.length() < 8
                || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("新密码至少8位且不超过72字节");
        }
        user.setPassword(encoder.encode(newPassword));
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        users.saveAndFlush(user);
    }

    private Optional<Friendship> relation(User a, User b) {
        return a.getId() < b.getId() ? friendships.findByLowUserAndHighUser(a, b)
                : friendships.findByLowUserAndHighUser(b, a);
    }

    public boolean friends(User a, User b) {
        return a != null && b != null && !a.getId().equals(b.getId())
                && relation(a, b).map(f -> f.getStatus() == FriendshipStatus.ACCEPTED).orElse(false);
    }

    public void requestFriend(User requester, String targetName) {
        User target = users.findByUsername(targetName).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (target.getId().equals(requester.getId())) {
            throw new IllegalArgumentException("不能添加自己");
        }
        if (relation(requester, target).isPresent()) {
            throw new IllegalArgumentException("好友关系或申请已存在");
        }
        Friendship f = new Friendship();
        f.setLowUser(requester.getId() < target.getId() ? requester : target);
        f.setHighUser(requester.getId() < target.getId() ? target : requester);
        f.setRequester(requester);
        friendships.save(f);
    }

    public void acceptFriend(User user, Long id) {
        Friendship f = friendships.findById(id).orElseThrow();
        if (f.getStatus() != FriendshipStatus.PENDING || f.getRequester().getId().equals(user.getId())
                || !(f.getLowUser().getId().equals(user.getId()) || f.getHighUser().getId().equals(user.getId()))) {
            throw new AccessDeniedException("无权接受申请");
        }
        f.setStatus(FriendshipStatus.ACCEPTED);
    }

    public boolean canView(User user, Post post) {
        if (user.isAdmin() || post.getAuthor().getId().equals(user.getId())) {
            return true;
        }
        return post.getReview() == Review.APPROVED && (post.getVisibility() == Visibility.PUBLIC
                || post.getVisibility() == Visibility.FRIENDS && friends(user, post.getAuthor()));
    }

    public Post visiblePost(User user, Long id) {
        Post p = posts.findById(id).orElseThrow();
        if (!canView(user, p)) {
            throw new AccessDeniedException("无权访问");
        }
        return p;
    }

    public Post ownedPost(User user, Long id) {
        Post p = posts.findById(id).orElseThrow();
        if (!p.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("无权修改");
        }
        return p;
    }

    public void savePost(Post p, User author, Kind kind, String title, String body, Visibility visibility,
            List<Long> tagIds) {
        if (title == null || title.isBlank() || title.length() > 150 || body != null && body.length() > 10000
                || visibility == null) {
            throw new IllegalArgumentException("标题、内容或可见范围无效");
        }
        if (p.getId() != null && p.getKind() != kind) {
            throw new IllegalArgumentException("内容类型无效");
        }
        p.setAuthor(author);
        p.setKind(kind);
        p.setTitle(title.trim());
        p.setBody(body);
        p.setVisibility(visibility);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (p.getId() == null) {
            p.setCreatedAt(now);
        }
        p.setUpdatedAt(now);
        p.setReview(Review.PENDING);
        p.setRejectionReason(null);
        List<Long> ids = tagIds == null ? List.of() : tagIds.stream().distinct().toList();
        List<Tag> selected = tags.findAllById(ids);
        if (selected.size() != ids.size()) {
            throw new IllegalArgumentException("标签不存在");
        }
        p.setTags(new HashSet<>(selected));
        if (kind == Kind.VIDEO && p.getFileKey() == null) {
            throw new IllegalArgumentException("请上传视频");
        }
        posts.save(p);
    }

    public void comment(User user, Long postId, Long parentId, String body) {
        Post p = posts.findByIdForUpdate(postId).orElseThrow();
        if (!canView(user, p)) {
            throw new AccessDeniedException("无权访问");
        }
        if (p.getReview() != Review.APPROVED) {
            throw new IllegalArgumentException("仅已审核内容可评论");
        }
        if (body == null || body.isBlank() || body.length() > 1000) {
            throw new IllegalArgumentException("评论须为1至1000字");
        }
        Comment parent = null;
        if (parentId != null) {
            parent = comments.findById(parentId).orElseThrow(() -> new IllegalArgumentException("要回复的评论不存在"));
            if (!parent.getPost().getId().equals(p.getId()) || parent.getParent() != null) {
                throw new IllegalArgumentException("只能回复当前作品的一级评论");
            }
        }
        Comment c = new Comment();
        c.setPost(p);
        c.setAuthor(user);
        c.setParent(parent);
        c.setBody(body.trim());
        comments.save(c);
    }

    public boolean toggleLike(User user, Long postId) {
        Post post = posts.findByIdForUpdate(postId).orElseThrow();
        if (!canView(user, post)) {
            throw new AccessDeniedException("无权访问");
        }
        if (post.getReview() != Review.APPROVED) {
            throw new IllegalArgumentException("仅已审核内容可点赞");
        }
        Optional<PostLike> existing = postLikes.findByPostAndUser(post, user);
        if (existing.isPresent()) {
            postLikes.delete(existing.get());
            return false;
        }
        PostLike like = new PostLike();
        like.setPost(post);
        like.setUser(user);
        postLikes.save(like);
        return true;
    }

    public Kind deletePost(User user, Long id) {
        Post post = posts.findByIdForUpdate(id).orElseThrow();
        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("无权修改");
        }
        var keys = new java.util.LinkedHashSet<String>(post.getImageKeys());
        if (post.getFileKey() != null) {
            keys.add(post.getFileKey());
        }
        postLikes.deleteForPost(id);
        comments.deleteRepliesForPost(id);
        comments.deleteRootsForPost(id);
        Kind kind = post.getKind();
        posts.delete(post);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (String key : keys) {
                            try {
                                files.delete(key);
                            } catch (RuntimeException | java.io.IOException e) {
                                log.warn("清理已删除作品的文件失败: {}", key, e);
                            }
                        }
                    }
                });
        return kind;
    }

    public void incrementView(Post post, User viewer) {
        if (!post.getAuthor().getId().equals(viewer.getId())) {
            post.setViewCount(post.getViewCount() + 1);
            posts.save(post);
        }
    }

    public void review(Long id, boolean approve, String reason) {
        Post p = posts.findById(id).orElseThrow();
        if (p.getReview() != Review.PENDING) {
            throw new IllegalArgumentException("只能审核待审核内容");
        }
        if (!approve && (reason == null || reason.isBlank() || reason.length() > 500)) {
            throw new IllegalArgumentException("驳回原因须为1至500字");
        }
        p.setReview(approve ? Review.APPROVED : Review.REJECTED);
        p.setRejectionReason(approve ? null : reason.trim());
    }
}
