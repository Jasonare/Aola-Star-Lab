package com.aola.lab;

import com.aola.lab.Model.Folder;
import com.aola.lab.Model.Friendship;
import com.aola.lab.Model.Kind;
import com.aola.lab.Model.Post;
import com.aola.lab.Model.ResourceFile;
import com.aola.lab.Model.Review;
import com.aola.lab.Model.User;
import com.aola.lab.Model.Visibility;
import com.aola.lab.Repositories.Folders;
import com.aola.lab.Repositories.Friendships;
import com.aola.lab.Repositories.Posts;
import com.aola.lab.Repositories.Resources;
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
        "app.storage=./target/test-uploads"
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

        var session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session.getAttribute("showLoginWelcome")).isEqualTo(true);
        mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "这里是Aola Star Lab，是一个奥拉星怀旧社区网站，目前包括发布文章、发布视频、资源下载等功能，后续会持续更新，感谢大家的支持！开发者：Jasonare")));
        mvc.perform(get("/").session((jakarta.servlet.http.HttpSession) session))
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
    void filesRequirePermissionAndValidateTypes() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "image.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1});
        FileStore.Stored stored = files.save(image, "image");
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
                assertThat(resource.isVisible()).isFalse();
                mvc.perform(get("/resources/{id}/file", resource.getId())).andExpect(status().is3xxRedirection());
                mvc.perform(get("/resources/{id}/file", resource.getId()).with(user("other").roles("USER")))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/resources/{id}/file", resource.getId()).with(user("adminuser").roles("ADMIN")))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Disposition",
                                org.hamcrest.Matchers.startsWith("attachment;")))
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                        .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                        .andExpect(content().contentType("application/octet-stream"))
                        .andExpect(content().bytes(file.getBytes()));
                resource.setVisible(true);
                resources.saveAndFlush(resource);
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
        mvc.perform(get("/posts").with(user("other").roles("USER")).param("q", "奥拉星"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("奥拉星新篇")));
        MockMultipartFile video = new MockMultipartFile("file", "demo.mp4", "video/mp4",
                new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});
        mvc.perform(multipart("/posts/save").file(video).with(user("author").roles("USER")).with(csrf())
                .param("kind", "VIDEO").param("title", "视频").param("visibility", "FRIENDS"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/folders").with(user("adminuser").roles("ADMIN")).with(csrf()).param("name", "资源"))
                .andExpect(status().is3xxRedirection());
        Long folderId = folders.findAll().get(0).getId();
        MockMultipartFile pdf = new MockMultipartFile("file", "guide.pdf", "application/pdf", "%PDF-1.4".getBytes());
        mvc.perform(multipart("/admin/resources").file(pdf).with(user("adminuser").roles("ADMIN")).with(csrf())
                .param("folderId", folderId.toString()))
                .andExpect(status().is3xxRedirection());
        ResourceFile resource = resources.findAll().get(0);
        assertThat(resource.isVisible()).isFalse();
        mvc.perform(post("/admin/resources/{id}/visibility", resource.getId())
                .with(user("adminuser").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(resources.findById(resource.getId()).orElseThrow().isVisible()).isTrue();
        mvc.perform(get("/posts/{id}/edit", blog.getId()).with(user("author").roles("USER")))
                .andExpect(status().isOk());
    }

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
    void pagesRenderForAuthenticatedUsers() throws Exception {
        createPost(Review.APPROVED, Visibility.PUBLIC);
        for (String path : new String[]{"/", "/friends", "/posts", "/posts/new", "/resources"}) {
            mvc.perform(get(path).with(user("author").roles("USER"))).andExpect(status().isOk());
        }
        mvc.perform(get("/admin").with(user("adminuser").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }
}
