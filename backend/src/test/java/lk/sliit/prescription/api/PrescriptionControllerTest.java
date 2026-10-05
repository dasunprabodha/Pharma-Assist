package lk.sliit.prescription.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.sliit.prescription.data.DataLoader;
import lk.sliit.prescription.errors.ConfigError;
import lk.sliit.prescription.errors.InvalidImageError;
import lk.sliit.prescription.history.ConfirmedStore;
import lk.sliit.prescription.namecheck.NameCheck;
import lk.sliit.prescription.service.DashboardService;
import lk.sliit.prescription.service.ScanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Error/fallback behaviour of the API (TC3-style), with the services mocked. */
@WebMvcTest(PrescriptionController.class)
class PrescriptionControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ScanService scanService;
    @MockitoBean
    private NameCheck nameCheck;
    @MockitoBean
    private ConfirmedStore confirmedStore;
    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private DataLoader dataLoader;

    private final MockMultipartFile photo = new MockMultipartFile("file", "rx.jpg", "image/jpeg", new byte[] {1});

    @Test
    void configErrorSwitchesToManualEntry() throws Exception {
        when(scanService.scan(any(), any())).thenThrow(new ConfigError("AccessDeniedException: no policy"));
        mvc.perform(multipart("/api/scans").file(photo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error_type").value("config_error"))
                .andExpect(jsonPath("$.fallback_to_manual").value(true))
                .andExpect(jsonPath("$.message").value(
                        "AI not configured (AWS credentials or Bedrock model access) – using manual entry"));
    }

    @Test
    void invalidImageAsksForAnotherFile() throws Exception {
        when(scanService.scan(any(), any())).thenThrow(new InvalidImageError("Only JPG, PNG or WEBP photos are accepted."));
        mvc.perform(multipart("/api/scans").file(photo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fallback_to_manual").value(false))
                .andExpect(jsonPath("$.message").value("Only JPG, PNG or WEBP photos are accepted."));
    }

    @Test
    void previewRendersImagePagesWithoutCallingAi() throws Exception {
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        mvc.perform(multipart("/api/preview").file(new MockMultipartFile("file", "rx.png", "image/png", png.toByteArray())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages.length()").value(1))
                .andExpect(jsonPath("$.pages[0]").value(org.hamcrest.Matchers.startsWith("data:image/jpeg;base64,")));
        org.mockito.Mockito.verifyNoInteractions(scanService);
    }

    @Test
    void confirmRejectsRowsWithoutDrugName() throws Exception {
        mvc.perform(post("/api/prescriptions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"source": "manual", "items": [
                          {"drug_name": "Panadol", "strength": "500 mg", "confidence": "high"},
                          {"drug_name": "", "strength": "20 mg", "confidence": "high"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rows[0]").value(2));
    }

    @Test
    void confirmSavesValidManualList() throws Exception {
        mvc.perform(post("/api/prescriptions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"source": "manual", "items": [
                          {"drug_name": "Panadol", "strength": "500 mg", "dose": "1 tablet",
                           "frequency": "three times daily (tds)", "duration": "3 days", "confidence": "high"}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("manual"))
                .andExpect(jsonPath("$.items[0].drug_name").value("Panadol"));
    }
}
