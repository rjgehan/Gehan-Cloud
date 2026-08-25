package cloud.gehan.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    record SharePayload(String url, String source, String title) {}

    private volatile String latestUrl;  // simple in-memory storage

    @PostMapping("/share")
    public ResponseEntity<Void> receive(@RequestBody SharePayload p) {
        log.info("Received share: {} ({})", p.url(), p.title());
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
