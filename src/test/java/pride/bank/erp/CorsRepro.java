package pride.bank.erp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class CorsRepro {

    @Autowired MockMvc mockMvc;

    @Value("${front-end.environment}")
    String origin;

    @Test
    @WithMockUser(authorities = "UPDATE_ASSET")
    void overriddenPutFromABrowser() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/assets/999999999")
                .header("Origin", origin)
                .header("X-HTTP-Method-Override", "PUT")
                .contentType("application/json").content("{}")).andReturn();
        System.out.println(">>> ORIGIN=" + origin);
        System.out.println(">>> actual overridden POST -> status " + r.getResponse().getStatus()
                + " | body: " + r.getResponse().getContentAsString().replace("\n", " "));
    }

    @Test
    void preflightForPost() throws Exception {
        MvcResult r = mockMvc.perform(options("/api/v1/assets/999999999")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,x-http-method-override,x-csrf-token"))
                .andReturn();
        System.out.println(">>> preflight(POST) -> status " + r.getResponse().getStatus()
                + " | allow-methods: " + r.getResponse().getHeader("Access-Control-Allow-Methods")
                + " | allow-headers: " + r.getResponse().getHeader("Access-Control-Allow-Headers"));
    }
}
