package com.aola.lab;

import com.aola.lab.Model.Comment;
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
import com.aola.lab.Repositories.PostLikes;
import com.aola.lab.Repositories.Posts;
import com.aola.lab.Repositories.Resources;
import com.aola.lab.Repositories.Tags;
import com.aola.lab.Repositories.Users;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class SiteController {
    private static final Pattern BYTE_RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)");

    private final LabService service;
    private final Users users;
    private final Logs logs;
    private final Friendships friends;
    private final Posts posts;
    private final Tags tags;
    private final Comments comments;
    private final PostLikes postLikes;
    private final Folders folders;
    private final Resources resources;
    private final FileStore files;

    public SiteController(LabService service, Users users, Logs logs, Friendships friends, Posts posts, Tags tags,
            Comments comments, PostLikes postLikes, Folders folders, Resources resources, FileStore files) {
        this.service = service;
        this.users = users;
        this.logs = logs;
        this.friends = friends;
        this.posts = posts;
        this.tags = tags;
        this.comments = comments;
        this.postLikes = postLikes;
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
        try {
            service.register(username, password);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalArgumentException("用户名已存在或数据不符合要求");
        }
        return "redirect:/login?registered";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(Authentication auth, @RequestParam String username,
            @RequestParam(required = false) String signature, jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response) {
        User user = current(auth);
        String oldName = user.getUsername();
        try {
            service.updateProfile(user, username, signature);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalArgumentException("用户名已存在或数据不符合要求");
        }
        if (!oldName.equals(user.getUsername())) {
            new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request, response, auth);
            return "redirect:/login?changed";
        }
        return "redirect:/profile?saved";
    }

    @PostMapping("/profile/password")
    public String changePassword(Authentication auth, @RequestParam String oldPassword, @RequestParam String newPassword,
            @RequestParam String confirmPassword, jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的新密码不一致");
        }
        service.changePassword(current(auth), oldPassword, newPassword);
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request, response, auth);
        return "redirect:/login?changed";
    }

    @GetMapping("/")
    public String dashboard(Authentication auth, jakarta.servlet.http.HttpServletRequest request, Model model) {
        User user = current(auth);
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute("showLoginWelcome"))) {
            model.addAttribute("showLoginWelcome", true);
            session.removeAttribute("showLoginWelcome");
        }
        model.addAttribute("blogCount", posts.countByAuthorAndKind(user, Kind.BLOG));
        model.addAttribute("videoCount", posts.countByAuthorAndKind(user, Kind.VIDEO));
        model.addAttribute("totalViews", posts.findByAuthorAndKindOrderByUpdatedAtDesc(user, Kind.BLOG).stream()
                .mapToLong(Post::getViewCount).sum()
                + posts.findByAuthorAndKindOrderByUpdatedAtDesc(user, Kind.VIDEO).stream()
                        .mapToLong(Post::getViewCount).sum());
        model.addAttribute("totalLikes", postLikes.countByPostAuthor(user));
        model.addAttribute("recentComments", comments.findTop5ByPostAuthorOrderByCreatedAtDesc(user));
        model.addAttribute("logs", logs.findTop5ByUserOrderByLoggedAtDescIdDesc(user));
        return "dashboard";
    }

    @GetMapping("/chronicle")
    public String chronicle() {
        return "chronicle";
    }

    @GetMapping("/works")
    public String works(Authentication auth, @RequestParam(defaultValue = "BLOG") Kind kind, Model model) {
        User user = current(auth);
        List<Post> userWorks = posts.findByAuthorAndKindOrderByUpdatedAtDesc(user, kind);
        model.addAttribute("works", userWorks);
        model.addAttribute("postDates", userWorks.stream().collect(java.util.stream.Collectors.toMap(
                Post::getId, this::postDateLabel)));
        model.addAttribute("likeCounts", userWorks.stream().collect(java.util.stream.Collectors.toMap(
                Post::getId, postLikes::countByPost)));
        model.addAttribute("commentCounts", userWorks.stream().collect(java.util.stream.Collectors.toMap(
                Post::getId, comments::countByPost)));
        model.addAttribute("selectedKind", kind);
        return "works";
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

    private String postDateLabel(Post post) {
        boolean updated = post.getUpdatedAt() != null && post.getCreatedAt() != null
                && post.getUpdatedAt().isAfter(post.getCreatedAt());
        LocalDateTime displayTime = updated ? post.getUpdatedAt() : post.getCreatedAt();
        return (updated ? "更新于 " : "发布于 ")
                + java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(displayTime);
    }

    @GetMapping("/posts")
    public String posts(Authentication auth, @RequestParam(required = false) String q,
            @RequestParam(required = false) Long tag, @RequestParam(required = false) Kind kind,
            @RequestParam(defaultValue = "time") String sort,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate until, Model model) {
        if (from != null && until != null && from.isAfter(until)) {
            throw new IllegalArgumentException("起始时间不能晚于终止时间");
        }
        User user = current(auth);
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime untilTime = until == null ? null : until.plusDays(1).atStartOfDay();
        List<Post> source = fromTime != null && untilTime != null
                ? posts.findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(fromTime, untilTime)
                : fromTime != null ? posts.findByUpdatedAtGreaterThanEqualOrderByUpdatedAtDesc(fromTime)
                : untilTime != null ? posts.findByUpdatedAtLessThanOrderByUpdatedAtDesc(untilTime)
                : posts.findAllByOrderByUpdatedAtDesc();
        List<Post> visiblePosts = source.stream()
                .filter(post -> service.canView(user, post))
                .filter(post -> kind == null || post.getKind() == kind)
                .filter(post -> query.isEmpty() || post.getTitle().toLowerCase(Locale.ROOT).contains(query)
                        || post.getBody() != null && post.getBody().toLowerCase(Locale.ROOT).contains(query))
                .filter(post -> tag == null || post.getTags().stream().anyMatch(item -> item.getId().equals(tag)))
                .sorted(postComparator(sort))
                .toList();
        model.addAttribute("posts", visiblePosts);
        model.addAttribute("postDates", visiblePosts.stream().collect(java.util.stream.Collectors.toMap(
                Post::getId, this::postDateLabel)));
        model.addAttribute("likeCounts", visiblePosts.stream().collect(java.util.stream.Collectors.toMap(
                Post::getId, postLikes::countByPost)));
        model.addAttribute("commentCounts", visiblePosts.stream().collect(java.util.stream.Collectors.toMap(
                Post::getId, comments::countByPost)));
        model.addAttribute("tags", tags.findAll());
        model.addAttribute("query", q);
        model.addAttribute("selectedTag", tag);
        model.addAttribute("selectedKind", kind);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("fromDate", from);
        model.addAttribute("untilDate", until);
        model.addAttribute("sectionTitle", kind == Kind.VIDEO ? "视频专区" : kind == Kind.BLOG ? "文章专区" : "内容库");
        return "posts";
    }

    private Comparator<Post> postComparator(String sort) {
        return switch (sort == null ? "time" : sort) {
            case "likes" -> Comparator.comparingLong((Post post) -> postLikes.countByPost(post))
                    .reversed().thenComparing(Post::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "views" -> Comparator.comparingLong(Post::getViewCount).reversed()
                    .thenComparing(Post::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(Post::getUpdatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };
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
            @RequestParam(required = false, defaultValue = "false") boolean imageSelectionProvided,
            @RequestParam(required = false) List<Integer> retainedImageIndexes,
            @RequestParam(required = false, defaultValue = "false") boolean imageOrderProvided,
            @RequestParam(required = false) List<String> imageOrder,
            @RequestParam(required = false) List<MultipartFile> files)
            throws IOException {
        User user = current(auth);
        Post post = id == null ? new Post() : service.ownedPost(user, id);
        List<String> removedImageKeys = new ArrayList<>();
        List<FileStore.Stored> uploaded = new ArrayList<>();
        try {
            List<MultipartFile> incoming = files == null ? List.of() : files.stream().filter(file -> !file.isEmpty()).toList();
            if (kind == Kind.BLOG) {
                List<String> originalKeys = new ArrayList<>(post.getImageKeys());
                List<String> originalTypes = new ArrayList<>(post.getImageTypes());
                List<String> order = new ArrayList<>();
                if (imageOrderProvided) {
                    if (imageOrder != null) order.addAll(imageOrder);
                } else {
                    List<Integer> retained = !imageSelectionProvided
                            ? java.util.stream.IntStream.range(0, originalKeys.size()).boxed().toList()
                            : retainedImageIndexes == null ? List.of()
                                    : retainedImageIndexes.stream().distinct().filter(index -> index >= 0 && index < originalKeys.size()).sorted().toList();
                    retained.forEach(index -> order.add("old:" + index));
                    for (int i = 0; i < incoming.size(); i++) order.add("new:" + i);
                }
                if (order.size() > 9) throw new IllegalArgumentException("文章最多保留或上传9张图片");
                java.util.Set<Integer> oldIndexes = new java.util.HashSet<>();
                java.util.Set<Integer> newIndexes = new java.util.HashSet<>();
                for (String item : order) {
                    if (item == null || !item.matches("(old|new):[0-9]{1,3}")) throw new IllegalArgumentException("图片顺序无效");
                    boolean old = item.startsWith("old:");
                    int index = Integer.parseInt(item.substring(4));
                    if (index >= (old ? originalKeys.size() : incoming.size())
                            || !(old ? oldIndexes : newIndexes).add(index)) throw new IllegalArgumentException("图片顺序无效");
                }
                if (newIndexes.size() != incoming.size()) throw new IllegalArgumentException("图片顺序无效");
                for (MultipartFile file : incoming) {
                    if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                        throw new IllegalArgumentException("文章仅支持上传图片");
                    }
                    uploaded.add(this.files.save(file, "image"));
                }
                List<String> keys = new ArrayList<>();
                List<String> types = new ArrayList<>();
                for (String item : order) {
                    int index = Integer.parseInt(item.substring(4));
                    if (item.startsWith("old:")) {
                        keys.add(originalKeys.get(index));
                        types.add(index < originalTypes.size() ? originalTypes.get(index) : "image/jpeg");
                    } else {
                        keys.add(uploaded.get(index).key());
                        types.add(uploaded.get(index).type());
                    }
                }
                for (int i = 0; i < originalKeys.size(); i++) {
                    if (!oldIndexes.contains(i)) removedImageKeys.add(originalKeys.get(i));
                }
                post.setOrderedImageLists(keys, types);
                post.setFileKey(null);
                post.setFileType(null);
            } else if (incoming.size() > 1) {
                throw new IllegalArgumentException("视频只能上传一个MP4文件");
            } else if (!incoming.isEmpty()) {
                MultipartFile file = incoming.get(0);
                FileStore.Stored saved = this.files.save(file, "video");
                uploaded.add(saved);
                post.setFileKey(saved.key());
                post.setFileType(saved.type());
            }
            service.savePost(post, user, kind, title, body, visibility, tagIds);
        } catch (RuntimeException | IOException e) {
            for (FileStore.Stored file : uploaded) {
                this.files.delete(file.key());
            }
            throw e;
        }
        for (String removedImageKey : removedImageKeys) {
            try {
                this.files.delete(removedImageKey);
            } catch (RuntimeException | IOException e) {
                org.slf4j.LoggerFactory.getLogger(SiteController.class)
                        .warn("清理已移除的文章图片失败: {}", removedImageKey, e);
            }
        }
        return "redirect:/posts/" + post.getId() + "?published";
    }

    @GetMapping("/posts/{id}")
    public String detail(Authentication auth, @PathVariable Long id, Model model) {
        Post post = service.visiblePost(current(auth), id);
        User viewer = current(auth);
        service.incrementView(post, viewer);
        model.addAttribute("post", post);
        boolean publiclyCacheableImages = post.getReview() == Review.APPROVED
                && post.getVisibility() == Visibility.PUBLIC;
        List<String> imageUrls = new ArrayList<>();
        for (int index = 0; index < post.getImageKeys().size(); index++) {
            String key = post.getImageKeys().get(index);
            String imageUrl = publiclyCacheableImages
                    ? files.temporaryUrl(key, 1800).orElse(null) : null;
            imageUrls.add(imageUrl == null ? "/posts/" + post.getId() + "/images/" + index : imageUrl);
        }
        model.addAttribute("imageUrls", imageUrls);
        model.addAttribute("postDateLabel", postDateLabel(post));
        model.addAttribute("likeCount", postLikes.countByPost(post));
        model.addAttribute("commentCount", comments.countByPost(post));
        model.addAttribute("liked", postLikes.existsByPostAndUser(post, viewer));
        List<Comment> topLevelComments = comments.findByPostAndParentIsNullOrderByCreatedAtDesc(post);
        for (Comment comment : topLevelComments) {
            comment.setReplies(comments.findByParentOrderByCreatedAtAsc(comment));
        }
        model.addAttribute("comments", topLevelComments);
        return "post-detail";
    }

    @PostMapping("/posts/{id}/like")
    public String like(Authentication auth, @PathVariable Long id) {
        service.toggleLike(current(auth), id);
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/comments")
    public String comment(Authentication auth, @PathVariable Long id, @RequestParam(required = false) Long parentId,
            @RequestParam String body) {
        service.comment(current(auth), id, parentId, body);
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/delete")
    public String deletePost(Authentication auth, @PathVariable Long id) {
        Kind kind = service.deletePost(current(auth), id);
        return "redirect:/works?kind=" + kind.name();
    }

    @GetMapping("/posts/{id}/file")
    public ResponseEntity<Resource> postFile(Authentication auth, @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
        Post post = service.visiblePost(current(auth), id);
        if (post.getFileKey() == null) {
            throw new IllegalArgumentException("文件不存在");
        }
        Resource file = files.load(post.getFileKey());
        long length = file.contentLength();
        String fileType = post.getFileType();
        if (!"video/mp4".equalsIgnoreCase(fileType) || rangeHeader == null) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, Long.toString(length))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(MediaType.parseMediaType(fileType))
                    .body(file);
        }
        Matcher matcher = BYTE_RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches() || rangeHeader.contains(",")) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .build();
        }
        long start;
        long end;
        try {
            String requestedStart = matcher.group(1);
            String requestedEnd = matcher.group(2);
            if (requestedStart.isEmpty() && requestedEnd.isEmpty()) {
                throw new NumberFormatException("Empty byte range");
            }
            if (requestedStart.isEmpty()) {
                long suffixLength = Long.parseLong(requestedEnd);
                if (suffixLength <= 0) {
                    throw new NumberFormatException("Invalid suffix range");
                }
                start = Math.max(0, length - suffixLength);
                end = length - 1;
            } else {
                start = Long.parseLong(requestedStart);
                end = requestedEnd.isEmpty() ? length - 1 : Long.parseLong(requestedEnd);
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .build();
        }
        if (length == 0 || start >= length || start > end) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                    .build();
        }
        end = Math.min(end, length - 1);
        byte[] content;
        try (var input = file.getInputStream()) {
            input.skipNBytes(start);
            content = input.readNBytes(Math.toIntExact(end - start + 1));
        }
        return ResponseEntity.status(org.springframework.http.HttpStatus.PARTIAL_CONTENT)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(content.length))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(fileType))
                .body(new org.springframework.core.io.ByteArrayResource(content));
    }

    @GetMapping("/posts/{id}/images/{index}")
    public ResponseEntity<Resource> postImage(Authentication auth, @PathVariable Long id, @PathVariable int index) {
        Post post = service.visiblePost(current(auth), id);
        if (index < 0 || index >= post.getImageKeys().size()) {
            throw new IllegalArgumentException("图片不存在");
        }
        String key = post.getImageKeys().get(index);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(index < post.getImageTypes().size() ? post.getImageTypes().get(index) : "image/jpeg"))
                .body(files.load(key));
    }

    @GetMapping("/resources")
    public String resources(Authentication auth, @RequestParam(required = false) Long folderId, Model model) {
        User user = current(auth);
        Folder activeFolder = folderId == null ? null : folders.findById(folderId).orElseThrow();
        List<ResourceFile> available = user.isAdmin() ? resources.findAll() : resources.findByVisibleTrue();
        model.addAttribute("items", activeFolder == null ? List.of() : available.stream()
                .filter(item -> item.getFolder().getId().equals(activeFolder.getId()))
                .toList());
        model.addAttribute("foldersForUpload", folders.findAll());
        model.addAttribute("folders", activeFolder == null ? folders.findByParentIsNullOrderByNameAsc()
                : folders.findByParentOrderByNameAsc(activeFolder));
        model.addAttribute("activeFolder", activeFolder);
        model.addAttribute("parentFolder", activeFolder == null ? null : activeFolder.getParent());
        return "resources";
    }

    @GetMapping("/resources/{id}/file")
    public ResponseEntity<Resource> resourceFile(Authentication auth, @PathVariable Long id) {
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
    public String admin(@RequestParam(required = false) String tagQuery, Model model) {
        List<Post> pending = posts.findAllByOrderByUpdatedAtDesc().stream()
                .filter(post -> post.getReview() == Review.PENDING).toList();
        model.addAttribute("pending", pending);
        model.addAttribute("pendingBlogs", pending.stream().filter(post -> post.getKind() == Kind.BLOG).toList());
        model.addAttribute("pendingVideos", pending.stream().filter(post -> post.getKind() == Kind.VIDEO).toList());
        String normalizedTagQuery = tagQuery == null ? "" : tagQuery.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("tags", tags.findAll().stream()
                .filter(tag -> normalizedTagQuery.isEmpty() || tag.getName().toLowerCase(Locale.ROOT).contains(normalizedTagQuery)).toList());
        model.addAttribute("tagQuery", tagQuery);
        model.addAttribute("folders", folders.findByParentIsNullOrderByNameAsc());
        model.addAttribute("allFolders", folders.findAll());
        model.addAttribute("foldersForUpload", folders.findAll());
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

    @PostMapping("/admin/tags/{id}")
    public String editTag(@PathVariable Long id, @RequestParam String name) {
        Tag tag = tags.findById(id).orElseThrow();
        name = name.trim();
        if (name.isEmpty() || name.length() > 40 || (tags.existsByName(name) && !tag.getName().equals(name))) {
            throw new IllegalArgumentException("标签名称无效或已存在");
        }
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
    public String folder(@RequestParam String name, @RequestParam(required = false) Long parentId) {
        name = name.trim();
        if (name.isEmpty() || name.length() > 100 || folders.existsByName(name)) {
            throw new IllegalArgumentException("目录名称无效或已存在");
        }
        Folder folder = new Folder();
        folder.setName(name);
        if (parentId != null) {
            folder.setParent(folders.findById(parentId).orElseThrow());
        }
        folders.save(folder);
        return "redirect:/admin";
    }

    @PostMapping("/admin/folders/{id}/edit")
    public String editFolder(@PathVariable Long id, @RequestParam String name) {
        Folder folder = folders.findById(id).orElseThrow();
        name = name.trim();
        if (name.isEmpty() || name.length() > 100 || folders.existsByNameAndIdNot(name, id)) {
            throw new IllegalArgumentException("目录名称无效或已存在");
        }
        folder.setName(name);
        folders.save(folder);
        return "redirect:/admin";
    }

    @Transactional
    @PostMapping("/admin/folders/{id}/delete")
    public String deleteFolder(@PathVariable Long id) {
        Folder folder = folders.findById(id).orElseThrow();
        if (folders.existsByParent(folder) || resources.existsByFolder(folder)) {
            throw new IllegalArgumentException("该目录包含子目录或资源，请先移除内容");
        }
        folders.delete(folder);
        return "redirect:/admin";
    }

    @PostMapping("/admin/resources")
    public String resource(@RequestParam Long folderId, @RequestParam MultipartFile file) throws IOException {
        Folder folder = folders.findById(folderId).orElseThrow();
        FileStore.Stored saved = files.save(file, "resource");
        try {
            ResourceFile resource = new ResourceFile();
            resource.setFolder(folder);
            resource.setName(file.getOriginalFilename());
            resource.setFileKey(saved.key());
            resource.setFileType(saved.type());
            resource.setVisible(true);
            resources.save(resource);
        } catch (RuntimeException e) {
            files.delete(saved.key());
            throw e;
        }
        return "redirect:/admin?tab=folders-tab";
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
