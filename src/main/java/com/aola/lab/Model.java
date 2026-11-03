package com.aola.lab;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

public class Model {
    public enum Visibility {
        PUBLIC, FRIENDS, PRIVATE
    }

    public enum Review {
        PENDING, APPROVED, REJECTED
    }

    public enum Kind {
        BLOG, VIDEO
    }

    public enum FriendshipStatus {
        PENDING, ACCEPTED
    }

    @Entity
    @Table(name = "app_users")
    @Getter
    @Setter
    public static class User {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, unique = true, length = 40)
        private String username;

        @Column(nullable = false)
        private String password;

        @Column(nullable = false)
        private boolean admin;
    }

    @Entity
    @Table(name = "login_log")
    @Getter
    @Setter
    public static class LoginLog {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(optional = false)
        private User user;

        @Column(nullable = false)
        private LocalDateTime loggedAt = LocalDateTime.now();
    }

    @Entity
    @Table(name = "friendship", uniqueConstraints = @UniqueConstraint(columnNames = {"low_user_id", "high_user_id"}))
    @Getter
    @Setter
    public static class Friendship {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(optional = false)
        @JoinColumn(name = "low_user_id")
        private User lowUser;

        @ManyToOne(optional = false)
        @JoinColumn(name = "high_user_id")
        private User highUser;

        @ManyToOne(optional = false)
        private User requester;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private FriendshipStatus status = FriendshipStatus.PENDING;
    }

    @Entity
    @Table(name = "tags")
    @Getter
    @Setter
    public static class Tag {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, unique = true, length = 40)
        private String name;
    }

    @Entity(name = "Post")
    @Table(name = "posts")
    @Getter
    @Setter
    public static class Post {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(optional = false)
        private User author;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Kind kind;

        @Column(nullable = false, length = 150)
        private String title;

        @Column(length = 10000)
        private String body;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Visibility visibility;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Review review = Review.PENDING;

        @Column(length = 500)
        private String rejectionReason;

        @Column(length = 100)
        private String fileKey;

        @Column(length = 100)
        private String fileType;

        @Column(nullable = false)
        private long viewCount;

        @ElementCollection
        @CollectionTable(name = "post_images", joinColumns = @JoinColumn(name = "post_id"))
        @Column(name = "file_key", length = 100)
        private java.util.List<String> imageKeys = new java.util.ArrayList<>();

        @ElementCollection
        @CollectionTable(name = "post_image_types", joinColumns = @JoinColumn(name = "post_id"))
        @Column(name = "file_type", length = 100)
        private java.util.List<String> imageTypes = new java.util.ArrayList<>();

        @ManyToMany
        @JoinTable(name = "post_tags",
                joinColumns = @JoinColumn(name = "post_id"),
                inverseJoinColumns = @JoinColumn(name = "tags_id"))
        private Set<Tag> tags = new HashSet<>();

        @Column(nullable = false, updatable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        @Column(nullable = false)
        private LocalDateTime updatedAt;

        @jakarta.persistence.PrePersist
        void initializeTimestamps() {
            if (createdAt == null) {
                createdAt = LocalDateTime.now();
            }
            if (updatedAt == null) {
                updatedAt = createdAt;
            }
        }
    }

    @Entity
    @Table(name = "post_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
    @Getter
    @Setter
    public static class PostLike {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(optional = false)
        @JoinColumn(name = "post_id")
        private Post post;

        @ManyToOne(optional = false)
        @JoinColumn(name = "user_id")
        private User user;

        @Column(nullable = false)
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @Entity
    @Table(name = "comments")
    @Getter
    @Setter
    public static class Comment {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(optional = false)
        private Post post;

        @ManyToOne(optional = false)
        private User author;

        @ManyToOne
        @JoinColumn(name = "parent_id")
        private Comment parent;

        @OneToMany(mappedBy = "parent")
        @jakarta.persistence.OrderBy("createdAt ASC")
        private java.util.List<Comment> replies = new java.util.ArrayList<>();

        @Column(nullable = false, length = 1000)
        private String body;

        @Column(nullable = false)
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @Entity
    @Table(name = "resource_folders")
    @Getter
    @Setter
    public static class Folder {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, unique = true, length = 100)
        private String name;

        @ManyToOne
        @JoinColumn(name = "parent_id")
        private Folder parent;

        @OneToMany(mappedBy = "parent")
        private java.util.List<Folder> children = new java.util.ArrayList<>();
    }

    @Entity
    @Table(name = "resources")
    @Getter
    @Setter
    public static class ResourceFile {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(optional = false)
        private Folder folder;

        @Column(nullable = false, length = 200)
        private String name;

        @Column(nullable = false, length = 100)
        private String fileKey;

        @Column(nullable = false, length = 100)
        private String fileType;

        @Column(nullable = false)
        private boolean visible;
    }
}
