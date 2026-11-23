package org.rgddallas.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rgddallas.media.entity.VideoPlayback;
import org.rgddallas.media.entity.VideoPlaybackRepository;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackPersistenceTests {

    @TempDir
    Path directory;

    @Test
    void importedLegacyRecordsAndIdSequenceSurviveRestart() throws Exception {
        String url = "jdbc:h2:" + directory.resolve("playback") + ";DB_CLOSE_ON_EXIT=FALSE";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM 'classpath:legacy-playback.sql' FROM_1X");
        }

        LocalDateTime importedTimestamp = LocalDateTime.of(2018, 6, 14, 10, 30);
        try (var application = start(url)) {
            var repository = application.getBean(VideoPlaybackRepository.class);
            VideoPlayback imported = repository.findById(42L).orElseThrow();
            assertThat(imported.getFileSequence()).isEqualTo(7L);
            assertThat(imported.getLastPlayed()).isEqualTo(importedTimestamp);

            imported.setFileSequence(8L);
            repository.save(imported);

            VideoPlayback newRecord = new VideoPlayback();
            newRecord.setFileSequence(1L);
            newRecord.setLastPlayed(importedTimestamp);
            assertThat(repository.save(newRecord).getId()).isEqualTo(43L);
        }

        try (var application = start(url)) {
            var repository = application.getBean(VideoPlaybackRepository.class);
            assertThat(repository.findById(42L).orElseThrow().getFileSequence()).isEqualTo(8L);
            assertThat(repository.findById(43L).orElseThrow().getLastPlayed()).isEqualTo(importedTimestamp);
        }
    }

    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(WebappmediaApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + url, "--logging.file.name=");
    }
}
