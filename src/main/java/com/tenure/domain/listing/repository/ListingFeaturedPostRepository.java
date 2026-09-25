package com.tenure.domain.listing.repository;

import com.tenure.domain.listing.entity.ListingFeaturedPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingFeaturedPostRepository extends JpaRepository<ListingFeaturedPost, Long> {
}
