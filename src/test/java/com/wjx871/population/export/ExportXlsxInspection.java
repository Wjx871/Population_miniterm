package com.wjx871.population.export;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.w3c.dom.Element;

/** Inspect the original ZIP and independently reopen with POI. Does not evaluate formulas. */
final class ExportXlsxInspection {
    record CellEvidence(String type, String text, String xml) {}
    record Inspection(String sha256, long poiFormulaCells, long xmlFormulaElements,
                      long externalLinkParts, long externalRelationships,
                      Map<String, CellEvidence> cells, Map<String, String> xmlParts) {}

    static Inspection inspect(Path file) throws Exception {
        String sha = sha256(file);
        Map<String, String> parts = new LinkedHashMap<>();
        Map<String, String> cellXml = new LinkedHashMap<>();
        long formulas = 0, externalLinks = 0, externalRelationships = 0;
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("xl/externalLinks/")) externalLinks++;
                if (!name.endsWith(".xml") && !name.endsWith(".rels")) continue;
                byte[] bytes;
                try (var input = zip.getInputStream(entry)) { bytes = input.readAllBytes(); }
                parts.put(name, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
                if (name.startsWith("xl/worksheets/")) {
                    formulas += document.getElementsByTagNameNS("*", "f").getLength();
                    var cells = document.getElementsByTagNameNS("*", "c");
                    for (int i = 0; i < cells.getLength(); i++) {
                        var cell = (Element) cells.item(i);
                        // sheet1 is the single production sheet; keep original cell markup.
                        if (name.equals("xl/worksheets/sheet1.xml")) {
                            var transformer = TransformerFactory.newInstance().newTransformer();
                            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                            var output = new StringWriter();
                            transformer.transform(new DOMSource(cell), new StreamResult(output));
                            cellXml.put(cell.getAttribute("r"), output.toString());
                        }
                    }
                }
                var relationships = document.getElementsByTagNameNS("*", "Relationship");
                for (int i = 0; i < relationships.getLength(); i++) {
                    if ("External".equals(((Element) relationships.item(i)).getAttribute("TargetMode"))) {
                        externalRelationships++;
                    }
                }
            }
        }
        Map<String, CellEvidence> cells = new LinkedHashMap<>();
        long poiFormulas = 0;
        // POI's default File overload opens the OPC package read/write and can rewrite it on close.
        try (var workbook = WorkbookFactory.create(file.toFile(), null, true)) {
            for (var sheet : workbook) for (var row : sheet) for (var cell : row) {
                if (cell.getCellType() == CellType.FORMULA) poiFormulas++;
                String value = cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : cell.toString();
                cells.put(cell.getAddress().formatAsString(), new CellEvidence(
                        cell.getCellType().name(), value, cellXml.get(cell.getAddress().formatAsString())));
            }
        }
        if (!sha.equals(sha256(file))) throw new java.io.IOException("Inspection changed original XLSX bytes");
        return new Inspection(sha, poiFormulas, formulas, externalLinks, externalRelationships, cells, parts);
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private ExportXlsxInspection() {}
}
