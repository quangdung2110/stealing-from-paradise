package com.flashsale.orderservice.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class ReturnToSenderRequest {

    // Ảnh bằng chứng (1-5 ảnh), nhận qua multipart/form-data
    private List<MultipartFile> evidenceImages;

    private String returnTrackingNumber;

    private String note;
}
