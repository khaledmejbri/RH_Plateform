package com.hr.identiteacces.controller;

import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @Value("${app.internal-api.token}")
    private String internalApiToken;

    @GetMapping("/by-role")
    public List<UserResponse> getUsersByRole(@RequestParam String role,
                                             @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalApiToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
        return userService.getUsersByRole(role);
    }
}
