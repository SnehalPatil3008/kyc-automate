package com.kycautomate.document.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class StorageService {

    private final Path storageDir;

    public StorageService(@Value("${kyc.storage.directory:./kyc-storage}") String storageDirectory) throws IOException {
        storageDir = Path.of(storageDirectory).toAbsolutePath().normalize();
        Files.createDirectories(storageDir);
    }

    public String store(byte[] bytes, String extension) throws IOException {
        String fileName = UUID.randomUUID() + (extension.startsWith(".") ? extension : "." + extension);
        Path target = storageDir.resolve(fileName).normalize();
        if (!target.startsWith(storageDir)) throw new IOException("Invalid storage path.");
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        return target.toAbsolutePath().toString();
    }

    public byte[] load(String pathStr) throws IOException {
        Path target = Path.of(pathStr).toAbsolutePath().normalize();
        if (!target.startsWith(storageDir)) throw new IOException("Document path is outside configured storage.");
        return Files.readAllBytes(target);
    }
}
