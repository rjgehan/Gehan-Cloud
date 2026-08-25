package com.example.demo.controller;

import com.example.demo.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** Serves the portal, telling the template whether to show the admin-only tiles. */
    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + UserService.ROLE_ADMIN)::equals);
        model.addAttribute("isAdmin", admin);
        return "index";
    }
}
