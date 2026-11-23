package org.rgddallas.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Created by Neelabh on 6/14/2017.
 */
@Entity
@Table(name = "video_playback")
public class VideoPlayback {

    @Id
    // Reuse the original Hibernate sequence and its increment of one.
    @SequenceGenerator(name = "playback_id", sequenceName = "hibernate_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "playback_id")
    private Long id;

    @Column(name = "file_sequence")
    private Long fileSequence;

    @Column(name = "last_played")
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
