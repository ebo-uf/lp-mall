package mall.user.controller;

import lombok.RequiredArgsConstructor;
import mall.common.dto.UserResponseDto;
import mall.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponseDto findById(@RequestHeader("Authorization") String authHeader) {
        String accessToken = authHeader.substring(7);
        return userService.findById(accessToken);
    }

    @GetMapping("/health")
    public String status() {
        return "User Service is working properly!";
    }

}