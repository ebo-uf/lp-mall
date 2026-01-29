package mall.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user-service") // 게이트웨이의 Path 설정과 맞춰야 합니다.
public class controller {

    @GetMapping("/health")
    public String status() {
        return "User Service is working properly!";
    }

    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome to the User Service!";
    }
}