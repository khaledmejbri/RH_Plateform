package com.hr.identiteacces.controller;

import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/by-role")
    public List<UserResponse> getUsersByRole(@RequestParam String role) {
        return userService.getUsersByRole(role);
    }
}
