/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.helper;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

@NoArgsConstructor
@Component
@Data
public class FileUpload {

    @Value("${images.upload-dir}")
    private String uploadDir;

    /**
     * This method handles uploading a file to a local directory.
     * It checks the file type, generates a new filename with a UUID,
     * and saves the file to the specified directory.
     *
     * @param file The file to be uploaded.
     * @return The relative path of the uploaded file (or null if the file is empty).
     * @throws IOException If an I/O error occurs during the file transfer.
     */
    public String uploadFileToLocalDirectory(MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            String originalFilename = file.getOriginalFilename();

            if (originalFilename == null ||
                    (!originalFilename.endsWith(".jpg")
                            && !originalFilename.endsWith(".png")
                            && !originalFilename.endsWith(".PNG")
                            && !originalFilename.endsWith(".jpeg")
                            && !originalFilename.endsWith(".jfif")
                            && !originalFilename.endsWith(".pdf"))) {
                throw new IllegalArgumentException("Invalid file type. Only JPG, PNG, JPEG, JFIF and PDF are allowed.");
            }

            String newFilename = UUID.randomUUID() + "_" + originalFilename;
            File destinationFile = createDestinationIfDoesNotExist(newFilename);
            file.transferTo(destinationFile);
            return uploadDir + "/" + newFilename;
        }
        return null;
    }

    /**
     * This helper method checks if the destination directory exists and creates it if necessary.
     * It then constructs the full path for the file using the given upload directory and new filename.
     *
     * @param newFilename The new filename for the uploaded file.
     * @return The destination file where the file will be stored.
     */
    private File createDestinationIfDoesNotExist(String newFilename) {
        File uploadPath = new File(uploadDir);
        if (!uploadPath.exists()) {
            boolean created = uploadPath.mkdirs();
            if (!created) {
                System.err.println("Failed to create directories: " + uploadPath.getAbsolutePath());
            }
        }

        return Paths.get(uploadDir, newFilename).toFile();
    }
}
