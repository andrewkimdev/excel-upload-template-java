package com.foo.core.file;

import lombok.Data;

/**
 * 파일 루트 경로, 상대 디렉터리, 파일명을 함께 보관하는 경로 DTO이다.
 */
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
