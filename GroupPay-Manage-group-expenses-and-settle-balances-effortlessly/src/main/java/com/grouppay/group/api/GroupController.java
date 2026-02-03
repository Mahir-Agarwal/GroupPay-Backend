package com.grouppay.group.api;

import com.grouppay.group.application.AddMemberService;
import com.grouppay.group.application.CreateGroupService;
import com.grouppay.group.domain.Group;
import com.grouppay.group.domain.GroupMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final CreateGroupService createGroupService;
    private final AddMemberService addMemberService;
    private final com.grouppay.group.application.GetUserGroupsService getUserGroupsService;
    private final com.grouppay.group.application.DeleteGroupService deleteGroupService;

    /**
     * Creates a new group.
     * 
     * @param name        Name of the group.
     * @param description Optional description.
     * @param creatorId   ID of the creating user.
     * @return The created Group.
     */
    @PostMapping
    public ResponseEntity<Group> createGroup(@RequestParam String name, @RequestParam(required = false) String description, @RequestParam Long creatorId) {
        return ResponseEntity.ok(createGroupService.createGroup(name, description, creatorId));
    }

    /**
     * Adds a member to the group.
     * 
     * @param groupId ID of the group.
     * @param userId  ID of the user to add.
     * @return The created GroupMember.
     */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupMember> addMember(@PathVariable Long groupId, @RequestParam Long userId) {
        return ResponseEntity.ok(addMemberService.addMember(groupId, userId));
    }

    /**
     * Get groups for a user.
     * @param userId ID of the user.
     * @return List of groups.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<java.util.List<Group>> getUserGroups(@PathVariable Long userId) {
        return ResponseEntity.ok(getUserGroupsService.getUserGroups(userId));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        addMemberService.removeMember(groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        deleteGroupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }
}
