package com.staybook.favorite.dto;

import com.staybook.accommodation.dto.AccommodationDtos.AccommodationSummary;
import java.time.LocalDateTime;

/**
 * @param favoritedAt 찜한 시각
 */
public record FavoriteResponse(AccommodationSummary accommodation, LocalDateTime favoritedAt) {
}
