package lk.sliit.prescription.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.sliit.prescription.checker.Checker;
import lk.sliit.prescription.data.DataLoader;
import lk.sliit.prescription.image.DrugImages;
import lk.sliit.prescription.namecheck.NameCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Feature 2 end to end through the API, with the real simulated CSVs (no AWS involved). */
@WebMvcTest(CheckController.class)
@Import({Checker.class, DrugImages.class, DataLoader.class, NameCheck.class})
class CheckControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void manualListGetsChecklistWithAlternativeAndWarning() throws Exception {
        mvc.perform(post("/api/check").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items": [
                          {"drug_name": "Lipitor", "strength": "20 mg", "confidence": "high"},
                          {"drug_name": "Klacid", "strength": "500 mg", "confidence": "high"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].status").value("out_of_stock"))
                .andExpect(jsonPath("$.rows[0].alternatives[0].brand_name").value("Atorvastatin (generic)"))
                .andExpect(jsonPath("$.warnings[0].rule_id").value("R008"))
                .andExpect(jsonPath("$.warnings[0].severity").value("moderate"));
    }

    @Test
    void stockMatchesUseTheCheckersMatcher() throws Exception {
        mvc.perform(post("/api/stock-matches").contentType(MediaType.APPLICATION_JSON).content("""
                        [{"drug_name": "Atorvastatin", "strength": "20 mg"}, {"drug_name": "Xyzzol", "strength": null}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].item_id").value("S025"))
                .andExpect(jsonPath("$[0].image_url").value("/api/stock/S025/image"))
                .andExpect(jsonPath("$[1]").isEmpty());
    }

    @Test
    void imageEndpointServesSafeSvg() throws Exception {
        mvc.perform(get("/api/stock/S013/image"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/svg+xml"))
                .andExpect(header().string("X-Image-Kind", "illustration"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'"));
        mvc.perform(get("/api/stock/NOPE/image")).andExpect(status().isNotFound());
    }
}
