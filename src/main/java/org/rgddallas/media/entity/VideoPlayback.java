package org.rgddallas.media.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import java.time.LocalDateTime;

/**
 * Created by Neelabh on 6/14/2017.
 */
@Entity
public class VideoPlayback {

    @Id
    // Keep the Hibernate 5 sequence and allocation size used by existing databases.
    @SequenceGenerator(name = "playbackIds", sequenceName = "hibernate_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "playbackIds")
    private Long id;

    private Long fileSequence;

    private LocalDateTime lastPlayed;

    public Long getFileSequence() {
        return fileSequence;
    }

    public void setFileSequence(Long fileSequence) {
        this.fileSequence = fileSequence;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(LocalDateTime lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    @Override
    public String toString() {
        return "VideoPlayback{" +
                "fileSequence=" + fileSequence +
                '}';
    }
}
