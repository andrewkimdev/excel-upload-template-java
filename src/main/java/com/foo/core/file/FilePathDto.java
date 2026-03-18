package com.foo.core.file;
import lombok.Data;

@Data
public class FilePathDto {
    private String rootPath;
    private String filename;
    private String relativeDirectoryPath;

    public FilePathDto(String rootPath, String relativeDirectoryPath, String filename) {
        this.rootPath = rootPath.replaceAll("\\\\", "/");
        this.relativeDirectoryPath = relativeDirectoryPath.replaceAll("\\\\", "/");
        this.filename = filename;
    }

    public String getFilename() { return filename; }

    public String getRootPath() { return rootPath; }

    public String getRelativeDirectoryPath() { return relativeDirectoryPath; }

    public String getAbsoluteDirectoryPath() { return rootPath + relativeDirectoryPath; }
}