package pride.bank.erp.appnotification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.user.User;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE + "/notifications")
@RequiredArgsConstructor
public class AppNotificationController {

    private final AppNotificationService service;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<Page<AppNotificationDTO>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(service.getNotifications(user.getId(), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(service.getUnreadCount(user.getId()));
    }

    @PutMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        User user = currentUserService.getCurrentUser();
        service.markRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllRead() {
        User user = currentUserService.getCurrentUser();
        service.markAllRead(user.getId());
        return ResponseEntity.ok().build();
    }
}
