package org.rgddallas.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rgddallas.media.entity.VideoPlayback;
import org.rgddallas.media.entity.VideoPlaybackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:media-http",
        "logging.file.name="
})
class WebappmediaApplicationTests {

    @TempDir
    static Path media;

    @Value("${local.server.port}")
    int port;

    @Autowired
    VideoPlaybackRepository playback;

    @DynamicPropertySource
    static void mediaPaths(DynamicPropertyRegistry registry) {
        registry.add("media.audio-location", () -> media.resolve("audio").toString());
        registry.add("media.video-location", () -> media.resolve("video").toString());
        registry.add("media.video-kirtan-location", () -> media.resolve("kirtan").toString());
    }

    @BeforeEach
    void clearTestPlayback() {
        playback.deleteAll();
    }

    @Test
    void playbackControlsPersistSequenceAndBackdateLastPlayed() throws Exception {
        assertThat(request("GET", "/playback", 200).body()).isEqualTo("0");
        assertThat(request("PUT", "/playback/next", 200).body()).isEqualTo("1");
        long id = playback.findAll().getFirst().getId();
        assertThat(request("PUT", "/playback/next", 200).body()).isEqualTo("2");
        assertThat(request("PUT", "/playback/prev", 200).body()).isEqualTo("1");
        assertThat(request("PUT", "/playback/set/7", 200).body()).isEqualTo("7");
        assertThat(request("GET", "/playback", 200).body()).isEqualTo("7");

        VideoPlayback row = playback.findById(id).orElseThrow();
        assertThat(row.getLastPlayed().toLocalDate()).isEqualTo(LocalDate.now().minusDays(2));
        assertThat(request("GET", "/playback/records", 200).body()).contains("\"fileSequence\":7",
                "\"id\":" + id, "\"lastPlayed\":\"" + row.getLastPlayed().toLocalDate());
        assertThat(request("PUT", "/playback/reset", 200).body()).isEqualTo("1");
        assertThat(playback.findAll()).singleElement().satisfies(reset -> {
            assertThat(reset.getId()).isEqualTo(id);
            assertThat(reset.getFileSequence()).isEqualTo(1L);
        });
    }

    @Test
    void videoRepeatsSameDayAdvancesOnNewDayAndFallsBackWithoutChangingState() throws Exception {
        Path videos = Files.createDirectories(media.resolve("video/subdirectory"));
        Files.writeString(videos.resolve("001_first.mp4"), "first video");
        Files.writeString(videos.resolve("002_second.mp4"), "second video");

        assertThat(request("GET", "/api/video2", 200).body()).isEqualTo("first video");
        assertThat(request("GET", "/playback", 200).body()).isEqualTo("2");
        assertThat(request("GET", "/api/video2", 200).body()).isEqualTo("first video");
        assertThat(request("GET", "/playback", 200).body()).isEqualTo("2");

        VideoPlayback row = playback.findAll().getFirst();
        row.setLastPlayed(LocalDateTime.now().minusDays(1));
        playback.save(row);
        assertThat(request("GET", "/api/video2", 200).body()).isEqualTo("second video");
        assertThat(request("GET", "/playback", 200).body()).isEqualTo("3");

        row = playback.findAll().getFirst();
        row.setLastPlayed(LocalDateTime.now().minusDays(1).withNano(0));
        playback.save(row);
        assertThat(request("GET", "/api/video2", 200).body()).isEqualTo("first video");
        VideoPlayback afterFallback = playback.findById(row.getId()).orElseThrow();
        assertThat(afterFallback.getFileSequence()).isEqualTo(3L);
        assertThat(afterFallback.getLastPlayed()).isEqualTo(row.getLastPlayed());
    }

    @Test
    void audioAndKirtanEndpointsServeFilesAndMissingAudioStillReturnsBadRequest() throws Exception {
        Path audio = Files.createDirectories(media.resolve("audio/subdirectory"));
        Files.writeString(audio.resolve("Arti.MP3"), "audio contents");
        Path kirtan = Files.createDirectories(media.resolve("kirtan"));
        Files.writeString(kirtan.resolve("video.mov"), "kirtan contents");

        HttpResponse<String> response = request("GET", "/api/audio/arti", 200);
        assertThat(response.body()).isEqualTo("audio contents");
        assertThat(response.headers().firstValue("Content-Type")).hasValue("audio/mpeg");
        assertThat(request("GET", "/api/audio/missing", 400).body()).isEqualTo("File Not Found");
        assertThat(request("GET", "/api/video-kirtan", 200).body()).isEqualTo("kirtan contents");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/"})
    void legacyPlaybackRoutesServeAndResetThePersistedRow(String suffix) throws Exception {
        VideoPlayback row = new VideoPlayback();
        row.setFileSequence(7L);
        row.setLastPlayed(LocalDateTime.now().minusDays(1));
        long id = playback.save(row).getId();

        assertThat(request("GET", "/playback" + suffix, 200).body()).isEqualTo("7");
        assertThat(request("GET", "/playback/records" + suffix, 200).body())
                .contains("\"fileSequence\":7", "\"id\":" + id);
        assertThat(request("PUT", "/playback/reset" + suffix, 200).body()).isEqualTo("1");
        assertThat(playback.findById(id).orElseThrow().getFileSequence()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/"})
    void legacyMediaRoutesServeFiles(String suffix) throws Exception {
        Path audio = Files.createDirectories(media.resolve("audio"));
        Files.writeString(audio.resolve("AM_Arti.mp3"), "morning audio");
        Path videos = Files.createDirectories(media.resolve("video/subdirectory"));
        Files.writeString(videos.resolve("001_first.mp4"), "first video");
        Path kirtan = Files.createDirectories(media.resolve("kirtan"));
        Files.writeString(kirtan.resolve("video.mov"), "kirtan contents");

        assertThat(request("GET", "/api/audio/AM_Arti" + suffix, 200).body()).isEqualTo("morning audio");
        assertThat(request("GET", "/api/video2" + suffix, 200).body()).isEqualTo("first video");
        assertThat(request("GET", "/api/video-kirtan" + suffix, 200).body()).isEqualTo("kirtan contents");
    }

    @Test
    void homepageUsesTheLocalAudioRouteAndKeepsTheVideoSource() throws Exception {
        assertThat(request("GET", "/", 200).body())
                .contains("src=\"/api/audio/AM_Arti\" type=\"audio/mpeg\"",
                        "src=\"http://localhost:8080/api/video2\" type=\"video/mp4\"")
                .doesNotContain("/api/audio2/");
    }

    private HttpResponse<String> request(String method, String path, int status) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
            assertThat(response.headers().firstValue("Location")).isEmpty();
            return response;
        }
    }
}
