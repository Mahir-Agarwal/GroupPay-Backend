package com.grouppay.group.application;

import com.grouppay.group.domain.Group;
import com.grouppay.group.domain.GroupMember;
import com.grouppay.group.infrastructure.GroupRepository;
import com.grouppay.notification.application.NotificationService;
import com.grouppay.notification.domain.NotificationType;
import com.grouppay.user.domain.User;
import com.grouppay.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddMemberService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Adds a user to an existing group.
     */
    @Transactional
    public GroupMember addMember(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if already member
        boolean isAlreadyMember = group.getMembers() != null && group.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId));
        
        if (isAlreadyMember) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .isAdmin(false)
                .build();

        if (group.getMembers() != null) {
            group.getMembers().add(member);
        }
        
        
        groupRepository.save(group);

        // Notify the user
        notificationService.createNotification(
                userId,
                "Added to Group",
                "You have been added to the group: " + group.getName(),
                NotificationType.SYSTEM
        );

        return member;
    }

    /**
     * Removes a user from a group.
     */
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        
        group.getMembers().removeIf(m -> m.getUser().getId().equals(userId));
        groupRepository.save(group);
    }
}
