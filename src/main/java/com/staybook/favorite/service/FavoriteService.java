package com.staybook.favorite.service;

import com.staybook.accommodation.dto.AccommodationDtos.AccommodationSummary;
import com.staybook.accommodation.service.AccommodationQueryService;
import com.staybook.common.exception.BaseException;
import com.staybook.common.response.PageResponse;
import com.staybook.favorite.domain.Favorite;
import com.staybook.favorite.dto.FavoriteResponse;
import com.staybook.favorite.exception.FavoriteErrorCode;
import com.staybook.favorite.repository.FavoriteRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final int MAX_PAGE_SIZE = 50;

    private final FavoriteRepository favoriteRepository;
    private final AccommodationQueryService accommodationQueryService;
    private final Clock clock;

    /** 찜 추가. 이미 찜한 숙소를 다시 찜해도 에러 없이 성공이다. 공개된 숙소만 찜할 수 있다. */
    @Transactional
    public void add(Long userId, Long accommodationId) {
        if (!accommodationQueryService.existsListed(accommodationId)) {
            throw new BaseException(FavoriteErrorCode.ACCOMMODATION_NOT_FOUND);
        }
        favoriteRepository.insertIfAbsent(userId, accommodationId, LocalDateTime.now(clock));
    }

    /** 찜 삭제. 없는 찜을 지워도 에러 없이 성공이다. */
    @Transactional
    public void remove(Long userId, Long accommodationId) {
        favoriteRepository.deleteByUserAndAccommodation(userId, accommodationId);
    }

    /** 내 찜 목록, 최근에 찜한 순. 찜한 뒤 숨겨진 숙소는 목록에서 빠진다. */
    @Transactional(readOnly = true)
    public PageResponse<FavoriteResponse> getMine(Long userId, int page, int size) {
        Page<Favorite> favorites = favoriteRepository.findByUserId(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, AccommodationSummary> summaries = accommodationQueryService.getListedSummaries(
                favorites.getContent().stream().map(Favorite::getAccommodationId).toList());
        List<FavoriteResponse> content = favorites.getContent().stream()
                .filter(f -> summaries.containsKey(f.getAccommodationId()))
                .map(f -> new FavoriteResponse(summaries.get(f.getAccommodationId()), f.getCreatedAt()))
                .toList();
        return new PageResponse<>(content, favorites.getNumber(), favorites.getSize(),
                favorites.getTotalElements(), favorites.getTotalPages(), favorites.hasNext());
    }
}
