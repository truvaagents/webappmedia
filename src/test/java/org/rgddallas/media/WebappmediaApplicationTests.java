package org.rgddallas.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rgddallas.media.entity.VideoPlayback;
import org.rgddallas.media.entity.VideoPlaybackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "logging.file.name="})
@Transactional
class WebappmediaApplicationTests {
    @TempDir
    static Path temp;

    @Autowired
    WebApplicationContext webContext;

    @Autowired
    VideoPlaybackRepository repository;

    MockMvc mvc;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws Exception {
        String url = "jdbc:h2:file:" + temp.resolve("imported");
        Path sql = temp.resolve("playback.sql");
        try (var export = WebappmediaApplicationTests.class.getResourceAsStream("/h2-1x-playback.sql")) {
            Files.copy(export, sql);
        }
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM '" + sql + "' FROM_1X");
        }
        Files.createDirectories(temp.resolve("audio"));
        Files.createDirectories(temp.resolve("video"));
        Files.createDirectories(temp.resolve("kirtan"));
        Files.writeString(temp.resolve("audio/AM_Arti.mp3"), "arti audio");
        Files.writeString(temp.resolve("video/001_first.mp4"), "first lecture");
        Files.writeString(temp.resolve("video/007_lecture.mp4"), "seventh lecture");
        Files.writeString(temp.resolve("kirtan/song.mov"), "kirtan video");
        registry.add("spring.datasource.url", () -> url);
        registry.add("media.audio-location", () -> temp.resolve("audio").toString());
        registry.add("media.video-location", () -> temp.resolve("video").toString());
        registry.add("media.video-kirtan-location", () -> temp.resolve("kirtan").toString());
    }

    @BeforeEach
    void setupMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    @Test
    void servesHomepageAndItsMediaWithEitherSlashForm() throws Exception {
        // MockMvc forwards the welcome page rather than running the container's second dispatch.
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"/api/audio/AM_Arti\"")))
                .andExpect(content().string(containsString("src=\"/api/video2\"")));

        for (String slash : new String[]{"", "/"}) {
            for (String audioRoute : new String[]{"/api/audio/", "/api/audio2/"}) {
                mvc.perform(get(audioRoute + "AM_Arti" + slash)).andExpect(status().isOk())
                        .andExpect(content().contentType("audio/mpeg")).andExpect(content().string("arti audio"));
                mvc.perform(put(audioRoute + "AM_Arti" + slash)).andExpect(status().isMethodNotAllowed());
            }
            mvc.perform(get("/api/video2" + slash)).andExpect(status().isOk())
                    .andExpect(content().string("seventh lecture"));
            mvc.perform(get("/api/video-kirtan" + slash)).andExpect(status().isOk())
                    .andExpect(content().string("kirtan video"));
            mvc.perform(put("/api/video2" + slash)).andExpect(status().isMethodNotAllowed());
            mvc.perform(put("/api/video-kirtan" + slash)).andExpect(status().isMethodNotAllowed());
            mvc.perform(get("/playback" + slash)).andExpect(status().isOk()).andExpect(content().string("8"));
        }
        VideoPlayback played = repository.findById(42L).orElseThrow();
        assertThat(played.getLastPlayed().toLocalDate()).isEqualTo(LocalDateTime.now().toLocalDate());
    }

    @Test
    void importedRowsAndNextGeneratedIdArePreserved() throws Exception {
        for (String slash : new String[]{"", "/"}) {
            mvc.perform(get("/playback/records" + slash)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(42))
                    .andExpect(jsonPath("$[0].fileSequence").value(7))
                    .andExpect(jsonPath("$[0].lastPlayed").value("2001-05-06T07:08:09.123456"))
                    .andExpect(jsonPath("$[1].id").value(87))
                    .andExpect(jsonPath("$[1].fileSequence").value(19))
                    .andExpect(jsonPath("$[1].lastPlayed").value("2002-06-07T08:09:10"));
            mvc.perform(put("/playback/records" + slash)).andExpect(status().isMethodNotAllowed());
        }
        VideoPlayback newRecord = new VideoPlayback();
        newRecord.setFileSequence(1L);
        assertThat(repository.save(newRecord).getId()).isEqualTo(101L);
    }

    @Test
    void playbackCommandsRetainPutAndBothSlashForms() throws Exception {
        for (String slash : new String[]{"", "/"}) {
            assertPlaybackCommand("/reset" + slash, "1");
            assertPlaybackCommand("/next" + slash, "2");
            assertPlaybackCommand("/prev" + slash, "1");
            assertPlaybackCommand("/set/12" + slash, "12");
            mvc.perform(get("/playback" + slash)).andExpect(status().isOk()).andExpect(content().string("12"));
            mvc.perform(put("/playback" + slash)).andExpect(status().isMethodNotAllowed());
        }
        assertThat(repository.findById(42L).orElseThrow().getFileSequence()).isEqualTo(12L);
        assertThat(repository.findById(87L).orElseThrow().getFileSequence()).isEqualTo(19L);
    }

    private void assertPlaybackCommand(String path, String sequence) throws Exception {
        mvc.perform(get("/playback" + path)).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/playback" + path)).andExpect(status().isOk()).andExpect(content().string(sequence));
    }
}
