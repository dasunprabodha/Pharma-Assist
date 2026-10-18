package lk.sliit.prescription.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.sliit.prescription.data.DataLoader;
import lk.sliit.prescription.errors.ConfigError;
import lk.sliit.prescription.errors.InvalidImageError;
import lk.sliit.prescription.history.ConfirmedStore;
import lk.sliit.prescription.history.ImageStore;
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
    @MockitoBean
    private ImageStore imageStore;

    private final MockMultipartFile photo = new MockMultipartFile("file", "rx.jpg", "image/jpeg", new byte[] {1});

    @Test
    void configErrorSwitchesToManualEntry() throws Exception {
        when(scanService.scan(any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenThrow(new ConfigError("AccessDeniedException: no policy"));
        mvc.perform(multipart("/api/scans").file(photo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error_type").value("config_error"))
                .andExpect(jsonPath("$.fallback_to_manual").value(true))
                .andExpect(jsonPath("$.message").value(
                        "AI not configured (AWS credentials or Bedrock model access) – using manual entry"));
    }

    @Test
    void invalidImageAsksForAnotherFile() throws Exception {
        when(scanService.scan(any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenThrow(new InvalidImageError("Only JPG, PNG or WEBP photos are accepted."));
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

    @Test
    void nameSuggestionsReturnOneListPerName() throws Exception {
        when(nameCheck.suggest("Lipitr")).thenReturn(
                java.util.List.of(new NameCheck.Suggestion("S024", "Lipitor", "atorvastatin", "20 mg", false, 92)));
        when(nameCheck.suggest("")).thenReturn(java.util.List.of());
        mvc.perform(post("/api/name-suggestions").contentType(MediaType.APPLICATION_JSON).content("[\"Lipitr\", \"\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0][0].name").value("Lipitor"))
                .andExpect(jsonPath("$[0][0].generic_name").value("atorvastatin"))
                .andExpect(jsonPath("$[0][0].in_stock").value(false))
                .andExpect(jsonPath("$[1]").isEmpty());
    }

    private static final String ITEMS = "[{\"drug_name\":\"Lipitor\",\"strength\":\"20 mg\",\"dose\":null,"
            + "\"frequency\":null,\"duration\":null,\"confidence\":\"high\",\"note\":null}]";

    @Test
    void confirmingAnAiListKeepsItsImage() throws Exception {
        when(imageStore.pageCount(anyString())).thenReturn(1);
        mvc.perform(post("/api/prescriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"ai\",\"scan_id\":\"aaaa1111\",\"corrections\":0,\"items\":" + ITEMS + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString()) // record fields are unwrapped next to image_pages
                .andExpect(jsonPath("$.items[0].drug_name").value("Lipitor"))
                .andExpect(jsonPath("$.image_pages").value(1));
        verify(imageStore).keep(eq("aaaa1111"), anyString());
    }

    @Test
    void manualListNeverKeepsAnImage() throws Exception {
        mvc.perform(post("/api/prescriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"manual\",\"scan_id\":\"aaaa1111\",\"items\":" + ITEMS + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image_pages").value(0));
        verify(imageStore, never()).keep(anyString(), anyString());
    }

    @Test
    void savedImageIsServedWithoutCaching() throws Exception {
        when(imageStore.read("bbbb2222", 1)).thenReturn(java.util.Optional.of(new byte[] {9}));
        mvc.perform(get("/api/prescriptions/bbbb2222/images/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/prescriptions/bbbb2222/images/2")).andExpect(status().isNotFound());
    }

    @Test
    void deleteImageEndpoint() throws Exception {
        mvc.perform(delete("/api/prescriptions/bbbb2222/images")).andExpect(status().isNoContent());
        verify(imageStore).delete("bbbb2222");
    }
}
