package com.tenure.domain.item.repository;

import java.time.LocalDate;

public record ItemLastWornDateRow(Long itemId, LocalDate wornDate) {
}
