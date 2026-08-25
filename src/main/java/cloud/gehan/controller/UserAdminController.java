package cloud.gehan.controller;

import cloud.gehan.security.LocalNetwork;
import cloud.gehan.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin-only user management. Access is restricted to ROLE_ADMIN in SecurityConfig. */
@Controller
@RequestMapping("/users")
public class UserAdminController {

    private final UserService userService;
    private final LocalNetwork localNetwork;

    public UserAdminController(UserService userService, LocalNetwork localNetwork) {
        this.userService = userService;
        this.localNetwork = localNetwork;
    }

    @GetMapping
    public String page(Model model, Authentication authentication, HttpServletRequest request) {
        model.addAttribute("users", userService.list());
        model.addAttribute("currentUsername", authentication.getName());
        // Shown so portal.trusted-networks can be set from what the app actually sees,
        // rather than from a guess about how the proxy and NAT rewrite things.
        model.addAttribute("clientIp", request.getRemoteAddr());
        model.addAttribute("clientOnLan", localNetwork.includes(request.getRemoteAddr()));
        return "users";
    }

    @PostMapping
    public String create(@RequestParam String username,
                         @RequestParam(defaultValue = "USER") String role,
                         RedirectAttributes redirect) {
        try {
            userService.create(username, role);
            redirect.addFlashAttribute("message",
                    "Created \"" + username.trim() + "\". They set their password on first login.");
        } catch (UserService.RejectedException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            String name = userService.delete(id, authentication.getName());
            redirect.addFlashAttribute("message", "Deleted \"" + name + "\".");
        } catch (UserService.RejectedException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/reset")
    public String reset(@PathVariable long id, RedirectAttributes redirect) {
        try {
            String name = userService.resetPassword(id);
            redirect.addFlashAttribute("message",
                    "Reset \"" + name + "\". Their next login sets a new password.");
        } catch (UserService.RejectedException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable long id,
                             @RequestParam String role,
                             Authentication authentication,
                             RedirectAttributes redirect) {
        try {
            String name = userService.changeRole(id, role, authentication.getName());
            redirect.addFlashAttribute("message", "\"" + name + "\" is now " + role + ".");
        } catch (UserService.RejectedException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }
}
