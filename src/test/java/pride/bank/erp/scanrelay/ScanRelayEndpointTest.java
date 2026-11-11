package pride.bank.erp.scanrelay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The relay through the real filter chain: the PDA's two calls answer without a login, the
 * computer's need one, and a whole pairing - show, scan the code, scan a label, disconnect - works
 * end to end, including the disconnect sent the way the browser sends it (POST + override header).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScanRelayEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;

    @Test
    void thePdaNeedsNoLoginAndAnUnknownCodeIsAPairingEndedNotARefusal() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/scan-relay/connect")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ZZZZZZZZ\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("errorCode").asText()).isEqualTo(ScanRelayController.PAIRING_ENDED);

        MvcResult scan = mockMvc.perform(post("/api/v1/scan-relay/scans")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"nope\",\"code\":\"PBL-1\"}"))
                .andReturn();
        assertThat(scan.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void theComputerSideNeedsASignedInUser() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/scan-pairings")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    @WithMockUser
    void aWholePairingWorksEndToEnd() throws Exception {
        User owner = users.findAll().stream().filter(User::isEnabled).findFirst().orElseThrow();
        String bearer = "Bearer " + jwtService.generateToken(owner);

        JsonNode started = body(mockMvc.perform(post("/api/v1/scan-pairings").header("Authorization", bearer))
                .andReturn());
        String pairingId = started.path("pairingId").asText();
        assertThat(started.path("qrValue").asText()).startsWith(ScanRelayService.PAIR_PREFIX);

        // The PDA: no Authorization header at all.
        JsonNode connected = body(mockMvc.perform(post("/api/v1/scan-relay/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", started.path("qrValue").asText()))))
                .andReturn());
        String token = connected.path("relayToken").asText();
        assertThat(token).isNotBlank();

        JsonNode status = body(mockMvc.perform(get("/api/v1/scan-pairings/" + pairingId).header("Authorization", bearer))
                .andReturn());
        assertThat(status.path("connected").asBoolean()).isTrue();

        MvcResult sent = mockMvc.perform(post("/api/v1/scan-relay/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "code", "PBL-2026-LPT07"))))
                .andReturn();
        assertThat(sent.getResponse().getStatus()).isEqualTo(200);

        MvcResult empty = mockMvc.perform(post("/api/v1/scan-relay/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "code", "  "))))
                .andReturn();
        assertThat(empty.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(empty).path("errorCode").asText()).isEqualTo(ScanRelayController.INVALID_SCAN);

        // Disconnect exactly as the browser sends it behind the firewall.
        MvcResult ended = mockMvc.perform(post("/api/v1/scan-pairings/" + pairingId)
                        .header("Authorization", bearer).header("X-HTTP-Method-Override", "DELETE"))
                .andReturn();
        assertThat(ended.getResponse().getStatus()).isEqualTo(200);

        MvcResult after = mockMvc.perform(post("/api/v1/scan-relay/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token, "code", "PBL-2026-LPT07"))))
                .andReturn();
        assertThat(after.getResponse().getStatus()).as("the PDA learns the pairing is over").isEqualTo(404);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
