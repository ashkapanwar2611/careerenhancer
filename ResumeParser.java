package com.byteforce.careerenhancer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class ResumeParser {

    public String extractText(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt")) {
            return Files.readString(path, StandardCharsets.UTF_8);
        } else if (name.endsWith(".docx")) {
            return readDocx(path);
        } else if (name.endsWith(".pdf")) {
            return readPdf(path);
        }
        throw new IOException("Unsupported file type. Please use PDF, DOCX or TXT.");
    }

    
    private String readDocx(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry("word/document.xml");
            if (entry == null) {
                throw new IOException("Not a valid DOCX file (word/document.xml missing).");
            }
            String xml;
            try (InputStream in = zip.getInputStream(entry)) {
                xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            xml = xml.replace("</w:p>", "\n").replace("<w:tab/>", "\t").replace("<w:br/>", "\n");
            String text = xml.replaceAll("<[^>]+>", "");
            return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                       .replace("&quot;", "\"").replace("&apos;", "'");
        }
    }

    
    private String readPdf(Path path) throws IOException {
        try {
            Class<?> loader = Class.forName("org.apache.pdfbox.Loader");
            Class<?> docClass = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");

            Object doc = loader.getMethod("loadPDF", File.class).invoke(null, path.toFile());
            try {
                Object stripper = stripperClass.getDeclaredConstructor().newInstance();
                Method getText = stripperClass.getMethod("getText", docClass);
                return (String) getText.invoke(stripper, doc);
            } finally {
                ((AutoCloseable) doc).close();
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("PDF support needs the Apache PDFBox library. "
                    + "Run the project with Maven (mvn compile exec:java) or use a .docx/.txt resume.");
        } catch (InvocationTargetException e) {
            throw new IOException("Could not read PDF: " + e.getCause().getMessage(), e.getCause());
        } catch (Exception e) {
            throw new IOException("Could not read PDF: " + e.getMessage(), e);
        }