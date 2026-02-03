package com.grouppay.group.infrastructure;

import com.grouppay.group.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    // Custom queries can be added here
    Optional<Group> findByName(String name);
    List<Group> findByMembers_User_Id(Long userId);
}
