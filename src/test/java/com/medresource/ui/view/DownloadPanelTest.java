package com.medresource.ui.view;

import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;
import com.medresource.ui.view.DownloadPanel.Choice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del formulario para descargar el reporte, sin ventana.
 */
class DownloadPanelTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);

    private static DownloadPanel panel() {
        return new DownloadPanel(DocumentInfo.draft("Reporte de la simulación", DATE), ExportFormat.PDF);
    }

    @Test
    void showsTheProposedDataAndTheChosenFormat() {
        DownloadPanel panel = panel();

        assertEquals("Reporte de la simulación", panel.titleField().getText());
        assertEquals("Santiago Herrera, Omar Gámez", panel.authorsField().getText());
        assertEquals("Universidad Popular del Cesar", panel.institutionField().getText());
        assertEquals("Ingeniería de Sistemas", panel.programField().getText());
        assertEquals("", panel.courseField().getText());
        assertEquals(ExportFormat.PDF, panel.getFormat());
        assertTrue(panel.formatTile(ExportFormat.PDF).isSelected());
        assertFalse(panel.formatTile(ExportFormat.WORD).isSelected());
    }

    @Test
    void clickingATileChangesTheFormat() {
        DownloadPanel panel = panel();

        panel.formatTile(ExportFormat.WORD).doClick();

        assertEquals(ExportFormat.WORD, panel.getFormat());
        assertTrue(panel.formatTile(ExportFormat.WORD).isSelected());
        assertFalse(panel.formatTile(ExportFormat.PDF).isSelected());
    }

    @Test
    void downloadingGivesTheFormatAndTheCoverWrittenByTheUser() {
        DownloadPanel panel = panel();
        List<Choice> chosen = new ArrayList<>();
        panel.onDownload(chosen::add);
        panel.authorsField().setText("Ana Pérez, Luis Díaz y Marta Ruiz");
        panel.courseField().setText("Programación III");
        panel.teacherField().setText("Docente Uno");

        panel.downloadButton().doClick();

        assertEquals(1, chosen.size());
        DocumentInfo info = chosen.getFirst().info();
        assertEquals(ExportFormat.PDF, chosen.getFirst().format());
        assertEquals(List.of("Ana Pérez", "Luis Díaz", "Marta Ruiz"), info.authors());
        assertEquals("Programación III", info.course());
        assertEquals("Docente Uno", info.teacher());
        assertEquals(DATE, info.date());
        assertEquals("", panel.errorText());
    }

    @Test
    void withoutTitleOrAuthorsItWarnsAndDoesNotDownload() {
        DownloadPanel panel = panel();
        List<Choice> chosen = new ArrayList<>();
        panel.onDownload(chosen::add);

        panel.titleField().setText("  ");
        panel.downloadButton().doClick();
        assertEquals(DownloadPanel.MISSING_DATA, panel.errorText());

        panel.titleField().setText("Reporte");
        panel.authorsField().setText(" , ");
        panel.downloadButton().doClick();

        assertTrue(chosen.isEmpty());
        assertTrue(panel.choice().isEmpty());
    }

    @Test
    void cancelAndTheCrossRunTheCancelAction() {
        DownloadPanel panel = panel();
        int[] cancelled = {0};
        panel.onCancel(() -> cancelled[0]++);

        panel.cancelButton().doClick();
        panel.closeButton().doClick();

        assertEquals(2, cancelled[0]);
    }

    @Test
    void wrongDataIsRejected() {
        DocumentInfo draft = DocumentInfo.draft("Reporte", DATE);

        assertThrows(IllegalArgumentException.class, () -> new DownloadPanel(null, ExportFormat.PDF));
        assertThrows(IllegalArgumentException.class, () -> new DownloadPanel(draft, null));
        assertThrows(IllegalArgumentException.class, () -> panel().setFormat(null));
        assertThrows(IllegalArgumentException.class, () -> panel().onDownload(null));
        assertThrows(IllegalArgumentException.class, () -> panel().onCancel(null));
    }
}
