package com.grouppay.group.application;

import com.grouppay.group.domain.Group;
import com.grouppay.group.domain.GroupMember;
import com.grouppay.group.infrastructure.GroupRepository;
import com.grouppay.user.domain.User;
import com.grouppay.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateGroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new group and automatically adds the creator as an admin member.
     * <p>
     * Validation:
     * - Name cannot be empty.
     * - Creator must exist.
     * </p>
     *
     * @param name        The name of the group.
     * @param description Brief description of the group.
     * @param creatorId   The ID of the user creating the group.
     * @return The saved Group entity with the creator added as a member.
     */
    @Transactional
    public Group createGroup(String name, String description, Long creatorId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Group name cannot be empty");
        }
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = Group.builder()
                .name(name)
                .description(description)
                .createdBy(creator)
                .build();

        GroupMember adminMember = GroupMember.builder()
                .group(group)
                .user(creator)
                .isAdmin(true)
                .build();
        
        group.setMembers(java.util.List.of(adminMember));
        
        Group savedGroup = groupRepository.save(group);
        return savedGroup;
    }
}
