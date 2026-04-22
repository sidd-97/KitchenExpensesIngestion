package com.cloudkitchen.ingestion.parser;

import com.cloudkitchen.ingestion.model.enums.ParsedFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * Chain of Responsibility Pattern:
 * Each inner Handler tries to detect the format and passes to next on failure.
 */
@Slf4j
@Component
public class FormatDetectionChain {

    public ParsedFormat detect(byte[] content, String filename) {
        return new PdfHandler(new ExcelXlsxHandler(new ExcelXlsHandler(new CsvHandler(new UnknownHandler()))))
                .handle(content, filename);
    }

    interface Handler {
        ParsedFormat handle(byte[] content, String filename);
    }

    record PdfHandler(Handler next) implements Handler {
        public ParsedFormat handle(byte[] content, String filename) {
            if (filename.toLowerCase().endsWith(".pdf")) {
                try (PDDocument ignored = Loader.loadPDF(content)) {
                    return ParsedFormat.PDF;
                } catch (Exception ignored) {}
            }
            return next.handle(content, filename);
        }
    }

    record ExcelXlsxHandler(Handler next) implements Handler {
        public ParsedFormat handle(byte[] content, String filename) {
            if (filename.toLowerCase().endsWith(".xlsx")) {
                try {
                    WorkbookFactory.create(new ByteArrayInputStream(content)).close();
                    return ParsedFormat.EXCEL_XLSX;
                } catch (Exception ignored) {}
            }
            return next.handle(content, filename);
        }
    }

    record ExcelXlsHandler(Handler next) implements Handler {
        public ParsedFormat handle(byte[] content, String filename) {
            if (filename.toLowerCase().endsWith(".xls")) {
                return ParsedFormat.EXCEL_XLS;
            }
            return next.handle(content, filename);
        }
    }

    record CsvHandler(Handler next) implements Handler {
        public ParsedFormat handle(byte[] content, String filename) {
            if (filename.toLowerCase().endsWith(".csv")) return ParsedFormat.CSV;
            return next.handle(content, filename);
        }
    }

    record UnknownHandler() implements Handler {
        public ParsedFormat handle(byte[] content, String filename) {
            return ParsedFormat.UNKNOWN;
        }
    }
}