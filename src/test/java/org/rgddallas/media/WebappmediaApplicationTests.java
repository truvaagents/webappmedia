package org.rgddallas.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rgddallas.media.entity.VideoPlayback;
import org.rgddallas.media.entity.VideoPlaybackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:playback-tests",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.file.name="
})
@AutoConfigureMockMvc
class WebappmediaApplicationTests {

    private static final byte[] FIRST_VIDEO = {1, 2, 3};
    private static final byte[] SECOND_VIDEO = {4, 5, 6};

    @TempDir
    static Path media;

    @Autowired
    MockMvc mvc;

    @Autowired
    VideoPlaybackRepository repository;

    @DynamicPropertySource
    static void mediaPaths(DynamicPropertyRegistry properties) {
        properties.add("media.video.location", () -> media.resolve("video").toString());
        properties.add("media.kirtan.location", () -> media.resolve("kirtan").toString());
        properties.add("media.audio.location", () -> media.resolve("audio").toString());
    }

    @BeforeEach
    void resetFixtures() throws Exception {
        repository.deleteAll();
        Files.write(Files.createDirectories(media.resolve("video/sub")).resolve("001_first.mp4"), FIRST_VIDEO);
        Files.write(media.resolve("video/002_second.mp4"), SECOND_VIDEO);
        Files.write(Files.createDirectories(media.resolve("kirtan")).resolve("clip.mov"), new byte[]{7});
        Files.write(Files.createDirectories(media.resolve("audio/sub")).resolve("Song.MP3"), new byte[]{8});
    }

    @Test
    void playbackControlsPersistSequenceAndKeepTheSameRecord() throws Exception {
        mvc.perform(get("/playback")).andExpect(status().isOk()).andExpect(content().string("0"));
        mvc.perform(put("/playback/reset")).andExpect(status().isOk()).andExpect(content().string("1"));
        Long id = repository.findAll().getFirst().getId();

        mvc.perform(put("/playback/next")).andExpect(status().isOk()).andExpect(content().string("2"));
        mvc.perform(put("/playback/prev")).andExpect(status().isOk()).andExpect(content().string("1"));
        mvc.perform(put("/playback/set/10")).andExpect(status().isOk()).andExpect(content().string("10"));
        mvc.perform(get("/playback")).andExpect(status().isOk()).andExpect(content().string("10"));
        mvc.perform(get("/playback/records")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.intValue()))
                .andExpect(jsonPath("$[0].fileSequence").value(10))
                .andExpect(jsonPath("$[0].lastPlayed").isString());

        VideoPlayback saved = repository.findById(id).orElseThrow();
        assertThat(saved.getLastPlayed()).isBefore(LocalDate.now().atStartOfDay());
    }

    @Test
    void dailyVideoAdvancesNextSequenceAndReplaysOnTheSameDay() throws Exception {
        mvc.perform(put("/playback/reset")).andExpect(status().isOk());
        mvc.perform(get("/api/video2")).andExpect(status().isOk()).andExpect(content().bytes(FIRST_VIDEO));
        mvc.perform(get("/api/video2")).andExpect(status().isOk()).andExpect(content().bytes(FIRST_VIDEO));
        VideoPlayback saved = repository.findAll().getFirst();
        assertThat(saved.getFileSequence()).isEqualTo(2L);
        assertThat(saved.getLastPlayed().toLocalDate()).isEqualTo(LocalDate.now());

        saved.setLastPlayed(LocalDateTime.now().minusDays(1));
        repository.save(saved);
        mvc.perform(get("/api/video2")).andExpect(status().isOk()).andExpect(content().bytes(SECOND_VIDEO));
        assertThat(repository.findById(saved.getId()).orElseThrow().getFileSequence()).isEqualTo(3L);
    }

    @Test
    void missingSequencePlaysFirstVideoWithoutChangingStoredState() throws Exception {
        mvc.perform(put("/playback/reset")).andExpect(status().isOk());
        mvc.perform(put("/playback/set/99")).andExpect(status().isOk());
        VideoPlayback before = repository.findAll().getFirst();

        mvc.perform(get("/api/video2")).andExpect(status().isOk()).andExpect(content().bytes(FIRST_VIDEO));

        VideoPlayback after = repository.findById(before.getId()).orElseThrow();
        assertThat(after.getFileSequence()).isEqualTo(99L);
        assertThat(after.getLastPlayed()).isEqualTo(before.getLastPlayed());
    }

    @Test
    void servesAudioAndKirtanFromTheirConfiguredDirectories() throws Exception {
        mvc.perform(get("/api/audio/song")).andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg")).andExpect(content().bytes(new byte[]{8}));
        mvc.perform(get("/api/audio/missing")).andExpect(status().isBadRequest())
                .andExpect(content().string("File Not Found"));
        mvc.perform(get("/api/video-kirtan")).andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{7}));
    }
}
