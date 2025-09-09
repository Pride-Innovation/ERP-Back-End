/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.passwordReset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE)
public class PasswordResetController {

    @Autowired
    public PasswordResetService passwordResetService;

    @PostMapping("/request-password-reset")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(@RequestParam String email) {
        Map<String, Object> response = passwordResetService.requestPasswordReset(email);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestParam String token, @RequestParam String password) {
        Map<String, Object> response = passwordResetService.resetPassword(token, password);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
