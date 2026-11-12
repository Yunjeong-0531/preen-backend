package com.staybook.review.controller;

import com.staybook.common.response.ApiResponse;
import com.staybook.review.spotify.SpotifyService;
import com.staybook.review.spotify.SpotifyService.Track;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
public class SpotifyController {

    private final SpotifyService spotifyService;

    /** 리뷰에 붙일 음악 검색. 로그인이 필요하다. */
    @GetMapping("/api/spotify/tracks/search")
    public ApiResponse<List<Track>> searchTracks(
            @RequestParam("q") @NotBlank(message = "검색어를 입력해 주세요.") @Size(max = 100) String query) {
        return ApiResponse.ok(spotifyService.searchTracks(query.trim()));
    }
}
