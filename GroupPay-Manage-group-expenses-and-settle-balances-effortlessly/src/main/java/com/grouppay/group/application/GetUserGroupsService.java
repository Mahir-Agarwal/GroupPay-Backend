package com.grouppay.group.application;

import com.grouppay.group.domain.Group;
import com.grouppay.group.infrastructure.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetUserGroupsService {

    private final GroupRepository groupRepository;

    @Transactional(readOnly = true)
    public List<Group> getUserGroups(Long userId) {
        return groupRepository.findByMembers_User_Id(userId);
    }
}
