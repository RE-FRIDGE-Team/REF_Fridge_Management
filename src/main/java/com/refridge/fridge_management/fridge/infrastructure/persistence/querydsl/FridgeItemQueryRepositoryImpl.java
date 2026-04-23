package com.refridge.fridge_management.fridge.infrastructure.persistence.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.refridge.fridge_management.fridge.domain.FridgeItem;
import com.refridge.fridge_management.fridge.domain.QFridgeItem;
import com.refridge.fridge_management.fridge.domain.vo.ItemStatus;
import com.refridge.fridge_management.fridge.domain.vo.SectionType;
import com.refridge.fridge_management.fridge.domain.repository.FridgeItemQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * FridgeItemQueryRepository QueryDSL 구현체.
 *
 * <h2>네이밍 규칙</h2>
 * Spring Data Custom Repository 패턴:
 * 인터페이스 이름 + "Impl" 접미사를 반드시 붙여야
 * Spring이 자동으로 {@link com.refridge.fridge_management.fridge.domain.repository.FridgeItemRepository}에
 * 주입한다.
 *
 * <h2>Q클래스 생성</h2>
 * {@code QFridgeItem}은 Gradle annotationProcessor(querydsl-apt:jakarta)가
 * 빌드 시 {@code build/generated/sources/annotationProcessor}에 자동 생성한다.
 * IDE에서 인식 안 될 경우: Gradle → compileJava 실행 후 "Generated Sources Root"로 마크.
 *
 * <h2>동적 조건 — BooleanBuilder</h2>
 * null 파라미터는 조건에서 제외되므로 항상 안전하게 사용 가능.
 *
 * @author 승훈
 * @since 2025-06-01
 * @see FridgeItemQueryRepository
 */
@Repository
@RequiredArgsConstructor
public class FridgeItemQueryRepositoryImpl implements FridgeItemQueryRepository {

    private final JPAQueryFactory queryFactory;

    // QueryDSL 생성 Q클래스 (빌드 후 사용 가능)
    private static final QFridgeItem fi = QFridgeItem.fridgeItem;

    // ── 냉장고 아이템 동적 필터 조회 ──────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <h3>쿼리 전략</h3>
     * <ul>
     *   <li>Count 쿼리와 Content 쿼리를 분리하여 Count에서 불필요한 JOIN/ORDER BY를 제거한다.</li>
     *   <li>status 파라미터가 null이면 ACTIVE만 기본 조회 (UI 기본값).</li>
     *   <li>keyword는 LIKE '%keyword%' (대소문자 무시).</li>
     * </ul>
     */
    @Override
    public Page<FridgeItem> searchItems(
            String fridgeId,
            SectionType sectionType,
            ItemStatus status,
            String keyword,
            Pageable pageable
    ) {
        BooleanBuilder where = buildSearchCondition(fridgeId, sectionType, status, keyword);

        // Content 쿼리
        JPAQuery<FridgeItem> contentQuery = queryFactory
                .selectFrom(fi)
                .where(where)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        applySorting(contentQuery, pageable.getSort());

        List<FridgeItem> content = contentQuery.fetch();

        // Count 쿼리 (ORDER BY 없음 — 성능)
        // fetchOne()은 결과 없을 시 null 반환 → Long으로 받아 null-safe 처리
        Long total = queryFactory
                .select(fi.count())
                .from(fi)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    // ── 유통기한 임박 아이템 조회 ─────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <h3>인덱스 활용</h3>
     * {@code idx_fi_fridge_status (fridge_id, status)} →
     * fridge_id + status = ACTIVE 필터링 후
     * {@code idx_fi_expires_at (expires_at)} 또는 복합 인덱스 활용.
     */
    @Override
    public List<FridgeItem> findNearExpiryItems(String fridgeId, LocalDate today, int thresholdDays) {
        return queryFactory
                .selectFrom(fi)
                .where(
                        fi.fridgeId.eq(fridgeId),
                        fi.status.eq(ItemStatus.ACTIVE),
                        fi.expirationInfo.expiresAt.between(today, today.plusDays(thresholdDays))
                )
                .orderBy(fi.expirationInfo.expiresAt.asc())
                .fetch();
    }

    // ── 회원 기간별 소비/폐기 아이템 조회 ────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <h3>인덱스 활용</h3>
     * {@code idx_fi_member_status (member_id, status, expires_at)}.
     * statuses는 IN 절로 변환.
     */
    @Override
    public List<FridgeItem> findByMemberAndPeriod(
            String memberId,
            LocalDate from,
            LocalDate to,
            List<ItemStatus> statuses
    ) {
        return queryFactory
                .selectFrom(fi)
                .where(
                        fi.memberId.eq(memberId),
                        fi.status.in(statuses),
                        fi.expirationInfo.expiresAt.between(from, to)
                )
                .orderBy(fi.expirationInfo.expiresAt.desc())
                .fetch();
    }

    // ── private 헬퍼 ─────────────────────────────────────────────────

    private BooleanBuilder buildSearchCondition(
            String fridgeId,
            SectionType sectionType,
            ItemStatus status,
            String keyword
    ) {
        BooleanBuilder builder = new BooleanBuilder();

        // fridgeId는 필수 조건
        builder.and(fi.fridgeId.eq(fridgeId));

        // status null이면 ACTIVE 기본값
        ItemStatus effectiveStatus = (status != null) ? status : ItemStatus.ACTIVE;
        builder.and(fi.status.eq(effectiveStatus));

        // 구역 필터 (선택)
        if (sectionType != null) {
            builder.and(fi.sectionType.eq(sectionType));
        }

        // 식품명 키워드 (선택, LIKE 검색)
        if (StringUtils.hasText(keyword)) {
            builder.and(fi.groceryItemRef.name.containsIgnoreCase(keyword));
        }

        return builder;
    }

    /**
     * Pageable Sort → QueryDSL OrderSpecifier 변환.
     * 지원 정렬 필드: expiresAt, purchasePrice, groceryItemName
     */
    private void applySorting(JPAQuery<FridgeItem> query, Sort sort) {
        if (sort.isUnsorted()) {
            query.orderBy(fi.expirationInfo.expiresAt.asc()); // 기본: 유통기한 오름차순
            return;
        }
        sort.forEach(order -> {
            boolean asc = order.isAscending();
            switch (order.getProperty()) {
                case "expiresAt"        -> query.orderBy(asc
                        ? fi.expirationInfo.expiresAt.asc()
                        : fi.expirationInfo.expiresAt.desc());
                case "purchasePrice"    -> query.orderBy(asc
                        ? fi.purchasePrice.amount.asc()
                        : fi.purchasePrice.amount.desc());
                case "groceryItemName"  -> query.orderBy(asc
                        ? fi.groceryItemRef.name.asc()
                        : fi.groceryItemRef.name.desc());
                default -> query.orderBy(fi.expirationInfo.expiresAt.asc());
            }
        });
    }
}