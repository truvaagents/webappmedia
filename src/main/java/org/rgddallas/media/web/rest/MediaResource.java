package org.rgddallas.media.web.rest;

import org.rgddallas.media.entity.VideoPlayback;
import org.rgddallas.media.entity.VideoPlaybackRepository;
import org.rgddallas.media.util.FileSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * This class contains resources the serves the media content - audio and video as REST controllers.
 * <p>
 * Created by Neelabh on 3/12/2017.
 */

@RestController
@RequestMapping("/api")
@CrossOrigin
public class MediaResource {
    public static final Logger log = LoggerFactory.getLogger(MediaResource.class);

    public static final String MP4_EXTN = ".mp4";
    public static final String MP3_EXTN = ".mp3";
    public static final String KIRTAN = ".mov";
    public static final String VIDEO_LOCATION = "/volume1/video/Intranet/";
    public static final String VIDEO_KIRTAN_LOCATION = "/volume1/video/Intranet/video_kirtan/";
    //public static final String VIDEO_KIRTAN_LOCATION = "\\\\192.168.1.144\\video\\Intranet\\video_kirtan\\";
    //public static final String VIDEO_LOCATION = "\\\\192.168.1.144\\video\\Intranet\\";
    public static final String AUDIO_LOCATION = "/volume1/music/Arti/";
    //public static final String AUDIO_LOCATION = "C:\\temp\\";

    private final VideoPlaybackRepository videoPlaybackRepo;
    private final String videoLocation;
    private final String videoKirtanLocation;
    private final String audioLocation;

    public MediaResource(VideoPlaybackRepository videoPlaybackRepo,
                         @Value("${media.video.location:" + VIDEO_LOCATION + "}") String videoLocation,
                         @Value("${media.kirtan.location:" + VIDEO_KIRTAN_LOCATION + "}") String videoKirtanLocation,
                         @Value("${media.audio.location:" + AUDIO_LOCATION + "}") String audioLocation) {
        this.videoPlaybackRepo = videoPlaybackRepo;
        this.videoLocation = videoLocation;
        this.videoKirtanLocation = videoKirtanLocation;
        this.audioLocation = audioLocation;
    }

    /**
     * Gets the audio file content from the server.
     *
     * @param fileName file to return, with no spaces
     * @return audio content as a byte stream
     */
    @GetMapping(value = "/audio/{fileName}", produces = "audio/mpeg")
    public ResponseEntity<byte[]> getAudio(@PathVariable String fileName) {
        log.info("Got Audio file request for file {}", fileName);

        return getMediaAsStream(audioLocation, fileName, MP3_EXTN);
    }

