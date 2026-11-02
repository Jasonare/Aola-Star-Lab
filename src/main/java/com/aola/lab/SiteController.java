package com.aola.lab;

import com.aola.lab.Model.Folder;
import com.aola.lab.Model.Kind;
import com.aola.lab.Model.Post;
import com.aola.lab.Model.ResourceFile;
import com.aola.lab.Model.Review;
import com.aola.lab.Model.Tag;
import com.aola.lab.Model.User;
import com.aola.lab.Model.Visibility;
import com.aola.lab.Repositories.Comments;
import com.aola.lab.Repositories.Folders;
import com.aola.lab.Repositories.Friendships;
import com.aola.lab.Repositories.Logs;
import com.aola.lab.Repositories.Posts;
import com.aola.lab.Repositories.Resources;
import com.aola.lab.Repositories.Tags;
import com.aola.lab.Repositories.Users;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class SiteController {
    private final LabService service;
    private final Users users;
    private final Logs logs;
    private final Friendships friends;
    private final Posts posts;
    private final Tags tags;
    private final Comments comments;
    private final Folders folders;
    private final Resources resources;
    private final FileStore files;

    public SiteController(LabService service, Users users, Logs logs, Friendships friends, Posts posts, Tags tags,
            Comments comments, Folders folders, Resources resources, FileStore files) {
        this.service = service;
        this.users = users;
        this.logs = logs;
        this.friends = friends;
        this.posts = posts;
        this.tags = tags;
        this.comments = comments;
        this.folders = folders;
        this.resources = resources;
        this.files = files;
    }

    private User current(Authentication auth) {
        return service.user(auth.getName());
    }

    @ModelAttribute("currentUser")
    public User currentUser(Authentication auth) {
        return auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser") ? null : current(auth);
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password) {
        service.register(username, password);
        return "redirect:/login?registered";
    }

    @GetMapping("/")
    public String dashboard(Authentication auth, Model model) {
        User user = current(auth);
        model.addAttribute("blogCount", posts.countByAuthorAndKind(user, Kind.BLOG));
        model.addAttribute("videoCount", posts.countByAuthorAndKind(user, Kind.VIDEO));
        model.addAttribute("recentComments", comments.findTop5ByPostAuthorOrderByCreatedAtDesc(user));
        model.addAttribute("logs", logs.findTop5ByUserOrderByLoggedAtDescIdDesc(user));
        return "dashboard";
    }

    @GetMapping("/friends")
    public String friends(Authentication auth, Model model) {
        User user = current(auth);
        model.addAttribute("friendships", friends.findByLowUserOrHighUser(user, user));
        return "friends";
    }

    @PostMapping("/friends/request")
    public String friendRequest(Authentication auth, @RequestParam String username) {
        service.requestFriend(current(auth), username);
        return "redirect:/friends";
    }

    @PostMapping("/friends/{id}/accept")
    public String friendAccept(Authentication auth, @PathVariable Long id) {
        service.acceptFriend(current(auth), id);
        return "redirect:/friends";
    }

    @GetMapping("/posts")
    public String posts(Authentication auth, @RequestParam(required = false) String q,
            @RequestParam(required = false) Long tag, @RequestParam(required = false) Kind kind, Model model) {
        User user = current(auth);
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("posts", posts.findAllByOrderByCreatedAtDesc().stream()
                .filter(post -> service.canView(user, post))
                .filter(post -> kind == null || post.getKind() == kind)
                .filter(post -> query.isEmpty() || post.getTitle().toLowerCase(Locale.ROOT).contains(query)
                        || post.getBody() != null && post.getBody().toLowerCase(Locale.ROOT).contains(query))
                .filter(post -> tag == null || post.getTags().stream().anyMatch(item -> item.getId().equals(tag)))
                .toList());
        model.addAttribute("tags", tags.findAll());
        model.addAttribute("query", q);
        model.addAttribute("selectedTag", tag);
        model.addAttribute("selectedKind", kind);
        return "posts";
    }

    @GetMapping("/posts/new")
    public String newPost(@RequestParam(defaultValue = "BLOG") Kind kind, Model model) {
        Post post = new Post();
        post.setKind(kind);
        post.setVisibility(Visibility.PUBLIC);
        model.addAttribute("post", post);
        model.addAttribute("selectedTagIds", List.of());
        model.addAttribute("tags", tags.findAll());
        return "post-form";
    }

    @GetMapping("/posts/{id}/edit")
    public String edit(Authentication auth, @PathVariable Long id, Model model) {
        Post post = service.ownedPost(current(auth), id);
        model.addAttribute("post", post);
        model.addAttribute("selectedTagIds", post.getTags().stream().map(Tag::getId).toList());
        model.addAttribute("tags", tags.findAll());
        return "post-form";
    }

    @PostMapping("/posts/save")
    public String save(Authentication auth, @RequestParam(required = false) Long id, @RequestParam Kind kind,
            @RequestParam String title, @RequestParam(required = false) String body,
            @RequestParam Visibility visibility, @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) MultipartFile file)
            throws IOException {
        User user = current(auth);
        Post post = id == null ? new Post() : service.ownedPost(user, id);
        FileStore.Stored uploaded = null;
        try {
            if (file != null && !file.isEmpty()) {
                uploaded = files.save(file, kind == Kind.BLOG ? "image" : "video");
                post.setFileKey(uploaded.key());
                post.setFileType(uploaded.type());
            }
            service.savePost(post, user, kind, title, body, visibility, tagIds);
        } catch (RuntimeException | IOException e) {
            if (uploaded != null) {
                files.delete(uploaded.key());
            }
            throw e;
        }
        return "redirect:/posts/" + post.getId();
    }

    @GetMapping("/posts/{id}")
    public String detail(Authentication auth, @PathVariable Long id, Model model) {
        Post post = service.visiblePost(current(auth), id);
        model.addAttribute("post", post);
        model.addAttribute("comments", comments.findByPostOrderByCreatedAtDesc(post));
        return "post-detail";
    }

    @PostMapping("/posts/{id}/comments")
    public String comment(Authentication auth, @PathVariable Long id, @RequestParam String body) {
        service.comment(current(auth), id, body);
        return "redirect:/posts/" + id;
    }

    @GetMapping("/posts/{id}/file")
    public ResponseEntity<FileSystemResource> postFile(Authentication auth, @PathVariable Long id) {
        Post post = service.visiblePost(current(auth), id);
        if (post.getFileKey() == null) {
            throw new IllegalArgumentException("文件不存在");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(post.getFileType()))
                .body(files.load(post.getFileKey()));
    }

    @GetMapping("/resources")
    public String resources(Authentication auth, Model model) {
        User user = current(auth);
        model.addAttribute("items", user.isAdmin() ? resources.findAll() : resources.findByVisibleTrue());
        model.addAttribute("folders", folders.findAll());
        return "resources";
    }

    @GetMapping("/resources/{id}/file")
    public ResponseEntity<FileSystemResource> resourceFile(Authentication auth, @PathVariable Long id) {
        ResourceFile resource = resources.findById(id).orElseThrow();
        if (!resource.isVisible() && !current(auth).isAdmin()) {
            throw new AccessDeniedException("无权访问");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(resource.getName(), StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(files.load(resource.getFileKey()));
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("pending", posts.findAllByOrderByCreatedAtDesc().stream()
                .filter(post -> post.getReview() == Review.PENDING)
                .toList());
        model.addAttribute("tags", tags.findAll());
        model.addAttribute("folders", folders.findAll());
        return "admin";
    }

    @PostMapping("/admin/review/{id}")
    public String review(@PathVariable Long id, @RequestParam boolean approve,
            @RequestParam(required = false) String reason) {
        service.review(id, approve, reason);
        return "redirect:/admin";
    }

    @PostMapping("/admin/tags")
    public String addTag(@RequestParam String name) {
        name = name.trim();
        if (name.isEmpty() || name.length() > 40 || tags.existsByName(name)) {
            throw new IllegalArgumentException("标签无效或已存在");
        }
        Tag tag = new Tag();
        tag.setName(name);
        tags.save(tag);
        return "redirect:/admin";
    }

    @Transactional
    @PostMapping("/admin/tags/{id}/delete")
    public String deleteTag(@PathVariable Long id) {
        Tag tag = tags.findById(id).orElseThrow();
        for (Post post : posts.findAll()) {
            if (post.getTags().removeIf(item -> item.getId().equals(id))) {
                posts.save(post);
            }
        }
        tags.delete(tag);
        return "redirect:/admin";
    }

    @PostMapping("/admin/folders")
    public String folder(@RequestParam String name) {
        name = name.trim();
        if (name.isEmpty() || name.length() > 100 || folders.existsByName(name)) {
            throw new IllegalArgumentException("目录名称无效或已存在");
        }
        Folder folder = new Folder();
        folder.setName(name);
        folders.save(folder);
        return "redirect:/admin";
    }

    @PostMapping("/admin/resources")
    public String resource(@RequestParam Long folderId, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "false") boolean visible) throws IOException {
        Folder folder = folders.findById(folderId).orElseThrow();
        FileStore.Stored saved = files.save(file, "resource");
        try {
            ResourceFile resource = new ResourceFile();
            resource.setFolder(folder);
            resource.setName(file.getOriginalFilename());
            resource.setFileKey(saved.key());
            resource.setFileType(saved.type());
            resource.setVisible(visible);
            resources.save(resource);
        } catch (RuntimeException e) {
            files.delete(saved.key());
            throw e;
        }
        return "redirect:/resources";
    }

    @PostMapping("/admin/resources/{id}/visibility")
    public String resourceVisibility(@PathVariable Long id) {
        ResourceFile resource = resources.findById(id).orElseThrow();
        resource.setVisible(!resource.isVisible());
        resources.save(resource);
        return "redirect:/resources";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String badRequest(IllegalArgumentException e, Model model, HttpServletResponse response) {
        response.setStatus(400);
        model.addAttribute("message", e.getMessage());
        return "problem";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public String notFound(Model model, HttpServletResponse response) {
        response.setStatus(404);
        model.addAttribute("message", "请求的内容不存在。");
        return "problem";
    }
}
