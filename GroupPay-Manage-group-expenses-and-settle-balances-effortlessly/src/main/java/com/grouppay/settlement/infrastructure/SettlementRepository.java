package com.grouppay.settlement.infrastructure;

import com.grouppay.settlement.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByGroupId(Long groupId);
    List<Settlement> findByPayerId(Long payerId);
    List<Settlement> findByPayeeId(Long payeeId);
}
