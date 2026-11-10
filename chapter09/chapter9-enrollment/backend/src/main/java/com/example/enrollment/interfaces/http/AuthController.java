package com.example.enrollment.interfaces.http;

import com.example.enrollment.infrastructure.security.CampusPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    @GetMapping("/me")
    public ResponseEntity<IdentityDto> me(@AuthenticationPrincipal CampusPrincipal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(IdentityDto.from(principal));
    }
}
