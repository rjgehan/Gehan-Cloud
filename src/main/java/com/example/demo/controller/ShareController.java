package com.example.demo.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api")
public class ShareController {
    record SharePayload(String url, String source, String title) {}

    private volatile String latestUrl;  // simple in-memory storage

    @PostMapping("/share")
    public ResponseEntity<Void> receive(@RequestBody SharePayload p) {
        System.out.println("Shared: " + p.url() + " (" + p.title() + ")");
        latestUrl = p.url();
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/share/latest")
    public ResponseEntity<String> latest() {
        if (latestUrl == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latestUrl);
    }
}