    /**
     * REST controller to return the content of the video file to be played. This is based on the current sequence
     * number stored in the DB. This number gets updated once in a day. If the request is received on same day
     * then same file is returned and the sequence remains unchanged.
     *
     */
    @GetMapping(value = "/video2", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void getTodaysVideoToPlay(OutputStream output) throws IOException {

        List<VideoPlayback> videoSeqList = videoPlaybackRepo.findAll();
        if (videoSeqList.isEmpty()) {
            throw new IllegalStateException("Initialize the playback sequence before requesting a video");
        }
        VideoPlayback videoPlayback = videoSeqList.get(0);

        long sequenceToUse = videoPlayback.getFileSequence();
        // Keep the legacy day-of-month comparison for stored playback state.
        if (LocalDateTime.now().getDayOfMonth() == videoPlayback.getLastPlayed().getDayOfMonth()) {
            sequenceToUse--;
        }

        String queryStr = "[0]*" + sequenceToUse + "_.*";
        log.debug("File pattern to search : {}", queryStr);
        File file = new File(getFileAsInputStream(videoLocation, queryStr, MP4_EXTN));

        if (file.canRead()) {
            updateSequence(videoPlayback, sequenceToUse + 1L);
        } else { // Play the first video without changing the saved sequence.
            log.info("File not found for sequence {}, defaulting to Sequence number 1", sequenceToUse);

            file = new File(getFileAsInputStream(videoLocation, "[0]*1_.*", MP4_EXTN));
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            inputStream.transferTo(output);
        }
    }

    /**
     * REST Endpoint to send the video kirtna file stream.
     *
     * @param output
     * @throws IOException
     */
    @GetMapping(value = "/video-kirtan", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void getVideoToPlay(OutputStream output) throws IOException {

        File folder = new File(videoKirtanLocation);
        File[] files = folder.listFiles();

        int totalFiles = files.length;
        log.info("Total files inside the folder - {}", totalFiles);

        Random random = new Random();
        int index = random.nextInt(totalFiles);
        log.info("File - {}", index);

        try (InputStream inputStream = new FileInputStream(files[index])) {
            inputStream.transferTo(output);
        }
    }

    /**
     * Increments the current sequence and save into the DB.
     *
     * @param videoPlayback the video playback entity
     * @param sequenceToSet optional sequence to set
     * @return the updated sequence
     */
    public Long updateSequence(VideoPlayback videoPlayback, Long sequenceToSet) {
        if (sequenceToSet == null) {
            sequenceToSet = videoPlayback.getFileSequence() + 1L;
        }

        videoPlayback.setFileSequence(sequenceToSet);
        videoPlayback.setLastPlayed(LocalDateTime.now());

        videoPlayback = videoPlaybackRepo.save(videoPlayback);

        log.debug("Incremented Sequence number and saved : {}", videoPlayback.getFileSequence());

        return videoPlayback.getFileSequence();
    }


    /**
     * Utility method to get the media content from file system and prepare a response entity.
     *
     * @param location      directory to search into
     * @param fileName      file name
     * @param fileExtension file extension - mp3 or mp4
     * @return the response entity with file content, if found
     */
    public ResponseEntity<byte[]> getMediaAsStream(String location, String fileName, String fileExtension) {
        ResponseEntity<byte[]> response;

        try {
            byte[] byteStream = getFileStreamAsByteArray(location, fileName, fileExtension);
            if (byteStream != null) {
                response = ResponseEntity.ok(byteStream);
            } else {
                throw new FileNotFoundException();
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            log.warn("No media file found for {}", fileName);
            response = ResponseEntity.badRequest().body("File Not Found".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("IO Exception ", e);
            response = ResponseEntity.internalServerError().body("Internal Server Error".getBytes(StandardCharsets.UTF_8));
        }

        return response;
    }

    /**
     * Utility method.
     *
     * @param fileLocation
     * @param fileName
     * @param fileExtension
     * @return
     * @throws IOException
     */
    public byte[] getFileStreamAsByteArray(String fileLocation, String fileName, String fileExtension) throws IOException {
        FileSearchService fileSearchService = new FileSearchService();

        String fileToSearch = fileName + fileExtension;
        log.debug("file to search {}", fileToSearch);
        List<String> filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileToSearch,
                MP4_EXTN.equals(fileExtension));

        log.debug("files found : {}", filesFound);
        return filesFound.isEmpty() ? null : Files.readAllBytes(Path.of(filesFound.get(0)));
    }

    /**
     * @param fileLocation
     * @param fileName
     * @param fileExtension
     * @return
     * @throws IOException
     */
    public String getFileAsInputStream(String fileLocation, String fileName, String fileExtension) throws IOException {
        FileSearchService fileSearchService = new FileSearchService();

        String fileFound = "";

        String fileToSearch = fileName + fileExtension;
        log.debug("file to search {}", fileToSearch);
        List<String> filesFound = null;

        if (MP4_EXTN.equals(fileExtension)) {
            filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileToSearch, true);

        } else if (KIRTAN.equals(fileExtension)) {
            filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileLocation, true);
        }

        else {
            filesFound = fileSearchService.searchDirectory(new File(fileLocation), fileToSearch, false);
        }

        log.debug("files found : {}", filesFound);

        filesFound.forEach(file -> log.debug("List of files found {}", file));

        if (!CollectionUtils.isEmpty(filesFound)) {
            fileFound = filesFound.get(0);

        }

        return fileFound;
    }
}
