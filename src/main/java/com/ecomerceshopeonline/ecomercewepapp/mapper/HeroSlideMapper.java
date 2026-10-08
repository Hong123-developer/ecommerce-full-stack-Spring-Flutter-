package com.ecomerceshopeonline.ecomercewepapp.mapper;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.HeroSlideRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.HeroSlideResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.HeroSlide;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class HeroSlideMapper {

    public HeroSlideResponse toResponse(HeroSlide slide) {
        return new HeroSlideResponse(
                slide.getId(),
                slide.getEyebrow(),
                slide.getTitle(),
                slide.getSubtitle(),
                slide.getImageUrl(),
                slide.getImageAlt(),
                slide.getPrimaryLabel(),
                slide.getPrimaryLink(),
                slide.getSecondaryLabel(),
                slide.getSecondaryLink(),
                slide.getCard1ImageUrl(),
                slide.getCard1Title(),
                slide.getCard1Caption(),
                slide.getCard1Link(),
                slide.getCard2ImageUrl(),
                slide.getCard2Title(),
                slide.getCard2Caption(),
                slide.getCard2Link(),
                slide.getAccentColor(),
                slide.getSortOrder(),
                slide.isEnabled(),
                slide.getCreatedAt(),
                slide.getUpdatedAt());
    }

    /** {@code sortOrder} is resolved by the caller, which knows where "the end" is. */
    public HeroSlide toEntity(HeroSlideRequest request, int sortOrder) {
        return HeroSlide.builder()
                .eyebrow(clean(request.eyebrow()))
                .title(clean(request.title()))
                .subtitle(clean(request.subtitle()))
                .imageUrl(clean(request.imageUrl()))
                .imageAlt(clean(request.imageAlt()))
                .primaryLabel(clean(request.primaryLabel()))
                .primaryLink(clean(request.primaryLink()))
                .secondaryLabel(clean(request.secondaryLabel()))
                .secondaryLink(clean(request.secondaryLink()))
                .card1ImageUrl(clean(request.card1ImageUrl()))
                .card1Title(clean(request.card1Title()))
                .card1Caption(clean(request.card1Caption()))
                .card1Link(clean(request.card1Link()))
                .card2ImageUrl(clean(request.card2ImageUrl()))
                .card2Title(clean(request.card2Title()))
                .card2Caption(clean(request.card2Caption()))
                .card2Link(clean(request.card2Link()))
                .accentColor(cleanColor(request.accentColor()))
                .sortOrder(sortOrder)
                .enabled(request.enabled() == null || request.enabled())
                .build();
    }

    /** In-place update of a managed entity - id and audit columns are left alone. */
    public void apply(HeroSlideRequest request, HeroSlide target) {
        target.setEyebrow(clean(request.eyebrow()));
        target.setTitle(clean(request.title()));
        target.setSubtitle(clean(request.subtitle()));
        target.setImageUrl(clean(request.imageUrl()));
        target.setImageAlt(clean(request.imageAlt()));
        target.setPrimaryLabel(clean(request.primaryLabel()));
        target.setPrimaryLink(clean(request.primaryLink()));
        target.setSecondaryLabel(clean(request.secondaryLabel()));
        target.setSecondaryLink(clean(request.secondaryLink()));
        target.setCard1ImageUrl(clean(request.card1ImageUrl()));
        target.setCard1Title(clean(request.card1Title()));
        target.setCard1Caption(clean(request.card1Caption()));
        target.setCard1Link(clean(request.card1Link()));
        target.setCard2ImageUrl(clean(request.card2ImageUrl()));
        target.setCard2Title(clean(request.card2Title()));
        target.setCard2Caption(clean(request.card2Caption()));
        target.setCard2Link(clean(request.card2Link()));
        target.setAccentColor(cleanColor(request.accentColor()));
        if (request.sortOrder() != null) {
            target.setSortOrder(request.sortOrder());
        }
        if (request.enabled() != null) {
            target.setEnabled(request.enabled());
        }
    }

    /** Trims, and stores blank as null so the storefront can simply test "is it set". */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** {@link #clean} plus upper case, so "#d97706" and "#D97706" are stored as one color. */
    private static String cleanColor(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }
}
