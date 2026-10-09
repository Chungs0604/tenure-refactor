package com.tenure.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.category.enums.MeasurementField;
import com.tenure.domain.category.repository.CategoryRepository;
import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.entity.ItemMeasurement;
import com.tenure.domain.item.enums.WearTarget;
import com.tenure.domain.item.repository.ItemMeasurementRepository;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실측 교체가 실제 flush 순서에서도 안전한지 확인한다.
 *
 * 기존 행을 지우고 새 행을 넣는 두 작업이 같은 트랜잭션 안에서 벌어진다. Mockito 단위 테스트는
 * flush 순서를 흉내내지 못해 이 경로를 통과시켰었다 — INSERT 가 DELETE 보다 먼저 나가면
 * uk_item_measurement_item 에 걸려 500 이 난다. 실제 Hibernate 세션으로만 재현/고정된다.
 */
@DisplayName("실측 교체 — flush 순서")
class ItemFormApplierIntegrationTest extends IntegrationTestSupport {

	private static final Long CATEGORY_ID = 116L;

	@Autowired private ItemFormApplier itemFormApplier;
	@Autowired private ItemRepository itemRepository;
	@Autowired private ItemMeasurementRepository itemMeasurementRepository;
	@Autowired private CategoryRepository categoryRepository;
	@Autowired private UserRepository userRepository;

	private User owner;
	private Item item;
	private Category category;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		owner = userRepository.save(User.signUp(
				"form-applier-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "form-applier-%s".formatted(tag), null, Gender.MALE, 170, 64));

		category = categoryRepository.findLeafWithParent(CATEGORY_ID).orElseThrow();

		item = itemRepository.save(Item.register(owner, category, "LVC 1955 501", "Levis", "L",
				WearTarget.MALE, "item/cover.jpg", LocalDate.of(2026, 4, 20)));
	}

	@AfterEach
	void tearDown() {
		itemMeasurementRepository.deleteByItemId(item.getId());
		itemRepository.deleteById(item.getId());
		userRepository.deleteById(owner.getId());
	}

	@Test
	@Transactional
	@DisplayName("기존 실측이 있는 아이템을 다시 교체해도 유니크 제약에 걸리지 않는다")
	void replaceMeasurements_existingRow_doesNotViolateUniqueConstraint() {
		itemFormApplier.replaceMeasurements(item, category,
				Map.of(MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(45.0)));

		assertThatCode(() -> itemFormApplier.replaceMeasurements(item, category,
				Map.of(MeasurementField.SHOULDER_WIDTH, BigDecimal.valueOf(47.5))))
				.doesNotThrowAnyException();

		ItemMeasurement result = itemMeasurementRepository.findByItemId(item.getId()).orElseThrow();
		assertThat(result.getShoulderWidth()).isEqualByComparingTo("47.5");
	}
}
