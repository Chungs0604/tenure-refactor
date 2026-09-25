package com.tenure.domain.listing.repository;

import com.tenure.domain.listing.entity.ItemListing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemListingRepository extends JpaRepository<ItemListing, Long> {
}
