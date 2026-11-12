package com.aola.lab;

import com.aola.lab.Model.Folder;
import com.aola.lab.Model.Friendship;
import com.aola.lab.Model.Kind;
import com.aola.lab.Model.Post;
import com.aola.lab.Model.PostLike;
import com.aola.lab.Model.ResourceFile;
import com.aola.lab.Model.Review;
import com.aola.lab.Model.Tag;
import com.aola.lab.Model.User;
import com.aola.lab.Model.Visibility;
import com.aola.lab.Repositories.Folders;
import com.aola.lab.Repositories.Friendships;
import com.aola.lab.Repositories.Posts;
import com.aola.lab.Repositories.Resources;
import com.aola.lab.Repositories.Tags;
import com.aola.lab.Repositories.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cos.enabled=false",
        "app.cos.image-required=false",
        "app.storage=./target/test-uploads",
        "app.video-storage=./target/test-videos"
})
@AutoConfigureMockMvc
@Transactional
class ApplicationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    LabService service;
    @Autowired
    Users users;
    @Autowired
    Posts posts;
    @Autowired
    Friendships friendships;
    @Autowired
    Resources resources;
    @Autowired
    Tags tags;
    @Autowired
    Repositories.PostLikes postLikes;
    @Autowired
    Folders folders;
    @Autowired
    FileStore files;

    private User author;
    private User other;
    private User admin;

    @BeforeEach
    void setup() {
        service.register("author", "password123");
        service.register("other", "password123");
        service.register("adminuser", "password123");
        author = service.user("author");
        other = service.user("other");
        admin = service.user("adminuser");
        admin.setAdmin(true);
        users.save(admin);
    }

    private Post createPost(Review status, Visibility visibility) {
        Post p = new Post();
        p.setAuthor(author);
        p.setKind(Kind.BLOG);
        p.setTitle("星空");
        p.setBody("内容");
        p.setVisibility(visibility);
        p.setReview(status);
        return posts.saveAndFlush(p);
    }

    private org.springframework.mock.web.MockHttpSession loginSession(String username, String password) throws Exception {
        return (org.springframework.mock.web.MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("username", username).param("password", password))
                .andExpect(header().string("Location", "/"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void profileChangesInvalidateAllExistingSessionsAndPreserveIdentity() throws Exception {
        var first = loginSession("author", "password123");
        var second = loginSession("author", "password123");
        Long id = author.getId();
        mvc.perform(get("/profile").session(first)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("个人信息")));
        mvc.perform(post("/profile").session(first).param("username", "新名字123").param("signature", "签名"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/profile").session(first).with(csrf()).param("username", "新名字123").param("signature", "签名"))
                .andExpect(header().string("Location", "/login?changed"));
        assertThat(service.user("新名字123").getId()).isEqualTo(id);
        assertThat(service.user("新名字123").getSignature()).isEqualTo("签名");
        service.register("author", "password123");
        mvc.perform(get("/profile").session(second)).andExpect(status().is3xxRedirection());
        var renamed = loginSession("新名字123", "password123");
        mvc.perform(post("/profile").session(renamed).with(csrf()).param("username", "新名字123").param("signature", ""))
                .andExpect(header().string("Location", "/profile?saved"));
        assertThat(service.user("新名字123").getSignature()).isNull();
        mvc.perform(get("/profile").session(renamed)).andExpect(status().isOk());
    }

    @Test
    void passwordChangesRequireOldPasswordAndInvalidateOtherSessions() throws Exception {
        var first = loginSession("author", "password123");
        var second = loginSession("author", "password123");
        mvc.perform(post("/profile/password").session(first).with(csrf()).param("oldPassword", "wrong")
                .param("newPassword", "newpassword123").param("confirmPassword", "newpassword123"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("旧密码不正确")));
        mvc.perform(post("/profile/password").session(first).with(csrf()).param("oldPassword", "password123")
                .param("newPassword", "newpassword123").param("confirmPassword", "newpassword123"))
                .andExpect(header().string("Location", "/login?changed"));
        mvc.perform(get("/profile").session(second)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").with(csrf()).param("username", "author").param("password", "password123"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login?error")));
        loginSession("author", "newpassword123");
    }

    @Test
    void profileRejectsInvalidNamesAndProtectsBuiltInAdmin() throws Exception {
        mvc.perform(post("/profile").with(user("author")).with(csrf()).param("username", "other"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/profile").with(user("author")).with(csrf()).param("username", "坏!名字"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/profile").with(user("author")).with(csrf()).param("username", "author")
                .param("signature", "长".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(post("/profile").with(user("aolaAdmin").roles("ADMIN")).with(csrf()).param("username", "renamedAdmin"))
                .andExpect(status().isBadRequest());
        String original = service.user("aolaAdmin").getPassword();
        service.changePassword(service.user("aolaAdmin"), "aolaResource", "newpassword123");
        new SecurityConfig().bootstrapAdmin(users, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()).run(null);
        assertThat(service.user("aolaAdmin").getPassword()).isNotEqualTo(original);
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("newpassword123", service.user("aolaAdmin").getPassword())).isTrue();
    }

    @Test
    void chineseUsernamesCanRegisterAndLogin() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("username", "奥拉伙伴123")
                .param("password", "password123"))
                .andExpect(status().is3xxRedirection());
        assertThat(service.user("奥拉伙伴123").getUsername()).isEqualTo("奥拉伙伴123");
        mvc.perform(post("/register").with(csrf()).param("username", "奥拉伙伴123")
                .param("password", "password123"))
                .andExpect(status().isBadRequest());
        var login = mvc.perform(post("/login").with(csrf()).param("username", "奥拉伙伴123")
                .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"))
                .andReturn();
        mvc.perform(get("/").session((org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("奥拉伙伴123")));
        mvc.perform(post("/register").with(csrf()).param("username", "奥拉!伙伴")
                .param("password", "password123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationCannotCreateAdminAndCsrfIsRequired() throws Exception {
        mvc.perform(post("/register").param("username", "newuser").param("password", "password123")
                .param("admin", "true"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/register").with(csrf()).param("username", "newuser").param("password", "password123")
                .param("admin", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(service.user("newuser").isAdmin()).isFalse();
        mvc.perform(post("/register").with(csrf()).param("username", "aolaAdmin")
                .param("password", "password123"))
                .andExpect(status().isBadRequest());
        assertThat(users.findByUsername("aolaAdmin")).isPresent();
        assertThat(users.findByUsername("aolaAdmin").orElseThrow().isAdmin()).isTrue();
        mvc.perform(post("/admin/tags").with(user("other").roles("USER")).with(csrf()).param("name", "tag"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminLoginRequiresVerificationCode() throws Exception {
        mvc.perform(post("/login").with(csrf())
                .param("username", "aolaAdmin")
                .param("password", "aolaResource")
                .param("verificationCode", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login?error")));

        mvc.perform(post("/login").with(csrf())
                .param("username", "aolaAdmin")
                .param("password", "aolaResource")
                .param("verificationCode", "5aWl5ouJ5pif5oCA5pen6LWE5rqQ5pW05ZCI"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"));
    }

    @Test
    void ordinaryUserCanLoginWithoutVerificationCode() throws Exception {
        var loginResult = mvc.perform(post("/login").with(csrf())
                .param("username", "author")
                .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"))
                .andReturn();

        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session.getAttribute("showLoginWelcome")).isEqualTo(true);
        mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "这里是Aola Star Lab，是一个奥拉星怀旧社区网站，目前包括发布文章、发布视频、资源下载等功能，后续会持续更新，感谢大家的支持！开发者：Jasonare")));
        mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("alert(\"这里是Aola Star Lab"))));
    }

    @Test
    void reviewVisibilityAndFriendshipAreSymmetric() throws Exception {
        Post p = createPost(Review.PENDING, Visibility.PUBLIC);
        assertThat(service.canView(other, p)).isFalse();
        assertThat(service.canView(author, p)).isTrue();
        assertThat(service.canView(admin, p)).isTrue();
        mvc.perform(get("/posts/{id}", p.getId()).with(user("author").roles("USER")))
                .andExpect(status().isOk());
        assertThat(posts.findById(p.getId()).orElseThrow().getViewCount()).isZero();
        mvc.perform(get("/posts/{id}", p.getId()).with(user("other").roles("USER")))
                .andExpect(status().isForbidden());
        service.review(p.getId(), true, null);
        assertThat(service.canView(other, p)).isTrue();
        p.setVisibility(Visibility.FRIENDS);
        posts.saveAndFlush(p);
        assertThat(service.canView(other, p)).isFalse();
        service.requestFriend(author, "other");
        Friendship f = friendships.findAll().get(0);
        assertThatThrownBy(() -> service.acceptFriend(author, f.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        service.acceptFriend(other, f.getId());
        assertThat(service.friends(author, other)).isTrue();
        assertThat(service.friends(other, author)).isTrue();
        assertThat(service.canView(other, p)).isTrue();
        mvc.perform(get("/posts/{id}", p.getId()).with(user("other").roles("USER")))
                .andExpect(status().isOk());
        assertThat(posts.findById(p.getId()).orElseThrow().getViewCount()).isEqualTo(1);
    }

    @Test
    void rejectedPostMustHaveReasonAndEditingResubmits() {
        Post p = createPost(Review.PENDING, Visibility.PUBLIC);
        assertThatThrownBy(() -> service.review(p.getId(), false, " "))
                .isInstanceOf(IllegalArgumentException.class);
        service.review(p.getId(), false, "请修改");
        assertThat(p.getReview()).isEqualTo(Review.REJECTED);
        assertThat(p.getRejectionReason()).isEqualTo("请修改");
        service.savePost(p, author, Kind.BLOG, "新标题", "正文", Visibility.PUBLIC, null);
        assertThat(p.getReview()).isEqualTo(Review.PENDING);
        assertThat(p.getRejectionReason()).isNull();
    }

    @Test
    void blogImagesCanBeAddedRetainedAndRemovedFromEditor() throws Exception {
        byte[] pngBytes = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1};
        MockMultipartFile firstImage = new MockMultipartFile("files", "first.png", "image/png", pngBytes);
        MockMultipartFile secondImage = new MockMultipartFile("files", "second.png", "image/png", pngBytes);
        mvc.perform(multipart("/posts/save").file(firstImage).file(secondImage)
                .with(user("author").roles("USER")).with(csrf())
                .param("kind", "BLOG").param("title", "多图文章").param("visibility", "PUBLIC")
                .param("imageSelectionProvided", "true"))
                .andExpect(status().is3xxRedirection());
        Post blog = posts.findAll().stream().filter(post -> post.getTitle().equals("多图文章")).findFirst().orElseThrow();
        assertThat(blog.getImageKeys()).hasSize(2);
        String removedKey = blog.getImageKeys().get(0);
        String retainedKey = blog.getImageKeys().get(1);

        mvc.perform(multipart("/posts/save").with(user("author").roles("USER")).with(csrf())
                .param("id", blog.getId().toString()).param("kind", "BLOG").param("title", "多图文章")
                .param("visibility", "PUBLIC").param("imageSelectionProvided", "true")
                .param("retainedImageIndexes", "1"))
                .andExpect(status().is3xxRedirection());
        Post updated = posts.findById(blog.getId()).orElseThrow();
        assertThat(updated.getImageKeys()).containsExactly(retainedKey);
        assertThatThrownBy(() -> files.load(removedKey)).isInstanceOf(IllegalArgumentException.class);
        assertThat(files.load(retainedKey).exists()).isTrue();

        mvc.perform(multipart("/posts/save").with(user("author").roles("USER")).with(csrf())
                .param("id", blog.getId().toString()).param("kind", "BLOG").param("title", "多图文章")
                .param("visibility", "PUBLIC").param("imageSelectionProvided", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(posts.findById(blog.getId()).orElseThrow().getImageKeys()).isEmpty();
    }

    @Test
    void orderedImagesPersistAndRejectInvalidOrders() throws Exception {
        Post blog = createPost(Review.PENDING, Visibility.PUBLIC);
        blog.getImageKeys().add("legacy-first");
        blog.getImageTypes().add("image/png");
        blog.getImageKeys().add("legacy-second");
        blog.getImageTypes().add("image/jpeg");
        posts.saveAndFlush(blog);
        Long id = blog.getId();
        entityManager.clear();
        assertThat(posts.findById(id).orElseThrow().getOrderedImages()).isNull();
        MockMultipartFile image = new MockMultipartFile("files", "new.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1});
        mvc.perform(multipart("/posts/save").file(image).with(user("author").roles("USER")).with(csrf())
                .param("id", id.toString()).param("kind", "BLOG").param("title", "排序")
                .param("visibility", "PUBLIC").param("imageOrderProvided", "true")
                .param("imageOrder", "old:1", "new:0", "old:0"))
                .andExpect(status().is3xxRedirection());
        posts.flush();
        entityManager.clear();
        Post reloaded = posts.findById(id).orElseThrow();
        assertThat(reloaded.getImageKeys()).hasSize(3);
        assertThat(reloaded.getImageKeys().get(0)).isEqualTo("legacy-second");
        assertThat(reloaded.getImageKeys().get(2)).isEqualTo("legacy-first");
        assertThat(reloaded.getImageTypes()).containsExactly("image/jpeg", "image/png", "image/png");
        String newKey = reloaded.getImageKeys().get(1);
        mvc.perform(multipart("/posts/save").with(user("author").roles("USER")).with(csrf())
                .param("id", id.toString()).param("kind", "BLOG").param("title", "排序")
                .param("visibility", "PUBLIC").param("imageOrderProvided", "true")
                .param("imageOrder", "old:0", "old:0"))
                .andExpect(status().isBadRequest());
        assertThat(posts.findById(id).orElseThrow().getImageKeys()).containsExactly("legacy-second", newKey, "legacy-first");
        files.delete(newKey);
    }

    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager entityManager;

    @Test
    void videoEndpointSupportsByteRangesAfterVisibilityCheck() throws Exception {
        byte[] videoBytes = new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 1, 2, 3, 4,
                0, 0, 0, 9, 'm', 'd', 'a', 't', 5,
                0, 0, 0, 8, 'm', 'o', 'o', 'v'};
        int videoLength = videoBytes.length;
        FileStore.Stored stored = files.save(new MockMultipartFile("file", "clip.mp4", "video/mp4", videoBytes), "video");
        videoBytes = java.nio.file.Files.readAllBytes(files.load(stored.key()).getFile().toPath());
        Post video = createPost(Review.APPROVED, Visibility.PUBLIC);
        video.setKind(Kind.VIDEO);
        video.setFileKey(stored.key());
        video.setFileType(stored.type());
        posts.saveAndFlush(video);

        mvc.perform(get("/posts/{id}/file", video.getId()).with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(header().string("Content-Length", Integer.toString(videoBytes.length)))
                .andExpect(content().bytes(videoBytes));
        mvc.perform(get("/posts/{id}/file", video.getId()).with(user("author").roles("USER"))
                .header("Range", "bytes=4-7"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 4-7/" + videoLength))
                .andExpect(header().string("Content-Length", "4"))
                .andExpect(content().bytes(new byte[]{'f', 't', 'y', 'p'}));
        mvc.perform(get("/posts/{id}/file", video.getId()).with(user("author").roles("USER"))
                .header("Range", "bytes=" + videoLength + "-" + (videoLength + 4)))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */" + videoLength));
    }

    @Test
    void cosObjectKeysKeepValidatedFileExtensions() {
        String id = "e43fc0c1-bd1d-40b5-8d4c-2b3eb6508d29";
        assertThat(FileStore.objectKey(id, "mp4")).isEqualTo(id + ".mp4");
        assertThat(FileStore.objectKey(id, "")).isEqualTo(id);
    }

    @Test
    void filesRequirePermissionAndValidateTypes() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "image.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1});
        FileStore.Stored stored = files.save(image, "image");
        assertThat(stored.key()).matches("[0-9a-f-]{36}");
        FileStore.Stored mislabeledImage = files.save(new MockMultipartFile("file", "image.jpg", "image/jpeg",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1}), "image");
        assertThat(mislabeledImage.type()).isEqualTo("image/png");
        files.delete(mislabeledImage.key());
        Post p = createPost(Review.PENDING, Visibility.PUBLIC);
        p.setFileKey(stored.key());
        p.setFileType(stored.type());
        posts.saveAndFlush(p);
        mvc.perform(get("/posts/{id}/file", p.getId()).with(user("other").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/posts/{id}/file", p.getId()).with(user("author").roles("USER")))
                .andExpect(status().isOk());
        assertThatThrownBy(() -> files.save(new MockMultipartFile("file", "bad.png", "image/png",
                "not an image".getBytes()), "image"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> files.load("../secrets")).isInstanceOf(IllegalArgumentException.class);
        Folder folder = new Folder();
        folder.setName("files");
        folders.saveAndFlush(folder);
        ResourceFile r = new ResourceFile();
        r.setFolder(folder);
        r.setName("image.png");
        r.setFileKey(stored.key());
        r.setFileType(stored.type());
        resources.saveAndFlush(r);
        mvc.perform(get("/resources/{id}/file", r.getId()).with(user("other").roles("USER")))
                .andExpect(status().isForbidden());
        r.setVisible(true);
        resources.saveAndFlush(r);
        mvc.perform(get("/resources/{id}/file", r.getId()).with(user("other").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void uploadSizeLimitsAreCategorySpecific() {
        long videoLimit = 250L * 1024 * 1024;
        long resourceLimit = 5L * 1024 * 1024 * 1024;
        MockMultipartFile oversizedVideo = new MockMultipartFile("file", "large.mp4", "video/mp4",
                new byte[1]) {
            @Override
            public long getSize() {
                return videoLimit + 1;
            }
        };
        MockMultipartFile oversizedResource = new MockMultipartFile("file", "large.pdf", "application/pdf",
                new byte[1]) {
            @Override
            public long getSize() {
                return resourceLimit + 1;
            }
        };
        assertThatThrownBy(() -> files.save(oversizedVideo, "video"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("250MB");
        assertThatThrownBy(() -> files.save(oversizedResource, "resource"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5GB");
    }

    @Test
    void installerSignaturesAndPostCategoriesAreValidated() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "setup.EXE", "text/plain", new byte[]{'M', 'Z', 1});
        MockMultipartFile apk = new MockMultipartFile("file", "app.APK", "text/plain", new byte[]{'P', 'K', 3, 4, 1});
        MockMultipartFile emptyZipApk = new MockMultipartFile("file", "empty.apk", "application/octet-stream",
                new byte[]{'P', 'K', 5, 6});
        java.util.List<String> keys = new java.util.ArrayList<>();
        try {
            FileStore.Stored storedExe = files.save(exe, "resource");
            keys.add(storedExe.key());
            FileStore.Stored storedApk = files.save(apk, "resource");
            keys.add(storedApk.key());
            FileStore.Stored storedEmptyApk = files.save(emptyZipApk, "resource");
            keys.add(storedEmptyApk.key());
            assertThat(storedExe.type()).isEqualTo("application/octet-stream");
            assertThat(storedApk.type()).isEqualTo("application/vnd.android.package-archive");
            assertThat(storedEmptyApk.type()).isEqualTo("application/vnd.android.package-archive");
            assertThat(java.nio.file.Files.readAllBytes(files.load(storedExe.key()).getFile().toPath()))
                    .isEqualTo(exe.getBytes());
            assertThat(java.nio.file.Files.readAllBytes(files.load(storedApk.key()).getFile().toPath()))
                    .isEqualTo(apk.getBytes());
            for (MockMultipartFile invalid : new MockMultipartFile[]{
                    new MockMultipartFile("file", "bad.exe", "application/octet-stream", new byte[]{'M'}),
                    new MockMultipartFile("file", "bad.exe", "application/octet-stream", new byte[]{'P', 'K', 3, 4}),
                    new MockMultipartFile("file", "bad.apk", "application/octet-stream", new byte[]{'P', 'K', 3}),
                    new MockMultipartFile("file", "bad.apk", "application/octet-stream", new byte[]{'M', 'Z'}),
                    new MockMultipartFile("file", "bad.zip", "application/zip", new byte[]{'M', 'Z'})}) {
                assertThatThrownBy(() -> files.save(invalid, "resource")).isInstanceOf(IllegalArgumentException.class);
            }
            for (String category : new String[]{"image", "video"}) {
                assertThatThrownBy(() -> files.save(exe, category)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> files.save(apk, category)).isInstanceOf(IllegalArgumentException.class);
            }
            MockMultipartFile truncatedMp4 = new MockMultipartFile("file", "truncated.mp4", "video/mp4",
                    new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 1, 2, 3, 4});
            assertThatThrownBy(() -> files.save(truncatedMp4, "video"))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            for (String key : keys) {
                files.delete(key);
            }
        }
    }

    @Test
    void installerUploadsRequireAdminAndDownloadsRemainAttachments() throws Exception {
        Folder folder = new Folder();
        folder.setName("installers");
        folders.saveAndFlush(folder);
        MockMultipartFile exe = new MockMultipartFile("file", "setup.exe", "application/octet-stream",
                new byte[]{'M', 'Z', 1});
        MockMultipartFile apk = new MockMultipartFile("file", "app.apk", "application/octet-stream",
                new byte[]{'P', 'K', 3, 4, 1});
        mvc.perform(multipart("/admin/resources").file(exe).with(csrf()).param("folderId", folder.getId().toString()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/admin/resources").file(exe).with(user("other").roles("USER")).with(csrf())
                .param("folderId", folder.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/resources").file(exe).with(user("adminuser").roles("ADMIN"))
                .param("folderId", folder.getId().toString()))
                .andExpect(status().isForbidden());
        assertThat(resources.findAll()).isEmpty();
        java.util.List<String> keys = new java.util.ArrayList<>();
        try {
            for (MockMultipartFile file : new MockMultipartFile[]{exe, apk}) {
                mvc.perform(multipart("/admin/resources").file(file).with(user("adminuser").roles("ADMIN"))
                        .with(csrf()).param("folderId", folder.getId().toString()))
                        .andExpect(status().is3xxRedirection());
                ResourceFile resource = resources.findAll().stream()
                        .filter(r -> r.getName().equals(file.getOriginalFilename())).findFirst().orElseThrow();
                keys.add(resource.getFileKey());
                assertThat(resource.isVisible()).isTrue();
                mvc.perform(get("/resources?folderId={folderId}", folder.getId())
                        .with(user("other").roles("USER")))
                        .andExpect(status().isOk())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString(file.getOriginalFilename())))
                        .andExpect(content().string(org.hamcrest.Matchers.containsString(
                                "/resources/" + resource.getId() + "/file")));
                mvc.perform(get("/resources/{id}/file", resource.getId()))
                        .andExpect(status().is3xxRedirection());
                mvc.perform(get("/resources/{id}/file", resource.getId()).with(user("adminuser").roles("ADMIN")))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Disposition",
                                org.hamcrest.Matchers.startsWith("attachment;")))
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                        .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                        .andExpect(content().contentType("application/octet-stream"))
                        .andExpect(content().bytes(file.getBytes()));
                mvc.perform(get("/resources/{id}/file", resource.getId()).with(user("other").roles("USER")))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Disposition",
                                org.hamcrest.Matchers.startsWith("attachment;")))
                        .andExpect(content().contentType("application/octet-stream"))
                        .andExpect(content().bytes(file.getBytes()));
            }
            mvc.perform(multipart("/admin/resources")
                    .file(new MockMultipartFile("file", "bad.apk", "application/octet-stream", new byte[]{'M', 'Z'}))
                    .with(user("adminuser").roles("ADMIN")).with(csrf()).param("folderId", folder.getId().toString()))
                    .andExpect(status().isBadRequest());
            assertThat(resources.findAll()).hasSize(2);
        } finally {
            for (String key : keys) {
                files.delete(key);
            }
        }
    }

    @Test
    void contentListsHideInternalStatesAndSupportDescendingSorts() throws Exception {
        Post oldest = createPost(Review.APPROVED, Visibility.PUBLIC);
        oldest.setTitle("第一篇排序测试");
        oldest.setUpdatedAt(java.time.LocalDateTime.now().minusDays(2));
        oldest.setViewCount(30);
        posts.saveAndFlush(oldest);

        Post middle = createPost(Review.APPROVED, Visibility.PUBLIC);
        middle.setTitle("第二篇排序测试");
        middle.setUpdatedAt(java.time.LocalDateTime.now().minusDays(1));
        middle.setViewCount(10);
        posts.saveAndFlush(middle);

        Post newest = createPost(Review.APPROVED, Visibility.PUBLIC);
        newest.setTitle("第三篇排序测试");
        newest.setUpdatedAt(java.time.LocalDateTime.now());
        newest.setViewCount(20);
        posts.saveAndFlush(newest);

        for (User liker : new User[]{author, other, admin}) {
            PostLike like = new PostLike();
            like.setPost(oldest);
            like.setUser(liker);
            postLikes.save(like);
        }
        PostLike middleLike = new PostLike();
        middleLike.setPost(middle);
        middleLike.setUser(other);
        postLikes.save(middleLike);

        String timeSorted = mvc.perform(get("/posts").with(user("other").roles("USER"))
                .param("q", "排序测试").param("sort", "time"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("APPROVED"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("PUBLIC"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(timeSorted.indexOf("第三篇排序测试")).isLessThan(timeSorted.indexOf("第二篇排序测试"));

        String likesSorted = mvc.perform(get("/posts").with(user("other").roles("USER"))
                .param("q", "排序测试").param("sort", "likes"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(likesSorted.indexOf("第一篇排序测试")).isLessThan(likesSorted.indexOf("第二篇排序测试"));

        String viewsSorted = mvc.perform(get("/posts").with(user("other").roles("USER"))
                .param("q", "排序测试").param("sort", "views"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(viewsSorted.indexOf("第一篇排序测试")).isLessThan(viewsSorted.indexOf("第三篇排序测试"));
    }

    @Test
    void contentAndResourceWorkflows() throws Exception {
        mvc.perform(multipart("/posts/save").with(user("author").roles("USER")).with(csrf()).param("kind", "BLOG")
                .param("title", "奥拉星新篇").param("body", "你好").param("visibility", "PUBLIC"))
                .andExpect(status().is3xxRedirection());
        Post blog = posts.findAll().get(0);
        assertThat(blog.getReview()).isEqualTo(Review.PENDING);
        mvc.perform(post("/admin/review/{id}", blog.getId()).with(user("adminuser").roles("ADMIN")).with(csrf())
                .param("approve", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/posts/{id}/comments", blog.getId()).with(user("other").roles("USER")).with(csrf())
                .param("body", "不错"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/posts/{id}/like", blog.getId()).with(user("other").roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/posts/{id}", blog.getId()).with(user("other").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("取消点赞")));
        mvc.perform(post("/posts/{id}/like", blog.getId()).with(user("other").roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(blog.getViewCount()).isEqualTo(1);
        mvc.perform(get("/").with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("作品总浏览")));
        MockMultipartFile blogImage = new MockMultipartFile("files", "cover.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1});
        mvc.perform(get("/posts").with(user("other").roles("USER")).param("q", "奥拉星"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("奥拉星新篇")));
        mvc.perform(multipart("/posts/save").file(blogImage).with(user("author").roles("USER")).with(csrf())
                .param("id", blog.getId().toString()).param("kind", "BLOG").param("title", "奥拉星新篇")
                .param("body", "你好").param("visibility", "PUBLIC"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/posts/{id}/edit", blog.getId()).with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/posts/" + blog.getId() + "/images/0")));
        MockMultipartFile video = new MockMultipartFile("files", "demo.mp4", "video/mp4",
                new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 1, 2, 3, 4,
                        0, 0, 0, 9, 'm', 'd', 'a', 't', 5, 0, 0, 0, 8, 'm', 'o', 'o', 'v'});
        Tag videoTag = new Tag();
        videoTag.setName("攻略");
        videoTag = tags.saveAndFlush(videoTag);
        mvc.perform(multipart("/posts/save").file(video).with(user("author").roles("USER")).with(csrf())
                .param("kind", "VIDEO").param("title", "视频").param("visibility", "PUBLIC")
                .param("tagIds", videoTag.getId().toString()))
                .andExpect(status().is3xxRedirection());
        Post taggedVideo = posts.findAll().stream().filter(post -> post.getKind() == Kind.VIDEO)
                .findFirst().orElseThrow();
        assertThat(taggedVideo.getTags()).extracting(Tag::getName).containsExactly("攻略");
        Post videoPost = posts.findAll().stream().filter(post -> post.getKind() == Kind.VIDEO).findFirst().orElseThrow();
        String existingVideoKey = videoPost.getFileKey();
        mvc.perform(get("/posts/{id}/edit", videoPost.getId()).with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/posts/" + videoPost.getId() + "/file")));
        mvc.perform(multipart("/posts/save").with(user("author").roles("USER")).with(csrf())
                .param("id", videoPost.getId().toString()).param("kind", "VIDEO").param("title", "视频修改")
                .param("visibility", "FRIENDS"))
                .andExpect(status().is3xxRedirection());
        assertThat(videoPost.getFileKey()).isEqualTo(existingVideoKey);
        mvc.perform(post("/admin/review/{id}", videoPost.getId()).with(user("adminuser").roles("ADMIN")).with(csrf())
                .param("approve", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/posts/{id}/comments", videoPost.getId()).with(user("author").roles("USER")).with(csrf())
                .param("body", "视频评论"))
                .andExpect(status().is3xxRedirection());
        assertThat(existingVideoKey).startsWith("video/").endsWith(".mp4");
        assertThat(files.load(existingVideoKey).getFile().getParentFile().getName()).isEqualTo("test-videos");
        mvc.perform(post("/admin/folders").with(user("adminuser").roles("ADMIN")).with(csrf()).param("name", "资源"))
                .andExpect(status().is3xxRedirection());
        Long folderId = folders.findAll().get(0).getId();
        MockMultipartFile pdf = new MockMultipartFile("file", "guide.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mvc.perform(multipart("/admin/resources").file(pdf).with(user("adminuser").roles("ADMIN")).with(csrf())
                .param("folderId", folderId.toString()))
                .andExpect(status().is3xxRedirection());
        ResourceFile resource = resources.findAll().get(0);
        assertThat(resource.isVisible()).isTrue();
        mvc.perform(get("/resources").param("folderId", folderId.toString())
                .with(user("other").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("guide.pdf")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/resources/" + resource.getId() + "/file")));
        mvc.perform(get("/resources/{id}/file", resource.getId()).with(user("other").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf.getBytes()));
        mvc.perform(post("/admin/resources/{id}/visibility", resource.getId())
                .with(user("adminuser").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(resources.findById(resource.getId()).orElseThrow().isVisible()).isFalse();
        mvc.perform(get("/resources/{id}/file", resource.getId()).with(user("other").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/posts/{id}/edit", blog.getId()).with(user("author").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAnotherUsersPostIsForbidden() throws Exception {
        Post post = createPost(Review.APPROVED, Visibility.PUBLIC);
        service.toggleLike(other, post.getId());
        service.comment(other, post.getId(), null, "评论");
        mvc.perform(post("/posts/{id}/delete", post.getId()).with(user("other").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/posts/{id}/delete", post.getId()).with(user("adminuser").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(posts.existsById(post.getId())).isTrue();
        assertThat(postLikes.countByPost(post)).isEqualTo(1);
    }

    @Test
    void deletingPostRemovesInteractionsAndFilesOnlyAfterCommit() throws Exception {
        var image = files.save(new MockMultipartFile("file", "image.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1}), "image");
        var video = files.save(new MockMultipartFile("file", "clip.mp4", "video/mp4",
                new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 1, 2, 3, 4,
                        0, 0, 0, 9, 'm', 'd', 'a', 't', 5, 0, 0, 0, 8, 'm', 'o', 'o', 'v'}), "video");
        Post post = createPost(Review.APPROVED, Visibility.PUBLIC);
        post.setKind(Kind.VIDEO);
        post.setFileKey(video.key());
        post.getImageKeys().add(image.key());
        post.getImageTypes().add(image.type());
        post.setOrderedImageLists(java.util.List.of(image.key()), java.util.List.of(image.type()));
        Tag tag = new Tag();
        tag.setName("删除测试标签");
        tags.saveAndFlush(tag);
        post.getTags().add(tag);
        posts.saveAndFlush(post);
        Long id = post.getId();
        Long authorId = author.getId(), otherId = other.getId(), adminId = admin.getId(), tagId = tag.getId();
        service.toggleLike(other, id);
        service.comment(other, id, null, "一级评论");
        Long parentId = comments.findByPostAndParentIsNullOrderByCreatedAtDesc(post).get(0).getId();
        service.comment(author, id, parentId, "回复");
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        try {
            org.springframework.test.context.transaction.TestTransaction.start();
            service.deletePost(author, id);
            posts.flush();
            assertThat(files.load(image.key()).exists()).isTrue();
            assertThat(files.load(video.key()).exists()).isTrue();
            org.springframework.test.context.transaction.TestTransaction.flagForRollback();
            org.springframework.test.context.transaction.TestTransaction.end();
            assertThat(posts.existsById(id)).isTrue();
            assertThat(files.load(image.key()).exists()).isTrue();
            assertThat(files.load(video.key()).exists()).isTrue();

            org.springframework.test.context.transaction.TestTransaction.start();
            mvc.perform(post("/posts/{id}/delete", id).with(user("author").roles("USER")).with(csrf()))
                    .andExpect(header().string("Location", "/works?kind=VIDEO"));
            posts.flush();
            assertThat(files.load(image.key()).exists()).isTrue();
            assertThat(files.load(video.key()).exists()).isTrue();
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();
            assertThat(posts.existsById(id)).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from post_likes where post_id = ?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from comments where post_id = ?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from post_tags where post_id = ?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from post_images where post_id = ?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from post_image_types where post_id = ?", Long.class, id)).isZero();
            assertThatThrownBy(() -> files.load(image.key())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> files.load(video.key())).isInstanceOf(IllegalArgumentException.class);
        } finally {
            if (org.springframework.test.context.transaction.TestTransaction.isActive()) {
                org.springframework.test.context.transaction.TestTransaction.flagForRollback();
                org.springframework.test.context.transaction.TestTransaction.end();
            }
            if (posts.existsById(id)) service.deletePost(author, id);
            tags.deleteById(tagId);
            users.deleteAllById(java.util.List.of(authorId, otherId, adminId));
            files.delete(image.key());
            files.delete(video.key());
            org.springframework.test.context.transaction.TestTransaction.start();
        }
    }

    @Autowired
    Repositories.Comments comments;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void missingRecordsReturnFriendlyNotFound() throws Exception {
        mvc.perform(get("/posts/{id}", Long.MAX_VALUE).with(user("author").roles("USER")))
                .andExpect(status().isNotFound()).andExpect(view().name("problem"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("请求的内容不存在。")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("NoSuchElementException"))));
        mvc.perform(get("/resources/{id}/file", Long.MAX_VALUE).with(user("author").roles("USER")))
                .andExpect(status().isNotFound()).andExpect(view().name("problem"));
        mvc.perform(post("/friends/{id}/accept", Long.MAX_VALUE).with(user("author").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(view().name("problem"));
        mvc.perform(post("/admin/review/{id}", Long.MAX_VALUE).with(user("adminuser").roles("ADMIN")).with(csrf())
                .param("approve", "true"))
                .andExpect(status().isNotFound()).andExpect(view().name("problem"));
    }

    @Test
    void chroniclePageUsesSharedNavigationAndScopedStaticAssets() throws Exception {
        mvc.perform(get("/chronicle").with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("chronicle"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/chronicle/style.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/chronicle/app.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/chronicle/assets/emblem.png")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("奥拉编年史</a>")));
        mvc.perform(get("/chronicle")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/chronicle/app.js")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/lab/chronicle").contextPath("/lab").with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/lab/chronicle/assets/title-source.png")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-asset-base=\"/lab/chronicle/\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/lab/chronicle/nodes.js")));
        for (String asset : new String[]{"assets/sky.png", "assets/card.png", "assets/emblem.png", "assets/title-source.png", "nodes.js"}) {
            mvc.perform(get("/lab/chronicle/" + asset).contextPath("/lab").with(user("author").roles("USER")))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/chronicle/nodes.js").with(user("author").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2015")));
        mvc.perform(get("/chronicle/app.js").with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("assetBase")));
        mvc.perform(get("/chronicle/style.css").with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".chronicle-page .entry .card{max-width:none;")));
    }

    @Test
    void dashboardRendersDonationDialogAndServesQrCode() throws Exception {
        mvc.perform(get("/lab/").contextPath("/lab").with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"donate-open\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<dialog id=\"donate-dialog\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-labelledby=\"donate-title\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"donate-close\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/lab/images/donate-qrcode.png")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("donateDialog.showModal()")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("donateDialog.close()")));
        byte[] image = new org.springframework.core.io.ClassPathResource("static/images/donate-qrcode.png")
                .getInputStream().readAllBytes();
        mvc.perform(get("/lab/images/donate-qrcode.png").contextPath("/lab").with(user("author").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(image));
    }

    @Test
    void pagesRenderForAuthenticatedUsers() throws Exception {
        createPost(Review.APPROVED, Visibility.PUBLIC);
        for (String path : new String[]{"/", "/friends", "/posts", "/posts/new", "/resources", "/chronicle"}) {
            mvc.perform(get(path).with(user("author").roles("USER"))).andExpect(status().isOk());
        }
        mvc.perform(get("/admin").with(user("adminuser").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }
}
